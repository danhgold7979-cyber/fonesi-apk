package vn.fonesi.app.storage;

import android.content.Context;
import android.os.UserManager;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.File;
import java.io.IOException;
import vn.fonesi.apk.foundation.CatalogOwnership;

/** Existing fixed-path observations only. Caller must hold reservation and real access first. */
final class CatalogPath {
    private static final String LEAF = "manager-catalog-v1.sqlite";
    static final class Missing extends Exception {
        private static final long serialVersionUID = 1L;
        Missing() { super("Catalog source unavailable"); }
    }
    private final File originalParent;
    private final File parent;
    private final File file;
    private final Identity directory;
    private final Identity leaf;
    private CatalogPath(File originalParent, File parent, File file, Identity directory, Identity leaf) {
        this.originalParent = originalParent; this.parent = parent; this.file = file;
        this.directory = directory; this.leaf = leaf;
    }
    private static void require(boolean value) {
        if (!value) throw new IllegalStateException("Catalog source unavailable");
    }
    static void available(Context application) {
        require(application != null && !application.isDeviceProtectedStorage());
        Object service = application.getSystemService(Context.USER_SERVICE);
        require(service instanceof UserManager && ((UserManager) service).isUserUnlocked());
    }
    static CatalogPath existing(Context application) throws IOException, ErrnoException, Missing {
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
            if (error.errno == OsConstants.ENOENT) throw new Missing();
            throw error;
        }
        Identity leaf = new Identity(firstFile);
        require(firstFile.st_size > 0);
        CatalogPath path = new CatalogPath(originalParent, parent, file, directory, leaf);
        path.verify();
        return path;
    }
    String filename() { return file.getPath(); }
    void verify() throws IOException, ErrnoException {
        verifyPaths(originalParent, parent, file, directory, leaf);
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
            throw new IllegalStateException("Catalog source unavailable");
        }
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

}
