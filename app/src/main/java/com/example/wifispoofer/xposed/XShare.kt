package com.example.wifispoofer.xposed

import de.robv.android.xposed.XSharedPreferences
import com.example.wifispoofer.BuildConfig
import org.json.JSONArray

class XShare {

    private var xPref: XSharedPreferences? = null

    private fun pref(): XSharedPreferences {
        xPref = XSharedPreferences(
            BuildConfig.APPLICATION_ID,
            "${BuildConfig.APPLICATION_ID}_prefs"
        )
        xPref?.reload()
        return xPref as XSharedPreferences
    }

    val isEnabled: Boolean
        get() = pref().getBoolean("module_enabled", false)

    val hideRealNetworks: Boolean
        get() = pref().getBoolean("hide_real_networks", false)

    val fakeNetworks: List<FakeNetwork>
        get() {
            val jsonStr = pref().getString("fake_networks", "[]") ?: "[]"
            return parseFakeNetworks(jsonStr)
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
            android.util.Log.e("WiFiSpoofer", "Error parsing: ${e.message}")
        }
        
        return networks
    }

    fun reload() {
        pref().reload()
    }
}

data class FakeNetwork(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequency: Int,
    val capabilities: String
)
