package vn.fonesi.app.configuration

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import vn.fonesi.app.storage.*
import vn.fonesi.core.*
import vn.fonesi.core.update.FileQueueLock
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ConfigurationTest {
    private lateinit var context: Context
    private lateinit var db: FonesiDatabase
    private lateinit var name: String
    private lateinit var a: CloneMetadataRow
    private lateinit var b: CloneMetadataRow
    private lateinit var engine: ConfigurationEngineDouble
    private var allowed = true
    private val io = Executors.newSingleThreadExecutor()
    private fun <T> background(action: () -> T): T = io.submit(Callable(action)).get(30, TimeUnit.SECONDS)
    private fun lock() = FileQueueLock(context.filesDir.toPath().resolve("$name.lock"))
    private fun repo(provider: CloneEngine = engine, clock: () -> Long = { 100L }) =
        ConfigurationRepository(db, "private-engine-key", provider, lock(), { allowed }, clock)
    private fun archive() = ConfigurationArchive(listOf(
        ConfigurationEntry(7, "test.social", "Cửa hàng 🛒", "Nhóm bán hàng"),
        ConfigurationEntry(9, "test.social", "Tài khoản phụ", null),
    ))
    private fun originalNames() = listOf(a.label, b.label)
    private fun names() = db.clones().list().map { it.label }
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        name = "configuration-${UUID.randomUUID()}.db"
        db = FonesiDatabase.open(context, name)
        a = CloneMetadataRow("private-engine-key", "private-guest-a", "test.social", "A", "Old group", 1, 1)
        b = CloneMetadataRow("private-engine-key", "private-guest-b", "test.social", "B", null, 2, 2)
        engine = ConfigurationEngineDouble(a, b)
        background { db.clones().insert(a); db.clones().insert(b) }
    }
    @After fun close() { background { db.close() }; context.deleteDatabase(name); io.shutdownNow() }

    @Test fun unicodeNullAndQuotesRoundtripWithoutPrivateGuestIdentity(): Unit = background {
        val source = ConfigurationArchive(archive().entries + ConfigurationEntry(11, "test.other", "Tên \"cửa hàng\"", null))
        assertEquals(source, ConfigurationCodec.decode(ConfigurationCodec.encode(source)))
        val bytes = repo().export()
        val text = bytes.toString(Charsets.UTF_8)
        assertFalse(text.contains(a.engineKey)); assertFalse(text.contains(a.cloneId)); assertFalse(text.contains(a.launchToken))
        val json = org.json.JSONObject(text)
        assertEquals(setOf("format", "version", "clones"), json.keys().asSequence().toSet())
        assertEquals(setOf("ref", "package", "label", "group"), json.getJSONArray("clones").getJSONObject(0).keys().asSequence().toSet())
    }
    @Test fun duplicateUnknownMissingAndCoercedFieldsAreRejectedBeforeAnyWrite(): Unit = background {
        val valid = ConfigurationCodec.encode(archive()).toString(Charsets.UTF_8)
        val invalid = listOf(valid + "{}", valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"version\":1", "\"version\":\"1\""), valid.replace("\"version\":1", "\"version\":1,\"version\":1"),
            valid.replace("\"ref\":7", "\"ref\":7,\"ref\":7"), valid.replace("\"ref\":9", "\"ref\":7"),
            valid.replace("\"ref\":7", "\"ref\":7.0"), valid.replace("\"ref\":7", "\"ref\":-7"),
            valid.replace("\"label\":", "\"unknown\":"), valid.replace("\"group\":null", "\"group\":true"),
            valid.replace("\"format\":\"vn.fonesi.configuration\"", "\"format\":\"other\""),
            "{\"format\":\"vn.fonesi.configuration\",\"version\":1}")
        for (value in invalid) assertThrows(Exception::class.java) { ConfigurationCodec.decode(value.toByteArray()) }
        assertEquals(originalNames(), names())
    }
    @Test fun accountEngineLockAndJournalFieldsCannotBeImported(): Unit = background {
        val valid = ConfigurationCodec.encode(archive()).toString(Charsets.UTF_8)
        for (field in listOf("session", "password", "engineKey", "launchToken", "lockEnabled", "queue", "apkPaths")) {
            val root = valid.replaceFirst("{", "{\"$field\":false,")
            val entry = valid.replaceFirst("\"ref\":7", "\"ref\":7,\"$field\":\"value\"")
            assertThrows(Exception::class.java) { ConfigurationCodec.decode(root.toByteArray()) }
            assertThrows(Exception::class.java) { ConfigurationCodec.decode(entry.toByteArray()) }
        }
        assertEquals(originalNames(), names())
    }
    @Test fun invalidUtf8UnicodeControlsLengthsAndPackagesAreRejected(): Unit = background {
        assertThrows(Exception::class.java) { ConfigurationCodec.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
        val entry = archive().entries.first()
        for (changed in listOf(entry.copy(label = "\uD800"), entry.copy(label = "bad\u0001name"), entry.copy(label = " "),
            entry.copy(label = "a".repeat(81)), entry.copy(group = "a".repeat(41)), entry.copy(packageName = "../../outside"))) {
            assertThrows(Exception::class.java) { ConfigurationCodec.encode(ConfigurationArchive(listOf(changed))) }
        }
        val valid = ConfigurationCodec.encode(archive()).toString(Charsets.UTF_8)
        assertThrows(Exception::class.java) { ConfigurationCodec.decode(valid.replace("Cửa hàng 🛒", "\\uD800").toByteArray()) }
    }
    @Test fun inputOutputBudgetsAndZeroLengthProviderReadsAreHandled(): Unit = background {
        val large = ByteArray(ConfigurationCodec.MAX_BYTES + 1) { 32 }
        assertThrows(Exception::class.java) { ConfigurationCodec.decode(large) }
        assertThrows(Exception::class.java) { ConfigurationCodec.read(ByteArrayInputStream(large)) }
        val bytes = ConfigurationCodec.encode(archive())
        val provider = object : ByteArrayInputStream(bytes) {
            private var first = true
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (first) { first = false; return 0 }; return super.read(b, off, len)
            }
        }
        assertEquals(archive(), ConfigurationCodec.read(provider))
        val rows = (1..10_000).map { ConfigurationEntry(it, "test.social", "🛒".repeat(40), "🛒".repeat(20)) }
        assertThrows(Exception::class.java) { ConfigurationCodec.encode(ConfigurationArchive(rows)) }
    }
    @Test fun explicitPartialMappingRenamesOnlyTheChosenRealTarget(): Unit = background {
        assertEquals(1, repo().apply(archive(), mapOf(7 to b.launchToken)))
        assertEquals(a.label, db.clones().findByLaunchToken(a.launchToken)!!.label)
        assertEquals("Cửa hàng 🛒", db.clones().findByLaunchToken(b.launchToken)!!.label)
        assertEquals("Nhóm bán hàng", db.clones().findByLaunchToken(b.launchToken)!!.groupName)
        assertEquals(0, engine.mutations)
    }
    @Test fun staleTargetPreventsPartialImportEvenWhenAnotherTargetIsValid(): Unit = background {
        assertThrows(IllegalStateException::class.java) { repo().apply(archive(), mapOf(7 to a.launchToken, 9 to UUID.randomUUID().toString())) }
        assertEquals(originalNames(), names()); assertEquals(0, engine.mutations)
    }
    @Test fun wrongNamespacePackageOrMissingGuestCannotReceiveImportedNames(): Unit = background {
        db.clones().insert(a.copy(engineKey = "different-engine", cloneId = "different-id", launchToken = UUID.randomUUID().toString()))
        val other = db.clones().find("different-engine", "different-id")!!
        engine.records += CloneRecord(other.cloneId, "test.social", 1, setOf("signer"), false)
        assertThrows(IllegalStateException::class.java) { repo().apply(archive(), mapOf(7 to other.launchToken)) }
        val wrongPackage = ConfigurationArchive(listOf(archive().entries.first().copy(packageName = "other.package")))
        assertThrows(IllegalStateException::class.java) { repo().apply(wrongPackage, mapOf(7 to a.launchToken)) }
        engine.records.removeAll { it.id == a.cloneId }
        assertThrows(IllegalStateException::class.java) { repo().apply(archive(), mapOf(7 to a.launchToken)) }
        assertEquals(a.label, db.clones().findByLaunchToken(a.launchToken)!!.label)
    }
    @Test fun duplicateUnknownOrEmptyMappingsAreRejected(): Unit = background {
        assertThrows(IllegalArgumentException::class.java) { repo().apply(archive(), mapOf(7 to a.launchToken, 9 to a.launchToken)) }
        assertThrows(IllegalArgumentException::class.java) { repo().apply(archive(), mapOf(99 to a.launchToken)) }
        assertThrows(IllegalArgumentException::class.java) { repo().apply(archive(), emptyMap()) }
        assertEquals(originalNames(), names())
    }
    @Test fun storageFailureInsideTransactionRollsBackAllRenames(): Unit = background {
        var calls = 0
        val failing = repo(clock = { check(++calls != 2) { "Synthetic storage failure" }; 10L })
        assertThrows(IllegalStateException::class.java) { failing.apply(archive(), mapOf(7 to a.launchToken, 9 to b.launchToken)) }
        assertEquals(originalNames(), names())
    }
    @Test fun authorizationLostBeforeCommitRollsBackAllRenames(): Unit = background {
        val guarded = repo(clock = { allowed = false; 10L })
        assertThrows(IllegalStateException::class.java) { guarded.apply(archive(), mapOf(7 to a.launchToken)) }
        assertEquals(originalNames(), names())
        assertThrows(IllegalStateException::class.java) { guarded.export() }
    }
    @Test fun unavailableAmbiguousEngineAndBusyLockCannotWriteMetadata(): Unit = background {
        assertThrows(IllegalStateException::class.java) { repo(UnavailableCloneEngine()).apply(archive(), mapOf(7 to a.launchToken)) }
        lock().acquire()!!.use {
            assertThrows(IllegalStateException::class.java) { repo().apply(archive(), mapOf(7 to a.launchToken)) }
        }
        engine.records += engine.records.first()
        assertThrows(IllegalStateException::class.java) { repo().apply(archive(), mapOf(7 to a.launchToken)) }
        assertEquals(originalNames(), names())
    }
    @Test fun documentSelectionSurvivesModelRecreationWithoutAutomaticIo() {
        val saved = SavedStateHandle()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = ConfigurationViewModel(app, saved)
        val models = ViewModelStore().apply { put("first", model) }
        try {
            for (mode in DocumentAction.entries) {
            model.documentSelected(mode, Uri.parse("content://not-a-provider/file"))
            assertEquals(mode, model.state.value.pending)
            assertNull(model.state.value.archive); assertFalse(model.state.value.busy)
            assertEquals(setOf("document_uri", "document_action"), saved.keys())
            val restored = ConfigurationViewModel(app, SavedStateHandle(saved.keys().associateWith { saved.get<String>(it) }))
            models.put("restored", restored)
            assertEquals(mode, restored.state.value.pending)
            assertNull(restored.state.value.archive); assertFalse(restored.state.value.busy)
            restored.discard(); assertNull(restored.state.value.pending)
            }
        } finally { models.clear() }
    }
    @Test fun fileUriCannotBeStagedForPrivatePathAccess() {
        val saved = SavedStateHandle()
        val model = ConfigurationViewModel(ApplicationProvider.getApplicationContext(), saved)
        val models = ViewModelStore().apply { put("model", model) }
        try {
            model.documentSelected(DocumentAction.EXPORT, Uri.parse("file:///data/private/file"))
            assertNull(model.state.value.pending); assertTrue(saved.keys().isEmpty())
            val app = ApplicationProvider.getApplicationContext<Application>()
            model.documentSelected(DocumentAction.EXPORT, Uri.parse("content://${app.packageName}.sdk/document/private"))
            assertNull(model.state.value.pending); assertTrue(saved.keys().isEmpty())
        } finally { models.clear() }
    }
}

private class ConfigurationEngineDouble(vararg rows: CloneMetadataRow) : CloneEngine {
    override val availability = EngineAvailability.Ready(UpdateScope.SHARED_PACKAGE)
    val records = rows.map { CloneRecord(it.cloneId, it.packageName, 1, setOf("signer"), false) }.toMutableList()
    var mutations = 0
    override fun listClones() = records.toList()
    override fun create(source: InstalledSource, label: String): CloneRecord { mutations++; error("Not allowed by configuration import") }
    override fun launch(cloneId: String) { mutations++ }
    override fun delete(cloneId: String) { mutations++ }
    override fun update(target: UpdateTarget): List<CloneRecord> { mutations++; error("Not allowed by configuration import") }
}
