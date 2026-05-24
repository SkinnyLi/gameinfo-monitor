package com.gameperf.monitor

import android.app.Application
import com.gameperf.monitor.database.AppDatabase

class GamePerfApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: GamePerfApp
            private set
    }
}
