package vn.fonesi.rebuild;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static vn.fonesi.rebuild.CapabilityPolicy.*;
import static vn.fonesi.rebuild.OperationProtocol.*;

public final class KernelTest {
    private static int checks;
    private static final Provider P = new Provider("owned-test-provider", "test-revision");
    private static final Provider OTHER = new Provider("other", "test-revision");
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final String DIGEST = "a".repeat(64);
    public static void main(String[] args) {
        capabilityTests(); operationTests();
        if (checks < 30) throw new AssertionError("Expected nonzero complete suite");
        System.out.println("PASS " + checks + " explicit checks; synthetic JVM contract only; no provider executed");
    }
    private static Evidence evidence(Scope scope, Isolation isolation, Set<Claim> claims) {
        return new Evidence(P, "api34-arm64-4k", DIGEST, scope, isolation, claims);
    }
    private static Request request(Scope scope, Claim claim) { return new Request(P, "api34-arm64-4k", DIGEST, scope, claim); }
    private static void capabilityTests() {
        Set<Claim> all = Set.of(Claim.values());
        Evidence synthetic = evidence(Scope.SYNTHETIC, Isolation.HOST_UID_NAMESPACE, all);
        check(!assess(request(Scope.THIRD_PARTY, Claim.SPLIT_EXECUTION), synthetic).permitted());
        check(assess(request(Scope.SYNTHETIC, Claim.SPLIT_EXECUTION), synthetic).permitted());
        check(!assess(request(Scope.SYNTHETIC, Claim.SPLIT_EXECUTION), null).permitted());
        check(!assess(new Request(OTHER, "api34-arm64-4k", DIGEST, Scope.SYNTHETIC, Claim.SPLIT_EXECUTION), synthetic).permitted());
        check(!assess(new Request(P, "api35-arm64-16k", DIGEST, Scope.SYNTHETIC, Claim.SPLIT_EXECUTION), synthetic).permitted());
        check(!assess(request(Scope.SYNTHETIC, Claim.SPLIT_EXECUTION), evidence(Scope.SYNTHETIC, Isolation.UNKNOWN, Set.of(Claim.SPLIT_EXECUTION))).permitted());
        check(!assess(request(Scope.SYNTHETIC, Claim.ATOMIC_UPDATE), evidence(Scope.SYNTHETIC, Isolation.UNKNOWN, Set.of(Claim.ATOMIC_UPDATE))).permitted());
        check(!assess(request(Scope.SYNTHETIC, Claim.ADVERSARIAL_ISOLATION), evidence(Scope.SYNTHETIC, Isolation.UNKNOWN, all)).permitted());
        check(!assess(request(Scope.THIRD_PARTY, Claim.GUEST_LIFECYCLE), evidence(Scope.THIRD_PARTY, Isolation.HOST_UID_NAMESPACE, Set.of(Claim.GUEST_LIFECYCLE))).permitted());
        check(!assess(request(Scope.THIRD_PARTY, Claim.GUEST_LIFECYCLE), evidence(Scope.THIRD_PARTY, Isolation.SEPARATE_OS_UID, Set.of(Claim.GUEST_LIFECYCLE))).permitted());
        check(assess(request(Scope.THIRD_PARTY, Claim.ATOMIC_UPDATE), evidence(Scope.THIRD_PARTY, Isolation.SEPARATE_OS_UID, all)).permitted());
        check(!assess(request(Scope.THIRD_PARTY, Claim.ATOMIC_UPDATE), evidence(Scope.THIRD_PARTY, Isolation.UNKNOWN, all)).permitted());
        check(!assess(request(Scope.SYNTHETIC, Claim.GUEST_LIFECYCLE), evidence(Scope.SYNTHETIC, Isolation.HOST_UID_NAMESPACE, Set.of())).permitted());
        check(!assess(request(Scope.THIRD_PARTY, Claim.GUEST_LIFECYCLE), evidence(Scope.THIRD_PARTY, Isolation.HOST_UID_NAMESPACE, Set.of(Claim.GUEST_LIFECYCLE, Claim.ADVERSARIAL_ISOLATION))).permitted());
        check(!assess(request(Scope.SYNTHETIC, Claim.SIGNED_CLUSTER_IMPORT), evidence(Scope.SYNTHETIC, Isolation.UNKNOWN, all)).permitted());
        check(!assess(new Request(P, "api34-arm64-4k", "b".repeat(64), Scope.SYNTHETIC, Claim.SIGNED_CLUSTER_IMPORT), synthetic).permitted());
        check(!assess(new Request(new Provider(P.id(), "new-revision"), "api34-arm64-4k", DIGEST, Scope.SYNTHETIC, Claim.SIGNED_CLUSTER_IMPORT), synthetic).permitted());
        check(!assess(request(Scope.THIRD_PARTY, Claim.GUEST_LIFECYCLE), evidence(Scope.THIRD_PARTY, Isolation.HOST_UID_NAMESPACE,
            Set.of(Claim.GUEST_LIFECYCLE, Claim.ADVERSARIAL_ISOLATION, Claim.SIGNED_CLUSTER_IMPORT))).permitted());
        check(!assess(request(Scope.THIRD_PARTY, Claim.GUEST_LIFECYCLE), evidence(Scope.THIRD_PARTY, Isolation.HOST_UID_NAMESPACE,
            Set.of(Claim.GUEST_LIFECYCLE, Claim.ADVERSARIAL_ISOLATION, Claim.PRIVATE_CLUSTER_READBACK))).permitted());
        HashSet<Claim> mutable = new HashSet<>(all);
        Evidence immutable = evidence(Scope.SYNTHETIC, Isolation.HOST_UID_NAMESPACE, mutable);
        mutable.clear(); check(immutable.claims().size() == all.size());
        rejects(() -> immutable.claims().clear());
        rejects(() -> new Provider(" ", "revision"));
        rejects(() -> new Provider("provider", " revision"));
        rejects(() -> new Evidence(P, "", DIGEST, Scope.SYNTHETIC, Isolation.UNKNOWN, all));
    }
    private static Identity identity(Set<UUID> affected) { return new Identity(A, P, A, DIGEST, affected); }
    private static Readback readback(Provider provider, UUID clone, String digest, Set<UUID> peers, boolean privateStorage) {
        Map<UUID, PeerObservation> observations = new HashMap<>();
        for (UUID peer : peers) observations.put(peer, new PeerObservation(provider, digest, privateStorage));
        return new Readback(clone, 1, observations);
    }
    private static void operationTests() {
        Identity id = identity(Set.of(A, B));
        Operation prepared = Operation.prepare(id);
        check(prepared.state() == State.PREPARED && prepared.revision() == 0);
        check(prepared.recover().equals(prepared));
        rejects(() -> prepared.dispatch(1, id));
        rejects(() -> prepared.dispatch(0, identity(Set.of(A))));
        Operation dispatched = prepared.dispatch(0, id);
        check(dispatched.state() == State.DISPATCHED && dispatched.revision() == 1);
        rejects(() -> dispatched.cancel(1));
        rejects(() -> dispatched.dispatch(1, id));
        rejects(() -> prepared.verify(0, null));
        rejects(() -> dispatched.verify(0, null));
        Operation verified = dispatched.verify(1, readback(P, A, DIGEST, Set.of(A, B), true));
        check(verified.state() == State.VERIFIED && verified.revision() == 2);
        check(verified.recover().equals(verified));
        rejects(() -> verified.dispatch(2, id));
        check(dispatched.recover().state() == State.REVIEW_REQUIRED);
        check(dispatched.recover().recover().equals(dispatched.recover()));
        rejects(() -> dispatched.recover().dispatch(2, id));
        check(dispatched.verify(1, null).state() == State.REVIEW_REQUIRED);
        check(dispatched.verify(1, readback(OTHER, A, DIGEST, Set.of(A, B), true)).state() == State.REVIEW_REQUIRED);
        check(dispatched.verify(1, readback(P, B, DIGEST, Set.of(A, B), true)).state() == State.REVIEW_REQUIRED);
        check(dispatched.verify(1, readback(P, A, "b".repeat(64), Set.of(A, B), true)).state() == State.REVIEW_REQUIRED);
        check(dispatched.verify(1, readback(P, A, DIGEST, Set.of(A), true)).state() == State.REVIEW_REQUIRED);
        check(dispatched.verify(1, readback(P, A, DIGEST, Set.of(A, B), false)).state() == State.REVIEW_REQUIRED);
        check(dispatched.verify(1, new Readback(A, 1, Map.of(A, new PeerObservation(P, DIGEST, true),
            B, new PeerObservation(P, "b".repeat(64), true)))).state() == State.REVIEW_REQUIRED);
        check(dispatched.verify(1, new Readback(A, 1, Map.of(A, new PeerObservation(P, DIGEST, true),
            B, new PeerObservation(OTHER, DIGEST, true)))).state() == State.REVIEW_REQUIRED);
        check(dispatched.verify(1, new Readback(A, 1, Map.of(A, new PeerObservation(P, DIGEST, true),
            B, new PeerObservation(P, DIGEST, false)))).state() == State.REVIEW_REQUIRED);
        check(dispatched.verify(1, new Readback(A, 1, Map.of(A, new PeerObservation(P, DIGEST, true),
            B, new PeerObservation(P, DIGEST, true), UUID.randomUUID(), new PeerObservation(P, DIGEST, true)))).state() == State.REVIEW_REQUIRED);
        Map<UUID, PeerObservation> mutableReadback = new HashMap<>(Map.of(A, new PeerObservation(P, DIGEST, true)));
        Readback immutableReadback = new Readback(A, 1, mutableReadback); mutableReadback.clear();
        check(immutableReadback.peers().size() == 1);
        rejects(() -> immutableReadback.peers().clear());
        check(dispatched.verify(1, new Readback(A, 2, Map.of(A, new PeerObservation(P, DIGEST, true),
            B, new PeerObservation(P, DIGEST, true)))).state() == State.REVIEW_REQUIRED);
        rejects(() -> new Readback(A, 0, Map.of()));
        rejects(() -> new Operation(id, State.DISPATCHED, Long.MAX_VALUE).recover());
        rejects(() -> new Operation(id, State.DISPATCHED, Long.MAX_VALUE).verify(Long.MAX_VALUE, null));
        Operation cancelled = prepared.cancel(0);
        check(cancelled.state() == State.CANCELLED && cancelled.recover().equals(cancelled));
        rejects(() -> cancelled.dispatch(1, id));
        rejects(() -> identity(Set.of(B)));
        rejects(() -> new Identity(A, P, A, "short", Set.of(A)));
        rejects(() -> new Identity(A, P, A, "A".repeat(64), Set.of(A)));
        rejects(() -> new Operation(id, State.PREPARED, -1));
        rejects(() -> new Operation(id, State.PREPARED, Long.MAX_VALUE).dispatch(Long.MAX_VALUE, id));
        HashSet<UUID> peers = new HashSet<>(Set.of(A, B));
        Identity immutable = identity(peers); peers.clear(); check(immutable.affectedClones().size() == 2);
        rejects(() -> immutable.affectedClones().clear());
    }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static void rejects(Runnable action) {
        checks++;
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected rejection at check " + checks);
    }
}
