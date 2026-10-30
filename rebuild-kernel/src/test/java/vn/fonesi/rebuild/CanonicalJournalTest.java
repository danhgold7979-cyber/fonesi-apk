package vn.fonesi.rebuild;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static vn.fonesi.rebuild.CapabilityPolicy.*;
import static vn.fonesi.rebuild.OperationProtocol.*;

/** B3b desired rejection tests; historical accepted variants remain separate observations. */
public final class CanonicalJournalTest {
    private static final UUID OP = new UUID(0,1), CLONE = new UUID(0,2);
    private static final List<UUID> PEERS = List.of(new UUID(Long.MIN_VALUE,0), new UUID(0,Long.MIN_VALUE), CLONE);
    private static final String DIGEST = "a".repeat(64);
    private static int checks;
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Canonical check " + checks); }
    private static void rejects(Action action, String message) throws Exception {
        try { action.run(); } catch (IOException failure) {
            check(message == null || message.equals(failure.getMessage())); return;
        }
        throw new AssertionError("Expected IOException: " + message);
    }
    private static Object invoke(Method method, Object... args) throws Exception {
        try { return method.invoke(null, args); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            throw new AssertionError(error.getCause());
        }
    }
    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    private static byte[] seal(byte[] body) throws Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream(); result.writeBytes(body);
        result.writeBytes(MessageDigest.getInstance("SHA-256").digest(body)); return result.toByteArray();
    }
    private record Range(int start, int end) {}
    private static List<Range> strings(byte[] body) throws Exception {
        List<Range> fields = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(body))) {
            check(input.skipBytes(24) == 24);
            for (int field = 0; field < 7; field++) {
                if (field == 5) check(input.skipBytes(16) == 16);
                if (field == 6) {
                    int peers = input.readInt(); check(input.skipBytes(peers * 16) == peers * 16);
                }
                int size = input.readUnsignedShort(), start = body.length - input.available();
                check(input.skipBytes(size) == size); fields.add(new Range(start,start + size));
            }
        }
        return fields;
    }
    private static byte[] overlong(byte[] original, int field, int width) throws Exception {
        byte[] body = Arrays.copyOf(original, original.length - 32);
        Range range = strings(body).get(field); int ascii = body[range.start()] & 0xff;
        check(ascii > 0 && ascii < 128);
        byte[] changed = new byte[body.length + width - 1];
        System.arraycopy(body,0,changed,0,range.start());
        int length = range.end() - range.start() + width - 1;
        changed[range.start()-2] = (byte)(length >>> 8); changed[range.start()-1] = (byte)length;
        if (width == 2) {
            changed[range.start()] = (byte)(0xc0 | ascii >>> 6);
            changed[range.start()+1] = (byte)(0x80 | ascii & 63);
        } else {
            changed[range.start()] = (byte)0xe0;
            changed[range.start()+1] = (byte)(0x80 | ascii >>> 6);
            changed[range.start()+2] = (byte)(0x80 | ascii & 63);
        }
        System.arraycopy(body,range.start()+1,changed,range.start()+width,body.length-range.start()-1);
        return seal(changed);
    }
    private static Identity identity(String id, String revision, String environment, Set<UUID> peers) {
        return new Identity(OP,new Provider(id,revision),"a.b",environment,Scope.SYNTHETIC,CLONE,DIGEST,peers);
    }
    private static void noncanonical(byte[] bytes, Path root, Method decode) throws Exception {
        byte[] caller = bytes.clone();
        rejects(() -> invoke(decode, (Object)caller), "Noncanonical journal encoding");
        check(Arrays.equals(bytes,caller));
        Path file = root.resolve(OP + ".journal"); Files.write(file,bytes);
        OperationJournal journal = new OperationJournal(root);
        rejects(() -> journal.read(OP).recover(), "Noncanonical journal encoding");
        int[] callbacks = {0}; rejects(() -> journal.inspect(op -> callbacks[0]++), "Noncanonical journal encoding");
        Identity id = identity("p","r","e",new HashSet<>(PEERS));
        var request = new SharedUpdateDispatcher.Request("a.b",id,"e",Scope.SYNTHETIC);
        rejects(() -> new SharedUpdateDispatcher(root).execute(request,
                r -> { callbacks[0]++; return null; },r -> callbacks[0]++,
                (r,v) -> { callbacks[0]++; return null; }), "Noncanonical journal encoding");
        check(callbacks[0] == 0); check(Arrays.equals(bytes,Files.readAllBytes(file)));
        Files.delete(file);
    }
    public static void main(String[] args) throws Exception {
        Method encode = OperationJournal.class.getDeclaredMethod("encode",Operation.class);
        Method decode = OperationJournal.class.getDeclaredMethod("decode",byte[].class);
        encode.setAccessible(true); decode.setAccessible(true);
        Map<String,String> historical = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("../spec/codec-observations/b3a/observations.txt"))) {
            int equals = line.indexOf('='); historical.put(line.substring(0,equals),line.substring(equals+1));
        }
        byte[] ascii = HexFormat.of().parseHex(historical.get("valid.ascii.hex"));
        byte[] unicode = HexFormat.of().parseHex(historical.get("valid.unicode.hex"));
        Path root = Files.createTempDirectory("fonesi-b3b-canonical-");
        try {
            for (byte[] original : List.of(ascii,unicode)) {
                byte[] caller = original.clone();
                Operation parsed = (Operation)invoke(decode,(Object)caller);
                check(Arrays.equals(original,(byte[])invoke(encode,parsed)));
                Arrays.fill(caller,(byte)0);
                check(Arrays.equals(original,(byte[])invoke(encode,parsed)));
                Path file = root.resolve(OP + ".journal"); Files.write(file,original);
                check(new OperationJournal(root).read(OP).equals(parsed));
                check(Arrays.equals(original,Files.readAllBytes(file))); Files.delete(file);
            }
            Identity base = identity("p","r","e",new HashSet<>(PEERS));
            for (State state : State.values()) {
                long revision = state == State.PREPARED ? 0 : state == State.DISPATCHED || state == State.CANCELLED ? 1 : 2;
                Operation value = new Operation(base,state,revision); byte[] bytes = (byte[])invoke(encode,value);
                check(sha(bytes).equals(historical.get("valid.state." + state + ".sha256")));
                check(value.equals(invoke(decode,(Object)bytes)));
            }
            Set<UUID> maximumPeers = new HashSet<>(); for (int n=0;n<4096;n++) maximumPeers.add(new UUID(0,n));
            byte[] maximum = (byte[])invoke(encode,Operation.prepare(identity("p","r","e",maximumPeers)));
            check(sha(maximum).equals(historical.get("valid.maxPeers.canonicalSha256")));
            check(Arrays.equals(maximum,(byte[])invoke(encode,invoke(decode,(Object)maximum))));
            for (int field=0;field<7;field++) for (int width : new int[]{2,3}) noncanonical(overlong(ascii,field,width),root,decode);
            byte[] body = Arrays.copyOf(ascii,ascii.length-32); int peerStart = strings(body).get(5).end()+4;
            byte[] first = Arrays.copyOfRange(body,peerStart,peerStart+16);
            System.arraycopy(body,peerStart+32,body,peerStart,16); System.arraycopy(first,0,body,peerStart+32,16);
            noncanonical(seal(body),root,decode);
            for (int length : new int[]{0,1,31,32,35,262145}) {
                byte[] bytes = new byte[length]; rejects(() -> invoke(decode,(Object)bytes),"Journal length invalid");
            }
            for (int length=36;length<ascii.length;length++) {
                byte[] bytes = Arrays.copyOf(ascii,length); rejects(() -> invoke(decode,(Object)bytes),null);
            }
            // Retain one old checksum byte as an extra body byte, then compute a valid new checksum.
            final byte[] extra = seal(Arrays.copyOf(ascii,ascii.length-31));
            rejects(() -> invoke(decode,(Object)extra),"Trailing journal bytes");
            Path wrongFile = root.resolve(new UUID(0,99) + ".journal"); Files.write(wrongFile,ascii);
            rejects(() -> new OperationJournal(root).read(new UUID(0,99)),"Journal filename identity mismatch");
            check(Arrays.equals(ascii,Files.readAllBytes(wrongFile))); Files.delete(wrongFile);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("PASS " + checks + " B3b canonical-byte/rejection/retention checks; no provider");
    }
}
