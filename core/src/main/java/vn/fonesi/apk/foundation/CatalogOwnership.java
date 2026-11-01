package vn.fonesi.apk.foundation;

/** Pure ownership/write-mode condition, never path validation or authorization. */
public final class CatalogOwnership {
    private CatalogOwnership() { }

    public enum Role { DIRECTORY, REGULAR_FILE, SYMLINK, OTHER }
    public enum Decision {
        OWNERSHIP_CONDITION_MET, INVALID_OBSERVATION, ROLE_MISMATCH,
        OWNER_MISMATCH, EFFECTIVE_UID_MISMATCH, WORLD_WRITABLE,
        FILE_GROUP_WRITABLE, DIRECTORY_GROUP_MISMATCH
    }

    /**
     * Caller must derive role from lstat, not from a symlink target. Mode contains
     * only permission/special bits, not file-type bits. These are untrusted facts
     * until an actual observer supplies them; positive is not an open capability.
     */
    public static Decision assess(Role expected, Role observed, int mode,
                                  int ownerUid, int ownerGid, int appUid,
                                  int realGid, int effectiveGid, int effectiveUid) {
        if ((expected != Role.DIRECTORY && expected != Role.REGULAR_FILE)
                || observed == null || mode < 0 || (mode & ~07777) != 0
                || ownerUid < 0 || ownerGid < 0 || appUid < 0
                || realGid < 0 || effectiveGid < 0 || effectiveUid < 0) {
            return Decision.INVALID_OBSERVATION;
        }
        if (expected != observed) return Decision.ROLE_MISMATCH;
        if (ownerUid != appUid) return Decision.OWNER_MISMATCH;
        if (effectiveUid != appUid) return Decision.EFFECTIVE_UID_MISMATCH;
        if ((mode & 0002) != 0) return Decision.WORLD_WRITABLE;
        if ((mode & 0020) != 0) {
            if (expected == Role.REGULAR_FILE) return Decision.FILE_GROUP_WRITABLE;
            if (ownerGid != appUid || realGid != appUid || effectiveGid != appUid) {
                return Decision.DIRECTORY_GROUP_MISMATCH;
            }
        }
        return Decision.OWNERSHIP_CONDITION_MET;
    }
}
