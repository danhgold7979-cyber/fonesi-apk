package vn.fonesi.apk.foundation;

import java.util.concurrent.atomic.AtomicReference;

/** Process-local ownership only. Never an authentication, file or database authority. */
public final class CatalogReservation {
    public enum Error { WRONG_THREAD, STATE, RESOURCE_UNCERTAIN, INVARIANT }
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Error error;
        private Failure(Error error) { super("Catalog ownership unavailable"); this.error = error; }
        public Error error() { return error; }
    }
    private final AtomicReference<Token> current = new AtomicReference<>();
    /** Null means BUSY. Production must share one instance across all participating openers. */
    public Token tryAcquire() {
        Token candidate = new Token(this, Thread.currentThread());
        return current.compareAndSet(null, candidate) ? candidate : null;
    }
    private enum State { ACTIVE, QUARANTINED, RELEASED }
    public static final class Token implements AutoCloseable {
        private final CatalogReservation reservation;
        private final Thread owner;
        private State state = State.ACTIVE;
        private Token(CatalogReservation reservation, Thread owner) {
            this.reservation = reservation; this.owner = owner;
        }
        private void owner() {
            if (Thread.currentThread() != owner) throw new Failure(Error.WRONG_THREAD);
        }
        private void identity() {
            if (reservation.current.get() != this) throw new Failure(Error.INVARIANT);
        }
        public void requireActive() {
            owner();
            if (state == State.RELEASED) throw new Failure(Error.STATE);
            if (state == State.QUARANTINED) throw new Failure(Error.RESOURCE_UNCERTAIN);
            identity();
        }
        /** Sticky uncertainty; only this acquiring thread may quarantine before release. */
        public void quarantine() {
            owner();
            if (state == State.RELEASED) throw new Failure(Error.STATE);
            identity();
            state = State.QUARANTINED;
        }
        @Override public void close() {
            owner();
            if (state == State.RELEASED) return; // Never inspect or clear a newer token.
            if (state == State.QUARANTINED) throw new Failure(Error.RESOURCE_UNCERTAIN);
            identity();
            if (!reservation.current.compareAndSet(this, null)) throw new Failure(Error.INVARIANT);
            state = State.RELEASED;
        }
    }
}
