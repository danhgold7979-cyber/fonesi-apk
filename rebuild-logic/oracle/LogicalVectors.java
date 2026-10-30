package vn.fonesi.rebuild.logical;

import java.util.*;

/** Portable semantic observations. Package declaration is the only host rewrite. */
public final class LogicalVectors {
    private LogicalVectors() {}
    static final String DIGEST = "a".repeat(64);
    static final UUID OP = new UUID(0, 1), A = new UUID(0, 2), B = new UUID(-1, 3);
    static final CapabilityPolicy.Provider PROVIDER = new CapabilityPolicy.Provider("provider", "revision");
    private static final SortedMap<String, String> VALUES = new TreeMap<>();
    private interface Action { Object run(); }
    private static void put(String key, String value) {
        if (VALUES.put(key, value) != null) throw new AssertionError("Duplicate vector: " + key);
    }
    private static void outcome(String key, Action action) {
        try { Object value = action.run();
            if (value instanceof OperationProtocol.Operation) {
                OperationProtocol.Operation op = (OperationProtocol.Operation) value;
                put(key, op.state().name() + ":" + op.revision());
            } else put(key, "ACCEPT");
        } catch (IllegalArgumentException e) { put(key, "IllegalArgumentException");
        } catch (IllegalStateException e) { put(key, "IllegalStateException");
        } catch (NullPointerException e) { put(key, "NullPointerException"); }
    }
    static OperationProtocol.Identity identity(Set<UUID> peers) {
        return new OperationProtocol.Identity(OP, PROVIDER, "test.package", "environment",
                CapabilityPolicy.Scope.THIRD_PARTY, A, DIGEST, peers);
    }
    private static OperationProtocol.Readback readback(UUID operation, long revision,
            CapabilityPolicy.Provider provider, String digest, boolean privateStorage, Set<UUID> peers) {
        Map<UUID, OperationProtocol.PeerObservation> values = new HashMap<>();
        for (UUID peer : peers) values.put(peer, new OperationProtocol.PeerObservation(provider, digest, privateStorage));
        return new OperationProtocol.Readback(operation, revision, values);
    }
    private static void policy() {
        for (int mask = 0; mask < 64; mask++) {
            Set<CapabilityPolicy.Claim> claims = EnumSet.noneOf(CapabilityPolicy.Claim.class);
            for (CapabilityPolicy.Claim claim : CapabilityPolicy.Claim.values())
                if ((mask & (1 << claim.ordinal())) != 0) claims.add(claim);
            for (CapabilityPolicy.Isolation isolation : CapabilityPolicy.Isolation.values())
                for (CapabilityPolicy.Scope evidenceScope : CapabilityPolicy.Scope.values())
                    for (CapabilityPolicy.Scope requestScope : CapabilityPolicy.Scope.values())
                        for (CapabilityPolicy.Claim claim : CapabilityPolicy.Claim.values()) {
                            CapabilityPolicy.Evidence evidence = new CapabilityPolicy.Evidence(PROVIDER, "environment", DIGEST, evidenceScope, isolation, claims);
                            CapabilityPolicy.Request request = new CapabilityPolicy.Request(PROVIDER, "environment", DIGEST, requestScope, claim);
                            CapabilityPolicy.Decision result = CapabilityPolicy.assess(request, evidence);
                            put("policy/" + mask + "/" + isolation + "/" + evidenceScope + "/" + requestScope + "/" + claim,
                                    result.permitted() + ":" + result.reason());
                        }
        }
        CapabilityPolicy.Request request = new CapabilityPolicy.Request(PROVIDER, "environment", DIGEST, CapabilityPolicy.Scope.THIRD_PARTY, CapabilityPolicy.Claim.SIGNED_CLUSTER_IMPORT);
        put("policy/missing", CapabilityPolicy.assess(request, null).reason());
        for (int kind = 0; kind < 5; kind++) {
            CapabilityPolicy.Evidence evidence = new CapabilityPolicy.Evidence(
                    kind == 0 ? new CapabilityPolicy.Provider("different", "revision") : kind == 1 ? new CapabilityPolicy.Provider("provider", "different") : PROVIDER,
                    kind == 2 ? "different" : "environment", kind == 3 ? "b".repeat(64) : DIGEST,
                    kind == 4 ? CapabilityPolicy.Scope.SYNTHETIC : CapabilityPolicy.Scope.THIRD_PARTY,
                    CapabilityPolicy.Isolation.SEPARATE_OS_UID, EnumSet.allOf(CapabilityPolicy.Claim.class));
            put("policy/mismatch/" + kind, CapabilityPolicy.assess(request, evidence).reason());
        }
    }
    private static void text(String key, String text) {
        outcome("text/" + key + "/id", () -> new CapabilityPolicy.Provider(text, "revision"));
        outcome("text/" + key + "/revision", () -> new CapabilityPolicy.Provider("provider", text));
        outcome("text/" + key + "/request", () -> new CapabilityPolicy.Request(PROVIDER, text, DIGEST, CapabilityPolicy.Scope.THIRD_PARTY, CapabilityPolicy.Claim.SIGNED_CLUSTER_IMPORT));
        outcome("text/" + key + "/evidence", () -> new CapabilityPolicy.Evidence(PROVIDER, text, DIGEST, CapabilityPolicy.Scope.THIRD_PARTY, CapabilityPolicy.Isolation.UNKNOWN, Collections.emptySet()));
        outcome("text/" + key + "/identity", () -> new OperationProtocol.Identity(OP, PROVIDER, "test.package", text, CapabilityPolicy.Scope.THIRD_PARTY, A, DIGEST, Collections.singleton(A)));
    }
    private static void identities() {
        text("null", null); text("empty", ""); text("ascii", "provider"); text("question", "p?");
        text("nfc", "\u00e9"); text("nfd", "e\u0301"); text("cf", "\u200b");
        text("bmp256", "\u0800".repeat(256)); text("bmp257", "\u0800".repeat(257));
        text("pair128", "\ud83d\ude00".repeat(128)); text("pair129", "\ud83d\ude00".repeat(129));
        text("beforeSurrogate", "\ud7ff"); text("afterSurrogate", "\ue000"); text("maxScalar", "\udbff\udfff");
        for (int cp = 0; cp <= 0x9f; cp++) if (cp <= 0x1f || cp >= 0x7f) text("cc" + cp, "a" + (char)cp + "b");
        for (int cp = 0xd800; cp <= 0xdfff; cp++) text("surrogate" + cp, "p" + (char)cp);
        int[] whitespace = {9,10,11,12,13,32,133,160,5760,8192,8193,8194,8195,8196,8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288};
        for (int cp : whitespace) { text("edgeL" + cp, (char)cp + "a"); text("edgeR" + cp, "a" + (char)cp); text("inside" + cp, "a" + (char)cp + "b"); }
        for (String pkg : new String[]{"test.package", "a", "a..b", "1a.b", "a._b", "a.b_1", "a." + "b".repeat(253), "a." + "b".repeat(254)})
            outcome("package/" + pkg, () -> new OperationProtocol.Identity(OP, PROVIDER, pkg, "environment", CapabilityPolicy.Scope.THIRD_PARTY, A, DIGEST, Collections.singleton(A)));
        for (String digest : new String[]{"", "A".repeat(64), "a".repeat(63), "a".repeat(65), DIGEST})
            outcome("digest/" + digest, () -> new CapabilityPolicy.Request(PROVIDER, "environment", digest, CapabilityPolicy.Scope.THIRD_PARTY, CapabilityPolicy.Claim.SIGNED_CLUSTER_IMPORT));
        outcome("null/providerId", () -> new CapabilityPolicy.Provider(null,"revision"));
        outcome("null/evidenceProvider", () -> new CapabilityPolicy.Evidence(null,"environment",DIGEST,CapabilityPolicy.Scope.SYNTHETIC,CapabilityPolicy.Isolation.UNKNOWN,Collections.emptySet()));
        outcome("null/claims", () -> new CapabilityPolicy.Evidence(PROVIDER,"environment",DIGEST,CapabilityPolicy.Scope.SYNTHETIC,CapabilityPolicy.Isolation.UNKNOWN,null));
        outcome("null/claimMember", () -> new CapabilityPolicy.Evidence(PROVIDER,"environment",DIGEST,CapabilityPolicy.Scope.SYNTHETIC,CapabilityPolicy.Isolation.UNKNOWN,Collections.singleton(null)));
        outcome("null/affected", () -> identity(null));
        outcome("null/affectedMember", () -> identity(new HashSet<>(Arrays.asList(A,null))));
        outcome("null/readbackPeers", () -> new OperationProtocol.Readback(OP,1,null));
        outcome("null/readbackPeerValue", () -> new OperationProtocol.Readback(OP,1,Collections.singletonMap(A,null)));
        outcome("null/readbackPeerKey", () -> new OperationProtocol.Readback(OP,1,Collections.singletonMap(null,new OperationProtocol.PeerObservation(PROVIDER,DIGEST,true))));
        outcome("null/assessmentRequest", () -> CapabilityPolicy.assess(null,null));
        outcome("identity/absentTarget", () -> identity(Collections.singleton(B)));
    }
    private static void operations() {
        OperationProtocol.Identity identity = identity(new HashSet<>(Arrays.asList(A,B)));
        for (OperationProtocol.State state : OperationProtocol.State.values()) {
            for (long revision : new long[]{-1,0,1,2,3,Long.MAX_VALUE})
                outcome("state/" + state + "/" + revision, () -> new OperationProtocol.Operation(identity, state, revision));
            long revision = state == OperationProtocol.State.PREPARED ? 0 : state == OperationProtocol.State.DISPATCHED || state == OperationProtocol.State.CANCELLED ? 1 : 2;
            OperationProtocol.Operation op = new OperationProtocol.Operation(identity, state, revision);
            outcome("recover/" + state, op::recover);
            for (long expected : new long[]{-1,0,1,2,Long.MAX_VALUE}) {
                outcome("dispatch/" + state + "/" + expected, () -> op.dispatch(expected, identity));
                outcome("cancel/" + state + "/" + expected, () -> op.cancel(expected));
                outcome("verify/" + state + "/" + expected, () -> op.verify(expected, readback(OP, 1, PROVIDER, DIGEST, true, identity.affectedClones())));
            }
        }
        OperationProtocol.Operation prepared = OperationProtocol.Operation.prepare(identity);
        outcome("dispatch/identityDrift", () -> prepared.dispatch(0, identity(Collections.singleton(A))));
        for (int field = 0; field < 9; field++) {
            OperationProtocol.Identity changed = new OperationProtocol.Identity(field == 0 ? B : OP,
                    field == 1 ? new CapabilityPolicy.Provider("other", "revision") : field == 2 ? new CapabilityPolicy.Provider("provider", "other") : PROVIDER,
                    field == 3 ? "other.package" : "test.package", field == 4 ? "other" : "environment",
                    field == 5 ? CapabilityPolicy.Scope.SYNTHETIC : CapabilityPolicy.Scope.THIRD_PARTY,
                    field == 6 ? B : A, field == 7 ? "b".repeat(64) : DIGEST,
                    field == 8 ? Collections.singleton(A) : identity.affectedClones());
            outcome("dispatch/driftField" + field, () -> prepared.dispatch(0, changed));
        }
        outcome("dispatch/nullIdentity", () -> prepared.dispatch(0, null));
        OperationProtocol.Operation dispatched = prepared.dispatch(0, identity);
        outcome("readback/null", () -> dispatched.verify(1, null));
        for (int variant = 0; variant < 9; variant++) {
            OperationProtocol.Readback rb = readback(variant == 0 ? B : OP, variant == 1 ? 2 : 1,
                    variant == 2 ? new CapabilityPolicy.Provider("provider", "other") : PROVIDER,
                    variant == 3 ? "b".repeat(64) : variant == 8 ? "malformed" : DIGEST, variant != 4,
                    variant == 5 ? Collections.singleton(A) : variant == 6 ? new HashSet<>(Arrays.asList(A,B,OP)) : identity.affectedClones());
            outcome("readback/variant" + variant, () -> dispatched.verify(1, rb));
        }
        Map<UUID, OperationProtocol.PeerObservation> mixed = new HashMap<>();
        mixed.put(A,new OperationProtocol.PeerObservation(PROVIDER,DIGEST,true));
        mixed.put(B,new OperationProtocol.PeerObservation(PROVIDER,DIGEST,false));
        outcome("readback/oneBadPeer", () -> dispatched.verify(1,new OperationProtocol.Readback(OP,1,mixed)));
        outcome("readback/zeroRevision", () -> readback(OP,0,PROVIDER,DIGEST,true,identity.affectedClones()));
    }
    public static String observations() {
        VALUES.clear(); policy(); identities(); operations();
        StringBuilder output = new StringBuilder();
        VALUES.forEach((key,value) -> output.append(key).append('=').append(value).append('\n'));
        return output.toString();
    }
    public static void main(String[] args) { System.out.print(observations()); }
}
