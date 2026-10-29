package vn.fonesi.engine

import vn.fonesi.apk.VerifiedPrivateComponentInventory

/**
 * Signed PRIVATE catalog must belong to the exact APK cluster the source loader is using.
 * Matching metadata here does not establish Android guest execution or UID/account isolation.
 */
internal object PrivateGuestComponentPreflight {
    fun requireMatches(
        layout: GuestCodeLayout,
        inventory: VerifiedPrivateComponentInventory,
    ): VerifiedPrivateComponentInventory {
        val cluster = layout.cluster
        val base = cluster.apks.base
        require(inventory.clusterId == cluster.id &&
            inventory.clusterDigest == cluster.clusterDigest &&
            inventory.packageName == base.packageName &&
            inventory.versionCode == base.version &&
            inventory.signerDigests == base.signers) {
            "Signed private component manifest belongs to a different code image"
        }
        val names = cluster.apks.splits.map { checkNotNull(it.splitName) }.sorted()
        require(inventory.splitNames == names &&
            layout.splitNames.toSet() == names.toSet() &&
            layout.splits.toSet() == cluster.apks.splits.map { it.file }.toSet()) {
            "Signed component manifest split set differs from the private code layout"
        }
        require(inventory.components.all { it.sourceSplit == null || it.sourceSplit in names }) {
            "Component references an absent signed split"
        }
        return inventory
    }
}
