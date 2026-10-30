import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import vn.fonesi.rebuild.model.ManagerModel.CatalogKey;
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
    private static int nextId;
    private static Entry entry(String name, String group, Status status) {
        return new Entry(new CatalogKey("unverified-provider", "fixture-" + (++nextId)), "example.test", name, group, status);
    }
    public static void main(String[] args) throws Exception {
        CatalogKey equalA = new CatalogKey("engine", "clone");
        CatalogKey equalB = new CatalogKey(new String("engine"), new String("clone"));
        check(equalA.equals(equalB) && equalB.equals(equalA));
        check(equalA.hashCode() == equalB.hashCode());
        check(!equalA.equals(null) && !equalA.equals("engine:clone"));
        check(!equalA.equals(new CatalogKey("other", "clone")));
        check(!equalA.equals(new CatalogKey("engine", "other")));
        check(!new CatalogKey("a:b", "c").equals(new CatalogKey("a", "b:c")));
        List<CatalogKey> opaque = Arrays.asList(new CatalogKey("", ""), new CatalogKey(" ", " "),
                new CatalogKey("engine", "00000000-0000-0000-0000-000000000001"),
                new CatalogKey("engine", "legacy:not-a-uuid"), new CatalogKey("engine", "é"),
                new CatalogKey("engine", "e\u0301"), new CatalogKey("é", "same"), new CatalogKey("e\u0301", "same"),
                new CatalogKey("engine", " clone "),new CatalogKey("engine", "clone"),new CatalogKey("Aa", "same"),new CatalogKey("BB", "same"));
        check(new CatalogKey("Aa", "same").hashCode() == new CatalogKey("BB", "same").hashCode());
        check(!new CatalogKey("Aa", "same").equals(new CatalogKey("BB", "same")));
        check(new HashSet<>(opaque).size() == opaque.size());
        ManagerModel opaqueModel = new ManagerModel();
        List<Entry> opaqueEntries = new ArrayList<>();
        for (CatalogKey key : opaque) opaqueEntries.add(new Entry(key,"example.test","Opaque","",Status.QUARANTINED));
        check(opaqueModel.publish(opaqueModel.beginRefresh(),opaqueEntries));
        check(opaqueModel.snapshot().totalCount == opaque.size());
        check(opaqueModel.selectVisible(opaqueModel.snapshot().revision,true));
        check(opaqueModel.snapshot().selected.equals(new HashSet<>(opaque)));
        check(opaque.get(0).engineKey.equals("") && opaque.get(1).cloneId.equals(" "));
        check(opaque.get(5).cloneId.equals("e\u0301") && opaque.get(8).cloneId.equals(" clone "));
        check(opaqueModel.select(opaqueModel.snapshot().revision,new CatalogKey("Aa","same"),false));
        check(!opaqueModel.snapshot().selected.contains(new CatalogKey("Aa","same")));
        check(opaqueModel.snapshot().selected.contains(new CatalogKey("BB","same")));
        check(opaqueModel.select(opaqueModel.snapshot().revision,new CatalogKey("Aa","same"),true));
        check(opaqueModel.snapshot().selected.size() == opaque.size());
        check(!opaqueModel.snapshot().operationsEnabled());
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
        check(model.select(view, a.key, true));
        check(!model.select(view, a.key, true)); // old event cannot toggle/reapply
        long selectedView = model.snapshot().revision;
        check(model.select(selectedView, a.key, true));
        check(model.snapshot().revision == selectedView);
        check(model.snapshot().selected.size() == 1);
        ManagerModel.Snapshot frozen = model.snapshot();
        check(model.selectVisible(selectedView, true));
        check(model.snapshot().selected.size() == 3);
        check(frozen.selected.size() == 1);
        model.filter(model.snapshot().revision, "Istanbul", null);
        check(model.snapshot().selected.size() == 1);
        check(model.snapshot().selected.contains(b.key));
        check(!model.select(model.snapshot().revision, a.key, true));
        check(!model.select(model.snapshot().revision, new CatalogKey("missing", "missing"), true));
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
        rejects(() -> new Entry(null, "pkg", "name", "", Status.UNVERIFIED));
        rejects(() -> new CatalogKey(null, "id"));
        rejects(() -> new CatalogKey("engine", null));
        rejects(() -> new Entry(new CatalogKey("engine", "clone"), "", "name", "", Status.UNVERIFIED));
        rejects(() -> new Entry(new CatalogKey("engine", "clone"), "pkg", "", "", Status.UNVERIFIED));
        rejects(() -> new Entry(new CatalogKey("engine", "clone"), "pkg", "name", null, Status.UNVERIFIED));
        rejects(() -> new Entry(new CatalogKey("engine", "clone"), "pkg", "name", "", null));
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
        Entry otherNamespace = new Entry(new CatalogKey("different-provider", a.key.cloneId), "other.package", "Other", "", Status.UNVERIFIED);
        check(race.publish(duplicate, Arrays.asList(a, otherNamespace)));
        check(race.snapshot().phase == Phase.LOADED);
        check(race.snapshot().totalCount == 2);
        check(a.key.engineKey.equals("unverified-provider") && a.packageName.equals("example.test"));
        check(race.select(race.snapshot().revision, a.key, true));
        ManagerModel.Snapshot firstEngineSelected = race.snapshot();
        check(firstEngineSelected.selected.contains(a.key));
        check(!firstEngineSelected.selected.contains(otherNamespace.key));
        check(race.select(race.snapshot().revision, otherNamespace.key, true));
        check(race.snapshot().selected.size() == 2);
        check(firstEngineSelected.selected.size() == 1);
        check(race.select(race.snapshot().revision, new CatalogKey(a.key.engineKey,a.key.cloneId), false));
        check(race.snapshot().selected.equals(Collections.singleton(otherNamespace.key)));
        check(race.select(race.snapshot().revision,a.key,true));
        check(race.snapshot().selected.size() == 2);
        race.filter(race.snapshot().revision,"Đặng Ánh",null);
        check(race.snapshot().visible.size() == 1 && race.snapshot().visible.get(0) == a);
        check(race.snapshot().selected.equals(Collections.singleton(a.key)));
        check(!race.select(race.snapshot().revision,otherNamespace.key,true));
        race.filter(race.snapshot().revision,"",null);
        check(race.snapshot().selected.equals(Collections.singleton(a.key)));
        long exactDuplicate = race.beginRefresh();
        Entry changedPayload = new Entry(new CatalogKey(a.key.engineKey,a.key.cloneId), "different.package", "Changed", "other", Status.UNAVAILABLE);
        rejects(() -> race.publish(exactDuplicate, Arrays.asList(a,changedPayload)));
        check(race.snapshot().phase == Phase.LOADING);
        check(race.snapshot().totalCount == 0);
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
                    case "select": overflow.select(Long.MAX_VALUE, a.key, true); break;
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
        check(!pending.select(beforeFilter, a.key, true));
        long stable = pending.snapshot().revision;
        check(pending.selectVisible(stable, false));
        check(pending.snapshot().revision == stable);
        long failedToken = pending.beginRefresh();
        check(pending.fail(failedToken));
        long replacementToken = pending.beginRefresh();
        check(!pending.publish(failedToken, Arrays.asList(a)));
        check(pending.publish(replacementToken, Arrays.asList(b)));
        ManagerModel namespaceRefresh = new ManagerModel();
        long originalGeneration = namespaceRefresh.beginRefresh();
        check(namespaceRefresh.publish(originalGeneration,Arrays.asList(a)));
        check(namespaceRefresh.select(namespaceRefresh.snapshot().revision,a.key,true));
        long originalRevision = namespaceRefresh.snapshot().revision;
        long replacementGeneration = namespaceRefresh.beginRefresh();
        check(namespaceRefresh.snapshot().selected.isEmpty());
        check(namespaceRefresh.publish(replacementGeneration,Arrays.asList(otherNamespace)));
        check(namespaceRefresh.snapshot().selected.isEmpty());
        check(!namespaceRefresh.select(originalRevision,a.key,true));
        check(!namespaceRefresh.select(namespaceRefresh.snapshot().revision,a.key,true));
        check(namespaceRefresh.select(namespaceRefresh.snapshot().revision,otherNamespace.key,true));
        check(namespaceRefresh.snapshot().selected.equals(Collections.singleton(otherNamespace.key)));
        check(!namespaceRefresh.publish(originalGeneration,Arrays.asList(a)));
        check(namespaceRefresh.snapshot().visible.size() == 1 && namespaceRefresh.snapshot().visible.get(0) == otherNamespace);
        String atBudget = "x".repeat(4096);
        String beyondBudget = "x".repeat(4097);
        Entry boundedEntry = new Entry(new CatalogKey(beyondBudget, beyondBudget), atBudget, atBudget, atBudget, Status.UNVERIFIED);
        check(boundedEntry.key.cloneId.equals(beyondBudget));
        check(boundedEntry.name.equals(atBudget));
        check(boundedEntry.normalizedUnits() == 3 * 4096);
        rejects(() -> new Entry(a.key, beyondBudget, "name", "", Status.UNVERIFIED));
        rejects(() -> new Entry(a.key, "pkg", beyondBudget, "", Status.UNVERIFIED));
        rejects(() -> new Entry(a.key, "pkg", "name", beyondBudget, Status.UNVERIFIED));
        String expanding = "\uac01".repeat(2730);
        Entry expandedEntry = new Entry(a.key, "p", expanding, "", Status.UNVERIFIED);
        check(expandedEntry.normalizedUnits() == 8191);
        rejects(() -> new Entry(a.key, "p", expanding + "\uac01", "", Status.UNVERIFIED));
        ManagerModel budgets = new ManagerModel();
        check(budgets.publish(budgets.beginRefresh(), Arrays.asList(a)));
        check(budgets.select(budgets.snapshot().revision, a.key, true));
        ManagerModel.Snapshot beforeInvalid = budgets.snapshot();
        rejects(() -> budgets.filter(beforeInvalid.revision, beyondBudget, null));
        rejects(() -> budgets.filter(beforeInvalid.revision, "", beyondBudget));
        rejects(() -> budgets.filter(beforeInvalid.revision, expanding + "\uac01", null));
        check(budgets.snapshot().revision == beforeInvalid.revision);
        check(budgets.snapshot().query.equals(beforeInvalid.query));
        check(budgets.snapshot().group == beforeInvalid.group);
        check(budgets.snapshot().selected.equals(beforeInvalid.selected));
        check(budgets.snapshot().visible.equals(beforeInvalid.visible));
        budgets.filter(budgets.snapshot().revision, atBudget, atBudget);
        check(budgets.snapshot().query.equals(atBudget));
        check(budgets.snapshot().group.equals(atBudget));
        budgets.filter(budgets.snapshot().revision, expanding, null);
        check(budgets.snapshot().query.equals(expanding));
        for (String fieldName : Arrays.asList("foldedName", "foldedGroup", "foldedPackage")) {
            Field cached = Entry.class.getDeclaredField(fieldName);
            check(java.lang.reflect.Modifier.isFinal(cached.getModifiers()));
            check(java.lang.reflect.Modifier.isPrivate(cached.getModifiers()));
        }
        int cacheUnits = a.normalizedUnits();
        for (int i = 0; i < 10; i++) budgets.filter(budgets.snapshot().revision, "dang", null);
        check(a.normalizedUnits() == cacheUnits);
        if (checks < 186) throw new AssertionError("missing tests");
        System.out.println("PASS " + checks + " manager presentation checks; no provider/data/UI execution");
    }
}
