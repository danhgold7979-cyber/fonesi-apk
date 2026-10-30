package vn.fonesi.rebuild.logical;

import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reviewed B1b compatibility port. Immutable transition model. Persistence adapter must commit DISPATCHED before external mutation. */
public final class OperationProtocol {
    private OperationProtocol() {}
    public enum State { PREPARED, DISPATCHED, VERIFIED, CANCELLED, REVIEW_REQUIRED }
    public static final class Identity {
        private final UUID operation;
        private final CapabilityPolicy.Provider provider;
        private final String packageName;
        private final String environment;
        private final CapabilityPolicy.Scope scope;
        private final UUID cloneId;
        private final String clusterSha256;
        private final Set<UUID> affectedClones;
        public Identity(UUID operation, CapabilityPolicy.Provider provider, String packageName, String environment, CapabilityPolicy.Scope scope, UUID cloneId,
                           String clusterSha256, Set<UUID> affectedClones) {
            Objects.requireNonNull(operation); Objects.requireNonNull(provider); Objects.requireNonNull(cloneId); Objects.requireNonNull(scope);
            if (packageName == null || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || packageName.length() > 255)
                throw new IllegalArgumentException("Invalid package identity");
            IdentityText.require(environment);
            if (clusterSha256 == null || !clusterSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid cluster digest");
            affectedClones = ImmutableCollections.copySet(affectedClones);
            if (!affectedClones.contains(cloneId)) throw new IllegalArgumentException("Target absent from affected set");
            this.operation = operation;
            this.provider = provider;
            this.packageName = packageName;
            this.environment = environment;
            this.scope = scope;
            this.cloneId = cloneId;
            this.clusterSha256 = clusterSha256;
            this.affectedClones = affectedClones;
        }
        public UUID operation() { return operation; }
        public CapabilityPolicy.Provider provider() { return provider; }
        public String packageName() { return packageName; }
        public String environment() { return environment; }
        public CapabilityPolicy.Scope scope() { return scope; }
        public UUID cloneId() { return cloneId; }
        public String clusterSha256() { return clusterSha256; }
        public Set<UUID> affectedClones() { return affectedClones; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Identity)) return false;
            Identity other = (Identity) value;
            return Objects.equals(operation, other.operation)
                    && Objects.equals(provider, other.provider)
                    && Objects.equals(packageName, other.packageName)
                    && Objects.equals(environment, other.environment)
                    && Objects.equals(scope, other.scope)
                    && Objects.equals(cloneId, other.cloneId)
                    && Objects.equals(clusterSha256, other.clusterSha256)
                    && Objects.equals(affectedClones, other.affectedClones);
        }
        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(operation);
            hash = 31 * hash + Objects.hashCode(provider);
            hash = 31 * hash + Objects.hashCode(packageName);
            hash = 31 * hash + Objects.hashCode(environment);
            hash = 31 * hash + Objects.hashCode(scope);
            hash = 31 * hash + Objects.hashCode(cloneId);
            hash = 31 * hash + Objects.hashCode(clusterSha256);
            hash = 31 * hash + Objects.hashCode(affectedClones);
            return hash;
        }
        @Override public String toString() { return "Identity[" + "operation=" + operation + ", " + "provider=" + provider + ", " + "packageName=" + packageName + ", " + "environment=" + environment + ", " + "scope=" + scope + ", " + "cloneId=" + cloneId + ", " + "clusterSha256=" + clusterSha256 + ", " + "affectedClones=" + affectedClones + "]"; }

    }
    public static final class PeerObservation {
        private final CapabilityPolicy.Provider provider;
        private final String clusterSha256;
        private final boolean privateStorageVerified;
        public PeerObservation(CapabilityPolicy.Provider provider, String clusterSha256,
                                  boolean privateStorageVerified) {
            Objects.requireNonNull(provider); Objects.requireNonNull(clusterSha256);
            this.provider = provider;
            this.clusterSha256 = clusterSha256;
            this.privateStorageVerified = privateStorageVerified;
        }
        public CapabilityPolicy.Provider provider() { return provider; }
        public String clusterSha256() { return clusterSha256; }
        public boolean privateStorageVerified() { return privateStorageVerified; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof PeerObservation)) return false;
            PeerObservation other = (PeerObservation) value;
            return Objects.equals(provider, other.provider)
                    && Objects.equals(clusterSha256, other.clusterSha256)
                    && Objects.equals(privateStorageVerified, other.privateStorageVerified);
        }
        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(provider);
            hash = 31 * hash + Objects.hashCode(clusterSha256);
            hash = 31 * hash + Objects.hashCode(privateStorageVerified);
            return hash;
        }
        @Override public String toString() { return "PeerObservation[" + "provider=" + provider + ", " + "clusterSha256=" + clusterSha256 + ", " + "privateStorageVerified=" + privateStorageVerified + "]"; }

    }
    public static final class Readback {
        private final UUID operation;
        private final long dispatchRevision;
        private final Map<UUID, PeerObservation> peers;
        public Readback(UUID operation, long dispatchRevision, Map<UUID, PeerObservation> peers) {
            Objects.requireNonNull(operation); if (dispatchRevision < 1) throw new IllegalArgumentException("Invalid dispatch revision"); peers = ImmutableCollections.copyMap(peers);
            this.operation = operation;
            this.dispatchRevision = dispatchRevision;
            this.peers = peers;
        }
        public UUID operation() { return operation; }
        public long dispatchRevision() { return dispatchRevision; }
        public Map<UUID, PeerObservation> peers() { return peers; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Readback)) return false;
            Readback other = (Readback) value;
            return Objects.equals(operation, other.operation)
                    && Objects.equals(dispatchRevision, other.dispatchRevision)
                    && Objects.equals(peers, other.peers);
        }
        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(operation);
            hash = 31 * hash + Objects.hashCode(dispatchRevision);
            hash = 31 * hash + Objects.hashCode(peers);
            return hash;
        }
        @Override public String toString() { return "Readback[" + "operation=" + operation + ", " + "dispatchRevision=" + dispatchRevision + ", " + "peers=" + peers + "]"; }

    }
    public static final class Operation {
        private final Identity identity;
        private final State state;
        private final long revision;
        public Operation(Identity identity, State state, long revision) {
            Objects.requireNonNull(identity); Objects.requireNonNull(state);
            long reachable;
            switch (state) {
                case PREPARED: reachable = 0; break;
                case DISPATCHED: case CANCELLED: reachable = 1; break;
                case VERIFIED: case REVIEW_REQUIRED: reachable = 2; break;
                default: throw new AssertionError(state);
            }
            if (revision != reachable) throw new IllegalArgumentException("Unreachable state/revision pair");
            this.identity = identity;
            this.state = state;
            this.revision = revision;
        }
        public Identity identity() { return identity; }
        public State state() { return state; }
        public long revision() { return revision; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Operation)) return false;
            Operation other = (Operation) value;
            return Objects.equals(identity, other.identity)
                    && Objects.equals(state, other.state)
                    && Objects.equals(revision, other.revision);
        }
        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(identity);
            hash = 31 * hash + Objects.hashCode(state);
            hash = 31 * hash + Objects.hashCode(revision);
            return hash;
        }
        @Override public String toString() { return "Operation[" + "identity=" + identity + ", " + "state=" + state + ", " + "revision=" + revision + "]"; }

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
