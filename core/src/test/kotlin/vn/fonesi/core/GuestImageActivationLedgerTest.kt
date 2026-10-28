package vn.fonesi.core

import org.junit.After
import org.junit.Test
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.Comparator
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Pure JVM tests. They do not demonstrate Android guest execution or account isolation. */
class GuestImageActivationLedgerTest {
    private val root = Files.createTempDirectory("fonesi-activation-test-")
    private val clone = UUID.randomUUID().toString()
    private val signer = setOf("a".repeat(64))
    private val first = image(1)
    private val second = image(2)
    private val accepted = mutableSetOf(first, second)
    private fun image(version: Long, signers: Set<String> = signer, pkg: String = "vn.fonesi.fixture") =
        GuestImageRef(UUID.randomUUID().toString(), pkg, version, signers)
    private fun ledger() = GuestImageActivationLedger(root) { it in accepted }
    private fun confirmedFirst(): GuestImageActivationState {
        val staged = ledger().stage(clone, 0, first)
        return ledger().confirmFromRuntimeReadback(clone, staged.revision, first)
    }
    private inline fun <reified T : Throwable> fails(crossinline body: () -> Unit) {
        val failure = runCatching { body() }.exceptionOrNull()
        check(failure is T) { "Expected " + T::class.java.simpleName + ", got " + failure }
    }

    @After fun clean() {
        if (Files.exists(root, NOFOLLOW_LINKS)) Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    @Test fun stageDoesNotClaimAnActiveGuestAndSurvivesRestart() {
        val staged = ledger().stage(clone, 0, first)
        check(staged == GuestImageActivationState(1, null, first))
        check(ledger().read(clone) == staged)
        check(ledger().readVerified(clone) == staged)
    }

    @Test fun onlyExactRuntimeReadbackCanConfirmThePendingImage() {
        ledger().stage(clone, 0, first)
        fails<IllegalArgumentException> { ledger().confirmFromRuntimeReadback(clone, 1, second) }
        check(ledger().read(clone).active == null)
        check(ledger().confirmFromRuntimeReadback(clone, 1, first) == GuestImageActivationState(2, first, null))
    }

    @Test fun imageMutationBlocksConfirmationWithoutLosingPending() {
        ledger().stage(clone, 0, first)
        accepted.remove(first)
        fails<IllegalStateException> { ledger().confirmFromRuntimeReadback(clone, 1, first) }
        check(ledger().read(clone).pending == first)
        fails<IllegalStateException> { ledger().readVerified(clone) }
    }

    @Test fun existingActiveImageCannotBeSilentlyReplacedByInvalidStore() {
        confirmedFirst()
        accepted.remove(first)
        fails<IllegalStateException> { ledger().stage(clone, 2, second) }
        check(ledger().read(clone) == GuestImageActivationState(2, first, null))
    }

    @Test fun downgradeSignerAndPackageSwitchAreRejected() {
        confirmedFirst()
        val downgrade = image(0)
        val changedSigner = image(3, setOf("b".repeat(64)))
        val changedPackage = image(3, pkg = "vn.fonesi.another")
        accepted += listOf(downgrade, changedSigner, changedPackage)
        fails<IllegalArgumentException> { ledger().stage(clone, 2, downgrade) }
        fails<IllegalArgumentException> { ledger().stage(clone, 2, changedSigner) }
        fails<IllegalArgumentException> { ledger().stage(clone, 2, changedPackage) }
        check(ledger().read(clone).revision == 2L)
    }

    @Test fun unresolvedMutationCannotBeDroppedOrRestaged() {
        confirmedFirst()
        ledger().stage(clone, 2, second)
        fails<IllegalArgumentException> { ledger().abortBeforeSdk(clone, 3, false) }
        fails<IllegalStateException> { ledger().stage(clone, 3, first) }
        check(ledger().read(clone) == GuestImageActivationState(3, first, second))
    }

    @Test fun provenPreSdkAbortKeepsThePreviousActiveImageAndAdvancesRevision() {
        confirmedFirst()
        ledger().stage(clone, 2, second)
        check(ledger().abortBeforeSdk(clone, 3, true) == GuestImageActivationState(4, first, null))
        fails<IllegalArgumentException> { ledger().stage(clone, 2, second) }
        check(ledger().stage(clone, 4, second).pending == second)
    }

    @Test fun staleRevisionCannotOverrideNewerMutation() {
        ledger().stage(clone, 0, first)
        fails<IllegalArgumentException> { ledger().confirmFromRuntimeReadback(clone, 0, first) }
        fails<IllegalArgumentException> { ledger().stage(clone, 0, second) }
        check(ledger().read(clone).pending == first)
    }

    @Test fun invalidImageReferencesAreRejectedBeforeIOMutation() {
        fails<IllegalArgumentException> { GuestImageRef("../bad", "vn.fonesi.fixture", 1, signer) }
        fails<IllegalArgumentException> { GuestImageRef(UUID.randomUUID().toString(), "invalid", 1, signer) }
        fails<IllegalArgumentException> { GuestImageRef(UUID.randomUUID().toString(), "vn.fonesi.fixture", -1, signer) }
        fails<IllegalArgumentException> { GuestImageRef(UUID.randomUUID().toString(), "vn.fonesi.fixture", 1, setOf("bad")) }
        fails<IllegalArgumentException> { ledger().read("../foreign") }
        check(ledger().read(clone) == GuestImageActivationState())
    }

    @Test fun corruptRecordFailsClosedInsteadOfResettingOrDeletingIt() {
        ledger().stage(clone, 0, first)
        val file = root.resolve("$clone.state")
        val content = Files.readAllBytes(file)
        content[content.size - 1] = (content.last().toInt() xor 0x12).toByte()
        Files.write(file, content)
        fails<IllegalArgumentException> { ledger().read(clone) }
        fails<IllegalArgumentException> { ledger().stage(clone, 1, second) }
        check(Files.isRegularFile(file, NOFOLLOW_LINKS))
    }

    @Test fun symlinkedStateIsNotFollowed() {
        val other = Files.createTempFile(root, "unrelated", ".bin")
        val link = root.resolve("$clone.state")
        Files.createSymbolicLink(link, other)
        fails<IllegalArgumentException> { ledger().read(clone) }
        fails<IllegalArgumentException> { ledger().stage(clone, 0, first) }
        check(Files.isSymbolicLink(link) && Files.exists(other))
    }

    @Test fun symlinkedRootIsRejected() {
        val other = Files.createTempDirectory("fonesi-external-root-")
        try {
            val link = root.resolve("linked")
            Files.createSymbolicLink(link, other)
            fails<IllegalArgumentException> { GuestImageActivationLedger(link) { true } }
        } finally {
            Files.delete(other)
        }
    }

    @Test fun independentCloneStateCannotBeInheritedOrSwapped() {
        ledger().stage(clone, 0, first)
        val other = UUID.randomUUID().toString()
        check(ledger().read(other) == GuestImageActivationState())
        ledger().stage(other, 0, second)
        check(ledger().read(clone).pending == first)
        check(ledger().read(other).pending == second)
    }

    @Test fun concurrentSameRevisionWritersCannotBothStage() {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(first, second).map { image ->
                pool.submit<Boolean> {
                    start.await()
                    runCatching { ledger().stage(clone, 0, image) }.isSuccess
                }
            }
            start.countDown()
            check(futures.count { it.get(5, TimeUnit.SECONDS) } == 1)
            val state = ledger().read(clone)
            check(state.revision == 1L && state.pending in setOf(first, second))
        } finally { pool.shutdownNow() }
    }
}
