package vn.fonesi.rebuild.catalog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import vn.fonesi.rebuild.model.ManagerController;
import vn.fonesi.rebuild.model.ManagerModel;

/** Executes the real loader against fake Android scheduling/reader. NOT Android runtime evidence. */
public final class CatalogLoaderHostTest {
    private static int checks;
    private static CatalogLoader loader;
    private static ThreadPoolExecutor worker;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= end) throw new AssertionError("Host orchestration wait timed out");
            Thread.sleep(1);
        }
    }
    private static ManagerController owner() {
        ManagerController c = new ManagerController();
        c.attach(state -> check(Looper.myLooper() == Looper.getMainLooper()));
        return c;
    }
    private static void request(ManagerController owner, CatalogPage.Position after) {
        ManagerController.RefreshTicket ticket = owner.beginRefresh(owner.current().token);
        loader.request(owner, ticket, after);
    }
    private static void setup() throws Exception {
        Looper.getMainLooper(); Handler.reset(); CatalogReader.calls.clear();
        loader = CatalogLoader.get(new Context());
        worker = (ThreadPoolExecutor) field(loader, "worker");
        check(worker.getMaximumPoolSize() == 1);
        check(worker.getQueue().remainingCapacity() == 1);
    }
    private static void cleanup() throws Exception {
        worker.shutdownNow(); check(worker.awaitTermination(5, TimeUnit.SECONDS));
        Field singleton = CatalogLoader.class.getDeclaredField("instance"); singleton.setAccessible(true); singleton.set(null, null);
        Handler.reset(); CatalogReader.calls.clear();
    }
    private static void successAndNext() throws Exception {
        setup();
        try {
            ManagerController owner = owner(); request(owner, null);
            CatalogReader.Call first = CatalogReader.started();
            check(first.after == null); check(first.deadline == 2000);
            first.release.countDown(); await(() -> Handler.dueCount() == 1); Handler.drain();
            check(owner.current().phase == ManagerModel.Phase.LOADED);
            check(owner.current().totalCount == 1);
            CatalogPage.Position next = loader.nextPosition(owner); check(next != null);
            check(loader.nextPosition(new ManagerController()) == null);
            request(owner, next); CatalogReader.Call second = CatalogReader.started();
            check(second.after == next); check(loader.nextPosition(owner) == null);
            second.fail = true; second.release.countDown(); await(() -> Handler.dueCount() == 1); Handler.drain();
            check(owner.current().phase == ManagerModel.Phase.FAILED);
            check(loader.nextPosition(owner) == null);
            check(Handler.pendingCount() == 0);
        } finally { cleanup(); }
    }
    private static void boundedReplacementAndCrossOwner() throws Exception {
        setup();
        try {
            ManagerController first = owner(); request(first, null);
            CatalogReader.Call blocked = CatalogReader.started();
            ManagerController second = owner(); request(second, null);
            check(blocked.cancel.isCanceled()); check(worker.getQueue().size() == 1);
            check(first.current().phase == ManagerModel.Phase.FAILED);
            check(second.current().phase == ManagerModel.Phase.LOADING);
            request(second, null); check(worker.getQueue().size() == 1);
            check(second.current().phase == ManagerModel.Phase.LOADING);
            check(worker.getActiveCount() == 1); check(CatalogReader.calls.isEmpty());
            loader.cancel(first); check(worker.getQueue().size() == 1);
            first.detach(); blocked.release.countDown();
            CatalogReader.Call replacement = CatalogReader.started();
            check(!replacement.cancel.isCanceled()); check(CatalogReader.calls.isEmpty());
            replacement.release.countDown(); await(() -> Handler.dueCount() == 1); Handler.drain();
            check(second.current().phase == ManagerModel.Phase.LOADED);
            check(first.current().phase == ManagerModel.Phase.FAILED);
            check(first.current().visible.isEmpty()); check(!first.current().active);
            check(loader.nextPosition(first) == null); check(loader.nextPosition(second) != null);
        } finally { cleanup(); }
    }
    private static void pauseAndTimeout() throws Exception {
        setup();
        try {
            ManagerController owner = owner(); request(owner, null);
            CatalogReader.Call paused = CatalogReader.started();
            loader.cancel(owner); owner.detach(); check(paused.cancel.isCanceled());
            paused.release.countDown(); await(() -> worker.getActiveCount() == 0);
            Handler.drain(); check(!owner.current().active); check(owner.current().visible.isEmpty());
            check(loader.nextPosition(owner) == null); check(Handler.pendingCount() == 0);
            owner.attach(state -> check(Looper.myLooper() == Looper.getMainLooper())); request(owner, null);
            CatalogReader.Call timed = CatalogReader.started();
            SystemClock.now = 1999; Handler.drain(); check(owner.current().phase == ManagerModel.Phase.LOADING);
            SystemClock.now = 2000; Handler.drain();
            check(owner.current().phase == ManagerModel.Phase.FAILED); check(timed.cancel.isCanceled());
            timed.release.countDown(); await(() -> worker.getActiveCount() == 0); Handler.drain();
            check(owner.current().phase == ManagerModel.Phase.FAILED); check(loader.nextPosition(owner) == null);
            check(Handler.pendingCount() == 0);
        } finally { cleanup(); }
    }
    private static void rejectionAndLateMain() throws Exception {
        setup();
        try {
            ManagerController owner = owner(); Handler.rejectDelayed = true; request(owner, null);
            check(owner.current().phase == ManagerModel.Phase.FAILED); check(CatalogReader.calls.isEmpty());
            Handler.rejectDelayed = false; request(owner, null); CatalogReader.Call reject = CatalogReader.started();
            Handler.rejectPost = true; reject.release.countDown(); await(() -> worker.getActiveCount() == 0);
            check(((AtomicReference<?>) field(loader, "current")).get() == null);
            check(Handler.pendingCount() == 0); check(loader.nextPosition(owner) == null);
            check(owner.current().phase == ManagerModel.Phase.LOADING); // no main handler, no off-main failure callback
            Handler.rejectPost = false; request(owner, null); CatalogReader.Call delayed = CatalogReader.started();
            delayed.release.countDown(); await(() -> Handler.dueCount() == 1);
            SystemClock.now = 2000; Handler.drain();
            check(owner.current().phase == ManagerModel.Phase.FAILED); check(loader.nextPosition(owner) == null);
            worker.shutdown(); request(owner, null);
            check(owner.current().phase == ManagerModel.Phase.FAILED); check(Handler.pendingCount() == 0);
        } finally { cleanup(); }
    }
    public static void main(String[] args) throws Exception {
        successAndNext(); boundedReplacementAndCrossOwner(); pauseAndTimeout(); rejectionAndLateMain();
        if (checks < 89) throw new AssertionError("Missing orchestration checks " + checks);
        System.out.println("CatalogLoaderHostTest: " + checks + " checks PASS; actual loader, fake Android scheduling/reader, real bounded executor; NOT Android runtime");
    }
}
