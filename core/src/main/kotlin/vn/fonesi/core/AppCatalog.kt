package vn.fonesi.core

/** A product category, never a claim of runtime compatibility. */
object AppCatalog {
    private val priorityPackages = setOf(
        "com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite",
        "com.zing.zalo", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.instagram.barcelona", "com.instagram.android", "com.twitter.android",
        "com.shopee.vn", "com.google.android.youtube",
    )

    fun isPriority(packageName: String): Boolean = packageName in priorityPackages
}
