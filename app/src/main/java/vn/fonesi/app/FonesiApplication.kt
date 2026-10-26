package vn.fonesi.app

import android.app.Application
import android.content.Context
import vn.fonesi.app.runtime.RuntimeBindings

class FonesiApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        RuntimeBindings.attach(this)
    }
}
