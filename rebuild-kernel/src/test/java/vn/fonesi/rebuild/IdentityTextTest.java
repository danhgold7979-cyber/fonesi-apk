package vn.fonesi.rebuild;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import static vn.fonesi.rebuild.CapabilityPolicy.*;
import static vn.fonesi.rebuild.OperationProtocol.*;

/** Desired B1b contract tests, separate from historical unsafe acceptance observations. */
public final class IdentityTextTest {
    private static int checks;
    private static final UUID ID = UUID.fromString("12345678-1234-5678-1234-567812345678");
    private static final String DIGEST = "a".repeat(64);
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError(); }
    private static void rejected(Action action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | IOException expected) { checks++; return; }
        throw new AssertionError("Unsafe identity accepted");
    }
    private static Identity identity(String environment) {
        return new Identity(ID, new Provider("p?", "r"), "example.synthetic", environment,
                Scope.SYNTHETIC, ID, DIGEST, Set.of(ID));
    }
    private static void boundary(String text, boolean valid) throws Exception {
        Action[] paths = {
            () -> check(new Provider(text, "r").id().equals(text)),
            () -> check(new Provider("p", text).revision().equals(text)),
            () -> check(identity(text).environment().equals(text)),
            () -> check(new Request(new Provider("p", "r"), text, DIGEST, Scope.SYNTHETIC,
                    Claim.ATOMIC_UPDATE).environment().equals(text)),
            () -> check(new Evidence(new Provider("p", "r"), text, DIGEST, Scope.SYNTHETIC,
                    Isolation.UNKNOWN, Set.of()).environment().equals(text))
        };
        for (Action path : paths) { if (valid) path.run(); else rejected(path); }
    }
    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw new AssertionError(e.getCause());
        }
    }
    private static byte[] historicalInvalid(String text, int field, State state) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(0x464f4e32); out.writeInt(2);
            out.writeLong(ID.getMostSignificantBits()); out.writeLong(ID.getLeastSignificantBits());
            out.writeUTF(field == 0 ? text : "p?"); out.writeUTF(field == 1 ? text : "r");
            out.writeUTF("example.synthetic"); out.writeUTF(field == 2 ? text : "synthetic-env");
            out.writeUTF("SYNTHETIC");
            out.writeLong(ID.getMostSignificantBits()); out.writeLong(ID.getLeastSignificantBits());
            out.writeUTF(DIGEST); out.writeInt(1);
            out.writeLong(ID.getMostSignificantBits()); out.writeLong(ID.getLeastSignificantBits());
            out.writeUTF(state.name()); out.writeLong(state == State.PREPARED ? 0 : 1);
        }
        byte[] body = buffer.toByteArray();
        buffer.writeBytes(MessageDigest.getInstance("SHA-256").digest(body));
        return buffer.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        for (String bad : new String[]{null, "", " ", "p\ud800", "p\udc00", "\udc00\ud800", "\ud800x", "x\udc00y",
                "a".repeat(257), "\ud83d\ude00".repeat(129)}) boundary(bad, false);
        for (int c = 0xd800; c <= 0xdfff; c++) boundary("x" + (char)c + "y", false);
        for (int c = 0; c <= 0x9f; c++) if (c <= 0x1f || c >= 0x7f) boundary("p" + (char)c + "x", false);
        int[] spaces = {9,10,11,12,13,32,133,160,5760,8192,8193,8194,8195,8196,8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288};
        for (int c : spaces) {
            String s = Character.toString(c);
            boundary(s, false); boundary(s + "x", false); boundary("x" + s, false);
            boundary("x" + s + "y", c > 0x9f || c == 32);
        }
        for (String good : new String[]{"\ud7ff", "\ue000", "\udbff\udfff", "p?", "é", "e\u0301", "\u200b", "x\u00a0y", "a".repeat(256),
                "\u0800".repeat(256), "\ud83d\ude00".repeat(128), "x".repeat(254) + "\ud83d\ude00"}) boundary(good, true);
        check(!new Provider("é", "r").equals(new Provider("e\u0301", "r")));
        Map<String,String> vectors = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("../spec/bridge-oracle/v1/observations.txt"))) {
            int split = line.indexOf('='); vectors.put(line.substring(0, split), line.substring(split + 1));
        }
        Method decode = OperationJournal.class.getDeclaredMethod("decode", byte[].class);
        Method encode = OperationJournal.class.getDeclaredMethod("encode", Operation.class);
        Method scope = SharedUpdateDispatcher.class.getDeclaredMethod("scopeDirectory", SharedUpdateDispatcher.Request.class);
        decode.setAccessible(true); encode.setAccessible(true); scope.setAccessible(true);
        Path root = Files.createTempDirectory("fonesi-b1b-synthetic-");
        try {
            OperationJournal journal = new OperationJournal(root);
            SharedUpdateDispatcher dispatcher = new SharedUpdateDispatcher(root);
            for (String bad : new String[]{"p\ud800", "p\udc00", "p\u0000x", "\u00a0", "x\u00a0", "\u0085x", "a".repeat(257)}) {
                for (int field = 0; field < 3; field++) for (State state : new State[]{State.PREPARED, State.DISPATCHED}) {
                    byte[] bytes = historicalInvalid(bad, field, state);
                    Path file = root.resolve(ID + ".journal"); Files.write(file, bytes);
                    rejected(() -> invoke(decode, null, (Object)bytes));
                    rejected(() -> journal.read(ID).recover());
                    int[] calls = {0}; rejected(() -> journal.inspect(op -> calls[0]++));
                    var request = new SharedUpdateDispatcher.Request("example.synthetic", identity("synthetic-env"), "synthetic-env", Scope.SYNTHETIC);
                    rejected(() -> dispatcher.execute(request, r -> { calls[0]++; return null; },
                            r -> calls[0]++, (r,v) -> { calls[0]++; return null; }));
                    check(calls[0] == 0); check(Arrays.equals(bytes, Files.readAllBytes(file)));
                    Files.delete(file);
                }
            }
            // Streaming inspection may deliver valid rows before failure; it must never deliver the invalid row.
            UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
            Operation valid = Operation.prepare(new Identity(other, new Provider("other", "r"), "example.synthetic",
                    "synthetic-env", Scope.SYNTHETIC, other, DIGEST, Set.of(other)));
            journal.create(valid);
            byte[] invalid = historicalInvalid("p\ud800", 0, State.DISPATCHED);
            Path badFile = root.resolve(ID + ".journal"); Files.write(badFile, invalid);
            List<Operation> delivered = new ArrayList<>(); rejected(() -> journal.inspect(delivered::add));
            check(delivered.stream().allMatch(valid::equals));
            int[] external = {0};
            var mixedRequest = new SharedUpdateDispatcher.Request("example.synthetic", identity("synthetic-env"), "synthetic-env", Scope.SYNTHETIC);
            rejected(() -> dispatcher.execute(mixedRequest, r -> { external[0]++; return null; },
                    r -> external[0]++, (r,v) -> { external[0]++; return null; }));
            check(external[0] == 0); check(Arrays.equals(invalid, Files.readAllBytes(badFile)));
            check(journal.read(other).equals(valid));
            Files.delete(badFile); Files.delete(root.resolve(other + ".journal"));
            for (Map.Entry<String,String> vector : vectors.entrySet()) {
                if (!vector.getKey().startsWith("codec.") || !vector.getKey().endsWith(".hex")) continue;
                byte[] bytes = HexFormat.of().parseHex(vector.getValue());
                Path file = root.resolve(ID + ".journal");
                Files.write(file, bytes);
                if (vector.getKey().contains("OBSERVED-")) {
                    rejected(() -> invoke(decode, null, (Object)bytes));
                    rejected(() -> journal.read(ID).recover());
                    int[] callbacks = {0};
                    rejected(() -> journal.inspect(op -> callbacks[0]++));
                    SharedUpdateDispatcher.Request request = new SharedUpdateDispatcher.Request("example.synthetic", identity("synthetic-env"), "synthetic-env", Scope.SYNTHETIC);
                    rejected(() -> dispatcher.execute(request, r -> { callbacks[0]++; return null; },
                            r -> callbacks[0]++, (r,v) -> { callbacks[0]++; return null; }));
                    check(callbacks[0] == 0); check(Arrays.equals(bytes, Files.readAllBytes(file)));
                } else {
                    Operation operation = (Operation)invoke(decode, null, (Object)bytes);
                    check(Arrays.equals(bytes, (byte[])invoke(encode, null, operation)));
                    check(journal.read(ID).equals(operation));
                    if (operation.state() == State.DISPATCHED) check(operation.recover().state() == State.REVIEW_REQUIRED);
                    String label = vector.getKey().contains("supplementary") ? "supplementary"
                            : vector.getKey().contains("literal-question") ? "literal-question" : "ascii";
                    Identity id = operation.identity();
                    var request = new SharedUpdateDispatcher.Request(id.packageName(), id, id.environment(), id.scope());
                    Path namespace = (Path)invoke(scope, dispatcher, request);
                    check(namespace.getFileName().toString().equals(vectors.get("scope." + label + ".hash")));
                }
                Files.delete(file);
            }
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("PASS " + checks + " B1b identity/parity/no-replay checks (synthetic only)");
    }
}
