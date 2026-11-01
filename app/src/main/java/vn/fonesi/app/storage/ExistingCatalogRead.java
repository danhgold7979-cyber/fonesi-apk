package vn.fonesi.app.storage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.OperationCanceledException;
import vn.fonesi.apk.foundation.CatalogReservation;

/** Dormant existing-file entry. No caller, bootstrap, schema acceptance or auth broker. */
final class ExistingCatalogRead {
    private ExistingCatalogRead() { }

    enum Status { OPEN_UNVALIDATED, MISSING, BUSY, CANCELLED, REJECTED, RESOURCE_UNCERTAIN }
    /** Identity only. A real future broker must associate this exact object with a live session. */
    static final class Request { }
    interface AccessBoundary { void check(Request request); }
    static final class Result {
        final Status status;
        final Lease lease;
        private Result(Status status, Lease lease) { this.status = status; this.lease = lease; }
    }
    static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final Status status;
        private Failure(Status status) { super("Catalog source unavailable"); this.status = status; }
    }
    private static void require(boolean valid) {
        if (!valid) throw new Failure(Status.REJECTED);
    }
    static final class Lease implements AutoCloseable {
        private final Thread owner;
        private final CatalogReservation.Token reservation;
        private final SQLiteDatabase database;
        private final CancellationSignal cancellation;
        private final Request request;
        private final AccessBoundary access;
        private final Context application;
        private final CatalogPath path;
        private boolean closed;
        private boolean invalidated;
        private boolean closeUncertain;
        private boolean externalResourcesUncertain;
        private Lease(CatalogReservation.Token reservation, SQLiteDatabase database, CancellationSignal cancellation,
                      Request request, AccessBoundary access, Context application,
                      CatalogPath path) {
            this.reservation = reservation;
            this.application = application; this.path = path;
            this.owner = Thread.currentThread(); this.database = database;
            this.cancellation = cancellation; this.request = request; this.access = access;
        }
        /** Trusted future reader only; must not retain this raw handle beyond the lease. */
        SQLiteDatabase database() {
            require(Thread.currentThread() == owner);
            try {
                require(!closed && !invalidated);
                reservation.requireActive();
                check(cancellation, access, request);
                available(application);
                path.verify();
                check(cancellation, access, request);
                require(!closed && !invalidated);
                require(database.isOpen() && database.isReadOnly());
                return database;
            } catch (OperationCanceledException error) {
                invalidated = true;
                throw new Failure(Status.CANCELLED);
            } catch (Failure error) {
                invalidated = true;
                throw new Failure(error.status);
            } catch (Exception error) {
                invalidated = true;
                throw new Failure(Status.REJECTED);
            }
        }
        /** Owner only, before close: an uncertain external Cursor must quarantine this lease. */
        void quarantineResources() {
            require(Thread.currentThread() == owner && !closed);
            invalidated = true;
            externalResourcesUncertain = true;
            reservation.quarantine();
        }
        @Override public void close() {
            require(Thread.currentThread() == owner);
            if (closed) {
                if (closeUncertain) throw new Failure(Status.RESOURCE_UNCERTAIN);
                return;
            }
            closed = true;
            closeUncertain = true;
            try { database.close(); }
            catch (RuntimeException error) {
                quarantine(reservation);
                throw new Failure(Status.RESOURCE_UNCERTAIN);
            }
            if (externalResourcesUncertain) throw new Failure(Status.RESOURCE_UNCERTAIN);
            try { reservation.close(); }
            catch (RuntimeException error) {
                quarantine(reservation);
                throw new Failure(Status.RESOURCE_UNCERTAIN);
            }
            closeUncertain = false;
        }
    }
    private static void quarantine(CatalogReservation.Token reservation) {
        try { reservation.quarantine(); }
        catch (RuntimeException error) { /* Uncertain remains uncertain; never force-release. */ }
    }
    private static void available(Context application) { CatalogPath.available(application); }
    private static void check(CancellationSignal cancellation, AccessBoundary access, Request request) {
        cancellation.throwIfCanceled(); access.check(request); cancellation.throwIfCanceled();
    }

    static Result openExisting(Context context, Request request,
                               CancellationSignal cancellation, AccessBoundary access) {
        if (Looper.myLooper() == Looper.getMainLooper() || context == null
                || request == null || cancellation == null || access == null) {
            return new Result(Status.REJECTED, null);
        }
        CatalogReservation.Token reservation = CatalogProcessGate.tryAcquire();
        if (reservation == null) return new Result(Status.BUSY, null);
        SQLiteDatabase database = null;
        Lease lease = null;
        Status status = Status.REJECTED;
        boolean openAttempted = false;
        boolean transferred = false;
        boolean uncertain = false;
        try {
            check(cancellation, access, request);
            require(!context.isDeviceProtectedStorage());
            Context application = context.getApplicationContext();
            available(application);
            CatalogPath path = CatalogPath.existing(application);
            check(cancellation, access, request);
            available(application);
            openAttempted = true;
            database = SQLiteDatabase.openDatabase(path.filename(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    ignored -> { throw new SQLiteDatabaseCorruptException("Catalog source unavailable"); });
            require(database.isOpen() && database.isReadOnly());
            path.verify();
            available(application);
            check(cancellation, access, request);
            lease = new Lease(reservation, database, cancellation, request, access, application,
                    path);
            status = Status.OPEN_UNVALIDATED;
            transferred = true;
        } catch (CatalogPath.Missing error) {
            status = Status.MISSING;
        } catch (OperationCanceledException error) {
            status = Status.CANCELLED;
        } catch (Failure error) {
            status = error.status;
        } catch (Exception error) {
            status = Status.REJECTED;
        } finally {
            if (!transferred) {
                if (database != null) {
                    try { database.close(); }
                    catch (RuntimeException error) { uncertain = true; }
                } else if (openAttempted) {
                    // No returned handle after attempted native open: do not assert cleanup.
                    uncertain = true;
                }
                if (uncertain) {
                    quarantine(reservation);
                    status = Status.RESOURCE_UNCERTAIN;
                } else {
                    try { reservation.close(); }
                    catch (RuntimeException error) {
                        quarantine(reservation);
                        status = Status.RESOURCE_UNCERTAIN;
                    }
                }
            }
        }
        return new Result(status, lease);
    }
}
