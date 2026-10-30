package vn.fonesi.rebuild.catalog;

import java.util.Objects;

/** Pure delivery ownership/deadline guard; this is not storage or provider authority. */
public final class CatalogRequestGate {
    public static final class Ticket {
        private final Object owner;
        private final long deadline;
        private Ticket(Object owner, long deadline) { this.owner = owner; this.deadline = deadline; }
    }
    private Ticket current;

    public synchronized Ticket replace(Object owner, long now, long timeout) {
        Objects.requireNonNull(owner, "owner");
        if (now < 0 || timeout <= 0 || now > Long.MAX_VALUE - timeout) {
            throw new IllegalArgumentException("Invalid monotonic deadline");
        }
        current = new Ticket(owner, now + timeout);
        return current;
    }
    public synchronized boolean current(Ticket ticket) { return ticket != null && ticket == current; }
    public synchronized boolean live(Ticket ticket, long now) {
        return current(ticket) && now >= 0 && now < ticket.deadline;
    }
    public synchronized boolean finish(Ticket ticket, long now) {
        if (!live(ticket, now)) return false;
        current = null;
        return true;
    }
    public synchronized boolean invalidate(Ticket ticket) {
        if (!current(ticket)) return false;
        current = null;
        return true;
    }
    public synchronized boolean cancel(Object owner) {
        if (current == null || current.owner != owner) return false;
        current = null;
        return true;
    }
    public static long deadline(Ticket ticket) { return Objects.requireNonNull(ticket, "ticket").deadline; }
}
