package com.example.wifispoofer

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Application класс WiFi Spoofer
 * 
 * @author Neural-type
 * @version 11.0
 */
class App : Application() {

    val globalScope = CoroutineScope(Dispatchers.Default)

    companion object {
        lateinit var instance: App
            private set

        fun commonInit() {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("WiFiSpoofer", "App initialized in DEBUG mode")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        commonInit()
    }
}
