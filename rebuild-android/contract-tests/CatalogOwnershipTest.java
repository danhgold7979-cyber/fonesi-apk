import vn.fonesi.rebuild.catalog.CatalogOwnership;

/** Synthetic observed metadata only; not Android lstat, ACL or isolation evidence. */
public final class CatalogOwnershipTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        int uid = 10123;
        for (int mode = 0; mode <= 0777; mode++) {
            for (boolean directory : new boolean[]{false, true}) {
                for (int changed = 0; changed <= 5; changed++) {
                    int owner = changed == 1 ? uid + 1 : uid;
                    int group = changed == 2 ? 2000 : uid;
                    int effectiveUid = changed == 3 ? 0 : uid;
                    int primary = changed == 4 ? 2000 : uid;
                    int effective = changed == 5 ? 2000 : uid;
                    boolean expected = changed != 1 && (mode & 0002) == 0
                            && ((mode & 0020) == 0 || (directory && changed == 0));
                    check(CatalogOwnership.permits(directory, mode, owner, group,
                            uid, effectiveUid, primary, effective) == expected);
                }
            }
        }
        check(CatalogOwnership.permits(true, 0771, uid, uid, uid, uid, uid, uid));
        check(!CatalogOwnership.permits(true, 0771, uid, 2000, uid, uid, uid, uid));
        check(!CatalogOwnership.permits(true, 0777, uid, uid, uid, uid, uid, uid));
        check(!CatalogOwnership.permits(false, 0660, uid, uid, uid, uid, uid, uid));
        check(!CatalogOwnership.permits(false, 0666, uid, uid, uid, uid, uid, uid));
        check(CatalogOwnership.permits(false, 0600, uid, uid, uid, uid, uid, uid));
        check(CatalogOwnership.permits(false, 0644, uid, uid, uid, uid, uid, uid));
        // Same rules hold for a secondary Android user's full sandbox UID, not an appId-only match.
        int secondaryUid = 1_010_123;
        check(CatalogOwnership.permits(true, 0771, secondaryUid, secondaryUid,
                secondaryUid, secondaryUid, secondaryUid, secondaryUid));
        check(!CatalogOwnership.permits(true, 0771, secondaryUid, uid,
                secondaryUid, secondaryUid, secondaryUid, secondaryUid));
        if (checks != 6153) throw new AssertionError("missing checks " + checks);
        System.out.println("CatalogOwnershipTest: " + checks + " permission truth-table checks PASS; synthetic metadata only");
    }
}
