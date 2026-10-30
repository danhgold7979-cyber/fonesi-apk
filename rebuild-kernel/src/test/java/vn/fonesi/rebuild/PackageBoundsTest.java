package vn.fonesi.rebuild;

import java.util.Set;
import java.util.UUID;

/** Ordinary bounded constructor regressions only; no malformed envelope reproduction. */
public final class PackageBoundsTest {
    private static int checks;
    private static OperationProtocol.Identity identity(String packageName) {
        UUID id = new UUID(0,1);
        return new OperationProtocol.Identity(id,new CapabilityPolicy.Provider("p","r"),packageName,"e",
                CapabilityPolicy.Scope.SYNTHETIC,id,"a".repeat(64),Set.of(id));
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected bounded package rejection");
    }
    public static void main(String[] args) {
        String valid255 = "a.".repeat(127) + "a";
        OperationProtocol.Identity value = identity(valid255);
        if (value.packageName().length() != 255) throw new AssertionError("Boundary drift");
        checks++;
        new SharedUpdateDispatcher.Request(valid255,value,"e",CapabilityPolicy.Scope.SYNTHETIC); checks++;
        for (String bad : new String[]{null,"","a","a..b","1a.b","a.b-",valid255+"a",valid255+"aa"}) {
            reject(() -> identity(bad));
            reject(() -> new SharedUpdateDispatcher.Request(bad,value,"e",CapabilityPolicy.Scope.SYNTHETIC));
        }
        System.out.println("PASS " + checks + " bounded host package-constructor checks; no denied reproduction");
    }
}
