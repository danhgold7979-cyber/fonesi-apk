package vn.fonesi.rebuild;

import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable transition model. Persistence adapter must commit DISPATCHED before external mutation. */
public final class OperationProtocol {
    private OperationProtocol() {}
    public enum State { PREPARED, DISPATCHED, VERIFIED, CANCELLED, REVIEW_REQUIRED }
    public record Identity(UUID operation, CapabilityPolicy.Provider provider, String packageName, String environment, CapabilityPolicy.Scope scope, UUID cloneId,
                           String clusterSha256, Set<UUID> affectedClones) {
        public Identity {
            Objects.requireNonNull(operation); Objects.requireNonNull(provider); Objects.requireNonNull(cloneId); Objects.requireNonNull(scope);
            if (packageName == null || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || packageName.length() > 255)
                throw new IllegalArgumentException("Invalid package identity");
            IdentityText.require(environment);
            if (clusterSha256 == null || !clusterSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid cluster digest");
            affectedClones = Set.copyOf(affectedClones);
            if (!affectedClones.contains(cloneId)) throw new IllegalArgumentException("Target absent from affected set");
        }
    }
    public record PeerObservation(CapabilityPolicy.Provider provider, String clusterSha256,
                                  boolean privateStorageVerified) {
        public PeerObservation { Objects.requireNonNull(provider); Objects.requireNonNull(clusterSha256); }
    }
    public record Readback(UUID operation, long dispatchRevision, Map<UUID, PeerObservation> peers) {
        public Readback { Objects.requireNonNull(operation); if (dispatchRevision < 1) throw new IllegalArgumentException("Invalid dispatch revision"); peers = Map.copyOf(peers); }
    }
    public record Operation(Identity identity, State state, long revision) {
        public Operation {
            Objects.requireNonNull(identity); Objects.requireNonNull(state);
            long reachable = switch (state) {
                case PREPARED -> 0;
                case DISPATCHED, CANCELLED -> 1;
                case VERIFIED, REVIEW_REQUIRED -> 2;
            };
            if (revision != reachable) throw new IllegalArgumentException("Unreachable state/revision pair");
        }
        public static Operation prepare(Identity identity) { return new Operation(identity, State.PREPARED, 0); }
        public Operation dispatch(long expectedRevision, Identity freshlyAuthorizedIdentity) {
            require(State.PREPARED, expectedRevision);
            if (!identity.equals(freshlyAuthorizedIdentity)) throw new IllegalStateException("Identity or consent changed");
            return next(State.DISPATCHED);
        }
        public Operation cancel(long expectedRevision) {
            require(State.PREPARED, expectedRevision); return next(State.CANCELLED);
        }
        public Operation verify(long expectedRevision, Readback readback) {
            require(State.DISPATCHED, expectedRevision);
            if (readback == null || revision != readback.dispatchRevision() || !identity.operation().equals(readback.operation()) ||
                !identity.affectedClones().equals(readback.peers().keySet())) return next(State.REVIEW_REQUIRED);
            for (PeerObservation peer : readback.peers().values()) {
                if (!peer.privateStorageVerified() || !identity.provider().equals(peer.provider()) ||
                    !identity.clusterSha256().equals(peer.clusterSha256())) return next(State.REVIEW_REQUIRED);
            }
            return next(State.VERIFIED);
        }
        public Operation recover() { return state == State.DISPATCHED ? next(State.REVIEW_REQUIRED) : this; }
        private void require(State expected, long expectedRevision) {
            if (state != expected || revision != expectedRevision) throw new IllegalStateException("Stale or forbidden transition");
        }
        private Operation next(State value) { return new Operation(identity, value, Math.incrementExact(revision)); }
    }
}
