package vn.fonesi.rebuild;

import java.util.EnumSet;
import java.util.Set;

/** Independent D3 desired-contract truth table, not a differential compatibility oracle. */
public final class PolicyModeTest {
    private PolicyModeTest() {}
    private static int checks;
    private static final int IMPORT = 1, READBACK = 2, EXECUTE = 4, UPDATE = 8, LIFE = 16, ADV = 32;
    private static final String DIGEST = "a".repeat(64);
    private static final CapabilityPolicy.Provider PROVIDER = new CapabilityPolicy.Provider("provider", "revision");
    private static int bit(CapabilityPolicy.Claim claim) {
        switch (claim) {
            case SIGNED_CLUSTER_IMPORT: return IMPORT;
            case PRIVATE_CLUSTER_READBACK: return READBACK;
            case SPLIT_EXECUTION: return EXECUTE;
            case ATOMIC_UPDATE: return UPDATE;
            case GUEST_LIFECYCLE: return LIFE;
            case ADVERSARIAL_ISOLATION: return ADV;
            default: throw new AssertionError("Unreviewed claim");
        }
    }
    private static boolean has(int mask, int bits) { return (mask & bits) == bits; }
    private static Set<CapabilityPolicy.Claim> claims(int mask) {
        Set<CapabilityPolicy.Claim> result = EnumSet.noneOf(CapabilityPolicy.Claim.class);
        for (CapabilityPolicy.Claim claim : CapabilityPolicy.Claim.values()) if (has(mask,bit(claim))) result.add(claim);
        return result;
    }
    private static CapabilityPolicy.Request request(CapabilityPolicy.Scope scope, CapabilityPolicy.Claim claim) {
        return new CapabilityPolicy.Request(PROVIDER,"environment",DIGEST,scope,claim);
    }
    private static CapabilityPolicy.Evidence evidence(int mask, CapabilityPolicy.Isolation mode, CapabilityPolicy.Scope scope) {
        return new CapabilityPolicy.Evidence(PROVIDER,"environment",DIGEST,scope,mode,claims(mask));
    }
    private static String expected(int mask, CapabilityPolicy.Isolation mode,
            CapabilityPolicy.Scope requestedScope, CapabilityPolicy.Scope evidenceScope, CapabilityPolicy.Claim requestedClaim) {
        int wanted = bit(requestedClaim);
        boolean protectedRequest = requestedScope == CapabilityPolicy.Scope.THIRD_PARTY && (wanted & (EXECUTE | UPDATE | LIFE)) != 0;
        boolean needsStrongMode = has(mask,ADV) || wanted == ADV || protectedRequest;
        // Explicit allowlist: a future mode cannot become strong by exclusion.
        boolean strong = mode == CapabilityPolicy.Isolation.SEPARATE_OS_UID || mode == CapabilityPolicy.Isolation.SEPARATE_GUEST_OS;
        // Ordered failed obligations encode the approved reason precedence independently.
        boolean[] failures = {
            requestedScope == CapabilityPolicy.Scope.THIRD_PARTY && evidenceScope == CapabilityPolicy.Scope.SYNTHETIC,
            needsStrongMode && !strong,
            !has(mask,wanted),
            (mask & (EXECUTE | LIFE)) != 0 && !has(mask,IMPORT | READBACK),
            has(mask,UPDATE) && !has(mask,EXECUTE),
            protectedRequest && !has(mask,ADV)
        };
        String[] reasons = {"SYNTHETIC_ONLY", "ISOLATION_MODE_UNSUPPORTED", "CLAIM_MISSING",
            "IMPORT_PROOF_MISSING", "EXECUTION_PROOF_MISSING", "ADVERSARIAL_PROOF_MISSING"};
        for (int i = 0; i < failures.length; i++) if (failures[i]) return reasons[i];
        return "EXACT_SCOPE_EVIDENCE";
    }
    private static void expect(String reason, CapabilityPolicy.Request request, CapabilityPolicy.Evidence evidence) {
        CapabilityPolicy.Decision result = CapabilityPolicy.assess(request,evidence);
        checks++;
        if (!reason.equals(result.reason()) || result.permitted() != reason.equals("EXACT_SCOPE_EVIDENCE"))
            throw new AssertionError("Expected " + reason + " got " + result + " for " + request + " / " + evidence);
    }
    private static void precedence() {
        CapabilityPolicy.Request req = request(CapabilityPolicy.Scope.THIRD_PARTY,CapabilityPolicy.Claim.ATOMIC_UPDATE);
        expect("EVIDENCE_MISSING",req,null);
        for (CapabilityPolicy.Provider wrong : new CapabilityPolicy.Provider[]{
                new CapabilityPolicy.Provider("other","revision"),new CapabilityPolicy.Provider("provider","other")}) {
            expect("PROVIDER_MISMATCH",req,new CapabilityPolicy.Evidence(wrong,"wrong","b".repeat(64),CapabilityPolicy.Scope.SYNTHETIC,CapabilityPolicy.Isolation.HOST_UID_NAMESPACE,claims(ADV)));
        }
        expect("ENVIRONMENT_MISMATCH",req,new CapabilityPolicy.Evidence(PROVIDER,"wrong","b".repeat(64),CapabilityPolicy.Scope.SYNTHETIC,CapabilityPolicy.Isolation.HOST_UID_NAMESPACE,claims(ADV)));
        expect("SYNTHETIC_ONLY",req,new CapabilityPolicy.Evidence(PROVIDER,"environment","b".repeat(64),CapabilityPolicy.Scope.SYNTHETIC,CapabilityPolicy.Isolation.HOST_UID_NAMESPACE,claims(ADV)));
        expect("CLUSTER_MISMATCH",req,new CapabilityPolicy.Evidence(PROVIDER,"environment","b".repeat(64),CapabilityPolicy.Scope.THIRD_PARTY,CapabilityPolicy.Isolation.HOST_UID_NAMESPACE,claims(ADV)));
        expect("ISOLATION_MODE_UNSUPPORTED",req,evidence(0,CapabilityPolicy.Isolation.HOST_UID_NAMESPACE,CapabilityPolicy.Scope.THIRD_PARTY));
        expect("CLAIM_MISSING",req,evidence(ADV,CapabilityPolicy.Isolation.SEPARATE_OS_UID,CapabilityPolicy.Scope.THIRD_PARTY));
        expect("IMPORT_PROOF_MISSING",req,evidence(UPDATE | EXECUTE | ADV,CapabilityPolicy.Isolation.SEPARATE_OS_UID,CapabilityPolicy.Scope.THIRD_PARTY));
        expect("EXECUTION_PROOF_MISSING",req,evidence(IMPORT | READBACK | UPDATE | ADV,CapabilityPolicy.Isolation.SEPARATE_OS_UID,CapabilityPolicy.Scope.THIRD_PARTY));
        expect("ADVERSARIAL_PROOF_MISSING",req,evidence(IMPORT | READBACK | EXECUTE | UPDATE,CapabilityPolicy.Isolation.SEPARATE_OS_UID,CapabilityPolicy.Scope.THIRD_PARTY));
        try { CapabilityPolicy.assess(null,null); throw new AssertionError("Null request accepted"); }
        catch (NullPointerException expected) { checks++; }
    }
    private static void focused() {
        for (CapabilityPolicy.Isolation mode : CapabilityPolicy.Isolation.values()) {
            // Synthetic non-adversarial dependency-complete proofs stay supported in every mode.
            for (CapabilityPolicy.Claim claim : CapabilityPolicy.Claim.values()) if (claim != CapabilityPolicy.Claim.ADVERSARIAL_ISOLATION)
                expect("EXACT_SCOPE_EVIDENCE",request(CapabilityPolicy.Scope.SYNTHETIC,claim),evidence(31,mode,CapabilityPolicy.Scope.SYNTHETIC));
            for (CapabilityPolicy.Claim claim : new CapabilityPolicy.Claim[]{CapabilityPolicy.Claim.SIGNED_CLUSTER_IMPORT,CapabilityPolicy.Claim.PRIVATE_CLUSTER_READBACK})
                expect("EXACT_SCOPE_EVIDENCE",request(CapabilityPolicy.Scope.THIRD_PARTY,claim),evidence(IMPORT | READBACK,mode,CapabilityPolicy.Scope.THIRD_PARTY));
        }
        for (CapabilityPolicy.Isolation weak : new CapabilityPolicy.Isolation[]{CapabilityPolicy.Isolation.UNKNOWN,CapabilityPolicy.Isolation.HOST_UID_NAMESPACE}) {
            // Each disjunct of the strong-mode obligation must independently reject.
            expect("ISOLATION_MODE_UNSUPPORTED",request(CapabilityPolicy.Scope.SYNTHETIC,CapabilityPolicy.Claim.SIGNED_CLUSTER_IMPORT),evidence(IMPORT | ADV,weak,CapabilityPolicy.Scope.SYNTHETIC));
            expect("ISOLATION_MODE_UNSUPPORTED",request(CapabilityPolicy.Scope.SYNTHETIC,CapabilityPolicy.Claim.ADVERSARIAL_ISOLATION),evidence(0,weak,CapabilityPolicy.Scope.SYNTHETIC));
            for (CapabilityPolicy.Claim claim : new CapabilityPolicy.Claim[]{CapabilityPolicy.Claim.SPLIT_EXECUTION,CapabilityPolicy.Claim.GUEST_LIFECYCLE,CapabilityPolicy.Claim.ATOMIC_UPDATE}) {
                expect("ISOLATION_MODE_UNSUPPORTED",request(CapabilityPolicy.Scope.THIRD_PARTY,claim),evidence(31,weak,CapabilityPolicy.Scope.THIRD_PARTY));
                expect("ISOLATION_MODE_UNSUPPORTED",request(CapabilityPolicy.Scope.THIRD_PARTY,claim),evidence(63,weak,CapabilityPolicy.Scope.THIRD_PARTY));
            }
        }
    }
    public static void main(String[] args) {
        for (int mask = 0; mask < 64; mask++)
            for (CapabilityPolicy.Isolation mode : CapabilityPolicy.Isolation.values())
                for (CapabilityPolicy.Scope requestScope : CapabilityPolicy.Scope.values())
                    for (CapabilityPolicy.Scope evidenceScope : CapabilityPolicy.Scope.values())
                        for (CapabilityPolicy.Claim claim : CapabilityPolicy.Claim.values())
                            expect(expected(mask,mode,requestScope,evidenceScope,claim),request(requestScope,claim),evidence(mask,mode,evidenceScope));
        precedence(); focused();
        System.out.println("D3 independent policy checks: " + checks);
    }
}
