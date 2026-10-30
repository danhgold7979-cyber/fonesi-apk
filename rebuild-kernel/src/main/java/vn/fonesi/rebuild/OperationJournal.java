package vn.fonesi.rebuild;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static vn.fonesi.rebuild.OperationProtocol.*;

/** Private-filesystem corruption protection and cooperating-process CAS, not hostile-write authentication. */
public final class OperationJournal {
    private static final int MAGIC = 0x464f4e32, MAX_BYTES = 256 * 1024, MAX_PEERS = 4096;
    private final Path root;
    private final Object rootIdentity;
    enum PublishPoint { BEFORE_RENAME, AFTER_RENAME }
    @FunctionalInterface interface FailureProbe { void observe(PublishPoint point) throws IOException; }
    private final FailureProbe failureProbe;
    public OperationJournal(Path privateRoot) throws IOException { this(privateRoot, point -> {}); }
    // Package-private test fault seam, never selected by persisted input or external caller.
    OperationJournal(Path privateRoot, FailureProbe failureProbe) throws IOException {
        this.failureProbe = Objects.requireNonNull(failureProbe);
        root = privateRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, NOFOLLOW_LINKS) || !root.toRealPath().equals(root))
            throw new IOException("Journal requires existing non-symlink private root");
        rootIdentity = Files.readAttributes(root, BasicFileAttributes.class, NOFOLLOW_LINKS).fileKey();
        if (rootIdentity == null) throw new IOException("Stable directory identity unavailable");
    }
    public Operation read(UUID operation) throws IOException {
        try (Lease lease = acquire()) {
            lease.requireValid(); return readLocked(operation); }
    }
    public void create(Operation initial) throws IOException {
        if (initial.state() != State.PREPARED || initial.revision() != 0)
            throw new IllegalArgumentException("Initial state must be PREPARED revision zero");
        try (Lease lease = acquire()) {
            lease.requireValid();
            Path target = path(initial.identity().operation());
            if (Files.exists(target, NOFOLLOW_LINKS)) throw new IOException("Operation already exists");
            publish(initial);
        }
    }
    public void compareAndSet(Operation expected, Operation replacement) throws IOException {
        validateTransition(expected, replacement);
        try (Lease lease = acquire()) {
            lease.requireValid();
            if (!readLocked(expected.identity().operation()).equals(expected)) throw new IOException("Stale operation revision or identity");
            publish(replacement);
        }
    }
    private void validateTransition(Operation from, Operation to) {
        if (!from.identity().equals(to.identity()) || to.revision() != Math.incrementExact(from.revision()))
            throw new IllegalArgumentException("Identity change or invalid revision");
        boolean legal = switch (from.state()) {
            case PREPARED -> to.state() == State.DISPATCHED || to.state() == State.CANCELLED;
            case DISPATCHED -> to.state() == State.VERIFIED || to.state() == State.REVIEW_REQUIRED;
            default -> false;
        };
        if (!legal) throw new IllegalArgumentException("Forbidden durable transition");
    }
    private Operation readLocked(UUID id) throws IOException {
        Path target = path(id);
        if (!Files.isRegularFile(target, NOFOLLOW_LINKS)) throw new IOException("Missing or unsafe journal");
        byte[] bytes;
        try (FileChannel file = FileChannel.open(target, READ, NOFOLLOW_LINKS)) {
            long size = file.size();
            if (size < 36 || size > MAX_BYTES) throw new IOException("Journal length invalid");
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining()) if (file.read(buffer) < 0) throw new EOFException("Truncated journal");
            if (file.size() != size) throw new IOException("Journal changed while reading");
            bytes = buffer.array();
        }
        Operation result = decode(bytes);
        if (!result.identity().operation().equals(id)) throw new IOException("Journal filename identity mismatch");
        return result;
    }
    private void publish(Operation operation) throws IOException {
        byte[] bytes = encode(operation);
        Path target = path(operation.identity().operation());
        if (Files.exists(target, NOFOLLOW_LINKS) && !Files.isRegularFile(target, NOFOLLOW_LINKS))
            throw new IOException("Unsafe journal target");
        Path staging = root.resolve(".pending-" + UUID.randomUUID());
        try {
            try (FileChannel file = FileChannel.open(staging, CREATE_NEW, WRITE, NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) file.write(buffer);
                file.force(true);
            }
            failureProbe.observe(PublishPoint.BEFORE_RENAME);
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            failureProbe.observe(PublishPoint.AFTER_RENAME);
            // A failure here means commit outcome is uncertain. Caller must never dispatch after uncertainty; reread is diagnosis only.
            try (FileChannel directory = FileChannel.open(root, READ, NOFOLLOW_LINKS)) { directory.force(true); }
        } finally {
            // Only this call's randomly named staging entry, never journal or account data.
            Files.deleteIfExists(staging);
        }
    }
    private Path path(UUID id) { return root.resolve(Objects.requireNonNull(id) + ".journal"); }
    private Lease acquire() throws IOException {
        if (!Files.isDirectory(root, NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) throw new IOException("Journal root changed");
        if (!rootIdentity.equals(Files.readAttributes(root, BasicFileAttributes.class, NOFOLLOW_LINKS).fileKey()))
            throw new IOException("Journal directory replaced");
        Path lockPath = root.resolve(".operation.lock");
        if (Files.exists(lockPath, NOFOLLOW_LINKS) && !Files.isRegularFile(lockPath, NOFOLLOW_LINKS))
            throw new IOException("Unsafe lock target");
        FileChannel channel = FileChannel.open(lockPath, CREATE, WRITE, NOFOLLOW_LINKS);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("Journal busy");
            return new Lease(channel, lock);
        } catch (IOException | OverlappingFileLockException e) {
            channel.close(); throw new IOException("Journal lock unavailable", e);
        }
    }
    private record Lease(FileChannel channel, FileLock lock) implements AutoCloseable {
        void requireValid() throws IOException { if (!lock.isValid()) throw new IOException("Journal lock lost"); }
        @Override public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
    }
    private static byte[] encode(Operation operation) throws IOException {
        Identity id = operation.identity();
        if (id.affectedClones().size() > MAX_PEERS) throw new IOException("Journal peer budget exceeded");
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(payload)) {
            out.writeInt(MAGIC); out.writeInt(1);
            writeUuid(out, id.operation()); out.writeUTF(id.provider().id()); out.writeUTF(id.provider().revision());
            writeUuid(out, id.cloneId()); out.writeUTF(id.clusterSha256());
            List<UUID> peers = id.affectedClones().stream().sorted().toList();
            out.writeInt(peers.size()); for (UUID peer : peers) writeUuid(out, peer);
            out.writeUTF(operation.state().name()); out.writeLong(operation.revision());
        }
        byte[] body = payload.toByteArray();
        if (body.length + 32 > MAX_BYTES) throw new IOException("Journal byte budget exceeded");
        payload.writeBytes(digest(body)); return payload.toByteArray();
    }
    private static Operation decode(byte[] bytes) throws IOException {
        byte[] body = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(digest(body), Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length)))
            throw new IOException("Journal checksum mismatch");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != MAGIC || in.readInt() != 1) throw new IOException("Unknown journal format");
            UUID operation = readUuid(in);
            CapabilityPolicy.Provider provider = new CapabilityPolicy.Provider(in.readUTF(), in.readUTF());
            UUID clone = readUuid(in); String cluster = in.readUTF();
            int count = in.readInt();
            if (count < 1 || count > MAX_PEERS) throw new IOException("Invalid peer count");
            Set<UUID> peers = new HashSet<>();
            for (int n = 0; n < count; n++) if (!peers.add(readUuid(in))) throw new IOException("Duplicate peer");
            State state = State.valueOf(in.readUTF()); long revision = in.readLong();
            if (in.available() != 0) throw new IOException("Trailing journal bytes");
            return new Operation(new Identity(operation, provider, clone, cluster, peers), state, revision);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid journal value", e); }
    }
    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
