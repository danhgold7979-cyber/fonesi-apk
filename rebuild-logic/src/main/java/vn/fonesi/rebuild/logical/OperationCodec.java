package vn.fonesi.rebuild.logical;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import static vn.fonesi.rebuild.logical.OperationProtocol.*;

/** Pure canonical v2 metadata envelope. Checksums are not authentication or encryption.
 * No persistence, recovery, dispatch or provider authority is granted by decoding.
 */
public final class OperationCodec {
    private static final int MAGIC = 0x464f4e32, MAX_BYTES = 256 * 1024, MAX_PEERS = 4096;
    private OperationCodec() {}
    public static byte[] encode(Operation operation) throws IOException {
        Identity id = operation.identity();
        if (id.affectedClones().size() > MAX_PEERS) throw new IOException("Journal peer budget exceeded");
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(payload)) {
            out.writeInt(MAGIC); out.writeInt(2);
            writeUuid(out, id.operation()); out.writeUTF(id.provider().id()); out.writeUTF(id.provider().revision());
            out.writeUTF(id.packageName()); out.writeUTF(id.environment()); out.writeUTF(id.scope().name());
            writeUuid(out, id.cloneId()); out.writeUTF(id.clusterSha256());
            List<UUID> peers = new ArrayList<>(id.affectedClones());
            Collections.sort(peers);
            out.writeInt(peers.size()); for (UUID peer : peers) writeUuid(out, peer);
            out.writeUTF(operation.state().name()); out.writeLong(operation.revision());
        }
        byte[] body = payload.toByteArray();
        if (body.length + 32 > MAX_BYTES) throw new IOException("Journal byte budget exceeded");
        byte[] checksum = digest(body);
        payload.write(checksum, 0, checksum.length); return payload.toByteArray();
    }
    public static Operation decode(byte[] bytes, UUID expectedOperation) throws IOException {
        Objects.requireNonNull(bytes);
        Objects.requireNonNull(expectedOperation);
        if (bytes.length < 36 || bytes.length > MAX_BYTES) throw new IOException("Journal length invalid");
        // B3b: all validation operates on this bounded owned snapshot, never a mutable caller array.
        byte[] owned = bytes.clone();
        byte[] body = Arrays.copyOf(owned, owned.length - 32);
        if (!MessageDigest.isEqual(digest(body), Arrays.copyOfRange(owned, owned.length - 32, owned.length)))
            throw new IOException("Journal checksum mismatch");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != MAGIC || in.readInt() != 2) throw new IOException("Unknown journal format");
            UUID operation = readUuid(in);
            CapabilityPolicy.Provider provider = new CapabilityPolicy.Provider(in.readUTF(), in.readUTF());
            String packageName = in.readUTF(); String environment = in.readUTF();
            CapabilityPolicy.Scope scope = CapabilityPolicy.Scope.valueOf(in.readUTF());
            UUID clone = readUuid(in); String cluster = in.readUTF();
            int count = in.readInt();
            if (count < 1 || count > MAX_PEERS) throw new IOException("Invalid peer count");
            Set<UUID> peers = new HashSet<>();
            for (int n = 0; n < count; n++) if (!peers.add(readUuid(in))) throw new IOException("Duplicate peer");
            State state = State.valueOf(in.readUTF()); long revision = in.readLong();
            if (in.available() != 0) throw new IOException("Trailing journal bytes");
            Operation result = new Operation(new Identity(operation, provider, packageName, environment, scope, clone, cluster, peers), state, revision);
            // Reject alternate wire representations; never rewrite or normalize persisted input.
            if (!Arrays.equals(owned, encode(result))) throw new IOException("Noncanonical journal encoding");
            if (!result.identity().operation().equals(expectedOperation))
                throw new IOException("Journal filename identity mismatch");
            return result;
        } catch (IllegalArgumentException e) {
            // Enum parsing causes can embed caller metadata; expose only the stable failure family.
            throw new IOException("Invalid journal value");
        }
    }
    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
