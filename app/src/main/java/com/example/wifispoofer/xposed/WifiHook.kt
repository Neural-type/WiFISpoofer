package com.example.wifispoofer.xposed

import android.net.wifi.ScanResult
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.example.wifispoofer.BuildConfig

object WifiHook {

    private val settings = XShare()
    private var mLastUpdated: Long = 0
    private const val UPDATE_INTERVAL: Long = 200
    
    private val ignorePkg = arrayListOf(
        BuildConfig.APPLICATION_ID,
        "com.android.location.fused"
    )

    fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("═══════════════════════════════════════")
        XposedBridge.log("WiFiSpoofer v11.1: Package loaded: ${lpparam.packageName}")
        
        if (ignorePkg.contains(lpparam.packageName)) {
            XposedBridge.log("WiFiSpoofer: Package in IGNORE list")
            return
        }

        when (lpparam.packageName) {
            "android" -> hookSystemServer(lpparam)
            else -> hookApplication(lpparam)
        }
    }

    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedBridge.log("WiFiSpoofer: Installing System Server hooks...")
            
            val WifiServiceImplClass = XposedHelpers.findClass(
                "com.android.server.wifi.WifiServiceImpl",
                lpparam.classLoader
            )
            
            for (method in WifiServiceImplClass.declaredMethods) {
                if (method.name == "getScanResults") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            handleGetScanResults(param)
                        }
                    })
                }
            }
            
            XposedBridge.log("WiFiSpoofer: ✓ System Server hooks installed")
            
        } catch (e: Throwable) {
            XposedBridge.log("WiFiSpoofer ERROR: ${e.message}")
        }
    }

    private fun hookApplication(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager",
                lpparam.classLoader,
                "getScanResults",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        handleGetScanResults(param)
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("WiFiSpoofer ERROR: ${e.message}")
        }
    }

    private fun handleGetScanResults(param: XC_MethodHook.MethodHookParam) {
        try {
            updateSettingsIfNeeded()
            if (!settings.isEnabled) return
            
            @Suppress("UNCHECKED_CAST")
            val originalResults = param.result as? List<ScanResult> ?: emptyList()
            
            val finalResults = ArrayList<ScanResult>()
            
            if (!settings.hideRealNetworks) {
                finalResults.addAll(originalResults)
            }
            
            for (network in settings.fakeNetworks) {
                val scanResult = ScanResult().apply {
                    SSID = network.ssid
                    BSSID = network.bssid
                    level = network.level
                    frequency = network.frequency
                    capabilities = network.capabilities
                    timestamp = System.currentTimeMillis() * 1000
                }
                finalResults.add(scanResult)
            }
            
            param.result = finalResults
            XposedBridge.log("WiFiSpoofer: Returned ${finalResults.size} networks")
            
        } catch (e: Throwable) {
            XposedBridge.log("WiFiSpoofer ERROR: ${e.message}")
        }
    }

    private fun updateSettingsIfNeeded() {
        if (System.currentTimeMillis() - mLastUpdated > UPDATE_INTERVAL) {
            settings.reload()
            mLastUpdated = System.currentTimeMillis()
        }
    }
}
