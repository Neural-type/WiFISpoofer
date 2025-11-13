package com.example.wifispoofer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wifispoofer.utils.FakeNetwork
import com.example.wifispoofer.utils.PrefManager

/**
 * MainActivity - Полнофункциональное приложение WiFi Spoofer v11.1
 * 
 * @author Neural-type
 * @version 11.1
 */
class MainActivity : AppCompatActivity() {

    private lateinit var switchModule: Switch
    private lateinit var switchHideReal: Switch
    private lateinit var btnScan: Button
    private lateinit var btnAddFake: Button
    private lateinit var listFakeNetworks: LinearLayout
    private lateinit var scrollFakeNetworks: ScrollView
    private lateinit var tvStatus: TextView
    
    private lateinit var wifiManager: WifiManager
    private val LOCATION_PERMISSION_CODE = 100

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val results = wifiManager.scanResults
            showScanResults(results.map { it.SSID to it.level })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        // Создаю UI програм programming
        createUI()
        
        // Загружаю сохраненные сети
        loadFakeNetworks()
        
        // Обновляю статус
        updateStatus()
        
        // Проверяю разрешения
        checkPermissions()
    }

    private fun createUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // === ЗАГОЛОВОК ===
        val title = TextView(this).apply {
            text = "WiFi Spoofer v11.1"
            textSize = 26f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        mainLayout.addView(title)

        // === ВКЛЮЧЕНИЕ МОДУЛЯ ===
        switchModule = Switch(this).apply {
            text = "Модуль включен"
            textSize = 18f
            isChecked = PrefManager.isEnabled
            setOnCheckedChangeListener { _, isChecked ->
                PrefManager.isEnabled = isChecked
                updateStatus()
            }
        }
        mainLayout.addView(switchModule)

        // === СКРЫТЬ РЕАЛЬНЫЕ СЕТИ ===
        switchHideReal = Switch(this).apply {
            text = "Скрыть реальные сети"
            textSize = 16f
            isChecked = PrefManager.hideRealNetworks
            setPadding(0, 16, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                PrefManager.hideRealNetworks = isChecked
                updateStatus()
            }
        }
        mainLayout.addView(switchHideReal)

        // === СТАТУС ===
        tvStatus = TextView(this).apply {
            text = ""
            textSize = 14f
            setPadding(0, 24, 0, 16)
        }
        mainLayout.addView(tvStatus)

        // === КНОПКИ ===
        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        btnScan = Button(this).apply {
            text = "Scan WiFi"
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener { scanWifi() }
        }
        buttonsLayout.addView(btnScan)

        btnAddFake = Button(this).apply {
            text = "Add Fake"
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener { showAddFakeDialog() }
        }
        buttonsLayout.addView(btnAddFake)

        mainLayout.addView(buttonsLayout)

        // === СПИСОК FAKE СЕТЕЙ ===
        val labelFakeNetworks = TextView(this).apply {
            text = "Fake Networks:"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        }
        mainLayout.addView(labelFakeNetworks)

        listFakeNetworks = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollFakeNetworks = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(listFakeNetworks)
        }
        mainLayout.addView(scrollFakeNetworks)

        setContentView(mainLayout)
    }

    private fun loadFakeNetworks() {
        listFakeNetworks.removeAllViews()
        
        val networks = PrefManager.getFakeNetworks()
        if (networks.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No fake networks. Add some!"
                textSize = 14f
                setPadding(8, 8, 8, 8)
            }
            listFakeNetworks.addView(emptyText)
        } else {
            for (network in networks) {
                addNetworkView(network)
            }
        }
    }

    private fun addNetworkView(network: FakeNetwork) {
        val networkLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0xFFEEEEEE.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val ssidText = TextView(this).apply {
            text = network.ssid
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        infoLayout.addView(ssidText)

        val detailsText = TextView(this).apply {
            text = "${network.level} dBm | ${network.frequency} MHz"
            textSize = 12f
        }
        infoLayout.addView(detailsText)

        networkLayout.addView(infoLayout)

        val btnDelete = Button(this).apply {
            text = "✕"
            setOnClickListener {
                PrefManager.removeFakeNetwork(network.ssid)
                loadFakeNetworks()
                updateStatus()
            }
        }
        networkLayout.addView(btnDelete)

        listFakeNetworks.addView(networkLayout)
    }

    private fun scanWifi() {
        if (!checkPermissions()) {
            return
        }

        PrefManager.scanningMode = true
        
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, filter)
        
        wifiManager.startScan()
        Toast.makeText(this, "Scanning WiFi networks...", Toast.LENGTH_SHORT).show()
    }

    private fun showScanResults(results: List<Pair<String, Int>>) {
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
        
        PrefManager.scanningMode = false

        if (results.isEmpty()) {
            Toast.makeText(this, "No networks found", Toast.LENGTH_SHORT).show()
            return
        }

        val networkNames = results.map { "${it.first} (${it.second} dBm)" }.toTypedArray()
        val selected = BooleanArray(results.size) { false }

        AlertDialog.Builder(this)
            .setTitle("Select networks to block")
            .setMultiChoiceItems(networkNames, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setPositiveButton("Block Selected") { _, _ ->
                val blockedList = results.filterIndexed { index, _ -> selected[index] }
                    .map { it.first }
                    .joinToString(",")
                
                PrefManager.blockedSSIDs = blockedList
                Toast.makeText(this, "Blocked ${blockedList.split(",").size} networks", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddFakeDialog() {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val ssidInput = EditText(this).apply {
            hint = "SSID (e.g., FREE_WIFI)"
        }
        dialogLayout.addView(ssidInput)

        val levelInput = EditText(this).apply {
            hint = "Signal Level (e.g., -55)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        dialogLayout.addView(levelInput)

        val freqInput = EditText(this).apply {
            hint = "Frequency (e.g., 2437 or 5180)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        dialogLayout.addView(freqInput)

        AlertDialog.Builder(this)
            .setTitle("Add Fake Network")
            .setView(dialogLayout)
            .setPositiveButton("Add") { _, _ ->
                val ssid = ssidInput.text.toString()
                val level = levelInput.text.toString().toIntOrNull() ?: -60
                val freq = freqInput.text.toString().toIntOrNull() ?: 2437

                if (ssid.isEmpty()) {
                    Toast.makeText(this, "SSID cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val network = FakeNetwork(
                    ssid = ssid,
                    bssid = FakeNetwork.generateRandomBSSID(),
                    level = level,
                    frequency = freq,
                    capabilities = "[WPA2-PSK-CCMP][ESS]"
                )

                PrefManager.addFakeNetwork(network)
                loadFakeNetworks()
                updateStatus()
                Toast.makeText(this, "Added $ssid", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatus() {
        val fakeCount = PrefManager.getFakeNetworks().size
        val blockedCount = PrefManager.blockedSSIDs.split(",").filter { it.isNotEmpty() }.size
        
        tvStatus.text = buildString {
            if (PrefManager.isEnabled) {
                append("✓ Module ACTIVE\n")
                append("Fake networks: $fakeCount\n")
                append("Blocked networks: $blockedCount\n")
                if (PrefManager.hideRealNetworks) {
                    append("All real networks hidden\n")
                }
            } else {
                append("✗ Module DISABLED\n")
            }
            append("\nIMPORTANT:\n")
            append("1. Enable in LSPosed\n")
            append("2. Scope: android\n")
            append("3. Reboot device")
        }
    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_CODE
                )
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted! You can now scan WiFi.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied. Cannot scan WiFi.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}
