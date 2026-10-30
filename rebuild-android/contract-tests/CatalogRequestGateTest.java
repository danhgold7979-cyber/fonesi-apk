import vn.fonesi.rebuild.catalog.CatalogRequestGate;

/** Deterministic fake monotonic clock/owner tests; no Android runtime claims. */
public final class CatalogRequestGateTest {
    private static int checks;
    private static void check(boolean result) { checks++; if (!result) throw new AssertionError("check " + checks); }
    private static void invalid(Runnable action) {
        checks++;
        try { action.run(); throw new AssertionError("Expected invalid deadline"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        CatalogRequestGate gate = new CatalogRequestGate();
        Object firstOwner = new Object();
        Object secondOwner = new Object();
        check(!gate.current(null));
        check(!gate.live(null, 0));
        check(!gate.finish(null, 0));
        check(!gate.invalidate(null));
        check(!gate.cancel(firstOwner));
        CatalogRequestGate.Ticket first = gate.replace(firstOwner, 100, 10);
        check(CatalogRequestGate.deadline(first) == 110);
        check(gate.current(first));
        check(gate.live(first, 100));
        check(gate.live(first, 109));
        check(!gate.live(first, 110));
        check(!gate.live(first, 111));
        check(!gate.live(first, -1));
        check(!gate.cancel(secondOwner));
        check(gate.current(first));
        check(!gate.finish(first, 110));
        check(gate.invalidate(first));
        check(!gate.finish(first, 100));
        check(!gate.invalidate(first));
        CatalogRequestGate.Ticket second = gate.replace(firstOwner, 200, 20);
        CatalogRequestGate.Ticket third = gate.replace(secondOwner, 201, 20);
        check(!gate.current(second));
        check(!gate.finish(second, 202));
        check(!gate.invalidate(second));
        check(!gate.cancel(firstOwner));
        check(gate.live(third, 202));
        check(gate.finish(third, 202));
        check(!gate.finish(third, 202));
        check(!gate.current(third));
        CatalogRequestGate.Ticket fourth = gate.replace(firstOwner, 300, 10);
        check(gate.cancel(firstOwner));
        check(!gate.live(fourth, 301));
        check(!gate.finish(fourth, 301));
        check(!gate.cancel(firstOwner));
        invalid(() -> gate.replace(firstOwner, -1, 1));
        invalid(() -> gate.replace(firstOwner, 0, 0));
        invalid(() -> gate.replace(firstOwner, 0, -1));
        invalid(() -> gate.replace(firstOwner, Long.MAX_VALUE, 1));
        CatalogRequestGate.Ticket edge = gate.replace(firstOwner, Long.MAX_VALUE - 1, 1);
        check(gate.live(edge, Long.MAX_VALUE - 1));
        check(!gate.live(edge, Long.MAX_VALUE));
        check(gate.invalidate(edge));
        for (int i = 0; i < 100; i++) {
            CatalogRequestGate.Ticket older = gate.replace(firstOwner, i, 10);
            CatalogRequestGate.Ticket newer = gate.replace(firstOwner, i, 10);
            check(!gate.finish(older, i));
            check(gate.finish(newer, i));
        }
        if (checks != 237) throw new AssertionError("Unexpected check count " + checks);
        System.out.println("Catalog request gate: " + checks + " checks PASS (pure fake-clock ownership only)");
    }
}
