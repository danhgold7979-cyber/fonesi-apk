package vn.fonesi.rebuild;

import java.util.Objects;
import java.util.Set;

/** Pure policy, not an attestation verifier. Only independently verified evidence belongs here. */
public final class CapabilityPolicy {
    private CapabilityPolicy() {}
    public record Provider(String id, String revision) {
        public Provider { requireText(id); requireText(revision); }
    }
    public enum Scope { SYNTHETIC, THIRD_PARTY }
    public enum Isolation { UNKNOWN, HOST_UID_NAMESPACE, SEPARATE_OS_UID, SEPARATE_GUEST_OS }
    public enum Claim { SIGNED_CLUSTER_IMPORT, PRIVATE_CLUSTER_READBACK, SPLIT_EXECUTION,
        ATOMIC_UPDATE, GUEST_LIFECYCLE, ADVERSARIAL_ISOLATION }
    public record Evidence(Provider provider, String environment, String clusterSha256, Scope scope,
                           Isolation isolation, Set<Claim> claims) {
        public Evidence {
            Objects.requireNonNull(provider); requireText(environment); requireDigest(clusterSha256);
            Objects.requireNonNull(scope); Objects.requireNonNull(isolation);
            claims = Set.copyOf(claims);
        }
    }
    public record Request(Provider provider, String environment, String clusterSha256, Scope scope, Claim capability) {
        public Request {
            Objects.requireNonNull(provider); requireText(environment); requireDigest(clusterSha256);
            Objects.requireNonNull(scope); Objects.requireNonNull(capability);
        }
    }
    public record Decision(boolean permitted, String reason) {}
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
            !claims.containsAll(Set.of(Claim.SIGNED_CLUSTER_IMPORT, Claim.PRIVATE_CLUSTER_READBACK)))
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
