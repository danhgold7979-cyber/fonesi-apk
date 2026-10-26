package vn.fonesi.app.runtime

import android.content.Context
import vn.fonesi.app.storage.FonesiDatabase
import vn.fonesi.core.CloneEngine
import vn.fonesi.core.UnavailableCloneEngine

/** Default build remains free of the unverified native SDK. */
object RuntimeBindings {
    const val candidate = false
    const val supportsSplits = false
    fun attach(context: Context) = Unit
    fun key(context: Context) = "not-integrated"
    fun engine(context: Context, db: FonesiDatabase): CloneEngine = UnavailableCloneEngine()
    fun status() = "Bản cloud không chứa engine clone."
    fun enable(context: Context, enabled: Boolean) = Unit
}
