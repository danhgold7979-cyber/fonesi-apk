package vn.fonesi.rebuild;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import static vn.fonesi.rebuild.CapabilityPolicy.*;
import static vn.fonesi.rebuild.OperationProtocol.*;

/** Test-only compatibility observations, NOT approval of every observed input. */
public final class BridgeOracleTest {
    private static final UUID OP = UUID.fromString("12345678-1234-5678-1234-567812345678");
    private static final UUID CLONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DIGEST = "0123456789abcdef".repeat(4);
    private static final Map<String, String> observed = new TreeMap<>();
    private static int checks;
    @FunctionalInterface private interface Probe { void run() throws Exception; }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void observe(String key, String value) {
        if (observed.putIfAbsent(key, value) != null) throw new AssertionError("duplicate observation key: " + key);
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof Exception exception) throw exception;
            if (wrapped.getCause() instanceof Error error) throw error;
            throw new AssertionError(wrapped.getCause());
        }
    }
    private static void classify(String key, Probe probe) throws Exception {
        try { probe.run(); observe(key, "ACCEPTED_OBSERVATION_ONLY"); }
        catch (IllegalArgumentException | NullPointerException | IOException expected) {
            observe(key, "REJECTED:" + expected.getClass().getSimpleName());
        }
    }
    private static Identity identity(String provider, String revision, String environment, Set<UUID> peers) {
        return new Identity(OP, new Provider(provider, revision), "example.synthetic", environment,
                Scope.SYNTHETIC, CLONE, DIGEST, peers);
    }
    private static void encoded(Method encode, Method decode, String label, Operation operation) throws Exception {
        byte[] bytes = (byte[]) invoke(encode, null, operation);
        observe("codec." + label + ".hex", HexFormat.of().formatHex(bytes));
        observe("codec." + label + ".sha256", sha(bytes));
        check(operation.equals(invoke(decode, null, (Object) bytes)), "codec roundtrip " + label);
        check(Arrays.equals(bytes, (byte[]) invoke(encode, null, operation)), "nondeterministic codec " + label);
    }
    private static byte[] reseal(byte[] bytes) throws Exception {
        byte[] body = Arrays.copyOf(bytes, bytes.length - 32);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(body);
        System.arraycopy(hash, 0, bytes, body.length, hash.length);
        return bytes;
    }
    private static void publicRead(OperationJournal journal, Path root, byte[] bytes) throws Exception {
        Files.write(root.resolve(OP + ".journal"), bytes);
        journal.read(OP);
    }
    private static String scope(Method scope, SharedUpdateDispatcher dispatcher, String provider, String revision) throws Exception {
        Identity id = identity(provider, revision, "synthetic-env", Set.of(CLONE));
        SharedUpdateDispatcher.Request request = new SharedUpdateDispatcher.Request("example.synthetic", id,
                "synthetic-env", Scope.SYNTHETIC);
        return ((Path) invoke(scope, dispatcher, request)).getFileName().toString();
    }
    private static void verifySources(Path repo, Path fixtures) throws Exception {
        List<String> manifest = Files.readAllLines(fixtures.resolve("source-sha256.txt"), StandardCharsets.UTF_8);
        check(manifest.size() == 4, "four pinned production sources required");
        Set<String> expected = Set.of("CapabilityPolicy.java", "OperationProtocol.java", "OperationJournal.java", "SharedUpdateDispatcher.java");
        Set<String> seen = new java.util.HashSet<>();
        for (String line : manifest) {
            String[] parts = line.split("  ", 2);
            check(parts.length == 2 && parts[0].matches("[0-9a-f]{64}"), "bad source manifest");
            Path file = repo.resolve(parts[1]).normalize();
            check(file.getParent().equals(repo.resolve("rebuild-kernel/src/main/java/vn/fonesi/rebuild")), "source outside oracle");
            check(expected.contains(file.getFileName().toString()) && seen.add(file.getFileName().toString()), "unexpected/duplicate production source");
            check(parts[0].equals(sha(Files.readAllBytes(file))), "oracle source drift: " + file.getFileName());
        }
        check(seen.equals(expected), "incomplete oracle manifest");
    }
    public static void main(String[] args) throws Exception {
        Path repo = Path.of("..").toAbsolutePath().normalize(); // check.sh runs from rebuild-kernel
        Path fixtures = repo.resolve("spec/bridge-oracle/v1");
        verifySources(fixtures.resolve("source"), fixtures); // fail before executing any drifted oracle
        Method encode = OperationJournal.class.getDeclaredMethod("encode", Operation.class);
        Method decode = OperationJournal.class.getDeclaredMethod("decode", byte[].class);
        Method scope = SharedUpdateDispatcher.class.getDeclaredMethod("scopeDirectory", SharedUpdateDispatcher.Request.class);
        encode.setAccessible(true); decode.setAccessible(true); scope.setAccessible(true);
        Identity base = identity("provider-ascii", "revision-1", "synthetic-env", Set.of(CLONE));
        for (State state : State.values()) {
            long reachable = switch (state) {
                case PREPARED -> 0;
                case DISPATCHED, CANCELLED -> 1;
                case VERIFIED, REVIEW_REQUIRED -> 2;
            };
            encoded(encode, decode, "ascii." + state.name(), new Operation(base, state, reachable));
            for (long revision = -1; revision <= 3; revision++) {
                final long value = revision;
                classify("state." + state + "." + value, () -> new Operation(base, state, value));
            }
        }
        UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");
        UUID negative = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID lowHigh = UUID.fromString("00000000-0000-0000-8000-000000000000");
        UUID positive = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        Set<UUID> peers = Set.of(CLONE, high, negative, lowHigh, positive);
        observe("uuid.signed-natural-order", String.join(",", peers.stream().sorted().map(UUID::toString).toList()));
        encoded(encode, decode, "high-bit-peers", Operation.prepare(identity("provider-ascii", "revision-1", "synthetic-env", peers)));
        encoded(encode, decode, "supplementary", Operation.prepare(identity("provider-\uD83D\uDE00", "r-\uD834\uDD1E", "env-\uD83D\uDE80", peers)));
        encoded(encode, decode, "OBSERVED-embedded-NUL", Operation.prepare(identity("p\u0000x", "r", "env", Set.of(CLONE))));
        encoded(encode, decode, "literal-question", Operation.prepare(identity("p?", "r", "env", Set.of(CLONE))));
        encoded(encode, decode, "OBSERVED-lone-surrogate", Operation.prepare(identity("p\uD800", "r", "env", Set.of(CLONE))));
        Map<String, String> textCases = new TreeMap<>();
        textCases.put("empty", ""); textCases.put("ascii-blank", " "); textCases.put("leading-space", " p");
        textCases.put("em-space", "\u2003"); textCases.put("nbsp", "\u00A0"); textCases.put("embedded-NUL", "p\u0000x");
        textCases.put("lone-high-surrogate", "p\uD800"); textCases.put("lone-low-surrogate", "p\uDC00");
        textCases.put("supplementary", "provider-\uD83D\uDE00"); textCases.put("length-256", "p".repeat(256));
        textCases.put("length-257", "p".repeat(257));
        for (Map.Entry<String, String> entry : textCases.entrySet()) {
            classify("text.provider." + entry.getKey(), () -> new Provider(entry.getValue(), "r"));
            classify("text.environment." + entry.getKey(), () -> identity("p", "r", entry.getValue(), Set.of(CLONE)));
        }
        classify("null.provider-id", () -> new Provider(null, "r"));
        classify("null.environment", () -> identity("p", "r", null, Set.of(CLONE)));
        classify("null.peers", () -> identity("p", "r", "env", null));
        Path temp = Files.createTempDirectory("fonesi-oracle-synthetic-");
        try {
            Path scopeRoot = Files.createDirectory(temp.resolve("scopes"));
            SharedUpdateDispatcher dispatcher = new SharedUpdateDispatcher(scopeRoot);
            for (String label : List.of("ascii", "supplementary", "embedded-NUL", "lone-high", "lone-low", "literal-question")) {
                String provider = switch (label) {
                    case "ascii" -> "provider-ascii";
                    case "supplementary" -> "provider-\uD83D\uDE00";
                    case "embedded-NUL" -> "p\u0000x";
                    case "lone-high" -> "p\uD800";
                    case "lone-low" -> "p\uDC00";
                    case "literal-question" -> "p?";
                    default -> throw new AssertionError();
                };
                observe("scope." + label + ".utf16-length", Integer.toString(provider.length()));
                observe("scope." + label + ".utf8-hex", HexFormat.of().formatHex(provider.getBytes(StandardCharsets.UTF_8)));
                observe("scope." + label + ".hash", scope(scope, dispatcher, provider, "r"));
            }
            observe("scope.revision-omitted", Boolean.toString(scope(scope, dispatcher, "provider-ascii", "r1")
                    .equals(scope(scope, dispatcher, "provider-ascii", "r2"))));
            // Defect observation only: this MUST NOT become required portable acceptance.
            observe("DEFECT.lone-surrogate-scope-alias", Boolean.toString(scope(scope, dispatcher, "p\uD800", "r")
                    .equals(scope(scope, dispatcher, "p\uDC00", "r"))));
            observe("DEFECT.malformed-aliases-valid-provider", Boolean.toString(scope(scope, dispatcher, "p\uD800", "r")
                    .equals(scope(scope, dispatcher, "p?", "r"))));
            check(!new Provider("p\uD800", "r").equals(new Provider("p?", "r")), "distinct UTF16 provider identities");
            Path journalRoot = Files.createDirectory(temp.resolve("journal"));
            OperationJournal journal = new OperationJournal(journalRoot);
            byte[] valid = (byte[]) invoke(encode, null, Operation.prepare(base));
            classify("read.valid", () -> publicRead(journal, journalRoot, valid));
            byte[] corrupt = valid.clone(); corrupt[10] ^= 1;
            classify("read.bad-checksum", () -> publicRead(journal, journalRoot, corrupt));
            byte[] oldVersion = valid.clone(); ByteBuffer.wrap(oldVersion).putInt(4, 1); reseal(oldVersion);
            classify("read.old-unbound-version", () -> publicRead(journal, journalRoot, oldVersion));
            byte[] unknownVersion = valid.clone(); ByteBuffer.wrap(unknownVersion).putInt(4, 99); reseal(unknownVersion);
            classify("read.unknown-version", () -> publicRead(journal, journalRoot, unknownVersion));
            classify("read.truncated", () -> publicRead(journal, journalRoot, new byte[10]));
            classify("read.oversized", () -> publicRead(journal, journalRoot, new byte[256 * 1024 + 1]));
            classify("read.trailing-byte", () -> publicRead(journal, journalRoot, Arrays.copyOf(valid, valid.length + 1)));
        } finally {
            // Only the exact newly created synthetic tree; never production/private user roots.
            try (var walk = Files.walk(temp)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        StringBuilder result = new StringBuilder();
        observed.forEach((key, value) -> result.append(key).append('=').append(value).append('\n'));
        if (args.length == 1 && args[0].equals("--emit-observations")) {
            System.out.print(result);
            return;
        }
        check(args.length == 0, "unknown test arguments");
        String expected = Files.readString(fixtures.resolve("observations.txt"), StandardCharsets.UTF_8);
        check(result.toString().equals(expected), "oracle observations drift; inspect defects before any fixture update");
        check(observed.size() >= 65, "missing observations");
        System.out.println("PASS " + checks + " oracle checks / " + observed.size()
                + " frozen observations; synthetic only; DEFECT observations are NOT acceptance requirements");
    }
}
