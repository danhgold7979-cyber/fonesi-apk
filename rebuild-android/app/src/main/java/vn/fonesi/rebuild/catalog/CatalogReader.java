package vn.fonesi.rebuild.catalog;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.fonesi.rebuild.model.ManagerModel;

/** Owned metadata only. Existing files never get upgrade, repair, deletion or read-write recovery. */
public final class CatalogReader {
    private final Context context;
    public CatalogReader(Context context) { this.context = context.getApplicationContext(); }

    public CatalogPage read(CatalogPage.Position after, CancellationSignal cancel, long deadline) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IOException("Catalog requires worker");
        try {
            check(cancel, deadline);
            UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
            require(!context.isDeviceProtectedStorage() && users != null && users.isUserUnlocked());
            File requestedParent = context.getNoBackupFilesDir();
            require(requestedParent != null);
            // Context supplies this path. Canonicalize platform ancestor aliases, but reject a linked leaf.
            stat(requestedParent, true);
            File parent = requestedParent.getCanonicalFile();
            stat(parent, true);
            File file = new File(parent, "manager-catalog-v1.sqlite");
            // Creation is exclusive. A partial file from any previous attempt is never initialized.
            boolean created = file.createNewFile();
            StructStat identity = stat(file, false);
            noSidecars(file);
            if (created) initialize(file, identity, cancel, deadline);
            check(cancel, deadline);
            same(file, identity);
            noSidecars(file);
            try (SQLiteDatabase db = open(file, true)) {
                require(db.isReadOnly());
                same(file, identity);
                schema(db, cancel, deadline);
                CatalogPage result = page(db, after, cancel, deadline);
                same(file, identity);
                stat(parent, true);
                stat(requestedParent, true);
                require(parent.equals(requestedParent.getCanonicalFile()));
                noSidecars(file);
                check(cancel, deadline);
                return result;
            }
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new IOException("Catalog unavailable; source retained"); }
    }

    private static SQLiteDatabase open(File file, boolean readOnly) {
        return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                (readOnly ? SQLiteDatabase.OPEN_READONLY : SQLiteDatabase.OPEN_READWRITE)
                        | SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                ignored -> { throw new SQLiteDatabaseCorruptException("Catalog retained"); });
    }
    private static void initialize(File file, StructStat identity, CancellationSignal cancel, long deadline) throws Exception {
        same(file, identity);
        require(file.length() == 0);
        check(cancel, deadline);
        try (SQLiteDatabase db = open(file, false)) {
            same(file, identity);
            try (Cursor c = db.rawQuery("PRAGMA journal_mode=DELETE", null, cancel)) {
                require(c.moveToNext() && "delete".equals(text(c, 0, 16)) && !c.moveToNext());
            }
            db.execSQL("PRAGMA synchronous=FULL");
            require(number(db, "PRAGMA main.synchronous", cancel) == 2);
            db.execSQL("PRAGMA encoding='UTF-8'");
            db.beginTransaction();
            try {
                check(cancel, deadline);
                db.execSQL(CatalogSql.META_DDL);
                db.execSQL(CatalogSql.CATALOG_DDL);
                db.execSQL("PRAGMA application_id=1178815793");
                db.execSQL("PRAGMA user_version=1");
                db.execSQL("INSERT INTO catalog_meta(id,catalog_id,revision) VALUES(1,?,0)",
                        new Object[] {UUID.randomUUID().toString()});
                check(cancel, deadline);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        same(file, identity);
    }
    private static StructStat stat(File file, boolean directory) throws Exception {
        StructStat s = Os.lstat(file.getAbsolutePath());
        require(s.st_uid == Process.myUid() && (s.st_mode & 0022) == 0
                && (directory ? OsConstants.S_ISDIR(s.st_mode) : OsConstants.S_ISREG(s.st_mode)));
        return s;
    }
    private static void same(File file, StructStat expected) throws Exception {
        StructStat actual = stat(file, false);
        require(actual.st_dev == expected.st_dev && actual.st_ino == expected.st_ino);
    }
    private static void noSidecars(File file) throws IOException {
        for (String suffix : new String[] {"-journal", "-wal", "-shm"}) {
            File sidecar = new File(file.getPath() + suffix);
            // lstat also detects dangling links; only definite ENOENT is acceptable.
            try { Os.lstat(sidecar.getPath()); throw new IOException("Catalog sidecar retained"); }
            catch (android.system.ErrnoException absent) { require(absent.errno == OsConstants.ENOENT); }
        }
    }
    private static void schema(SQLiteDatabase db, CancellationSignal cancel, long deadline) throws Exception {
        require(number(db, "PRAGMA application_id", cancel) == CatalogSql.APPLICATION_ID);
        require(number(db, "PRAGMA user_version", cancel) == 1);
        try (Cursor c = db.rawQuery("PRAGMA encoding", null, cancel)) {
            require(c.moveToNext() && "UTF-8".equals(text(c, 0, 16)) && !c.moveToNext());
        }
        // Reject oversized schema text before transferring it to a CursorWindow.
        String sql = "SELECT CASE WHEN length(CAST(type AS BLOB))<=16 THEN CAST(type AS BLOB) END, "
                + "CASE WHEN length(CAST(name AS BLOB))<=80 THEN CAST(name AS BLOB) END, "
                + "CASE WHEN length(CAST(tbl_name AS BLOB))<=80 THEN CAST(tbl_name AS BLOB) END, "
                + "CASE WHEN length(CAST(sql AS BLOB))<=4096 THEN CAST(sql AS BLOB) END FROM main.sqlite_master ORDER BY name LIMIT 4";
        String[][] expected = {{"table", "catalog_meta", "catalog_meta", CatalogSql.META_DDL.trim()},
                {"table", "manager_catalog", "manager_catalog", CatalogSql.CATALOG_DDL.trim()},
                {"index", "sqlite_autoindex_manager_catalog_1", "manager_catalog", null}};
        try (Cursor c = db.rawQuery(sql, null, cancel)) {
            for (String[] row : expected) {
                require(c.moveToNext());
                for (int i = 0; i < 4; i++) {
                    if (row[i] == null) require(c.isNull(i));
                    else require(row[i].equals(blobText(c, i, 4096)));
                }
            }
            require(!c.moveToNext());
        }
        try (Cursor c = db.rawQuery("PRAGMA database_list", null, cancel)) {
            require(c.moveToNext() && integer(c, 0) == 0 && "main".equals(text(c, 1, 16)) && !c.moveToNext());
        }
        require(number(db, "SELECT count(*) FROM sqlite_temp_master", cancel) == 0);
        table(db, "catalog_meta", new String[]{"id", "catalog_id", "revision"},
                new String[]{"INTEGER", "TEXT", "INTEGER"}, new int[]{0,1,1}, new int[]{1,0,0}, cancel);
        table(db, "manager_catalog", new String[]{"engine_key","clone_id","package_name","label","group_name","legacy_launch_token","accent_key","badge_text","cluster_id","readiness","launch_allowed"},
                new String[]{"TEXT","TEXT","TEXT","TEXT","TEXT","TEXT","TEXT","TEXT","TEXT","TEXT","INTEGER"},
                new int[]{1,1,1,1,0,0,0,0,0,1,1}, new int[]{1,2,0,0,0,0,0,0,0,0,0}, cancel);
        try (Cursor c = db.rawQuery("PRAGMA main.index_list(catalog_meta)", null, cancel)) { require(c.getColumnCount() == 5 && !c.moveToNext()); }
        try (Cursor c = db.rawQuery("PRAGMA main.index_list(manager_catalog)", null, cancel)) {
            require(c.getColumnCount() == 5 && c.moveToNext() && integer(c,0)==0
                    && "sqlite_autoindex_manager_catalog_1".equals(text(c,1,80)) && integer(c,2)==1
                    && "pk".equals(text(c,3,8)) && integer(c,4)==0 && !c.moveToNext());
        }
        try (Cursor c = db.rawQuery("PRAGMA main.index_xinfo(sqlite_autoindex_manager_catalog_1)", null, cancel)) {
            require(c.getColumnCount() == 6);
            for (int i=0;i<3;i++) {
                require(c.moveToNext() && integer(c,0)==i && integer(c,1)==(i==2 ? -1 : i)
                        && integer(c,3)==0 && "BINARY".equals(text(c,4,16)) && integer(c,5)==(i==2 ? 0 : 1));
                if (i==2) require(c.isNull(2)); else require((i==0 ? "engine_key" : "clone_id").equals(text(c,2,32)));
            }
            require(!c.moveToNext());
        }
        singleton(db, cancel);
        check(cancel, deadline);
    }
    private static void table(SQLiteDatabase db, String name, String[] names, String[] types, int[] notNull, int[] pk, CancellationSignal cancel) throws Exception {
        // Names are private fixed call-site literals, never catalog values.
        try (Cursor c = db.rawQuery("PRAGMA main.table_info(" + name + ")", null, cancel)) {
            require(c.getColumnCount()==6);
            for (int i=0;i<names.length;i++) require(c.moveToNext() && integer(c,0)==i
                    && names[i].equals(text(c,1,80)) && types[i].equals(text(c,2,16))
                    && integer(c,3)==notNull[i] && c.isNull(4) && integer(c,5)==pk[i]);
            require(!c.moveToNext());
        }
    }
    private static void singleton(SQLiteDatabase db, CancellationSignal cancel) throws Exception {
        try (Cursor c = db.rawQuery(CatalogSql.META_CARDINALITY, null, cancel)) {
            require(c.moveToNext() && integer(c,0)==1 && integer(c,1)==1 && !c.moveToNext());
        }
    }
    private static long number(SQLiteDatabase db, String sql, CancellationSignal cancel) throws Exception {
        try (Cursor c = db.rawQuery(sql, null, cancel)) {
            require(c.moveToNext() && c.getColumnCount()==1);
            long result=integer(c,0); require(!c.moveToNext()); return result;
        }
    }
    private static CatalogPage page(SQLiteDatabase db, CatalogPage.Position after, CancellationSignal cancel, long deadline) throws Exception {
        List<ManagerModel.Entry> entries = new ArrayList<>();
        Set<ManagerModel.CatalogKey> keys = new HashSet<>();
        Meta meta = null;
        long last = after == null ? Long.MIN_VALUE : after.lastRowId;
        long previous = last;
        boolean seenPosition = false;
        boolean more = false;
        boolean sentinel = false;
        int totalBytes = 0;
        singleton(db, cancel);
        try (Cursor c = db.rawQuery(after == null ? CatalogSql.FIRST_PAGE : CatalogSql.NEXT_PAGE,
                new String[] {Long.toString(last)}, cancel)) {
            while (c.moveToNext()) {
                check(cancel, deadline);
                Meta observed = metadata(c);
                if (meta == null) meta = observed; else require(meta.same(observed));
                if (after != null) require(after.catalogId.equals(meta.id) && after.revision==meta.revision);
                long present = integer(c, column(c,"row_present"));
                require(present==0 || present==1);
                if (present==0) {
                    require(entries.isEmpty() && !seenPosition && !sentinel);
                    sentinel=true;
                    // Every inner-page column must be NULL on a LEFT JOIN empty sentinel.
                    for (int i=column(c,"position");i<c.getColumnCount();i++) require(c.isNull(i));
                    continue;
                }
                require(!sentinel);
                long position = integer(c,column(c,"position"));
                require((!seenPosition && after==null) || position>previous);
                previous=position;
                seenPosition=true;
                String engine = field(c,"engine_key",false);
                String clone = field(c,"clone_id",false);
                String pkg = field(c,"package_name",false);
                String label = field(c,"label",false);
                String group = field(c,"group_name",true);
                require(!pkg.isEmpty() && !label.isEmpty());
                require(integer(c,column(c,"readiness_inert"))==1 && integer(c,column(c,"launch_inert"))==1);
                ManagerModel.CatalogKey key = new ManagerModel.CatalogKey(engine,clone);
                require(keys.add(key));
                int bytes = 0;
                for (String name : new String[]{"engine_key","clone_id","package_name","label","group_name"}) {
                    long count=integer(c,column(c,name+"_byte_length"));
                    if (count>0) bytes += (int) count;
                }
                // Budget includes the model's three cached normalized display strings, never key folding.
                ManagerModel.Entry entry = new ManagerModel.Entry(key,pkg,label,group,ManagerModel.Status.UNVERIFIED);
                bytes += 2 * entry.normalizedUnits();
                // Validate lookahead before deciding the next page, never silently skip malformed rows.
                if (entries.size()==128 || totalBytes+bytes>262144) {
                    require(!entries.isEmpty()); more=true; break;
                }
                entries.add(entry);
                totalBytes+=bytes;
                last=position;
            }
        }
        require(meta != null); // Missing metadata can never become an empty-success.
        // Page cursor MUST be closed before these independent metadata queries.
        singleton(db,cancel);
        try (Cursor c=db.rawQuery(CatalogSql.FINAL_META,null,cancel)) {
            require(c.moveToNext() && meta.same(metadata(c)) && !c.moveToNext());
        }
        check(cancel,deadline);
        return new CatalogPage(entries,meta.id,meta.revision,
                more ? new CatalogPage.Position(meta.id,meta.revision,last) : null);
    }
    private static final class Meta {
        final String id; final long revision;
        Meta(String id,long revision) { this.id=id; this.revision=revision; }
        boolean same(Meta other) { return id.equals(other.id) && revision==other.revision; }
    }
    private static Meta metadata(Cursor c) throws Exception {
        String id=field(c,"catalog_id",false);
        require(id.length()==36 && UUID.fromString(id).toString().equals(id));
        require(integer(c,column(c,"revision_valid"))==1);
        long revision=integer(c,column(c,"catalog_revision"));
        require(revision>=0);
        return new Meta(id,revision);
    }
    private static String field(Cursor c,String name,boolean nullable) throws Exception {
        long isText=integer(c,column(c,name+"_is_text"));
        long isNull=integer(c,column(c,name+"_is_null"));
        long length=integer(c,column(c,name+"_byte_length"));
        long over=integer(c,column(c,name+"_over_budget"));
        int bytesIndex=column(c,name+"_bytes");
        if (nullable && isNull==1) {
            require(isText==0 && length==-1 && over==0 && c.isNull(bytesIndex));
            return "";
        }
        int max=name.equals("catalog_id") ? 36 : 16384;
        require(isText==1 && isNull==0 && over==0 && length>=0 && length<=max
                && c.getType(bytesIndex)==Cursor.FIELD_TYPE_BLOB);
        byte[] bytes=c.getBlob(bytesIndex);
        require(bytes!=null && bytes.length==length);
        String value=decode(bytes,4096);
        return value;
    }
    private static int column(Cursor c,String name) throws IOException {
        int index=c.getColumnIndex(name); require(index>=0); return index;
    }
    private static long integer(Cursor c,int index) throws IOException {
        require(c.getType(index)==Cursor.FIELD_TYPE_INTEGER); return c.getLong(index);
    }
    private static String blobText(Cursor c,int index,int max) throws Exception {
        require(c.getType(index)==Cursor.FIELD_TYPE_BLOB);
        byte[] bytes=c.getBlob(index); require(bytes!=null && bytes.length<=max);
        return decode(bytes,max);
    }
    private static String text(Cursor c,int index,int max) throws Exception {
        require(c.getType(index)==Cursor.FIELD_TYPE_STRING);
        byte[] bytes=c.getBlob(index); require(bytes!=null && bytes.length<=max);
        return decode(bytes,max);
    }
    private static String decode(byte[] bytes,int utf16Max) throws Exception {
        return CatalogPage.decodeText(bytes,utf16Max);
    }
    private static void check(CancellationSignal cancel,long deadline) throws IOException {
        require(cancel!=null && !cancel.isCanceled() && SystemClock.elapsedRealtime()<deadline);
    }
    private static void require(boolean valid) throws IOException {
        if (!valid) throw new IOException("Catalog unavailable; source retained");
    }
}
