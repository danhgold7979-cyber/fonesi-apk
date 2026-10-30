package vn.fonesi.rebuild.logical;

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
    public static void main(String[] args) {
        String valid255 = "a.".repeat(127) + "a";
        if (identity(valid255).packageName().length() != 255) throw new AssertionError("Boundary drift");
        checks++;
        for (String bad : new String[]{null,"","a","a..b","1a.b","a.b-",valid255+"a",valid255+"aa"}) {
            try { identity(bad); } catch (IllegalArgumentException expected) { checks++; continue; }
            throw new AssertionError("Expected bounded package rejection");
        }
        System.out.println("PASS " + checks + " bounded portable package-constructor checks; no denied reproduction");
    }
}
