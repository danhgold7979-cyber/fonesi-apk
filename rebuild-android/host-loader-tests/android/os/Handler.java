package android.os;
import java.util.ArrayList;
import java.util.List;
public final class Handler {
    private static final class Task {
        final Runnable runnable;
        final long when;
        Task(Runnable runnable, long when) { this.runnable = runnable; this.when = when; }
    }
    private static final List<Task> tasks = new ArrayList<>();
    public static volatile boolean rejectPost;
    public static volatile boolean rejectDelayed;
    public Handler(Looper looper) { if (looper != Looper.getMainLooper()) throw new AssertionError(); }
    public boolean post(Runnable runnable) {
        if (rejectPost) return false;
        synchronized (tasks) { tasks.add(new Task(runnable, SystemClock.now)); }
        return true;
    }
    public boolean postDelayed(Runnable runnable, long delay) {
        if (rejectDelayed) return false;
        synchronized (tasks) { tasks.add(new Task(runnable, SystemClock.now + delay)); }
        return true;
    }
    public void removeCallbacks(Runnable runnable) {
        synchronized (tasks) { tasks.removeIf(task -> task.runnable == runnable); }
    }
    public static int dueCount() {
        synchronized (tasks) { return (int) tasks.stream().filter(t -> t.when <= SystemClock.now).count(); }
    }
    public static int pendingCount() { synchronized (tasks) { return tasks.size(); } }
    public static void drain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new AssertionError("not main");
        while (true) {
            Task next = null;
            synchronized (tasks) {
                for (Task t : tasks) if (t.when <= SystemClock.now && (next == null || t.when < next.when)) next = t;
                if (next != null) tasks.remove(next);
            }
            if (next == null) return;
            next.runnable.run();
        }
    }
    public static void reset() {
        synchronized (tasks) { tasks.clear(); }
        rejectPost = false; rejectDelayed = false; SystemClock.now = 0;
    }
}
