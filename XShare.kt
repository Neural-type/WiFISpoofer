package com.example.wifispoofer.xposed

import de.robv.android.xposed.XSharedPreferences
import com.example.wifispoofer.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * XShare - Класс для чтения настроек из XSharedPreferences
 * Используется внутри Xposed модуля
 * 
 * ВАЖНО: Это НЕ то же самое что обычные SharedPreferences!
 * XSharedPreferences работает через межпроцессное взаимодействие
 * 
 * @author Neural-type
 * @version 11.0
 * @based_on GPS Setter XShare.kt
 */
class XShare {

    private var xPref: XSharedPreferences? = null

    /**
     * Получение XSharedPreferences с reload()
     * ВАЖНО: Каждый раз создаем новый объект и вызываем reload()
     */
    private fun pref(): XSharedPreferences {
        xPref = XSharedPreferences(
            BuildConfig.APPLICATION_ID,
            "${BuildConfig.APPLICATION_ID}_prefs"
        )
        // КРИТИЧНО: reload() перед каждым чтением!
        xPref?.reload()
        return xPref as XSharedPreferences
    }

    /**
     * Включен ли модуль
     */
    val isEnabled: Boolean
        get() = pref().getBoolean("module_enabled", false)

    /**
     * Скрывать ли реальные сети
     */
    val hideRealNetworks: Boolean
        get() = pref().getBoolean("hide_real_networks", false)

    /**
     * Режим whitelist (true) или blacklist (false)
     */
    val whitelistMode: Boolean
        get() = pref().getBoolean("whitelist_mode", false)

    /**
     * Случайный уровень сигнала (±5 dBm)
     */
    val randomSignal: Boolean
        get() = pref().getBoolean("random_signal", false)

    /**
     * Динамический режим (сети появляются/исчезают)
     */
    val dynamicMode: Boolean
        get() = pref().getBoolean("dynamic_mode", false)

    /**
     * Список заблокированных SSID (через запятую)
     */
    val blockedSSIDs: List<String>
        get() {
            val blockedStr = pref().getString("blocked_ssids", "") ?: ""
            if (blockedStr.isEmpty()) {
                return emptyList()
            }
            
            return blockedStr.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

    /**
     * Список фейковых сетей (JSON)
     */
    val fakeNetworks: List<FakeNetwork>
        get() {
            val jsonStr = pref().getString("fake_networks", "[]") ?: "[]"
            return parseFakeNetworks(jsonStr)
        }

    /**
     * Парсинг JSON строки с фейковыми сетями
     */
    private fun parseFakeNetworks(jsonStr: String): List<FakeNetwork> {
        val networks = ArrayList<FakeNetwork>()
        
        try {
            val jsonArray = JSONArray(jsonStr)
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                
                networks.add(
                    FakeNetwork(
                        ssid = jsonObject.getString("ssid"),
                        bssid = jsonObject.getString("bssid"),
                        level = jsonObject.getInt("level"),
                        frequency = jsonObject.getInt("frequency"),
                        capabilities = jsonObject.getString("capabilities")
                    )
                )
            }
            
        } catch (e: Exception) {
            android.util.Log.e("WiFiSpoofer", "Error parsing fake networks: ${e.message}")
            e.printStackTrace()
        }
        
        return networks
    }

    /**
     * Принудительное обновление настроек
     * Вызывается периодически из WifiHook
     */
    fun reload() {
        pref().reload()
    }
}

/**
 * Data class для фейковой WiFi сети
 */
data class FakeNetwork(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequency: Int,
    val capabilities: String
)
