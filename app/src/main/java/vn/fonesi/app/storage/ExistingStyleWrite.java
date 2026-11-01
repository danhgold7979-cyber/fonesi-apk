package vn.fonesi.app.storage;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import vn.fonesi.apk.foundation.CatalogKey;
import vn.fonesi.apk.foundation.CatalogReservation;
import vn.fonesi.apk.foundation.StyleValue;

/** Dormant existing-only entry. No caller, grant implementation, bootstrap or migration. */
final class ExistingStyleWrite {
    private ExistingStyleWrite() { }
    enum Status {
        COMMITTED, COMMITTED_RESOURCE_UNCERTAIN, ROLLED_BACK, REJECTED, CANCELLED,
        STALE_OR_MISSING, BUDGET_EXCEEDED, REVISION_EXHAUSTED, INDETERMINATE,
        MISSING, BUSY, DEADLINE, RESOURCE_UNCERTAIN
    }
    static final class Request {
        final OwnedCatalogWriter.Snapshot snapshot;
        final CatalogKey key;
        final StyleValue style;
        Request(OwnedCatalogWriter.Snapshot snapshot, CatalogKey key, StyleValue style) {
            if (snapshot == null || key == null || style == null) throw new Failure(Status.REJECTED);
            this.snapshot = snapshot; this.key = key; this.style = style;
        }
    }
    static final class Result {
        final Status status;
        final OwnedCatalogWriter.Status writerOutcome;
        final long committedRevision;
        private Result(Status status, OwnedCatalogWriter.Status writerOutcome, long revision) {
            this.status = status; this.writerOutcome = writerOutcome; this.committedRevision = revision;
        }
    }
    private static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final Status status;
        Failure(Status status) { super("Catalog write unavailable"); this.status = status; }
    }
    private static void require(boolean value) { if (!value) throw new Failure(Status.REJECTED); }
    private static final class Opening {
        final Request request;
        final OwnedCatalogWriter.CommitBoundary boundary;
        final CancellationSignal cancellation;
        final long start = SystemClock.elapsedRealtime();
        final long millis;
        Opening(Request request, OwnedCatalogWriter.CommitBoundary boundary,
                CancellationSignal cancellation, long millis) {
            this.request = request; this.boundary = boundary; this.cancellation = cancellation;
            this.millis = millis; require(start >= 0);
        }
        void budget() {
            cancellation.throwIfCanceled();
            long now = SystemClock.elapsedRealtime(); require(now >= start);
            if (now - start >= millis) throw new Failure(Status.DEADLINE);
        }
        void authorize() {
            budget(); boundary.checkBeforeStaging(request.snapshot, request.key, request.style); budget();
        }
        void check(Context application, CatalogPath path, CatalogReservation.Token reservation) {
            reservation.requireActive(); authorize();
            CatalogPath.available(application);
            try { path.verify(); }
            catch (Exception failure) { throw new Failure(Status.REJECTED); }
            authorize();
        }
    }
    static Result apply(Context context, Request request, OwnedCatalogWriter.CommitBoundary boundary,
                        CancellationSignal cancellation, long openingBudgetMillis) {
        if (context == null || request == null || boundary == null || cancellation == null
                || openingBudgetMillis < 1 || openingBudgetMillis > 5000
                || Looper.myLooper() == Looper.getMainLooper()) {
            return new Result(Status.REJECTED, null, -1);
        }
        CatalogReservation.Token reservation = CatalogProcessGate.tryAcquire();
        if (reservation == null) return new Result(Status.BUSY, null, -1);
        SQLiteDatabase database = null;
        boolean nativeOpenPending = false;
        boolean uncertain = false;
        boolean writerEntered = false;
        OwnedCatalogWriter.Result writer = null;
        Status status = Status.REJECTED;
        long committedRevision = -1;
        try {
            Opening opening = new Opening(request, boundary, cancellation, openingBudgetMillis);
            reservation.requireActive(); opening.authorize();
            require(!context.isDeviceProtectedStorage());
            Context application = context.getApplicationContext();
            CatalogPath.available(application);
            CatalogPath path = CatalogPath.existing(application);
            opening.check(application, path, reservation);
            AssetManager assets = application.getAssets();
            OwnedCatalogWriter.Sql sql = OwnedCatalogWriter.Sql.load(assets);
            opening.check(application, path, reservation);
            nativeOpenPending = true;
            database = SQLiteDatabase.openDatabase(path.filename(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    ignored -> { throw new SQLiteDatabaseCorruptException("Catalog source unavailable"); });
            nativeOpenPending = false;
            require(database.isOpen() && database.isReadOnly() && !database.inTransaction());
            Status[] observedFailure = {null};
            try {
                OwnedCatalogWriter.inspectReadOnly(database, assets, cancellation, () -> {
                    try { opening.check(application, path, reservation); }
                    catch (Failure failure) { observedFailure[0] = failure.status; throw failure; }
                });
            } catch (OwnedCatalogWriter.ReadInspectionFailure failure) {
                if (failure.uncertain) { uncertain = true; throw new Failure(Status.RESOURCE_UNCERTAIN); }
                if (observedFailure[0] != null) throw new Failure(observedFailure[0]);
                throw new Failure(failure.cancelled ? Status.CANCELLED : Status.REJECTED);
            }
            try { database.close(); }
            catch (RuntimeException failure) { uncertain = true; throw new Failure(Status.RESOURCE_UNCERTAIN); }
            database = null;
            // Same token is still held; never release/reacquire across RO -> RW.
            opening.check(application, path, reservation);
            nativeOpenPending = true;
            database = SQLiteDatabase.openDatabase(path.filename(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    ignored -> { throw new SQLiteDatabaseCorruptException("Catalog source unavailable"); });
            nativeOpenPending = false;
            require(database.isOpen() && !database.isReadOnly() && !database.inTransaction());
            opening.check(application, path, reservation);
            // Opening budget ends here. The reviewed writer owns staging/commit/cancel semantics.
            writerEntered = true;
            writer = OwnedCatalogWriter.apply(database, sql, request.snapshot, request.key,
                    request.style, cancellation, boundary);
            require(writer != null);
            status = map(writer.status);
            if (writer.status == OwnedCatalogWriter.Status.COMMITTED) {
                require(writer.committedRevision >= 0);
                committedRevision = writer.committedRevision;
            }
            if (writer.status == OwnedCatalogWriter.Status.INDETERMINATE) uncertain = true;
        } catch (CatalogPath.Missing failure) {
            status = Status.MISSING;
        } catch (OperationCanceledException failure) {
            status = writerEntered ? Status.INDETERMINATE : Status.CANCELLED;
            if (writerEntered) uncertain = true;
        } catch (Failure failure) {
            status = writerEntered ? Status.INDETERMINATE : failure.status;
            if (writerEntered) uncertain = true;
        } catch (Exception failure) {
            status = writerEntered ? Status.INDETERMINATE : Status.REJECTED;
            if (writerEntered) uncertain = true;
        } finally {
            if (nativeOpenPending) uncertain = true;
            if (database != null) {
                try { database.close(); }
                catch (RuntimeException failure) { uncertain = true; }
            }
            if (uncertain) {
                quarantine(reservation);
                status = uncertainStatus(writer, committedRevision, writerEntered);
            } else {
                try { reservation.close(); }
                catch (RuntimeException failure) {
                    quarantine(reservation);
                    status = uncertainStatus(writer, committedRevision, writerEntered);
                }
            }
        }
        // No late deadline/access/cancellation check may erase known commit or resource uncertainty.
        return new Result(status, writer == null ? null : writer.status, committedRevision);
    }
    private static Status uncertainStatus(OwnedCatalogWriter.Result writer, long revision, boolean entered) {
        if (revision >= 0) return Status.COMMITTED_RESOURCE_UNCERTAIN;
        if ((writer == null && entered) || (writer != null && writer.status == OwnedCatalogWriter.Status.INDETERMINATE)) {
            return Status.INDETERMINATE;
        }
        return Status.RESOURCE_UNCERTAIN; // Known writerOutcome, including rollback, remains in Result.
    }
    private static void quarantine(CatalogReservation.Token reservation) {
        try { reservation.quarantine(); }
        catch (RuntimeException failure) { /* Never force-release unknown ownership. */ }
    }
    private static Status map(OwnedCatalogWriter.Status outcome) {
        switch (outcome) {
            case COMMITTED: return Status.COMMITTED;
            case ROLLED_BACK: return Status.ROLLED_BACK;
            case CANCELLED: return Status.CANCELLED;
            case STALE_OR_MISSING: return Status.STALE_OR_MISSING;
            case BUDGET_EXCEEDED: return Status.BUDGET_EXCEEDED;
            case REVISION_EXHAUSTED: return Status.REVISION_EXHAUSTED;
            case INDETERMINATE: return Status.INDETERMINATE;
            default: return Status.REJECTED;
        }
    }
}
