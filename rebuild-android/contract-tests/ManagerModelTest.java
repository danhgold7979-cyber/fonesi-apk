import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import vn.fonesi.rebuild.model.ManagerModel;
import vn.fonesi.rebuild.model.ManagerModel.Entry;
import vn.fonesi.rebuild.model.ManagerModel.Phase;
import vn.fonesi.rebuild.model.ManagerModel.Status;

public final class ManagerModelTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }
    private static void rejects(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException | NullPointerException | ArithmeticException expected) { return; }
        throw new AssertionError("expected rejection " + checks);
    }
    private static Entry entry(String name, String group, Status status) {
        return new Entry(UUID.randomUUID(), "unverified-provider", "example.test", name, group, status);
    }
    public static void main(String[] args) throws Exception {
        ManagerModel model = new ManagerModel();
        check(model.snapshot().phase == Phase.IDLE);
        check(model.snapshot().visible.isEmpty());
        check(!model.snapshot().operationsEnabled());
        check(!model.selectVisible(0, true));
        long old = model.beginRefresh();
        check(model.snapshot().phase == Phase.LOADING);
        long current = model.beginRefresh();
        check(current > old);
        check(!model.publish(old, null));
        check(!model.fail(old));
        Entry a = entry("Đặng Ánh", "Công việc", Status.QUARANTINED);
        Entry b = entry("Istanbul", "", Status.UNAVAILABLE);
        Entry c = entry("Other", "Công việc", Status.UNVERIFIED);
        List<Entry> input = new ArrayList<>(Arrays.asList(a, b, c));
        check(model.publish(current, input));
        input.clear();
        check(model.snapshot().totalCount == 3);
        check(!model.publish(current, Collections.emptyList()));
        check(!model.fail(current));
        check(!model.snapshot().operationsEnabled());
        check(model.filter(model.snapshot().revision, "dang anh", null).visible.get(0) == a);
        check(model.snapshot().visible.size() == 1);
        check(model.filter(model.snapshot().revision, "cong viec", null).visible.size() == 2);
        check(model.filter(model.snapshot().revision, "example.test", null).visible.size() == 3);
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            check(model.filter(model.snapshot().revision, "ISTANBUL", null).visible.get(0) == b);
        } finally { Locale.setDefault(before); }
        check(model.filter(model.snapshot().revision, "", "").visible.size() == 1);
        check(model.filter(model.snapshot().revision, "", "Công việc").visible.size() == 2);
        check(model.filter(model.snapshot().revision, "", "Cong viec").visible.isEmpty());
        model.filter(model.snapshot().revision, "", null);
        long view = model.snapshot().revision;
        check(model.select(view, a.id, true));
        check(!model.select(view, a.id, true)); // old event cannot toggle/reapply
        long selectedView = model.snapshot().revision;
        check(model.select(selectedView, a.id, true));
        check(model.snapshot().revision == selectedView);
        check(model.snapshot().selected.size() == 1);
        ManagerModel.Snapshot frozen = model.snapshot();
        check(model.selectVisible(selectedView, true));
        check(model.snapshot().selected.size() == 3);
        check(frozen.selected.size() == 1);
        model.filter(model.snapshot().revision, "Istanbul", null);
        check(model.snapshot().selected.size() == 1);
        check(model.snapshot().selected.contains(b.id));
        check(!model.select(model.snapshot().revision, a.id, true));
        check(!model.select(model.snapshot().revision, UUID.randomUUID(), true));
        check(model.selectVisible(model.snapshot().revision, false));
        check(model.snapshot().selected.isEmpty());
        try { frozen.visible.clear(); throw new AssertionError(); }
        catch (UnsupportedOperationException expected) { checks++; }
        try { frozen.selected.clear(); throw new AssertionError(); }
        catch (UnsupportedOperationException expected) { checks++; }
        long next = model.beginRefresh();
        check(model.snapshot().visible.isEmpty());
        check(model.snapshot().selected.isEmpty());
        rejects(() -> model.publish(next, Arrays.asList(a, a)));
        check(model.snapshot().phase == Phase.LOADING);
        check(model.snapshot().totalCount == 0);
        rejects(() -> model.publish(next, Arrays.asList(a, null)));
        check(model.fail(next));
        check(model.snapshot().phase == Phase.FAILED);
        check(!model.fail(next));
        check(!model.publish(next, Arrays.asList(a)));
        check(!model.snapshot().operationsEnabled());
        long empty = model.beginRefresh();
        check(model.publish(empty, Collections.emptyList()));
        check(model.snapshot().phase == Phase.LOADED);
        check(model.snapshot().totalCount == 0);
        rejects(() -> new Entry(null, "p", "pkg", "name", "", Status.UNVERIFIED));
        rejects(() -> new Entry(UUID.randomUUID(), " ", "pkg", "name", "", Status.UNVERIFIED));
        rejects(() -> new Entry(UUID.randomUUID(), "p", "", "name", "", Status.UNVERIFIED));
        rejects(() -> new Entry(UUID.randomUUID(), "p", "pkg", "", "", Status.UNVERIFIED));
        rejects(() -> new Entry(UUID.randomUUID(), "p", "pkg", "name", null, Status.UNVERIFIED));
        rejects(() -> new Entry(UUID.randomUUID(), "p", "pkg", "name", "", null));
        rejects(() -> model.filter(model.snapshot().revision, null, null));
        for (String fieldName : Arrays.asList("revision", "generation")) {
            ManagerModel overflow = new ManagerModel();
            Field field = ManagerModel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setLong(overflow, Long.MAX_VALUE);
            rejects(overflow::beginRefresh);
            check(overflow.snapshot().phase == Phase.IDLE);
            check(overflow.snapshot().totalCount == 0);
        }
        ManagerModel race = new ManagerModel();
        long raceToken = race.beginRefresh();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        Runnable completion = () -> {
            ready.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                if (race.publish(raceToken, Arrays.asList(a))) accepted.incrementAndGet();
            } catch (Exception unexpected) { failures.incrementAndGet(); }
        };
        Thread one = new Thread(completion);
        Thread two = new Thread(completion);
        one.setDaemon(true); two.setDaemon(true);
        one.start(); two.start();
        check(ready.await(10, TimeUnit.SECONDS));
        start.countDown(); one.join(10000); two.join(10000);
        check(!one.isAlive() && !two.isAlive());
        check(accepted.get() == 1);
        check(failures.get() == 0);
        check(race.snapshot().totalCount == 1);
        check(!race.snapshot().operationsEnabled());
        long duplicate = race.beginRefresh();
        Entry otherNamespace = new Entry(a.id, "different-provider", "other.package", "Other", "", Status.UNVERIFIED);
        rejects(() -> race.publish(duplicate, Arrays.asList(a, otherNamespace)));
        check(race.snapshot().phase == Phase.LOADING);
        check(race.snapshot().totalCount == 0);
        check(a.provider.equals("unverified-provider") && a.packageName.equals("example.test"));
        long staleFilter = model.snapshot().revision;
        model.filter(staleFilter, "newer", null);
        model.filter(staleFilter, "older", null);
        check(model.snapshot().query.equals("newer"));
        for (String operation : Arrays.asList("publish", "fail", "filter", "select", "selectVisible")) {
            ManagerModel overflow = new ManagerModel();
            long token = overflow.beginRefresh();
            if (!operation.equals("publish") && !operation.equals("fail")) {
                overflow.publish(token, Arrays.asList(a));
            }
            Field revision = ManagerModel.class.getDeclaredField("revision");
            revision.setAccessible(true);
            revision.setLong(overflow, Long.MAX_VALUE);
            ManagerModel.Snapshot prior = overflow.snapshot();
            rejects(() -> {
                switch (operation) {
                    case "publish": overflow.publish(token, Arrays.asList(a)); break;
                    case "fail": overflow.fail(token); break;
                    case "filter": overflow.filter(Long.MAX_VALUE, "change", null); break;
                    case "select": overflow.select(Long.MAX_VALUE, a.id, true); break;
                    case "selectVisible": overflow.selectVisible(Long.MAX_VALUE, true); break;
                    default: throw new AssertionError();
                }
            });
            check(overflow.snapshot().phase == prior.phase);
            check(overflow.snapshot().totalCount == prior.totalCount);
            check(overflow.snapshot().selected.equals(prior.selected));
            check(overflow.snapshot().query.equals(prior.query));
        }
        ManagerModel pending = new ManagerModel();
        long pendingToken = pending.beginRefresh();
        pending.filter(pending.snapshot().revision, "dang", null);
        check(pending.publish(pendingToken, Arrays.asList(a, b)));
        check(pending.snapshot().visible.size() == 1 && pending.snapshot().visible.get(0) == a);
        long beforeFilter = pending.snapshot().revision;
        pending.filter(beforeFilter, "Istanbul", null);
        check(!pending.select(beforeFilter, a.id, true));
        long stable = pending.snapshot().revision;
        check(pending.selectVisible(stable, false));
        check(pending.snapshot().revision == stable);
        long failedToken = pending.beginRefresh();
        check(pending.fail(failedToken));
        long replacementToken = pending.beginRefresh();
        check(!pending.publish(failedToken, Arrays.asList(a)));
        check(pending.publish(replacementToken, Arrays.asList(b)));
        if (checks < 108) throw new AssertionError("missing tests");
        System.out.println("PASS " + checks + " manager presentation checks; no provider/data/UI execution");
    }
}
