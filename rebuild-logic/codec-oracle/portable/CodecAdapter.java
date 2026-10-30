package vn.fonesi.rebuild.logical;

import java.util.UUID;
import java.io.IOException;

/** Test only: direct public pure-port calls, no filesystem or host reflection. */
final class CodecAdapter {
    private CodecAdapter() {}
    static byte[] encode(OperationProtocol.Operation value) throws Exception { return OperationCodec.encode(value); }
    static OperationProtocol.Operation decode(byte[] bytes, UUID expected) throws Exception {
        try { return OperationCodec.decode(bytes,expected); }
        catch (IOException failure) {
            // The shared negative recipes contain this exact sentinel in package/scope/digest/state.
            // Check the whole cause chain; Enum.valueOf diagnostics otherwise expose raw field text.
            for (Throwable cause=failure;cause!=null;cause=cause.getCause()) {
                if (cause.getMessage()!=null && cause.getMessage().contains("INVALID VALUE"))
                    throw new AssertionError("Raw malformed field leaked in portable diagnostic",failure);
            }
            throw failure;
        }
    }
    static String scope(OperationProtocol.Identity identity) throws Exception { return ScopeKey.forIdentity(identity); }
}
