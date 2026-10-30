package vn.fonesi.rebuild.logical;

import java.util.*;

/** Independent invariants; no filesystem, provider, Android, or object hash output. */
public final class LogicalContractTest {
    private static int checks;
    private LogicalContractTest() {}
    private static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Check " + checks); }
    private static void immutable(Runnable action) {
        checks++; try { action.run(); throw new AssertionError("Mutable collection"); }
        catch (UnsupportedOperationException expected) { /* required */ }
    }
    private static void equalValue(Object a, Object b) {
        check(a.equals(b)); check(b.equals(a)); check(a.hashCode() == b.hashCode());
        check(!a.equals(null)); check(!a.equals("different type"));
    }
    private static int valueHash(Object... components) {
        int hash = 0;
        for (Object component : components) hash = 31 * hash + Objects.hashCode(component);
        return hash;
    }
    public static void main(String[] args) {
        CapabilityPolicy.Provider provider = LogicalVectors.PROVIDER;
        equalValue(provider, new CapabilityPolicy.Provider("provider", "revision"));
        check(!new CapabilityPolicy.Provider("\u00e9", "r").equals(new CapabilityPolicy.Provider("e\u0301", "r")));
        Set<CapabilityPolicy.Claim> claims = EnumSet.of(CapabilityPolicy.Claim.SIGNED_CLUSTER_IMPORT);
        CapabilityPolicy.Evidence evidence = new CapabilityPolicy.Evidence(provider,"environment",LogicalVectors.DIGEST,CapabilityPolicy.Scope.THIRD_PARTY,CapabilityPolicy.Isolation.UNKNOWN,claims);
        claims.clear(); check(evidence.claims().size() == 1);
        immutable(() -> evidence.claims().clear());
        equalValue(evidence,new CapabilityPolicy.Evidence(provider,"environment",LogicalVectors.DIGEST,CapabilityPolicy.Scope.THIRD_PARTY,CapabilityPolicy.Isolation.UNKNOWN,Collections.singleton(CapabilityPolicy.Claim.SIGNED_CLUSTER_IMPORT)));
        CapabilityPolicy.Request request = new CapabilityPolicy.Request(provider,"environment",LogicalVectors.DIGEST,CapabilityPolicy.Scope.THIRD_PARTY,CapabilityPolicy.Claim.SIGNED_CLUSTER_IMPORT);
        equalValue(request,new CapabilityPolicy.Request(provider,"environment",LogicalVectors.DIGEST,CapabilityPolicy.Scope.THIRD_PARTY,CapabilityPolicy.Claim.SIGNED_CLUSTER_IMPORT));
        equalValue(new CapabilityPolicy.Decision(true,"reason"),new CapabilityPolicy.Decision(true,"reason"));
        Set<UUID> peers = new HashSet<>(Arrays.asList(LogicalVectors.A,LogicalVectors.B));
        OperationProtocol.Identity identity = LogicalVectors.identity(peers);
        peers.clear(); check(identity.affectedClones().size() == 2);
        immutable(() -> identity.affectedClones().clear());
        equalValue(identity,LogicalVectors.identity(new LinkedHashSet<>(Arrays.asList(LogicalVectors.B,LogicalVectors.A))));
        OperationProtocol.PeerObservation peer = new OperationProtocol.PeerObservation(provider,LogicalVectors.DIGEST,true);
        equalValue(peer,new OperationProtocol.PeerObservation(provider,LogicalVectors.DIGEST,true));
        Map<UUID,OperationProtocol.PeerObservation> map = new HashMap<>();
        map.put(LogicalVectors.A,peer); map.put(LogicalVectors.B,peer);
        OperationProtocol.Readback readback = new OperationProtocol.Readback(LogicalVectors.OP,1,map);
        map.clear(); check(readback.peers().size() == 2);
        immutable(() -> readback.peers().clear());
        Map<UUID,OperationProtocol.PeerObservation> reverse = new LinkedHashMap<>();
        reverse.put(LogicalVectors.B,peer); reverse.put(LogicalVectors.A,peer);
        equalValue(readback,new OperationProtocol.Readback(LogicalVectors.OP,1,reverse));
        OperationProtocol.Operation prepared = OperationProtocol.Operation.prepare(identity);
        equalValue(prepared,new OperationProtocol.Operation(identity,OperationProtocol.State.PREPARED,0));
        OperationProtocol.Operation dispatched = prepared.dispatch(0,identity);
        check(prepared.state() == OperationProtocol.State.PREPARED && prepared.revision() == 0);
        check(dispatched.verify(1,readback).state() == OperationProtocol.State.VERIFIED);
        check(dispatched.state() == OperationProtocol.State.DISPATCHED && dispatched.revision() == 1);
        check(dispatched.recover().state() == OperationProtocol.State.REVIEW_REQUIRED);
        check(prepared.recover() == prepared);
        check(provider.hashCode() == valueHash(provider.id(), provider.revision()));
        check(evidence.hashCode() == valueHash(evidence.provider(), evidence.environment(), evidence.clusterSha256(), evidence.scope(), evidence.isolation(), evidence.claims()));
        check(request.hashCode() == valueHash(request.provider(), request.environment(), request.clusterSha256(), request.scope(), request.capability()));
        check(new CapabilityPolicy.Decision(true,"reason").hashCode() == valueHash(true,"reason"));
        check(identity.hashCode() == valueHash(identity.operation(), identity.provider(), identity.packageName(), identity.environment(), identity.scope(), identity.cloneId(), identity.clusterSha256(), identity.affectedClones()));
        check(peer.hashCode() == valueHash(peer.provider(), peer.clusterSha256(), peer.privateStorageVerified()));
        check(readback.hashCode() == valueHash(readback.operation(), readback.dispatchRevision(), readback.peers()));
        check(prepared.hashCode() == valueHash(prepared.identity(), prepared.state(), prepared.revision()));
        check(provider.toString().equals("Provider[id=provider, revision=revision]"));
        immutable(() -> readback.peers().values().clear());
        immutable(() -> readback.peers().keySet().clear());
        immutable(() -> readback.peers().entrySet().iterator().next().setValue(peer));
        Iterator<UUID> peerIterator = identity.affectedClones().iterator(); peerIterator.next();
        immutable(peerIterator::remove);
        String first = LogicalVectors.observations();
        check(first.equals(LogicalVectors.observations()));
        check(first.contains("text/question/id=ACCEPT\n"));
        check(first.contains("text/surrogate55296/id=IllegalArgumentException\n"));
        check(first.contains("readback/oneBadPeer=REVIEW_REQUIRED:2\n"));
        check(first.contains("policy/mismatch/0=PROVIDER_MISMATCH\n"));
        System.out.println("Logical contract checks: " + checks);
    }
}
