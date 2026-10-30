package vn.fonesi.rebuild.logical;

import java.util.Objects;
import java.util.Set;

/** Reviewed B1c compatibility port, not new provider functionality. Pure policy, not an attestation verifier. Only independently verified evidence belongs here. */
public final class CapabilityPolicy {
    private CapabilityPolicy() {}
    public static final class Provider {
        private final String id;
        private final String revision;
        public Provider(String id, String revision) {
            requireText(id); requireText(revision);
            this.id = id;
            this.revision = revision;
        }
        public String id() { return id; }
        public String revision() { return revision; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Provider)) return false;
            Provider other = (Provider) value;
            return Objects.equals(id, other.id)
                    && Objects.equals(revision, other.revision);
        }
        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(id);
            hash = 31 * hash + Objects.hashCode(revision);
            return hash;
        }
        @Override public String toString() { return "Provider[" + "id=" + id + ", " + "revision=" + revision + "]"; }

    }
    public enum Scope { SYNTHETIC, THIRD_PARTY }
    public enum Isolation { UNKNOWN, HOST_UID_NAMESPACE, SEPARATE_OS_UID, SEPARATE_GUEST_OS }
    public enum Claim { SIGNED_CLUSTER_IMPORT, PRIVATE_CLUSTER_READBACK, SPLIT_EXECUTION,
        ATOMIC_UPDATE, GUEST_LIFECYCLE, ADVERSARIAL_ISOLATION }
    public static final class Evidence {
        private final Provider provider;
        private final String environment;
        private final String clusterSha256;
        private final Scope scope;
        private final Isolation isolation;
        private final Set<Claim> claims;
        public Evidence(Provider provider, String environment, String clusterSha256, Scope scope,
                           Isolation isolation, Set<Claim> claims) {
            Objects.requireNonNull(provider); requireText(environment); requireDigest(clusterSha256);
            Objects.requireNonNull(scope); Objects.requireNonNull(isolation);
            claims = ImmutableCollections.copySet(claims);
            this.provider = provider;
            this.environment = environment;
            this.clusterSha256 = clusterSha256;
            this.scope = scope;
            this.isolation = isolation;
            this.claims = claims;
        }
        public Provider provider() { return provider; }
        public String environment() { return environment; }
        public String clusterSha256() { return clusterSha256; }
        public Scope scope() { return scope; }
        public Isolation isolation() { return isolation; }
        public Set<Claim> claims() { return claims; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Evidence)) return false;
            Evidence other = (Evidence) value;
            return Objects.equals(provider, other.provider)
                    && Objects.equals(environment, other.environment)
                    && Objects.equals(clusterSha256, other.clusterSha256)
                    && Objects.equals(scope, other.scope)
                    && Objects.equals(isolation, other.isolation)
                    && Objects.equals(claims, other.claims);
        }
        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(provider);
            hash = 31 * hash + Objects.hashCode(environment);
            hash = 31 * hash + Objects.hashCode(clusterSha256);
            hash = 31 * hash + Objects.hashCode(scope);
            hash = 31 * hash + Objects.hashCode(isolation);
            hash = 31 * hash + Objects.hashCode(claims);
            return hash;
        }
        @Override public String toString() { return "Evidence[" + "provider=" + provider + ", " + "environment=" + environment + ", " + "clusterSha256=" + clusterSha256 + ", " + "scope=" + scope + ", " + "isolation=" + isolation + ", " + "claims=" + claims + "]"; }

    }
    public static final class Request {
        private final Provider provider;
        private final String environment;
        private final String clusterSha256;
        private final Scope scope;
        private final Claim capability;
        public Request(Provider provider, String environment, String clusterSha256, Scope scope, Claim capability) {
            Objects.requireNonNull(provider); requireText(environment); requireDigest(clusterSha256);
            Objects.requireNonNull(scope); Objects.requireNonNull(capability);
            this.provider = provider;
            this.environment = environment;
            this.clusterSha256 = clusterSha256;
            this.scope = scope;
            this.capability = capability;
        }
        public Provider provider() { return provider; }
        public String environment() { return environment; }
        public String clusterSha256() { return clusterSha256; }
        public Scope scope() { return scope; }
        public Claim capability() { return capability; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Request)) return false;
            Request other = (Request) value;
            return Objects.equals(provider, other.provider)
                    && Objects.equals(environment, other.environment)
                    && Objects.equals(clusterSha256, other.clusterSha256)
                    && Objects.equals(scope, other.scope)
                    && Objects.equals(capability, other.capability);
        }
        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(provider);
            hash = 31 * hash + Objects.hashCode(environment);
            hash = 31 * hash + Objects.hashCode(clusterSha256);
            hash = 31 * hash + Objects.hashCode(scope);
            hash = 31 * hash + Objects.hashCode(capability);
            return hash;
        }
        @Override public String toString() { return "Request[" + "provider=" + provider + ", " + "environment=" + environment + ", " + "clusterSha256=" + clusterSha256 + ", " + "scope=" + scope + ", " + "capability=" + capability + "]"; }

    }
    public static final class Decision {
        private final boolean permitted;
        private final String reason;
        public Decision(boolean permitted, String reason) {
            this.permitted = permitted;
            this.reason = reason;
        }
        public boolean permitted() { return permitted; }
        public String reason() { return reason; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Decision)) return false;
            Decision other = (Decision) value;
            return Objects.equals(permitted, other.permitted)
                    && Objects.equals(reason, other.reason);
        }
        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(permitted);
            hash = 31 * hash + Objects.hashCode(reason);
            return hash;
        }
        @Override public String toString() { return "Decision[" + "permitted=" + permitted + ", " + "reason=" + reason + "]"; }
    }
    public static Decision assess(Request request, Evidence evidence) {
        Objects.requireNonNull(request);
        if (evidence == null) return deny("EVIDENCE_MISSING");
        if (!request.provider().equals(evidence.provider())) return deny("PROVIDER_MISMATCH");
        if (!request.environment().equals(evidence.environment())) return deny("ENVIRONMENT_MISMATCH");
        if (request.scope() == Scope.THIRD_PARTY && evidence.scope() != Scope.THIRD_PARTY)
            return deny("SYNTHETIC_ONLY");
        if (!request.clusterSha256().equals(evidence.clusterSha256())) return deny("CLUSTER_MISMATCH");
        Set<Claim> claims = evidence.claims();
        // B1c: labels/claims are only necessary conditions, never independent attestation.
        boolean requiresStrongMode = claims.contains(Claim.ADVERSARIAL_ISOLATION)
                || request.capability() == Claim.ADVERSARIAL_ISOLATION
                || (request.scope() == Scope.THIRD_PARTY
                    && (request.capability() == Claim.SPLIT_EXECUTION
                        || request.capability() == Claim.GUEST_LIFECYCLE
                        || request.capability() == Claim.ATOMIC_UPDATE));
        boolean strongMode = evidence.isolation() == Isolation.SEPARATE_OS_UID
                || evidence.isolation() == Isolation.SEPARATE_GUEST_OS;
        if (requiresStrongMode && !strongMode) return deny("ISOLATION_MODE_UNSUPPORTED");
        if (!claims.contains(request.capability())) return deny("CLAIM_MISSING");
        // Fail closed for inconsistent evidence, even when the requested claim is lower level.
        if ((claims.contains(Claim.SPLIT_EXECUTION) || claims.contains(Claim.GUEST_LIFECYCLE)) &&
            (!claims.contains(Claim.SIGNED_CLUSTER_IMPORT) || !claims.contains(Claim.PRIVATE_CLUSTER_READBACK)))
            return deny("IMPORT_PROOF_MISSING");
        if (claims.contains(Claim.ATOMIC_UPDATE) && !claims.contains(Claim.SPLIT_EXECUTION))
            return deny("EXECUTION_PROOF_MISSING");
        if (request.scope() == Scope.THIRD_PARTY &&
            (request.capability() == Claim.GUEST_LIFECYCLE || request.capability() == Claim.SPLIT_EXECUTION ||
             request.capability() == Claim.ATOMIC_UPDATE)) {
            if (!claims.contains(Claim.ADVERSARIAL_ISOLATION))
                return deny("ADVERSARIAL_PROOF_MISSING");
        }
        return new Decision(true, "EXACT_SCOPE_EVIDENCE");
    }
    private static Decision deny(String reason) { return new Decision(false, reason); }
    private static void requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid cluster digest");
    }
    private static void requireText(String value) {
        IdentityText.require(value);
    }
}
