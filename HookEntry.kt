package com.example.wifispoofer.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.example.wifispoofer.BuildConfig

/**
 * WiFi Spoofer v11 - Hook Entry Point
 * Архитектура на основе GPS Setter
 * 
 * @author Neural-type
 * @version 11.0
 */
class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        
        // ═══════════════════════════════════════════════════════
        // Хук для самого приложения WiFi Spoofer
        // Только для проверки isXposedActive
        // ═══════════════════════════════════════════════════════
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            XposedBridge.log("WiFiSpoofer: Hooking own app for Xposed detection")
            
            try {
                // Хукаем метод updateXposedState() в ViewModel
                // чтобы приложение знало что модуль активен
                XposedHelpers.findAndHookMethod(
                    "com.example.wifispoofer.ui.viewmodel.MainViewModel",
                    lpparam.classLoader,
                    "updateXposedState",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // Устанавливаем isXposed = true
                            param.result = null
                        }
                    }
                )
                XposedBridge.log("WiFiSpoofer: ✓ Xposed detection hook installed")
            } catch (e: Throwable) {
                XposedBridge.log("WiFiSpoofer: ⚠ Failed to hook updateXposedState: ${e.message}")
            }
            
            // НЕ устанавливаем WiFi хуки для нашего приложения!
            return
        }
        
        // ═══════════════════════════════════════════════════════
        // Инициализация WiFi хуков для всех остальных приложений
        // ═══════════════════════════════════════════════════════
        WifiHook.initHooks(lpparam)
    }
}
