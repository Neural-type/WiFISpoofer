package com.example.wifispoofer.xposed

import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.example.wifispoofer.BuildConfig
import java.lang.reflect.Field

/**
 * WiFi Hook - Двухуровневая архитектура перехвата WiFi сетей
 * 
 * LEVEL 1: System Server Hook (bulletproof)
 *   - Хукает WifiServiceImpl на уровне System Server
 *   - Работает для ВСЕХ приложений автоматически
 *   - Невозможно обойти
 * 
 * LEVEL 2: Application Hook (fallback)
 *   - Хукает WifiManager в каждом приложении
 *   - Дополнительная защита
 *   - Fallback если system hook не сработал
 * 
 * @author Neural-type
 * @version 11.0
 * @based_on GPS Setter by jqssun
 */
object WifiHook {

    private val settings = XShare()
    private var mLastUpdated: Long = 0
    private const val UPDATE_INTERVAL: Long = 200 // миллисекунды
    
    /**
     * Список пакетов которые ИСКЛЮЧЕНЫ из хуков
     * Эти приложения будут видеть РЕАЛЬНЫЕ WiFi сети
     */
    private val ignorePkg = arrayListOf(
        BuildConfig.APPLICATION_ID,      // Само приложение WiFi Spoofer
        "com.android.location.fused"     // Системный location provider
    )

    /**
     * Инициализация хуков
     * Вызывается из HookEntry для каждого загруженного пакета
     */
    fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("═══════════════════════════════════════")
        XposedBridge.log("WiFiSpoofer v11: Package loaded: ${lpparam.packageName}")
        
        // ═══ ПРОВЕРКА: Исключен ли пакет? ═══
        if (ignorePkg.contains(lpparam.packageName)) {
            XposedBridge.log("WiFiSpoofer: ⚠️ Package in IGNORE list")
            XposedBridge.log("WiFiSpoofer: This app will see REAL WiFi networks")
            XposedBridge.log("═══════════════════════════════════════")
            return
        }

        // ═══ ВЫБОР УРОВНЯ ХУКА ═══
        when (lpparam.packageName) {
            "android" -> {
                // ★★★ LEVEL 1: System Server Hook ★★★
                XposedBridge.log("WiFiSpoofer: Mode = SYSTEM SERVER HOOK")
                XposedBridge.log("WiFiSpoofer: Bulletproof mode - hooks all apps globally")
                hookSystemServer(lpparam)
            }
            else -> {
                // ★★★ LEVEL 2: Application Hook ★★★
                XposedBridge.log("WiFiSpoofer: Mode = APPLICATION HOOK")
                XposedBridge.log("WiFiSpoofer: Fallback mode - hooks this app only")
                hookApplication(lpparam)
            }
        }
        
