package vn.fonesi.rebuild.catalog;

/** Permission decision only; caller must separately lstat type and verify path/device/inode. */
public final class CatalogOwnership {
    private CatalogOwnership() { }

    public static boolean permits(boolean directory, int mode, int ownerUid, int ownerGid,
                                  int processUid, int effectiveUid, int processGid, int effectiveGid) {
        if (ownerUid != processUid || (mode & 0002) != 0) return false;
        if ((mode & 0020) == 0) return true;
        // Framework no_backup may be0771. Only its current-app primary group is trusted.
        // Regular files never receive this exception, including newly created files.
        return directory && ownerGid == processUid && effectiveUid == processUid
                && processGid == processUid && effectiveGid == processUid;
    }
}
