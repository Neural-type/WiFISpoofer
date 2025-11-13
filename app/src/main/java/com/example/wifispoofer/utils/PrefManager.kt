package com.example.wifispoofer.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.example.wifispoofer.BuildConfig
import com.example.wifispoofer.App
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("WorldReadableFiles")
object PrefManager {

    private const val MODULE_ENABLED = "module_enabled"
    private const val FAKE_NETWORKS = "fake_networks"
    private const val HIDE_REAL_NETWORKS = "hide_real_networks"
    private const val WHITELIST_MODE = "whitelist_mode"
    private const val BLOCKED_SSIDS = "blocked_ssids"
    private const val RANDOM_SIGNAL = "random_signal"
    private const val DYNAMIC_MODE = "dynamic_mode"
    private const val SCANNING_MODE = "scanning_mode"

    private val pref: SharedPreferences by lazy {
        try {
            @Suppress("DEPRECATION")
            App.instance.getSharedPreferences(
                "${BuildConfig.APPLICATION_ID}_prefs",
                Context.MODE_WORLD_READABLE
            )
        } catch (e: SecurityException) {
            android.util.Log.w("WiFiSpoofer", "MODE_WORLD_READABLE failed, using MODE_PRIVATE")
            App.instance.getSharedPreferences(
                "${BuildConfig.APPLICATION_ID}_prefs",
                Context.MODE_PRIVATE
            )
        }
    }

    var isEnabled: Boolean
        get() = pref.getBoolean(MODULE_ENABLED, false)
        set(value) {
            pref.edit().putBoolean(MODULE_ENABLED, value).apply()
        }

    var hideRealNetworks: Boolean
        get() = pref.getBoolean(HIDE_REAL_NETWORKS, false)
        set(value) {
            pref.edit().putBoolean(HIDE_REAL_NETWORKS, value).apply()
        }

    var whitelistMode: Boolean
        get() = pref.getBoolean(WHITELIST_MODE, false)
        set(value) {
            pref.edit().putBoolean(WHITELIST_MODE, value).apply()
        }

    var randomSignal: Boolean
        get() = pref.getBoolean(RANDOM_SIGNAL, false)
        set(value) {
            pref.edit().putBoolean(RANDOM_SIGNAL, value).apply()
        }

    var dynamicMode: Boolean
        get() = pref.getBoolean(DYNAMIC_MODE, false)
        set(value) {
            pref.edit().putBoolean(DYNAMIC_MODE, value).apply()
        }

    var scanningMode: Boolean
        get() = pref.getBoolean(SCANNING_MODE, false)
        set(value) {
            pref.edit().putBoolean(SCANNING_MODE, value).apply()
        }

    var blockedSSIDs: String
        get() = pref.getString(BLOCKED_SSIDS, "") ?: ""
        set(value) {
            pref.edit().putString(BLOCKED_SSIDS, value).apply()
        }

    fun getFakeNetworks(): List<FakeNetwork> {
        val jsonStr = pref.getString(FAKE_NETWORKS, "[]") ?: "[]"
        return parseFakeNetworks(jsonStr)
    }

    fun saveFakeNetworks(networks: List<FakeNetwork>) {
        runInBackground {
            val jsonArray = JSONArray()
            
            for (network in networks) {
                val jsonObject = JSONObject()
                jsonObject.put("ssid", network.ssid)
                jsonObject.put("bssid", network.bssid)
                jsonObject.put("level", network.level)
                jsonObject.put("frequency", network.frequency)
                jsonObject.put("capabilities", network.capabilities)
                jsonArray.put(jsonObject)
            }
            
            pref.edit()
                .putString(FAKE_NETWORKS, jsonArray.toString())
                .apply()
        }
    }

    fun addFakeNetwork(network: FakeNetwork) {
        val networks = getFakeNetworks().toMutableList()
        networks.add(network)
        saveFakeNetworks(networks)
    }

    fun removeFakeNetwork(ssid: String) {
        val networks = getFakeNetworks().filter { it.ssid != ssid }
        saveFakeNetworks(networks)
    }

    fun clearFakeNetworks() {
        pref.edit().putString(FAKE_NETWORKS, "[]").apply()
    }

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

    @OptIn(DelicateCoroutinesApi::class)
    private fun runInBackground(method: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            method.invoke()
        }
    }
}

data class FakeNetwork(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequency: Int,
    val capabilities: String
) {
    companion object {
        fun generateRandomBSSID(): String {
            val random = java.util.Random()
            return String.format(
                "%02X:%02X:%02X:%02X:%02X:%02X",
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256)
            )
        }
    }
}
