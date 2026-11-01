package vn.fonesi.apk.foundation;

import static vn.fonesi.apk.foundation.CatalogOwnership.Decision.*;
import static vn.fonesi.apk.foundation.CatalogOwnership.Role.*;

public final class CatalogOwnershipTest {
    private static int checks;
    private static final int APP = 10123;
    private CatalogOwnershipTest() { }
    private static void check(CatalogOwnership.Decision actual,
                              CatalogOwnership.Decision expected) {
        checks++;
        if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }
    private static CatalogOwnership.Decision own(CatalogOwnership.Role role, int mode) {
        return CatalogOwnership.assess(role, role, mode, APP, APP, APP, APP, APP, APP);
    }
    public static void main(String[] args) {
        for (int mode : new int[]{0000, 0700, 0750, 0755, 0771, 0770, 0775, 04771}) {
            check(own(DIRECTORY, mode), OWNERSHIP_CONDITION_MET);
        }
        for (int mode : new int[]{0000, 0600, 0640, 0644, 0755, 04755}) {
            check(own(REGULAR_FILE, mode), OWNERSHIP_CONDITION_MET);
        }
        // Read/execute/special bits do not establish confidentiality or openness.
        for (int mode : new int[]{0602, 0666, 0777, 07777}) {
            check(own(DIRECTORY, mode), WORLD_WRITABLE);
            check(own(REGULAR_FILE, mode), WORLD_WRITABLE);
        }
        for (int mode : new int[]{0020, 0660, 0770, 0771, 0775, 04771}) {
            check(own(REGULAR_FILE, mode), FILE_GROUP_WRITABLE);
        }
        for (int mismatch = 0; mismatch < 3; mismatch++) {
            int gid = mismatch == 0 ? APP + 1 : APP;
            int real = mismatch == 1 ? APP + 1 : APP;
            int effective = mismatch == 2 ? APP + 1 : APP;
            check(CatalogOwnership.assess(DIRECTORY, DIRECTORY, 0771,
                    APP, gid, APP, real, effective, APP), DIRECTORY_GROUP_MISMATCH);
            check(CatalogOwnership.assess(DIRECTORY, DIRECTORY, 0750,
                    APP, gid, APP, real, effective, APP), OWNERSHIP_CONDITION_MET);
        }
        for (CatalogOwnership.Role role : new CatalogOwnership.Role[]{DIRECTORY, REGULAR_FILE}) {
            check(CatalogOwnership.assess(role, role, 0700, APP + 1, APP, APP,
                    APP, APP, APP), OWNER_MISMATCH);
            check(CatalogOwnership.assess(role, role, 0700, APP, APP, APP,
                    APP, APP, APP + 1), EFFECTIVE_UID_MISMATCH);
            for (CatalogOwnership.Role observed : CatalogOwnership.Role.values()) {
                if (observed != role) check(CatalogOwnership.assess(role, observed,
                        0700, APP, APP, APP, APP, APP, APP), ROLE_MISMATCH);
            }
            check(CatalogOwnership.assess(role, null, 0700, APP, APP, APP,
                    APP, APP, APP), INVALID_OBSERVATION);
        }
        for (CatalogOwnership.Role expected : new CatalogOwnership.Role[]{null, SYMLINK, OTHER}) {
            check(CatalogOwnership.assess(expected, DIRECTORY, 0700, APP, APP,
                    APP, APP, APP, APP), INVALID_OBSERVATION);
        }
        for (int mode : new int[]{-1, 010000, 0100700, Integer.MAX_VALUE}) {
            check(own(DIRECTORY, mode), INVALID_OBSERVATION);
        }
        for (int field = 0; field < 6; field++) {
            int[] ids = {APP, APP, APP, APP, APP, APP}; ids[field] = -1;
            check(CatalogOwnership.assess(DIRECTORY, DIRECTORY, 0700,
                    ids[0], ids[1], ids[2], ids[3], ids[4], ids[5]), INVALID_OBSERVATION);
        }
        check(CatalogOwnership.assess(DIRECTORY, DIRECTORY, 0771,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), OWNERSHIP_CONDITION_MET);
        System.out.println("CatalogOwnership HOST assertions: " + checks + " PASS");
    }
}
