package vn.fonesi.rebuild.catalog;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import vn.fonesi.rebuild.model.ManagerController;

/** One application-owned worker; only immutable metadata reaches the foreground controller. */
public final class CatalogLoader {
    private static final long TIMEOUT_MILLIS = 2_000;
    private static CatalogLoader instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(1), new ThreadPoolExecutor.AbortPolicy());
    private final CatalogRequestGate gate = new CatalogRequestGate();
    private final CatalogReader reader;
    private final AtomicReference<Request> current = new AtomicReference<>();
    private ManagerController nextOwner;
    private CatalogPage.Position next;

    private CatalogLoader(Context context) { reader = new CatalogReader(context.getApplicationContext()); }

    public static synchronized CatalogLoader get(Context context) {
        requireMain();
        if (instance == null) instance = new CatalogLoader(context);
        return instance;
    }

    public CatalogPage.Position nextPosition(ManagerController owner) {
        requireMain();
        return owner == nextOwner ? next : null;
    }

    public void request(ManagerController controller, ManagerController.RefreshTicket ticket,
                        CatalogPage.Position after) {
        requireMain();
        if (controller == null || ticket == null) throw new IllegalArgumentException("Missing request owner");
        abandonCurrent();
        clearNext();
        Request request = new Request(controller, ticket, after,
                gate.replace(controller, SystemClock.elapsedRealtime(), TIMEOUT_MILLIS));
        current.set(request);
        if (!main.postDelayed(request.timeout, TIMEOUT_MILLIS)) {
            fail(request);
            return;
        }
        try { worker.execute(request); }
        catch (RejectedExecutionException rejected) { fail(request); }
    }

    public void cancel(ManagerController owner) {
        requireMain();
        Request active = current.get();
        if (active != null && active.owner == owner) abandonCurrent();
        gate.cancel(owner);
        if (nextOwner == owner) clearNext();
    }

    private void abandonCurrent() {
        Request old = current.getAndSet(null);
        if (old == null) return;
        gate.invalidate(old.gateTicket);
        main.removeCallbacks(old.timeout);
        main.removeCallbacks(old.delivery);
        old.result = null;
        worker.remove(old);
        old.cancel.cancel();
        old.owner.fail(old.ticket); // A displaced foreground owner must not remain loading forever.
    }

    private void clearNext() { next = null; nextOwner = null; }

    private void fail(Request request) {
        requireMain();
        if (current.get() != request || !gate.invalidate(request.gateTicket)) return;
        current.compareAndSet(request, null);
        clearNext();
        main.removeCallbacks(request.timeout);
        main.removeCallbacks(request.delivery);
        request.result = null;
        worker.remove(request);
        request.cancel.cancel();
        request.owner.fail(request.ticket);
    }

    private void complete(Request request, CatalogPage result) {
        requireMain();
        if (current.get() != request || !gate.current(request.gateTicket)) return;
        if (result == null || request.cancel.isCanceled()
                || !gate.live(request.gateTicket, SystemClock.elapsedRealtime())) {
            fail(request);
            return;
        }
        if (!gate.finish(request.gateTicket, SystemClock.elapsedRealtime())) { fail(request); return; }
        current.compareAndSet(request, null);
        main.removeCallbacks(request.timeout);
        nextOwner = request.owner;
        next = result.next;
        try {
            if (!request.owner.publish(request.ticket, result.entries)) clearNext();
        } catch (RuntimeException invalidResult) {
            clearNext();
            request.owner.fail(request.ticket);
        }
    }

    private final class Request implements Runnable {
        final ManagerController owner;
        final ManagerController.RefreshTicket ticket;
        final CatalogPage.Position after;
        final CatalogRequestGate.Ticket gateTicket;
        final CancellationSignal cancel = new CancellationSignal();
        volatile CatalogPage result;
        final Runnable timeout = () -> fail(this);
        final Runnable delivery = () -> {
            CatalogPage completed = result;
            result = null;
            complete(this, completed);
        };
        Request(ManagerController owner, ManagerController.RefreshTicket ticket,
                CatalogPage.Position after, CatalogRequestGate.Ticket gateTicket) {
            this.owner = owner;
            this.ticket = ticket;
            this.after = after;
            this.gateTicket = gateTicket;
        }
        @Override public void run() {
            CatalogPage page = null;
            if (gate.live(gateTicket, SystemClock.elapsedRealtime()) && !cancel.isCanceled()) {
                try { page = reader.read(after, cancel, CatalogRequestGate.deadline(gateTicket)); }
                catch (IOException | RuntimeException unavailable) { /* Retain source; generic failure only. */ }
            }
            if (!gate.current(gateTicket) || cancel.isCanceled()) return;
            result = page;
            if (!main.post(delivery)) {
                result = null;
                gate.invalidate(gateTicket); // No UI thread means no publish; never run completion on worker.
                current.compareAndSet(this, null);
                main.removeCallbacks(timeout);
                cancel.cancel();
            }
        }
    }

    private static void requireMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Catalog delivery requires main thread");
    }
}
