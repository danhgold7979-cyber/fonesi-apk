package vn.fonesi.rebuild.catalog;
import android.content.Context;
import android.os.CancellationSignal;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import vn.fonesi.rebuild.model.ManagerModel;
/** Deliberately returns even after cancellation, proving loader suppression rather than cooperative reader luck. */
public final class CatalogReader {
    static final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();
    static final class Call {
        final CancellationSignal cancel;
        final CatalogPage.Position after;
        final long deadline;
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean fail;
        Call(CancellationSignal cancel, CatalogPage.Position after, long deadline) {
            this.cancel = cancel; this.after = after; this.deadline = deadline;
        }
    }
    public CatalogReader(Context context) { }
    public CatalogPage read(CatalogPage.Position after, CancellationSignal cancel, long deadline) throws IOException {
        Call call = new Call(cancel, after, deadline);
        calls.add(call);
        try {
            if (!call.release.await(5, TimeUnit.SECONDS)) throw new IOException("Host test did not release reader");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Test cleanup"); }
        if (call.fail) throw new IOException("Synthetic read failure");
        CatalogPage.Position next = new CatalogPage.Position("00000000-0000-0000-0000-000000000000", 0, 7);
        return new CatalogPage(Collections.singletonList(new ManagerModel.Entry(
                new ManagerModel.CatalogKey("engine", "opaque"), "p", "Label", "", ManagerModel.Status.UNVERIFIED)),
                next.catalogId, 0, next);
    }
    static Call started() throws InterruptedException {
        Call call = calls.poll(5, TimeUnit.SECONDS);
        if (call == null) throw new AssertionError("Reader not started");
        return call;
    }
}
