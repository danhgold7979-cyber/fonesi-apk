package vn.fonesi.app.storage

import vn.fonesi.core.CloneRecord

/**
 * Lock-time readback before a stop request. No guest is created or stopped here.
 * The caller must hold the same operation lock used by clone mutations.
 */
internal object CloneStopIdentityGuard {
    fun requireSameGuest(
        expected: CloneMetadataRow,
        observed: CloneMetadataRow?,
        activeEngineKey: String,
        expectedToken: String,
        live: List<CloneRecord>,
    ) {
        require(activeEngineKey.isNotBlank() && expectedToken.isNotBlank())
        check(expected.engineKey == activeEngineKey && expected.launchToken == expectedToken) {
            "Clone stop was requested for another engine or stale token"
        }
        // No metadata identity (including package/cluster generation) may change across
        // the caller's initial lookup and the readback made under the operation lock.
        check(observed == expected) { "Clone metadata changed before stop" }
        check(live.map { it.id }.distinct().size == live.size) {
            "Ambiguous engine clone identities"
        }
        check(live.singleOrNull { it.id == expected.cloneId }?.packageName == expected.packageName) {
            "Engine clone identity differs from saved metadata"
        }
    }
}