        XposedBridge.log("═══════════════════════════════════════")
    }

    // ═══════════════════════════════════════════════════════════════
    // LEVEL 1: SYSTEM SERVER HOOK
    // Хукаем WifiService на уровне Android System Server
    // ═══════════════════════════════════════════════════════════════

    /**
     * System Server Hook - главный bulletproof метод
     * Работает для ВСЕХ приложений в системе
     */
    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedBridge.log("WiFiSpoofer: Installing System Server hooks...")
            
            // Выбираем версию в зависимости от Android API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+)
                XposedBridge.log("WiFiSpoofer: Android 12+ detected (API ${Build.VERSION.SDK_INT})")
                hookSystemServerModern(lpparam)
            } else {
                // Android 11 и ниже (API 30-)
                XposedBridge.log("WiFiSpoofer: Android 11- detected (API ${Build.VERSION.SDK_INT})")
                hookSystemServerLegacy(lpparam)
            }
            
            XposedBridge.log("WiFiSpoofer: ✓ System Server hooks installed successfully")
            
        } catch (e: Throwable) {
            XposedBridge.log("WiFiSpoofer ERROR in System Server Hook: ${e.message}")
            XposedBridge.log("WiFiSpoofer ERROR Stack: ${android.util.Log.getStackTraceString(e)}")
        }
    }

    /**
     * System Server Hook для Android 12+ (API 31+)
     */
    private fun hookSystemServerModern(lpparam: XC_LoadPackage.LoadPackageParam) {
        val WifiServiceImplClass = XposedHelpers.findClass(
            "com.android.server.wifi.WifiServiceImpl",
            lpparam.classLoader
        )
        
        XposedBridge.log("WiFiSpoofer: WifiServiceImpl class found")

        // Хукаем ВСЕ варианты getScanResults()
        // В разных версиях Android могут быть разные сигнатуры
        for (method in WifiServiceImplClass.declaredMethods) {
            if (method.name == "getScanResults") {
                val paramTypes = method.parameterTypes.joinToString { it.simpleName }
                XposedBridge.log("WiFiSpoofer: Found getScanResults($paramTypes)")
                
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        handleSystemServerGetScanResults(param, lpparam.packageName)
                    }
                })
                
                XposedBridge.log("WiFiSpoofer: ✓ Hooked getScanResults($paramTypes)")
            }
        }
    }

    /**
     * System Server Hook для Android 11 и ниже (API 30-)
     */
    private fun hookSystemServerLegacy(lpparam: XC_LoadPackage.LoadPackageParam) {
        val WifiServiceImplClass = XposedHelpers.findClass(
            "com.android.server.wifi.WifiServiceImpl",
            lpparam.classLoader
        )

        // Хукаем getScanResults с параметром callingPackage
        try {
            XposedHelpers.findAndHookMethod(
                WifiServiceImplClass,
                "getScanResults",
                String::class.java,  // callingPackage
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        handleSystemServerGetScanResults(param, "legacy")
                    }
                }
            )
            XposedBridge.log("WiFiSpoofer: ✓ Hooked getScanResults(String)")
        } catch (e: Throwable) {
            XposedBridge.log("WiFiSpoofer: ⚠ getScanResults(String) not found, trying alternatives")
            
            // Fallback: хукаем любые getScanResults
            for (method in WifiServiceImplClass.declaredMethods) {
                if (method.name == "getScanResults") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            handleSystemServerGetScanResults(param, "legacy_fallback")
                        }
                    })
                }
            }
        }
    }

    /**
     * Обработчик getScanResults на уровне System Server
     */
    private fun handleSystemServerGetScanResults(param: XC_MethodHook.MethodHookParam, context: String) {
        try {
            // Получаем вызывающий пакет
            val callingPackage = if (param.args.isNotEmpty() && param.args[0] is String) {
                param.args[0] as String
            } else {
                "unknown"
            }
            
            XposedBridge.log("WiFiSpoofer: [System] getScanResults() called by: $callingPackage")
            
            // Пропускаем игнорируемые пакеты
            if (ignorePkg.contains(callingPackage)) {
                XposedBridge.log("WiFiSpoofer: [System] Skipping ignored package: $callingPackage")
                return
            }
            
            // Проверяем включен ли модуль
            updateSettingsIfNeeded()
            if (!settings.isEnabled) {
                XposedBridge.log("WiFiSpoofer: [System] Module disabled, passing through")
                return
            }
            
            // Получаем оригинальные результаты
            @Suppress("UNCHECKED_CAST")
            val originalResults = param.result as? List<ScanResult> ?: emptyList()
            XposedBridge.log("WiFiSpoofer: [System] Original networks: ${originalResults.size}")
            
            // Обрабатываем и подменяем результаты
            val finalResults = processScanResults(originalResults)
            param.result = finalResults
            
            XposedBridge.log("WiFiSpoofer: [System] ✓ Returned ${finalResults.size} networks to $callingPackage")
            
        } catch (e: Throwable) {
            XposedBridge.log("WiFiSpoofer ERROR in handleSystemServerGetScanResults: ${e.message}")
            XposedBridge.log("WiFiSpoofer ERROR Stack: ${android.util.Log.getStackTraceString(e)}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LEVEL 2: APPLICATION HOOK
    // Хукаем WifiManager напрямую в приложении (fallback)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Application Hook - fallback если system hook не сработал
     * Хукает WifiManager в конкретном приложении
     */
    private fun hookApplication(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedBridge.log("WiFiSpoofer: Installing Application hooks...")
            
            // ═══ Хук WifiManager.getScanResults() ═══
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager",
                lpparam.classLoader,
                "getScanResults",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedBridge.log("WiFiSpoofer: [App] WifiManager.getScanResults() called in ${lpparam.packageName}")
                            
                            // Проверяем настройки
                            updateSettingsIfNeeded()
                            if (!settings.isEnabled) {
                                XposedBridge.log("WiFiSpoofer: [App] Module disabled")
                                return
                            }
                            
                            // Обрабатываем результаты
                            @Suppress("UNCHECKED_CAST")
                            val originalResults = param.result as? List<ScanResult> ?: emptyList()
                            val finalResults = processScanResults(originalResults)
                            
                            param.result = finalResults
                            XposedBridge.log("WiFiSpoofer: [App] ✓ Returned ${finalResults.size} networks")
                            
                        } catch (e: Throwable) {
                            XposedBridge.log("WiFiSpoofer ERROR in app getScanResults: ${e.message}")
                        }
                    }
                }
            )
            XposedBridge.log("WiFiSpoofer: ✓ Hooked WifiManager.getScanResults()")
            
            // ═══ Хук WifiManager.startScan() ═══
            // Всегда возвращаем true чтобы приложения думали что сканирование успешно
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager",
                lpparam.classLoader,
                "startScan",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (settings.isEnabled) {
                            param.result = true
                            XposedBridge.log("WiFiSpoofer: [App] startScan() = true")
                        }
                    }
                }
            )
            XposedBridge.log("WiFiSpoofer: ✓ Hooked WifiManager.startScan()")
            
            XposedBridge.log("WiFiSpoofer: ✓ Application hooks installed successfully")
            
        } catch (e: Throwable) {
            XposedBridge.log("WiFiSpoofer ERROR in Application Hook: ${e.message}")
            XposedBridge.log("WiFiSpoofer ERROR Stack: ${android.util.Log.getStackTraceString(e)}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ОБРАБОТКА РЕЗУЛЬТАТОВ СКАНИРОВАНИЯ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Обработка результатов сканирования:
     * 1. Фильтрация реальных сетей (blacklist/whitelist)
     * 2. Добавление фейковых сетей
     */
    private fun processScanResults(originalResults: List<ScanResult>): List<ScanResult> {
        val finalResults = ArrayList<ScanResult>()
        
        // ═══ ШАГ 1: Обработка реальных сетей ═══
        if (!settings.hideRealNetworks) {
            for (result in originalResults) {
                val shouldBlock = if (settings.whitelistMode) {
                    // Whitelist: блокируем все КРОМЕ выбранных
                    !settings.blockedSSIDs.contains(result.SSID)
                } else {
                    // Blacklist: блокируем только выбранные
                    settings.blockedSSIDs.contains(result.SSID)
                }
                
                if (!shouldBlock) {
                    finalResults.add(result)
                    XposedBridge.log("WiFiSpoofer:   ✓ Real: ${result.SSID} (${result.level} dBm)")
                } else {
                    XposedBridge.log("WiFiSpoofer:   ✗ Blocked: ${result.SSID}")
                }
            }
        } else {
            XposedBridge.log("WiFiSpoofer: All real networks hidden (hide_real=true)")
        }
        
        // ═══ ШАГ 2: Добавление фейковых сетей ═══
        val fakeNetworks = createFakeScanResults()
        finalResults.addAll(fakeNetworks)
        
        XposedBridge.log("WiFiSpoofer: Summary: ${finalResults.size} total (${finalResults.size - fakeNetworks.size} real + ${fakeNetworks.size} fake)")
        
        return finalResults
    }

    /**
     * Создание фейковых ScanResult объектов из настроек
     */
    private fun createFakeScanResults(): List<ScanResult> {
        val results = ArrayList<ScanResult>()
        
        for (network in settings.fakeNetworks) {
            try {
                val scanResult = createScanResult(
                    ssid = network.ssid,
                    bssid = network.bssid,
                    level = network.level,
                    frequency = network.frequency,
                    capabilities = network.capabilities
                )
                results.add(scanResult)
                XposedBridge.log("WiFiSpoofer:   ★ Fake: ${network.ssid} (${network.level} dBm)")
            } catch (e: Exception) {
                XposedBridge.log("WiFiSpoofer ERROR creating fake network ${network.ssid}: ${e.message}")
            }
        }
        
        return results
    }

    /**
     * Создание ScanResult объекта со всеми необходимыми полями
     */
    private fun createScanResult(
        ssid: String,
        bssid: String,
        level: Int,
        frequency: Int,
        capabilities: String
    ): ScanResult {
        val scanResult = ScanResult()
        
        // Основные поля
        scanResult.SSID = ssid
        scanResult.BSSID = bssid
        scanResult.level = level
        scanResult.frequency = frequency
        scanResult.capabilities = capabilities
        scanResult.timestamp = System.currentTimeMillis() * 1000 // микросекунды
        
        // Для Android 6.0+ (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                setField(scanResult, "channelWidth", 2) // 80 MHz
                setField(scanResult, "centerFreq0", frequency)
                setField(scanResult, "centerFreq1", 0)
            } catch (e: Exception) {
                // Игнорируем если поля не существуют
            }
        }
        
        // Для Android 9.0+ (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                setField(scanResult, "wifiStandard", 5) // WiFi 5 (802.11ac)
            } catch (e: Exception) {
                // Игнорируем
            }
        }
        
        return scanResult
    }

    /**
     * Установка приватного поля через рефлексию
     */
    private fun setField(obj: Any, fieldName: String, value: Any) {
        try {
            val field: Field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(obj, value)
        } catch (e: NoSuchFieldException) {
            // Field doesn't exist, ignore
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // УПРАВЛЕНИЕ НАСТРОЙКАМИ
    // ═══════════════════════════════════════════════════════════════

    /**
     * Обновление настроек если прошел интервал
     * Паттерн из GPS Setter - избегаем частых reload()
     */
    private fun updateSettingsIfNeeded() {
        if (System.currentTimeMillis() - mLastUpdated > UPDATE_INTERVAL) {
            settings.reload()
            mLastUpdated = System.currentTimeMillis()
            XposedBridge.log("WiFiSpoofer: Settings reloaded")
        }
    }
}
