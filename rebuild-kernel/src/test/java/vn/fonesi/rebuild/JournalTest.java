package vn.fonesi.rebuild;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
import static vn.fonesi.rebuild.OperationProtocol.*;

public final class JournalTest {
    private static int checks;
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static final CapabilityPolicy.Provider PROVIDER = new CapabilityPolicy.Provider("test", "revision");
    private static Operation initial() {
        UUID id = UUID.randomUUID();
        return Operation.prepare(new Identity(id, PROVIDER, "example.app", "api34-arm64-4k", CapabilityPolicy.Scope.SYNTHETIC, id, "a".repeat(64), Set.of(id)));
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("hold-lock")) {
            try (FileChannel channel = FileChannel.open(Path.of(args[1]), WRITE);
                 FileLock lock = channel.lock()) {
                if (!lock.isValid()) throw new AssertionError("Lock unavailable");
                System.out.println("LOCKED"); System.out.flush();
                Thread.sleep(30_000);
            }
            return;
        }
        Path root = Files.createTempDirectory("fonesi-rebuild-journal-");
        OperationJournal journal = new OperationJournal(root);
        Operation first = initial();
        journal.create(first);
        check(journal.read(first.identity().operation()).equals(first));
        check(new OperationJournal(root).read(first.identity().operation()).equals(first));
        rejects(() -> journal.create(first));
        rejects(() -> journal.create(first.dispatch(0, first.identity())));
        rejects(() -> journal.read(UUID.randomUUID()));
        Operation dispatched = first.dispatch(0, first.identity());
        journal.compareAndSet(first, dispatched);
        check(journal.read(first.identity().operation()).equals(dispatched));
        rejects(() -> journal.compareAndSet(first, first.cancel(0)));
        rejects(() -> journal.compareAndSet(dispatched, new Operation(first.identity(), State.CANCELLED, 2)));
        rejects(() -> journal.compareAndSet(dispatched, new Operation(first.identity(), State.DISPATCHED, 2)));
        Operation recovered = new OperationJournal(root).read(first.identity().operation()).recover();
        journal.compareAndSet(dispatched, recovered);
        check(journal.read(first.identity().operation()).state() == State.REVIEW_REQUIRED);
        rejects(() -> journal.compareAndSet(recovered, new Operation(first.identity(), State.DISPATCHED, 3)));
        Operation second = initial(); journal.create(second);
        rejects(() -> journal.compareAndSet(second, new Operation(second.identity(), State.VERIFIED, 1)));
        rejects(() -> journal.compareAndSet(second, new Operation(first.identity(), State.CANCELLED, 1)));
        rejects(() -> journal.compareAndSet(second, new Operation(second.identity(), State.CANCELLED, 2)));
        Operation cancelled = second.cancel(0); journal.compareAndSet(second, cancelled);
        check(journal.read(second.identity().operation()).equals(cancelled));
        rejects(() -> journal.compareAndSet(cancelled, new Operation(second.identity(), State.PREPARED, 2)));
        Operation third = initial(); journal.create(third);
        Path data = root.resolve(third.identity().operation() + ".journal");
        byte[] original = Files.readAllBytes(data);
        byte[] corrupt = original.clone(); corrupt[12] ^= 1; Files.write(data, corrupt);
        rejects(() -> journal.read(third.identity().operation()));
        Files.write(data, Arrays.copyOf(original, 20)); rejects(() -> journal.read(third.identity().operation()));
        Files.write(data, Arrays.copyOf(original, original.length + 1)); rejects(() -> journal.read(third.identity().operation()));
        Files.write(data, new byte[256 * 1024 + 1]); rejects(() -> journal.read(third.identity().operation()));
        Files.write(data, original); check(journal.read(third.identity().operation()).equals(third));
        UUID wrongName = UUID.randomUUID(); Files.write(root.resolve(wrongName + ".journal"), original);
        rejects(() -> journal.read(wrongName));
        Path outside = Files.createTempFile("fonesi-journal-outside-", ".marker"); Files.writeString(outside, "untouched");
        Files.delete(data); Files.createSymbolicLink(data, outside);
        rejects(() -> journal.read(third.identity().operation()));
        rejects(() -> journal.compareAndSet(third, third.cancel(0)));
        check(Files.readString(outside).equals("untouched"));
        Path linkRoot = root.resolveSibling(root.getFileName() + "-link"); Files.createSymbolicLink(linkRoot, root);
        rejects(() -> new OperationJournal(linkRoot));
        Path otherRoot = Files.createTempDirectory("fonesi-journal-lock-link-");
        Files.createSymbolicLink(otherRoot.resolve(".operation.lock"), outside);
        rejects(() -> new OperationJournal(otherRoot).create(initial()));
        check(Files.readString(outside).equals("untouched"));
        try (FileChannel channel = FileChannel.open(root.resolve(".operation.lock"), WRITE);
             FileLock lock = channel.lock()) {
            check(lock.isValid()); rejects(() -> journal.read(first.identity().operation()));
        }
        check(journal.read(first.identity().operation()).equals(recovered));
        rejects(() -> new OperationJournal(root.resolve("missing")));
        try (var entries = Files.list(root)) { check(entries.noneMatch(p -> p.getFileName().toString().startsWith(".pending-"))); }
        Operation faultInitial = initial(); journal.create(faultInitial);
        Operation faultDispatched = faultInitial.dispatch(0, faultInitial.identity());
        OperationJournal beforeFault = new OperationJournal(root, point -> {
            if (point == OperationJournal.PublishPoint.BEFORE_RENAME) throw new IOException("injected before rename");
        });
        rejects(() -> beforeFault.compareAndSet(faultInitial, faultDispatched));
        check(journal.read(faultInitial.identity().operation()).equals(faultInitial));
        OperationJournal afterFault = new OperationJournal(root, point -> {
            if (point == OperationJournal.PublishPoint.AFTER_RENAME) throw new IOException("injected after rename");
        });
        rejects(() -> afterFault.compareAndSet(faultInitial, faultDispatched));
        check(journal.read(faultInitial.identity().operation()).equals(faultDispatched));
        check(new OperationJournal(root).read(faultInitial.identity().operation()).recover().state() == State.REVIEW_REQUIRED);
        rejects(() -> journal.compareAndSet(faultInitial, faultDispatched));
        Operation uncertain = journal.read(faultInitial.identity().operation());
        journal.compareAndSet(uncertain, uncertain.recover());
        check(journal.read(faultInitial.identity().operation()).state() == State.REVIEW_REQUIRED);
        Path directoryLockRoot = Files.createTempDirectory("fonesi-journal-directory-lock-");
        Files.createDirectory(directoryLockRoot.resolve(".operation.lock"));
        rejects(() -> new OperationJournal(directoryLockRoot).create(initial()));
        Path fifoRoot = Files.createTempDirectory("fonesi-journal-fifo-lock-");
        Process fifo = new ProcessBuilder("mkfifo", fifoRoot.resolve(".operation.lock").toString()).start();
        check(fifo.waitFor(10, TimeUnit.SECONDS) && fifo.exitValue() == 0);
        rejects(() -> new OperationJournal(fifoRoot).create(initial()));
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), JournalTest.class.getName(),
            "hold-lock", root.resolve(".operation.lock").toString()).redirectErrorStream(true).start();
        try (BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream()))) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!output.ready() && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
            check(output.ready()); check("LOCKED".equals(output.readLine()));
            rejects(() -> journal.read(first.identity().operation()));
        } finally {
            child.destroyForcibly(); check(child.waitFor(10, TimeUnit.SECONDS));
        }
        check(journal.read(first.identity().operation()).equals(recovered));
        Files.delete(data); // Remove only the test-owned symlink before malformed fixtures.
        for (byte[] malformed : List.of(
            fixture(third, 0, 2, "test", "PREPARED", 0, "a".repeat(64), false, false),
            fixture(third, 0x464f4e32, 1, "test", "PREPARED", 0, "a".repeat(64), false, false),
            fixture(third, 0x464f4e32, 3, "test", "PREPARED", 0, "a".repeat(64), false, false),
            fixture(third, 0x464f4e32, 2, " ", "PREPARED", 0, "a".repeat(64), false, false),
            fixture(third, 0x464f4e32, 2, "test", "UNKNOWN", 0, "a".repeat(64), false, false),
            fixture(third, 0x464f4e32, 2, "test", "VERIFIED", 0, "a".repeat(64), false, false),
            fixture(third, 0x464f4e32, 2, "test", "PREPARED", 99, "a".repeat(64), false, false),
            fixture(third, 0x464f4e32, 2, "test", "PREPARED", 0, "short", false, false),
            fixture(third, 0x464f4e32, 2, "test", "PREPARED", 0, "a".repeat(64), true, false),
            fixture(third, 0x464f4e32, 2, "test", "PREPARED", 0, "a".repeat(64), false, true))) {
            Files.write(data, malformed); rejects(() -> journal.read(third.identity().operation()));
        }
        Path replaceRoot = Files.createTempDirectory("fonesi-journal-root-pin-");
        OperationJournal pinned = new OperationJournal(replaceRoot);
        Files.move(replaceRoot, replaceRoot.resolveSibling(replaceRoot.getFileName() + "-old"));
        Files.createDirectory(replaceRoot); rejects(() -> pinned.create(initial()));
        Path leftover = root.resolve(".pending-crashed-test"); Files.writeString(leftover, "unowned staging");
        check(journal.read(first.identity().operation()).equals(recovered));
        check(Files.readString(leftover).equals("unowned staging"));
        if (checks < 30) throw new AssertionError("Incomplete journal suite");
        System.out.println("PASS " + checks + " filesystem journal checks; Linux host only; no guest data");
    }
    private static byte[] fixture(Operation operation, int magic, int version, String provider, String state,
                                  long revision, String digest, boolean duplicate, boolean trailing) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(magic); out.writeInt(version); uuid(out, operation.identity().operation());
            out.writeUTF(provider); out.writeUTF("revision"); out.writeUTF(operation.identity().packageName());
            out.writeUTF(operation.identity().environment()); out.writeUTF(operation.identity().scope().name());
            uuid(out, operation.identity().cloneId()); out.writeUTF(digest);
            out.writeInt(duplicate ? 2 : 1); uuid(out, operation.identity().cloneId());
            if (duplicate) uuid(out, operation.identity().cloneId());
            out.writeUTF(state); out.writeLong(revision); if (trailing) out.writeByte(1);
        }
        bytes.writeBytes(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())); return bytes.toByteArray();
    }
    private static void uuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }
    private static void check(boolean result) { checks++; if (!result) throw new AssertionError("Check " + checks); }
    private static void rejects(Checked action) throws Exception {
        checks++;
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection " + checks);
    }
}
