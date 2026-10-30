package vn.fonesi.rebuild;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static vn.fonesi.rebuild.CapabilityPolicy.*;
import static vn.fonesi.rebuild.OperationProtocol.*;

/** B3a historical observations only: accepted ambiguous bytes are NOT port requirements. */
public final class CodecObservationTest {
    private static final UUID OP = new UUID(0, 1), CLONE = new UUID(0, 2);
    private static final String DIGEST = "a".repeat(64);
    private static final List<UUID> PEERS = List.of(new UUID(Long.MIN_VALUE, 0), new UUID(0, Long.MIN_VALUE), CLONE);
    private static final String[] FIELDS = {"p", "r", "a.b", "e", "SYNTHETIC", DIGEST, "PREPARED"};
    private static final SortedMap<String, String> VALUES = new TreeMap<>();
    private static int checks;
    private CodecObservationTest() {}
    private static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Observation check " + checks); }
    private static void put(String key, String value) {
        if (VALUES.putIfAbsent(key, value) != null) throw new AssertionError("Duplicate observation key");
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw new AssertionError(e.getCause());
        }
    }
    private static Operation base(String provider, String revision, String environment) {
        return Operation.prepare(new Identity(OP, new Provider(provider, revision), "a.b", environment,
                Scope.SYNTHETIC, CLONE, DIGEST, new HashSet<>(PEERS)));
    }
    private static byte[] utf(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { out.writeUTF(text); }
        return Arrays.copyOfRange(bytes.toByteArray(), 2, bytes.size());
    }
    private static byte[] seal(byte[] body) throws Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream(); result.writeBytes(body);
        result.writeBytes(MessageDigest.getInstance("SHA-256").digest(body)); return result.toByteArray();
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static byte[] envelope(Map<Integer, byte[]> replacements, List<UUID> peers, int count,
                                   int version, long revision, boolean trailing) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(body)) {
            out.writeInt(0x464f4e32); out.writeInt(version); uuid(out, OP);
            for (int field = 0; field < FIELDS.length; field++) {
                if (field == 5) uuid(out, CLONE);
                if (field == 6) { out.writeInt(count); for (UUID peer : peers) uuid(out, peer); }
                byte[] bytes = replacements.containsKey(field) ? replacements.get(field) : utf(FIELDS[field]);
                out.writeShort(bytes.length); out.write(bytes);
            }
            out.writeLong(revision); if (trailing) out.writeByte(0);
        }
        return seal(body.toByteArray());
    }
    private static void readCase(String name, byte[] bytes, UUID expected, Path root,
                                 Method encode, Operation canonical) throws Exception {
        Path file = root.resolve(expected + ".journal"); Files.write(file, bytes);
        byte[] before = Files.readAllBytes(file);
        Operation decoded = null;
        try { decoded = new OperationJournal(root).read(expected); }
        catch (IOException | IllegalArgumentException expectedFailure) {
            put(name + ".result", "REJECTED:" + expectedFailure.getClass().getSimpleName());
            if (name.startsWith("bounds.")) put(name + ".message", String.valueOf(expectedFailure.getMessage()));
        }
        // A successful read followed by encoder failure must fail this harness, not appear rejected.
        if (decoded != null) {
            byte[] normalized = (byte[]) invoke(encode, null, decoded);
            put(name + ".result", "ACCEPTED_OBSERVATION_ONLY");
            put(name + ".canonicalWire", Boolean.toString(Arrays.equals(bytes, normalized)));
            put(name + ".sameBaseValue", Boolean.toString(canonical.equals(decoded)));
            put(name + ".canonicalSha256", sha(normalized));
        }
        check(Arrays.equals(before, Files.readAllBytes(file)));
        Files.delete(file);
    }
    private static void verifySources(Path repo, Path fixtures) throws Exception {
        List<String> lines = Files.readAllLines(fixtures.resolve("source-sha256.txt"));
        Set<String> expected = Set.of("CapabilityPolicy.java", "IdentityText.java", "OperationJournal.java", "OperationProtocol.java", "SharedUpdateDispatcher.java");
        Set<String> seen = new HashSet<>(); check(lines.size() == 5);
        for (String line : lines) {
            String[] entry = line.split("  ", 2); check(entry.length == 2);
            Path path = repo.resolve(entry[1]).normalize();
            check(path.getParent().equals(repo.resolve("rebuild-kernel/src/main/java/vn/fonesi/rebuild")));
            check(expected.contains(path.getFileName().toString()) && seen.add(path.getFileName().toString()));
            check(sha(Files.readAllBytes(path)).equals(entry[0]));
        }
        check(seen.equals(expected));
    }
    public static void main(String[] args) throws Exception {
        Path repo = Path.of("..").toAbsolutePath().normalize();
        Path fixtures = repo.resolve("spec/codec-observations/b3a"); verifySources(repo, fixtures);
        Method encode = OperationJournal.class.getDeclaredMethod("encode", Operation.class); encode.setAccessible(true);
        Method scope = SharedUpdateDispatcher.class.getDeclaredMethod("scopeDirectory", SharedUpdateDispatcher.Request.class); scope.setAccessible(true);
        Operation canonical = base("p", "r", "e");
        byte[] canonicalBytes = (byte[]) invoke(encode, null, canonical);
        check(Arrays.equals(canonicalBytes, envelope(Map.of(), PEERS, 3, 2, 0, false)));
        put("uuid.signedOrder", String.join(",", PEERS.stream().map(UUID::toString).toList()));
        put("valid.ascii.hex", HexFormat.of().formatHex(canonicalBytes));
        Path root = Files.createTempDirectory("fonesi-b3a-synthetic-");
        try {
            readCase("valid.ascii", canonicalBytes, OP, root, encode, canonical);
            for (State state : State.values()) {
                long revision = state == State.PREPARED ? 0 : state == State.DISPATCHED || state == State.CANCELLED ? 1 : 2;
                Operation value = new Operation(canonical.identity(), state, revision);
                byte[] encoded = (byte[]) invoke(encode, null, value);
                put("valid.state." + state + ".sha256", sha(encoded));
                readCase("valid.state." + state, encoded, OP, root, encode, value);
            }
            Set<UUID> maximumPeers = new HashSet<>();
            for (int n = 0; n < 4096; n++) maximumPeers.add(new UUID(0, n));
            Operation maximum = Operation.prepare(new Identity(OP, canonical.identity().provider(), "a.b", "e",
                    Scope.SYNTHETIC, CLONE, DIGEST, maximumPeers));
            byte[] maximumBytes = (byte[]) invoke(encode, null, maximum);
            put("valid.maxPeers.bytes", Integer.toString(maximumBytes.length));
            readCase("valid.maxPeers", maximumBytes, OP, root, encode, maximum);
            Operation unicode = base("p\ud83d\ude00", "r\ud834\udd1e", "e\ud83d\ude80");
            byte[] unicodeBytes = (byte[]) invoke(encode, null, unicode);
            put("valid.unicode.hex", HexFormat.of().formatHex(unicodeBytes));
            readCase("valid.unicode", unicodeBytes, OP, root, encode, unicode);
            SharedUpdateDispatcher dispatcher = new SharedUpdateDispatcher(root);
            for (Operation value : List.of(canonical, unicode)) {
                Identity id = value.identity();
                var request = new SharedUpdateDispatcher.Request(id.packageName(), id, id.environment(), id.scope());
                String key = ((Path) invoke(scope, dispatcher, request)).getFileName().toString();
                String label = value == canonical ? "scope.ascii" : "scope.unicode";
                put(label, key);
                String framed = id.provider().id().length() + ":" + id.provider().id() + ":" + id.packageName();
                check(key.equals(sha(framed.getBytes(StandardCharsets.UTF_8))));
                put(label + ".utf16Units", Integer.toString(id.provider().id().length()));
                put(label + ".preimageUtf8Hex", HexFormat.of().formatHex(framed.getBytes(StandardCharsets.UTF_8)));
                Identity nextRevision = new Identity(id.operation(), new Provider(id.provider().id(), "new-revision"),
                        id.packageName(), id.environment(), id.scope(), id.cloneId(), id.clusterSha256(), id.affectedClones());
                var nextRequest = new SharedUpdateDispatcher.Request(nextRevision.packageName(), nextRevision, nextRevision.environment(), nextRevision.scope());
                check(key.equals(((Path) invoke(scope, dispatcher, nextRequest)).getFileName().toString()));
            }
            for (int field = 0; field < FIELDS.length; field++) {
                byte[] original = utf(FIELDS[field]); int first = original[0] & 0xff;
                for (int width : new int[]{2, 3}) {
                    byte[] altered = new byte[original.length + width - 1];
                    if (width == 2) { altered[0] = (byte)(0xc0 | first >> 6); altered[1] = (byte)(0x80 | first & 63); }
                    else { altered[0] = (byte)0xe0; altered[1] = (byte)(0x80 | first >> 6); altered[2] = (byte)(0x80 | first & 63); }
                    System.arraycopy(original, 1, altered, width, original.length - 1);
                    readCase("ambiguous.overlong" + width + ".field" + field,
                            envelope(Map.of(field, altered), PEERS, 3, 2, 0, false), OP, root, encode, canonical);
                }
            }
            List<byte[]> malformed = List.of(new byte[]{0}, new byte[]{(byte)0xc0,(byte)0x80},
                    new byte[]{(byte)0xc2}, new byte[]{(byte)0xc2, 0x41}, new byte[]{(byte)0x80},
                    new byte[]{(byte)0xf0,(byte)0x9f,(byte)0x98,(byte)0x80},
                    utf("p\ud800"), utf("p\udc00"), utf("\u00a0"));
            for (int field : new int[]{0,1,3}) for (int n = 0; n < malformed.size(); n++)
                readCase("malformed.field" + field + ".case" + n,
                        envelope(Map.of(field, malformed.get(n)), PEERS, 3, 2, 0, false), OP, root, encode, canonical);
            List<UUID> reversed = new ArrayList<>(PEERS); Collections.reverse(reversed);
            readCase("ambiguous.reorderedPeers", envelope(Map.of(), reversed, 3, 2, 0, false), OP, root, encode, canonical);
            readCase("invalid.duplicatePeers", envelope(Map.of(), List.of(CLONE, CLONE), 2, 2, 0, false), OP, root, encode, canonical);
            readCase("invalid.missingClone", envelope(Map.of(), List.of(PEERS.get(0)), 1, 2, 0, false), OP, root, encode, canonical);
            for (int count : new int[]{-1,0,4097,Integer.MAX_VALUE})
                readCase("invalid.peerCount" + count, envelope(Map.of(), PEERS, count, 2, 0, false), OP, root, encode, canonical);
            for (int version : new int[]{1,3})
                readCase("invalid.version" + version, envelope(Map.of(), PEERS, 3, version, 0, false), OP, root, encode, canonical);
            byte[] wrongMagicBody = Arrays.copyOf(canonicalBytes, canonicalBytes.length - 32); wrongMagicBody[0] ^= 1;
            readCase("invalid.magic", seal(wrongMagicBody), OP, root, encode, canonical);
            byte[] inflatedUtfBody = Arrays.copyOf(canonicalBytes, canonicalBytes.length - 32);
            inflatedUtfBody[24] = (byte)0xff; inflatedUtfBody[25] = (byte)0xff;
            readCase("invalid.inflatedUtfLength", seal(inflatedUtfBody), OP, root, encode, canonical);
            readCase("invalid.scope", envelope(Map.of(4, utf("UNKNOWN")), PEERS, 3, 2, 0, false), OP, root, encode, canonical);
            readCase("invalid.package", envelope(Map.of(2, utf("bad package")), PEERS, 3, 2, 0, false), OP, root, encode, canonical);
            readCase("invalid.digest", envelope(Map.of(5, utf("short")), PEERS, 3, 2, 0, false), OP, root, encode, canonical);
            readCase("invalid.trailing", envelope(Map.of(), PEERS, 3, 2, 0, true), OP, root, encode, canonical);
            readCase("invalid.state", envelope(Map.of(6, utf("UNKNOWN")), PEERS, 3, 2, 0, false), OP, root, encode, canonical);
            readCase("invalid.revision", envelope(Map.of(), PEERS, 3, 2, 1, false), OP, root, encode, canonical);
            readCase("invalid.expectedUuid", canonicalBytes, new UUID(0,99), root, encode, canonical);
            for (int length : new int[]{0,1,31,32,35,36,262144,262145})
                readCase("bounds.length" + length, Arrays.copyOf(canonicalBytes, length), OP, root, encode, canonical);
            // Correctly sealed boundary cases distinguish outer length rejection from checksum rejection.
            for (int length : new int[]{35,36,262144,262145}) {
                byte[] body = Arrays.copyOf(canonicalBytes, length - 32);
                readCase("bounds.resealed" + length, seal(body), OP, root, encode, canonical);
            }
            for (int length = 0; length < canonicalBytes.length; length++)
                readCase("truncate." + String.format(Locale.ROOT, "%04d", length), Arrays.copyOf(canonicalBytes, length), OP, root, encode, canonical);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        StringBuilder output = new StringBuilder(); VALUES.forEach((key,value) -> output.append(key).append('=').append(value).append('\n'));
        if (args.length == 1 && args[0].equals("--emit-observations")) { System.out.print(output); return; }
        check(args.length == 0);
        check(Files.size(fixtures.resolve("observations.txt")) <= 32768);
        check(output.length() <= 32768);
        check(output.toString().equals(Files.readString(fixtures.resolve("observations.txt"), StandardCharsets.UTF_8)));
        System.out.println("PASS " + checks + " B3a checks / " + VALUES.size() + " pinned observations; ambiguous acceptance NOT desired contract");
    }
}
