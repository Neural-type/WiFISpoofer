package com.example.wifispoofer.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.example.wifispoofer.BuildConfig

class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            XposedBridge.log("WiFiSpoofer: Hooking own app for Xposed detection")
            return
        }
        
        WifiHook.initHooks(lpparam)
    }
}
