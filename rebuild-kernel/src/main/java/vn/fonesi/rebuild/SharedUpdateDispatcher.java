package vn.fonesi.rebuild;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static vn.fonesi.rebuild.OperationProtocol.*;
import static vn.fonesi.rebuild.CapabilityPolicy.*;

/** Trusted-host orchestration only. Callbacks must be independently implemented and verified. */
public final class SharedUpdateDispatcher {
    public record Request(String packageName, Identity identity, String environment, Scope scope) {
        public Request {
            Objects.requireNonNull(identity); Objects.requireNonNull(scope);
            if (packageName == null || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || packageName.length() > 255)
                throw new IllegalArgumentException("Invalid package identity");
            if (!identity.packageName().equals(packageName) || !identity.environment().equals(environment) || identity.scope() != scope)
                throw new IllegalArgumentException("Request differs from durable identity");
        }
    }
    public record PeerState(Provider provider, String packageName, String installedClusterSha256,
                            boolean ready, boolean stopped) {
        public PeerState {
            Objects.requireNonNull(provider); Objects.requireNonNull(packageName);
            if (installedClusterSha256 == null || !installedClusterSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid peer cluster");
        }
    }
    public record Preflight(Identity consent, boolean unlockedForeground, boolean frozenSourceVerified, boolean completePackageEnumeration,
                            Map<UUID, PeerState> peers, Evidence evidence) {
        public Preflight { Objects.requireNonNull(consent); peers = Map.copyOf(peers); }
    }
    @FunctionalInterface public interface Probe { Preflight inspect(Request request) throws Exception; }
    @FunctionalInterface public interface Mutation { void apply(Request request) throws Exception; }
    @FunctionalInterface public interface Inspector { Readback inspect(Request request, long dispatchRevision) throws Exception; }
    public enum Result { VERIFIED, PREFLIGHT_REJECTED, BUSY, DUPLICATE, SCOPE_REVIEW_REQUIRED, REVIEW_REQUIRED, STORAGE_UNCERTAIN }
    private final Path root;
    private final Object rootIdentity;
    private final OperationJournal.FailureProbe failureProbe;
    public SharedUpdateDispatcher(Path trustedRoot) throws IOException { this(trustedRoot, point -> {}); }
    SharedUpdateDispatcher(Path trustedRoot, OperationJournal.FailureProbe failureProbe) throws IOException {
        this.failureProbe = Objects.requireNonNull(failureProbe);
        root = trustedRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) throw new IOException("Unsafe dispatcher root");
        rootIdentity = Files.readAttributes(root, BasicFileAttributes.class, NOFOLLOW_LINKS).fileKey();
        if (rootIdentity == null) throw new IOException("Directory identity unavailable");
    }
    public Result execute(Request request, Probe probe, Mutation mutation, Inspector inspector) throws IOException {
        Objects.requireNonNull(request); Objects.requireNonNull(probe); Objects.requireNonNull(mutation); Objects.requireNonNull(inspector);
        Path directory = scopeDirectory(request);
        Path lockPath = directory.resolve(".dispatch.lock");
        if (Files.exists(lockPath, NOFOLLOW_LINKS) && !Files.isRegularFile(lockPath, NOFOLLOW_LINKS)) throw new IOException("Unsafe execution lease");
        try (FileChannel channel = FileChannel.open(lockPath, CREATE, WRITE, NOFOLLOW_LINKS)) {
            FileLock acquired;
            try { acquired = channel.tryLock(); } catch (OverlappingFileLockException busy) { return Result.BUSY; }
            if (acquired == null) return Result.BUSY;
            try (FileLock lease = acquired) {
                if (!lease.isValid()) return Result.BUSY;
                OperationJournal journal = new OperationJournal(root, failureProbe);
                boolean[] history = new boolean[2];
                journal.inspect(op -> {
                    if (op.identity().operation().equals(request.identity().operation())) history[0] = true;
                    if (op.identity().provider().id().equals(request.identity().provider().id()) &&
                        op.identity().packageName().equals(request.packageName()) &&
                        (op.state() == State.DISPATCHED || op.state() == State.REVIEW_REQUIRED)) history[1] = true;
                });
                if (history[0]) return Result.DUPLICATE;
                if (history[1]) return Result.SCOPE_REVIEW_REQUIRED;
                Preflight initial;
                try { initial = probe.inspect(request); validate(request, initial); }
                catch (Exception rejected) { return Result.PREFLIGHT_REJECTED; }
                Operation prepared = Operation.prepare(request.identity());
                try { journal.create(prepared); } catch (IOException uncertain) { return Result.STORAGE_UNCERTAIN; }
                try { validateStable(request, initial, probe.inspect(request)); }
                catch (Exception rejected) {
                    try { journal.compareAndSet(prepared, prepared.cancel(0)); }
                    catch (IOException uncertain) { return Result.STORAGE_UNCERTAIN; }
                    return Result.PREFLIGHT_REJECTED;
                }
                Operation dispatched = prepared.dispatch(0, request.identity());
                try { journal.compareAndSet(prepared, dispatched); }
                catch (IOException uncertain) { return Result.STORAGE_UNCERTAIN; } // Never invoke mutation after uncertain persistence.
                try {
                    validateStable(request, initial, probe.inspect(request));
                    mutation.apply(request);
                    Operation observed = dispatched.verify(dispatched.revision(), inspector.inspect(request, dispatched.revision()));
                    journal.compareAndSet(dispatched, observed);
                    return observed.state() == State.VERIFIED ? Result.VERIFIED : Result.REVIEW_REQUIRED;
                } catch (Exception uncertain) {
                    try { journal.compareAndSet(dispatched, dispatched.recover()); }
                    catch (IOException failed) { return Result.STORAGE_UNCERTAIN; }
                    return Result.REVIEW_REQUIRED;
                }
            }
        }
    }
    private void validate(Request request, Preflight preflight) {
        if (preflight == null || !preflight.consent().equals(request.identity()) || !preflight.unlockedForeground() || !preflight.frozenSourceVerified() || !preflight.completePackageEnumeration())
            throw new IllegalStateException("Authorization or frozen source unavailable");
        if (!preflight.peers().keySet().equals(request.identity().affectedClones())) throw new IllegalStateException("Peer consent mismatch");
        for (PeerState peer : preflight.peers().values()) {
            if (!peer.provider().equals(request.identity().provider()) || !peer.packageName().equals(request.packageName()) || !peer.ready() || !peer.stopped())
                throw new IllegalStateException("Peer is not safe for update");
        }
        if (preflight.peers().values().stream().map(PeerState::installedClusterSha256).distinct().count() != 1)
            throw new IllegalStateException("Shared package generations disagree");
        CapabilityPolicy.Request capability = new CapabilityPolicy.Request(request.identity().provider(), request.environment(),
            request.identity().clusterSha256(), request.scope(), Claim.ATOMIC_UPDATE);
        if (!CapabilityPolicy.assess(capability, preflight.evidence()).permitted()) throw new IllegalStateException("Capability unproven");
    }
    private void validateStable(Request request, Preflight initial, Preflight fresh) {
        validate(request, fresh);
        if (!initial.peers().equals(fresh.peers())) throw new IllegalStateException("Peer identity changed during preparation");
    }
    private Path scopeDirectory(Request request) throws IOException {
        if (!Files.isDirectory(root, NOFOLLOW_LINKS) || !root.toRealPath().equals(root) ||
            !rootIdentity.equals(Files.readAttributes(root, BasicFileAttributes.class, NOFOLLOW_LINKS).fileKey())) throw new IOException("Dispatcher root changed");
        // Revision deliberately omitted: unresolved older revisions retain the same package lease.
        String namespace = request.identity().provider().id();
        String key = namespace.length() + ":" + namespace + ":" + request.packageName();
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        Path directory = root.resolve(hash);
        try { Files.createDirectory(directory); }
        catch (FileAlreadyExistsException existing) {
            if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) throw new IOException("Unsafe package scope");
        }
        if (!directory.toRealPath().equals(directory)) throw new IOException("Scope directory redirected");
        try (FileChannel parent = FileChannel.open(root, READ, NOFOLLOW_LINKS)) { parent.force(true); }
        return directory;
    }
}
