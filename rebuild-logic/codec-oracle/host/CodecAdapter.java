package vn.fonesi.rebuild;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Test only: corrected host codec plus public journal's expected-UUID binding. */
final class CodecAdapter {
    private CodecAdapter() {}
    private static Object invoke(String name, Class<?> type, Object value) throws Exception {
        Method method = OperationJournal.class.getDeclaredMethod(name,type); method.setAccessible(true);
        try { return method.invoke(null,value); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception) throw (Exception)error.getCause();
            throw new AssertionError(error.getCause());
        }
    }
    static byte[] encode(OperationProtocol.Operation value) throws Exception {
        return (byte[])invoke("encode",OperationProtocol.Operation.class,value);
    }
    static OperationProtocol.Operation decode(byte[] bytes, UUID expected) throws Exception {
        Objects.requireNonNull(expected);
        OperationProtocol.Operation value = (OperationProtocol.Operation)invoke("decode",byte[].class,bytes);
        if (!value.identity().operation().equals(expected)) throw new IOException("Journal filename identity mismatch");
        return value;
    }
    static String scope(OperationProtocol.Identity identity) throws Exception {
        Objects.requireNonNull(identity);
        Path root = Files.createTempDirectory("fonesi-b3c-scope-");
        try {
            SharedUpdateDispatcher dispatcher = new SharedUpdateDispatcher(root);
            Method method = SharedUpdateDispatcher.class.getDeclaredMethod("scopeDirectory",SharedUpdateDispatcher.Request.class);
            method.setAccessible(true);
            SharedUpdateDispatcher.Request request = new SharedUpdateDispatcher.Request(identity.packageName(),identity,identity.environment(),identity.scope());
            try { return ((Path)method.invoke(dispatcher,request)).getFileName().toString(); }
            catch (InvocationTargetException error) {
                if (error.getCause() instanceof Exception) throw (Exception)error.getCause();
                throw new AssertionError(error.getCause());
            }
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
