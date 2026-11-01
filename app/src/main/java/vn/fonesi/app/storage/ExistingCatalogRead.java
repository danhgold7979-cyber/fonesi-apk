package vn.fonesi.app.storage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.os.UserManager;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.File;
import java.io.IOException;
import vn.fonesi.apk.foundation.CatalogOwnership;
import vn.fonesi.apk.foundation.CatalogReservation;

/** Dormant existing-file entry. No caller, bootstrap, schema acceptance or auth broker. */
final class ExistingCatalogRead {
    private static final String LEAF = "manager-catalog-v1.sqlite";
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
        private final File originalParent;
        private final File parent;
        private final File file;
        private final Identity directory;
        private final Identity leaf;
        private boolean closed;
        private boolean invalidated;
        private boolean closeUncertain;
        private boolean externalResourcesUncertain;
        private Lease(CatalogReservation.Token reservation, SQLiteDatabase database, CancellationSignal cancellation,
                      Request request, AccessBoundary access, Context application,
                      File originalParent, File parent, File file, Identity directory, Identity leaf) {
            this.reservation = reservation;
            this.application = application; this.originalParent = originalParent;
            this.parent = parent; this.file = file; this.directory = directory; this.leaf = leaf;
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
                verifyPaths(originalParent, parent, file, directory, leaf);
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
    private static final class Identity {
        final long device;
        final long inode;
        Identity(StructStat stat) { device = stat.st_dev; inode = stat.st_ino; }
        void verify(StructStat stat) { require(device == stat.st_dev && inode == stat.st_ino); }
    }
    private static StructStat observed(File path, CatalogOwnership.Role expected) throws ErrnoException {
        StructStat stat = Os.lstat(path.getPath());
        CatalogOwnership.Role role = OsConstants.S_ISDIR(stat.st_mode) ? CatalogOwnership.Role.DIRECTORY
                : OsConstants.S_ISREG(stat.st_mode) ? CatalogOwnership.Role.REGULAR_FILE
                : OsConstants.S_ISLNK(stat.st_mode) ? CatalogOwnership.Role.SYMLINK
                : CatalogOwnership.Role.OTHER;
        require(CatalogOwnership.assess(expected, role, stat.st_mode & 07777,
                stat.st_uid, stat.st_gid, android.os.Process.myUid(), Os.getgid(),
                Os.getegid(), Os.geteuid()) == CatalogOwnership.Decision.OWNERSHIP_CONDITION_MET);
        return stat;
    }
    private static void noSidecars(File file) throws ErrnoException {
        for (String suffix : new String[]{"-journal", "-wal", "-shm"}) {
            try { Os.lstat(file.getPath() + suffix); }
            catch (ErrnoException error) {
                if (error.errno == OsConstants.ENOENT) continue;
                throw error;
            }
            throw new Failure(Status.REJECTED);
        }
    }
    private static void available(Context application) {
        require(application != null && !application.isDeviceProtectedStorage());
        Object service = application.getSystemService(Context.USER_SERVICE);
        require(service instanceof UserManager && ((UserManager) service).isUserUnlocked());
    }
    private static void check(CancellationSignal cancellation, AccessBoundary access, Request request) {
        cancellation.throwIfCanceled(); access.check(request); cancellation.throwIfCanceled();
    }
    private static void verifyPaths(File originalParent, File parent, File file,
                                    Identity directory, Identity leaf) throws IOException, ErrnoException {
        require(originalParent.getCanonicalFile().equals(parent));
        directory.verify(observed(originalParent, CatalogOwnership.Role.DIRECTORY));
        directory.verify(observed(parent, CatalogOwnership.Role.DIRECTORY));
        StructStat current = observed(file, CatalogOwnership.Role.REGULAR_FILE);
        leaf.verify(current); require(current.st_size > 0);
        require(file.getCanonicalFile().equals(file) && file.getParentFile().equals(parent));
        noSidecars(file);
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
            File originalParent = application.getNoBackupFilesDir();
            require(originalParent != null);
            originalParent = originalParent.getAbsoluteFile();
            StructStat firstParent = observed(originalParent, CatalogOwnership.Role.DIRECTORY);
            Identity directory = new Identity(firstParent);
            File parent = originalParent.getCanonicalFile();
            directory.verify(observed(parent, CatalogOwnership.Role.DIRECTORY));
            File file = new File(parent, LEAF);
            StructStat firstFile;
            try { firstFile = observed(file, CatalogOwnership.Role.REGULAR_FILE); }
            catch (ErrnoException error) {
                if (error.errno == OsConstants.ENOENT) throw new Failure(Status.MISSING);
                throw error;
            }
            Identity leaf = new Identity(firstFile);
            require(firstFile.st_size > 0);
            verifyPaths(originalParent, parent, file, directory, leaf);
            check(cancellation, access, request);
            available(application);
            openAttempted = true;
            database = SQLiteDatabase.openDatabase(file.getPath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    ignored -> { throw new SQLiteDatabaseCorruptException("Catalog source unavailable"); });
            require(database.isOpen() && database.isReadOnly());
            verifyPaths(originalParent, parent, file, directory, leaf);
            available(application);
            check(cancellation, access, request);
            lease = new Lease(reservation, database, cancellation, request, access, application,
                    originalParent, parent, file, directory, leaf);
            status = Status.OPEN_UNVALIDATED;
            transferred = true;
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
