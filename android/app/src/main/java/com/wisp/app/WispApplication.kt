package com.wisp.app

import android.app.Application
import com.wisp.app.sync.SupabaseManager

/**
 * Wisp Application class
 */
class WispApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseManager.init(this)
    }
}
