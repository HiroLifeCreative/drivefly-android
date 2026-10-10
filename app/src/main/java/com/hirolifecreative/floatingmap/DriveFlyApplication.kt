package com.hirolifecreative.floatingmap

import android.app.Application
import android.content.Context

/** Attach runs before content providers, including native SDK startup. */
class DriveFlyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(base)
    }
}
