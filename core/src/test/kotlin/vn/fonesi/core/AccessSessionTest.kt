package vn.fonesi.core

import org.junit.Assert.*
import org.junit.Test

class AccessSessionTest {
    private var time = 1000L
    private fun session(required: Boolean = true) = AccessSession(required) { time }
    @Test fun disabledPolicyStillRequiresForeground() {
        val gate = session(false)
        assertFalse(gate.authorized); gate.foreground(false); assertTrue(gate.authorized)
        gate.background(false); assertFalse(gate.authorized)
    }
    @Test fun enabledPolicyNeverRestoresAnAuthenticatedSessionFromAnotherInstance() {
        val a = session(); a.foreground(true); assertTrue(a.complete(a.begin()!!, true)); assertTrue(a.authorized)
        val b = session(); b.foreground(true); assertFalse(b.authorized)
    }
    @Test fun staleCallbackCannotConsumeOrApproveTheCurrentRequest() {
        val gate = session(); gate.foreground(true)
        val first = gate.begin()!!; assertFalse(gate.complete(first, false))
        val second = gate.begin()!!; assertNotEquals(first, second)
        assertFalse(gate.complete(first, true)); assertFalse(gate.authorized)
        assertTrue(gate.complete(second, true)); assertTrue(gate.authorized)
    }
    @Test fun biometricCallbackAfterHomeCannotUnlockWhenReturning() {
        val gate = session(); gate.foreground(true); val id = gate.begin()!!
        gate.background(false); gate.foreground(true)
        assertFalse(gate.complete(id, true)); assertFalse(gate.authorized)
    }
    @Test fun rotationPreservesCurrentSessionAndPendingRequest() {
        val gate = session(); gate.foreground(true); val id = gate.begin()!!
        gate.background(true); gate.foreground(true)
        assertTrue(gate.complete(id, true))
        gate.background(true); gate.foreground(true); assertTrue(gate.authorized)
    }
    @Test fun systemCredentialCanReturnFromItsExpectedExternalScreen() {
        val gate = session(); gate.foreground(true); val id = gate.begin()!!
        gate.background(false, externalCredential = true); assertFalse(gate.authorized)
        gate.foreground(true); assertTrue(gate.complete(id, true)); assertTrue(gate.authorized)
    }
    @Test fun successWhileBackgroundNeverGrantsAccess() {
        val gate = session(); gate.foreground(true); val id = gate.begin()!!
        gate.background(false, externalCredential = true)
        assertFalse(gate.complete(id, true)); gate.foreground(true); assertFalse(gate.authorized)
    }
    @Test fun expiredOrInvalidMonotonicTimeDoesNotUnlock() {
        val gate = session(); gate.foreground(true); val expired = gate.begin()!!
        time += 120_001; assertFalse(gate.complete(expired, true))
        val backwards = gate.begin()!!; time--; assertFalse(gate.complete(backwards, true))
        assertFalse(gate.authorized)
    }
    @Test fun cancellationAndDuplicateRequestsKeepGateLocked() {
        val gate = session(); gate.foreground(true); val id = gate.begin()!!
        assertNull(gate.begin()); assertFalse(gate.complete(id, false)); assertFalse(gate.authorized)
    }
    @Test fun changingPolicyInvalidatesPriorAuthorizationAndPendingCallbacks() {
        val gate = session(false); gate.foreground(false); val id = gate.begin()!!
        gate.syncPolicy(true)
        assertFalse(gate.authorized); assertFalse(gate.complete(id, true))
    }
}
