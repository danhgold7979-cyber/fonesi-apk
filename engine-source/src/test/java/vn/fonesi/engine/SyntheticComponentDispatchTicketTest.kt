package vn.fonesi.engine

import com.android.apksig.ApkVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.fonesi.apk.PrivateApkCluster
import vn.fonesi.apk.PrivateApkStore
import vn.fonesi.core.InstalledSource
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class SyntheticComponentDispatchTicketTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fixtures = File(checkNotNull(System.getProperty("fonesi.nativeFixtures")))
    private fun apk(name: String) = File(fixtures, "$name.apk")
    private fun signer(): Set<String> = ApkVerifier.Builder(apk("main-disguised"))
        .setMinCheckedPlatformVersion(26).build().verify().signerCertificates.map {
            MessageDigest.getInstance("SHA-256").digest(it.encoded)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        }.toSet()

    private data class Fixture(
        val root: File,
        val store: PrivateApkStore,
        val cluster: PrivateApkCluster,
        val catalog: VerifiedPrivateSyntheticComponents,
        val attempt: UUID,
    )

    private fun fixture(): Fixture {
        val root = temporary.newFolder("private-code")
        val store = PrivateApkStore(root.toPath())
        val source = InstalledSource("vn.fonesi.fixture", 1, signer(), listOf(apk("main-disguised").path))
        val cluster = store.publish(source, 34)
        val catalog = VerifiedPrivateSyntheticComponents(
            clusterId = cluster.id,
            packageName = cluster.apks.base.packageName,
            version = cluster.apks.base.version,
            applicationName = "vn.fonesi.fixture.SyntheticGuestApplication",
            activityName = "vn.fonesi.fixture.PayloadActivity",
            activityExported = true,
            privatePath = cluster.apks.base.file.canonicalPath,
        )
        return Fixture(root, store, cluster, catalog, UUID.randomUUID())
    }

    @Test fun ticketRoundTripsAndValidatesAgainstFreshPrivateStoreReadback() {
        val f = fixture()
        val ticket = SyntheticComponentDispatchTicket.issue(f.catalog, f.cluster, f.attempt, f.root)
        val decoded = SyntheticComponentDispatchTicket.fromJson(ticket.toJson())
        assertEquals(ticket, decoded)
        val fresh = f.store.open(f.cluster.id, 34)
        SyntheticComponentDispatchTicket.validate(decoded, f.catalog, fresh, f.attempt, f.root)
    }

    @Test fun staleAttemptIdAndChangedComponentIdentityAreRejected() {
        val f = fixture()
        val ticket = SyntheticComponentDispatchTicket.issue(f.catalog, f.cluster, f.attempt, f.root)
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticComponentDispatchTicket.validate(ticket, f.catalog, f.cluster, UUID.randomUUID(), f.root)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticComponentDispatchTicket.validate(
                ticket.copy(activityName = "vn.fonesi.fixture.OtherActivity"),
                f.catalog, f.cluster, f.attempt, f.root,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticComponentDispatchTicket.validate(
                ticket.copy(applicationName = "vn.fonesi.fixture.OtherApplication"),
                f.catalog, f.cluster, f.attempt, f.root,
            )
        }
    }

    @Test fun changedSignerOrPayloadHashIsRejected() {
        val f = fixture()
        val ticket = SyntheticComponentDispatchTicket.issue(f.catalog, f.cluster, f.attempt, f.root)
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticComponentDispatchTicket.validate(
                ticket.copy(signerDigests = listOf("0".repeat(64))),
                f.catalog, f.cluster, f.attempt, f.root,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticComponentDispatchTicket.validate(
                ticket.copy(privateApkSha256 = "f".repeat(64)),
                f.catalog, f.cluster, f.attempt, f.root,
            )
        }
    }

    @Test fun alteredPrivateApkBytesAreRejectedImmediatelyBeforeDispatch() {
        val f = fixture()
        val ticket = SyntheticComponentDispatchTicket.issue(f.catalog, f.cluster, f.attempt, f.root)
        val privateApk = f.cluster.apks.base.file
        check(privateApk.setWritable(true))
        privateApk.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticComponentDispatchTicket.validate(ticket, f.catalog, f.cluster, f.attempt, f.root)
        }
        assertTrueOriginalFixtureStillExists()
    }

    @Test fun ticketJsonRejectsUnknownFieldsAndInvalidIdentity() {
        val f = fixture()
        val ticket = SyntheticComponentDispatchTicket.issue(f.catalog, f.cluster, f.attempt, f.root)
        val withUnknown = ticket.toJson().dropLast(1) + ",\"installed_original\":true}"
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticComponentDispatchTicket.fromJson(withUnknown)
        }
        val malformed = ticket.toJson().replace(ticket.attemptId, "not-a-uuid")
        assertThrows(IllegalArgumentException::class.java) {
            SyntheticComponentDispatchTicket.fromJson(malformed)
        }
    }

    private fun assertTrueOriginalFixtureStillExists() {
        check(apk("main-disguised").isFile) { "Original signed fixture must remain untouched" }
    }
}
