package vn.fonesi.rebuild.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import vn.fonesi.rebuild.model.ManagerModel.CatalogKey;

/** Foreground-bound presentation only. No storage/provider adapter or operation dispatch. */
public final class ManagerController {
    public interface View { void render(Render state); }

    public static final class Token {
        private final Object owner;
        private final long epoch;
        private final long revision;
        private Token(Object owner, long epoch, long revision) { this.owner = owner; this.epoch = epoch; this.revision = revision; }
    }

    public static final class RefreshTicket {
        private final long epoch;
        private final long generation;
        private RefreshTicket(long epoch, long generation) { this.epoch = epoch; this.generation = generation; }
    }

    public static final class Render {
        public final Token token;
        public final boolean active;
        public final ManagerModel.Phase phase;
        public final List<ManagerModel.Entry> visible;
        public final Set<CatalogKey> selected;
        public final int totalCount;
        public final String query;
        public final String group;
        private Render(Object owner, long epoch, boolean active, ManagerModel.Snapshot snapshot) {
            token = new Token(owner, epoch, snapshot.revision);
            this.active = active;
            phase = snapshot.phase;
            visible = active ? snapshot.visible : Collections.emptyList();
            selected = active ? snapshot.selected : Collections.emptySet();
            totalCount = active ? snapshot.totalCount : 0;
            query = active ? snapshot.query : "";
            group = active ? snapshot.group : null;
        }
        public boolean operationsEnabled() { return false; }
    }

    private final Object owner = new Object();
    private final ManagerModel model = new ManagerModel();
    private long epoch;
    private boolean active;
    private boolean rendering;
    private View view;
    private RefreshTicket pending;

    public synchronized void attach(View nextView) {
        Objects.requireNonNull(nextView, "view");
        if (rendering) throw new IllegalStateException("Cannot attach during render");
        if (active) detach();
        long nextEpoch = Math.addExact(epoch, 1);
        view = nextView;
        epoch = nextEpoch;
        active = true;
        render();
    }

    public synchronized void detach() {
        if (rendering) throw new IllegalStateException("Cannot detach during render");
        if (!active) return;
        long nextEpoch = Math.addExact(epoch, 1);
        if (pending != null) model.fail(pending.generation);
        model.selectVisible(model.snapshot().revision, false);
        pending = null;
        epoch = nextEpoch;
        active = false;
        View previous = view;
        view = null;
        // The last rendering clears sensitive row metadata before abandoning the view.
        deliver(previous);
    }

    public synchronized Render current() { return new Render(owner, epoch, active, model.snapshot()); }

    public synchronized boolean filter(Token token, String query, String group) {
        if (!valid(token)) return false;
        model.filter(token.revision, query, group);
        render();
        return true;
    }

    public synchronized boolean select(Token token, CatalogKey key, boolean selected) {
        if (!valid(token) || !model.select(token.revision, key, selected)) return false;
        render();
        return true;
    }

    public synchronized boolean selectVisible(Token token, boolean selected) {
        if (!valid(token) || !model.selectVisible(token.revision, selected)) return false;
        render();
        return true;
    }

    /** Reserved for a future reviewed adapter. The production Activity never calls this. */
    public synchronized RefreshTicket beginRefresh(Token token) {
        if (!valid(token)) return null;
        RefreshTicket created = new RefreshTicket(epoch, model.beginRefresh());
        pending = created;
        render();
        return created;
    }

    public synchronized boolean publish(RefreshTicket ticket, List<ManagerModel.Entry> entries) {
        if (!valid(ticket)) return false;
        if (!model.publish(ticket.generation, entries)) return false;
        pending = null;
        render();
        return true;
    }

    public synchronized boolean fail(RefreshTicket ticket) {
        if (!valid(ticket) || !model.fail(ticket.generation)) return false;
        pending = null;
        render();
        return true;
    }

    private boolean valid(Token token) {
        return !rendering && active && token != null && token.owner == owner && token.epoch == epoch && token.revision == model.snapshot().revision;
    }

    private boolean valid(RefreshTicket ticket) {
        return !rendering && active && ticket != null && ticket == pending && ticket.epoch == epoch;
    }

    private void render() { if (view != null) deliver(view); }

    private void deliver(View target) {
        rendering = true;
        try { target.render(current()); }
        finally { rendering = false; }
    }
}
