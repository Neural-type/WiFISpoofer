package com.example.wifispoofer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    
    private static final String PREFS_NAME = "WifiSpooferPrefs";
    // Сохраняем во внутреннем хранилище (можем писать без root)
    private static final String INTERNAL_CONFIG = "/data/data/com.example.wifispoofer/files/wifispoofer_config.json";
    // Копируем в /data/local/tmp/ через root (откуда модуль может читать)
    private static final String SHARED_CONFIG = "/data/local/tmp/wifispoofer_config.json";
    private static final String KEY_NETWORKS = "fake_networks";
    private static final String KEY_BLOCKED_SSIDS = "blocked_ssids";
    private static final String KEY_RANDOM_SIGNAL = "random_signal";
    private static final String KEY_HIDE_REAL = "hide_real_networks";
    private static final String KEY_DYNAMIC_MODE = "dynamic_mode";
    private static final String KEY_REAL_NETWORKS = "real_networks_cache";
    private static final String KEY_BYPASS_SPOOF = "bypass_spoof_mode";
    private static final String KEY_WHITELIST_MODE = "whitelist_mode";
    private static final String KEY_MODULE_ENABLED = "module_enabled";
    private static final String KEY_SCANNING_MODE = "scanning_mode";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private ListView networksListView;
    private NetworkAdapter adapter;
    private List<FakeNetwork> networks;
    private SharedPreferences prefs;
    private WifiManager wifiManager;
    
    private Switch hideRealNetworksSwitch;
    private Switch randomSignalSwitch;
    private Switch dynamicModeSwitch;
    private Switch whitelistModeSwitch;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Делаем SharedPreferences доступным для чтения модулем
        makePrefsWorldReadable();
        
        // ВАЖНО: Сохраняем настройки в файл для модуля
        saveConfigToFile();
        
        // КРИТИЧНО: Копируем файл в /data/local/tmp/ при КАЖДОМ запуске
        // /data/local/tmp/ очищается при перезагрузке!
        copyConfigToSharedLocation();
        
        networks = new ArrayList<>();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        
        setContentView(createMainLayout());
        
        loadNetworks();
        setupUI();
        loadSettings();
        checkPermissions();
    }
    
    private View createMainLayout() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);
        
        // Заголовок
        TextView title = new TextView(this);
        title.setText("WiFi Spoofer");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 20);
        mainLayout.addView(title);
        
        // БОЛЬШАЯ КНОПКА СТАРТ/СТОП
        Button startStopButton = new Button(this);
        updateStartStopButton(startStopButton);
        startStopButton.setTextSize(18);
        startStopButton.setPadding(20, 30, 20, 30);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, 0, 0, 20);
        startStopButton.setLayoutParams(buttonParams);
        startStopButton.setOnClickListener(v -> {
            boolean isEnabled = prefs.getBoolean(KEY_MODULE_ENABLED, false);
            prefs.edit().putBoolean(KEY_MODULE_ENABLED, !isEnabled).commit();
            makePrefsWorldReadable();
            updateStartStopButton(startStopButton);
            
            if (!isEnabled) {
                Toast.makeText(this, 
                    "✅ СПУФИНГ ЗАПУЩЕН!\n\nМодуль активен. Перезапустите WiFi для применения.", 
                    Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, 
                    "⏸️ СПУФИНГ ОСТАНОВЛЕН!\n\nМодуль выключен. Сети показываются как есть.", 
                    Toast.LENGTH_LONG).show();
            }
        });
        mainLayout.addView(startStopButton);
        
        // Разделитель
        View divider1 = new View(this);
        divider1.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 3));
        divider1.setBackgroundColor(0xFF666666);
        mainLayout.addView(divider1);
        
        // Настройки
        LinearLayout settingsLayout = new LinearLayout(this);
        settingsLayout.setOrientation(LinearLayout.VERTICAL);
        settingsLayout.setPadding(0, 0, 0, 20);
        
        // Скрыть реальные сети
        LinearLayout hideRealLayout = new LinearLayout(this);
        hideRealLayout.setOrientation(LinearLayout.HORIZONTAL);
        TextView hideRealLabel = new TextView(this);
        hideRealLabel.setText("Скрыть реальные сети: ");
        hideRealLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        hideRealNetworksSwitch = new Switch(this);
        hideRealNetworksSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting(KEY_HIDE_REAL, isChecked));
        hideRealLayout.addView(hideRealLabel);
        hideRealLayout.addView(hideRealNetworksSwitch);
        settingsLayout.addView(hideRealLayout);
        
        // Случайный сигнал
        LinearLayout randomSignalLayout = new LinearLayout(this);
        randomSignalLayout.setOrientation(LinearLayout.HORIZONTAL);
        TextView randomSignalLabel = new TextView(this);
        randomSignalLabel.setText("Случайный уровень сигнала: ");
        randomSignalLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        randomSignalSwitch = new Switch(this);
        randomSignalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting(KEY_RANDOM_SIGNAL, isChecked));
        randomSignalLayout.addView(randomSignalLabel);
        randomSignalLayout.addView(randomSignalSwitch);
        settingsLayout.addView(randomSignalLayout);
        
        // Динамический режим
        LinearLayout dynamicLayout = new LinearLayout(this);
        dynamicLayout.setOrientation(LinearLayout.HORIZONTAL);
        TextView dynamicLabel = new TextView(this);
        dynamicLabel.setText("Динамический режим (появление/исчезновение): ");
        dynamicLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        dynamicModeSwitch = new Switch(this);
        dynamicModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting(KEY_DYNAMIC_MODE, isChecked));
        dynamicLayout.addView(dynamicLabel);
        dynamicLayout.addView(dynamicModeSwitch);
        settingsLayout.addView(dynamicLayout);
        
        // Whitelist режим
        LinearLayout whitelistLayout = new LinearLayout(this);
        whitelistLayout.setOrientation(LinearLayout.HORIZONTAL);
        TextView whitelistLabel = new TextView(this);
        whitelistLabel.setText("Блокировать ВСЕ кроме выбранных (Whitelist): ");
        whitelistLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        whitelistModeSwitch = new Switch(this);
        whitelistModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSetting(KEY_WHITELIST_MODE, isChecked);
            if (isChecked) {
                Toast.makeText(this, 
                    "Whitelist режим: Будут заблокированы ВСЕ сети кроме выбранных вами", 
                    Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, 
                    "Blacklist режим: Будут заблокированы только выбранные вами сети", 
                    Toast.LENGTH_LONG).show();
            }
        });
        whitelistLayout.addView(whitelistLabel);
        whitelistLayout.addView(whitelistModeSwitch);
        settingsLayout.addView(whitelistLayout);
        
        mainLayout.addView(settingsLayout);
        
        // Кнопка добавления сети
        Button addButton = new Button(this);
        addButton.setText("+ Добавить фейковую сеть");
        addButton.setOnClickListener(v -> showAddNetworkDialog());
        mainLayout.addView(addButton);
        
        // Список сетей
        TextView networksLabel = new TextView(this);
        networksLabel.setText("Фейковые сети:");
        networksLabel.setTextSize(18);
        networksLabel.setPadding(0, 20, 0, 10);
        mainLayout.addView(networksLabel);
        
        networksListView = new ListView(this);
        networksListView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        mainLayout.addView(networksListView);
        
        // Кнопки управления
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.VERTICAL);
        buttonLayout.setPadding(0, 10, 0, 0);
        
        // Кнопка сканирования всех сетей
        Button scanButton = new Button(this);
        scanButton.setText("🔍 Сканировать все сети");
        scanButton.setOnClickListener(v -> scanAllNetworks());
        buttonLayout.addView(scanButton);
        
        // Кнопка управления блокировкой
        Button blockButton = new Button(this);
        blockButton.setText("⚙️ Управление блокировкой реальных сетей");
        blockButton.setOnClickListener(v -> showBlockNetworksDialog());
        buttonLayout.addView(blockButton);
        
        // Кнопка управления фейковыми сетями
        Button fakeNetworksButton = new Button(this);
        fakeNetworksButton.setText("📋 Управление фейковыми сетями");
        fakeNetworksButton.setOnClickListener(v -> showFakeNetworksManagementDialog());
        buttonLayout.addView(fakeNetworksButton);
        
        mainLayout.addView(buttonLayout);
        
        return mainLayout;
    }
    
    private void setupUI() {
        adapter = new NetworkAdapter(this, networks);
        networksListView.setAdapter(adapter);
        
        networksListView.setOnItemClickListener((parent, view, position, id) -> {
            FakeNetwork network = networks.get(position);
            showEditNetworkDialog(network, position);
        });
        
        networksListView.setOnItemLongClickListener((parent, view, position, id) -> {
            FakeNetwork network = networks.get(position);
            showDeleteConfirmDialog(network, position);
            return true;
        });
    }
    
    private void loadSettings() {
        hideRealNetworksSwitch.setChecked(prefs.getBoolean(KEY_HIDE_REAL, false));
        randomSignalSwitch.setChecked(prefs.getBoolean(KEY_RANDOM_SIGNAL, true));
        dynamicModeSwitch.setChecked(prefs.getBoolean(KEY_DYNAMIC_MODE, false));
        whitelistModeSwitch.setChecked(prefs.getBoolean(KEY_WHITELIST_MODE, false));
    }
    
    private void saveSetting(String key, boolean value) {
        prefs.edit().putBoolean(key, value).commit();
        makePrefsWorldReadable();
        saveConfigToFile(); // Сохраняем в файл для модуля
        Toast.makeText(this, "Настройка сохранена. Перезапустите приложения для применения.", Toast.LENGTH_SHORT).show();
    }
    
    private void updateStartStopButton(Button button) {
        if (prefs == null || button == null) {
            return;
        }
        
        boolean isEnabled = prefs.getBoolean(KEY_MODULE_ENABLED, false);
        if (isEnabled) {
            button.setText("⏸️ СТОП - Остановить спуфинг");
            button.setBackgroundColor(0xFFFF5252); // Красный
            button.setTextColor(0xFFFFFFFF);
        } else {
            button.setText("▶️ СТАРТ - Запустить спуфинг");
            button.setBackgroundColor(0xFF4CAF50); // Зеленый
            button.setTextColor(0xFFFFFFFF);
        }
    }
    
    private void showAddNetworkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Добавить сеть");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final EditText ssidInput = new EditText(this);
        ssidInput.setHint("SSID (название сети)");
        layout.addView(ssidInput);
        
        final EditText bssidInput = new EditText(this);
        bssidInput.setHint("BSSID (MAC адрес) - необязательно");
        layout.addView(bssidInput);
        
        final EditText levelInput = new EditText(this);
        levelInput.setHint("Уровень сигнала (-90 до -30)");
        levelInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        levelInput.setText("-55");
        layout.addView(levelInput);
        
        final Spinner frequencySpinner = new Spinner(this);
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, 
            new String[]{"2.4 GHz (2437 MHz)", "5 GHz (5180 MHz)"});
        freqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frequencySpinner.setAdapter(freqAdapter);
        layout.addView(frequencySpinner);
        
        final Spinner securitySpinner = new Spinner(this);
        ArrayAdapter<String> secAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"Открытая", "WPA2", "WPA", "WEP"});
        secAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        securitySpinner.setAdapter(secAdapter);
        layout.addView(securitySpinner);
        
        builder.setView(layout);
        
        builder.setPositiveButton("Добавить", (dialog, which) -> {
            String ssid = ssidInput.getText().toString();
            if (ssid.isEmpty()) {
                Toast.makeText(this, "Введите SSID", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String bssid = bssidInput.getText().toString();
            if (bssid.isEmpty()) {
                bssid = generateRandomMAC();
            }
            
            int level = -55;
            try {
                level = Integer.parseInt(levelInput.getText().toString());
            } catch (Exception e) {}
            
            int frequency = frequencySpinner.getSelectedItemPosition() == 0 ? 2437 : 5180;
            String capabilities = getCapabilities(securitySpinner.getSelectedItemPosition());
            
            FakeNetwork network = new FakeNetwork(ssid, bssid, level, frequency, capabilities);
            networks.add(network);
            adapter.notifyDataSetChanged();
            saveNetworks();
            
            Toast.makeText(this, "Сеть добавлена", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }
    
    private void showEditNetworkDialog(FakeNetwork network, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Редактировать сеть");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final EditText ssidInput = new EditText(this);
        ssidInput.setText(network.ssid);
        layout.addView(ssidInput);
        
        final EditText bssidInput = new EditText(this);
        bssidInput.setText(network.bssid);
        layout.addView(bssidInput);
        
        final EditText levelInput = new EditText(this);
        levelInput.setText(String.valueOf(network.level));
        levelInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        layout.addView(levelInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            network.ssid = ssidInput.getText().toString();
            network.bssid = bssidInput.getText().toString();
            try {
                network.level = Integer.parseInt(levelInput.getText().toString());
            } catch (Exception e) {}
            
            adapter.notifyDataSetChanged();
            saveNetworks();
            Toast.makeText(this, "Сеть обновлена", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }
    
    private void showDeleteConfirmDialog(FakeNetwork network, int position) {
        new AlertDialog.Builder(this)
            .setTitle("Удалить сеть?")
            .setMessage("Удалить сеть " + network.ssid + "?")
            .setPositiveButton("Удалить", (dialog, which) -> {
                networks.remove(position);
                adapter.notifyDataSetChanged();
                saveNetworks();
                Toast.makeText(this, "Сеть удалена", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
    
    private void scanAllNetworks() {
        if (!checkLocationPermission()) {
            Toast.makeText(this, "Требуется разрешение на определение местоположения для сканирования WiFi", Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSION_REQUEST_CODE);
            return;
        }
        
        // Показываем прогресс-диалог
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("Сканирование...")
            .setMessage("Поиск всех доступных WiFi сетей...\n\nЭто займет несколько секунд.")
            .setCancelable(false)
            .create();
        progressDialog.show();
        
        // ВКЛЮЧАЕМ РЕЖИМ СКАНИРОВАНИЯ - модуль пропустит перехват!
        prefs.edit().putBoolean(KEY_SCANNING_MODE, true).commit(); makePrefsWorldReadable();
        
        // Запускаем сканирование
        boolean scanStarted = wifiManager.startScan();
        
        if (!scanStarted) {
            prefs.edit().putBoolean(KEY_SCANNING_MODE, false).commit(); makePrefsWorldReadable();
            progressDialog.dismiss();
            Toast.makeText(this, "Не удалось запустить сканирование WiFi", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Даем время на сканирование
        new android.os.Handler().postDelayed(() -> {
            try {
                // Получаем результаты (они будут реальными, т.к. флаг SCANNING_MODE = true)
                List<ScanResult> scanResults = wifiManager.getScanResults();
                
                // ВЫКЛЮЧАЕМ РЕЖИМ СКАНИРОВАНИЯ
                prefs.edit().putBoolean(KEY_SCANNING_MODE, false).commit(); makePrefsWorldReadable();
                
                progressDialog.dismiss();
                
                if (scanResults == null || scanResults.isEmpty()) {
                    Toast.makeText(this, "Сети не найдены. Убедитесь что WiFi включен.", Toast.LENGTH_LONG).show();
                    return;
                }
                
                // СОХРАНЯЕМ результаты в кеш
                saveRealNetworksToCache(scanResults);
                
                Toast.makeText(this, "Найдено реальных сетей: " + scanResults.size(), Toast.LENGTH_SHORT).show();
                
                // Показываем результаты
                showScanResultsDialog(scanResults);
                
            } catch (Exception e) {
                prefs.edit().putBoolean(KEY_SCANNING_MODE, false).commit(); makePrefsWorldReadable();
                progressDialog.dismiss();
                Toast.makeText(this, "Ошибка при сканировании: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, 3000);
    }
    
    private void showScanResultsDialog(List<ScanResult> scanResults) {
        // Убираем дубликаты по SSID
        List<RealWifiNetwork> uniqueNetworks = new ArrayList<>();
        Set<String> seenSSIDs = new HashSet<>();
        
        for (ScanResult result : scanResults) {
            String ssid = result.SSID;
            if (ssid != null && !ssid.isEmpty() && !seenSSIDs.contains(ssid)) {
                seenSSIDs.add(ssid);
                uniqueNetworks.add(new RealWifiNetwork(
                    ssid,
                    result.BSSID,
                    result.level,
                    result.frequency
                ));
            }
        }
        
        if (uniqueNetworks.isEmpty()) {
            Toast.makeText(this, "Не найдено сетей с именами", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Создаем диалог с информацией
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Найдено сетей: " + uniqueNetworks.size());
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        for (RealWifiNetwork network : uniqueNetworks) {
            TextView networkInfo = new TextView(this);
            networkInfo.setText(String.format(
                "📡 %s\nMAC: %s\n📶 Сигнал: %d dBm | %.0f MHz\n",
                network.ssid, network.bssid, network.level, network.frequency
            ));
            networkInfo.setPadding(10, 10, 10, 20);
            networkInfo.setTextSize(14);
            layout.addView(networkInfo);
            
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2));
            divider.setBackgroundColor(0xFFCCCCCC);
            layout.addView(divider);
        }
        
        scrollView.addView(layout);
        builder.setView(scrollView);
        
        builder.setPositiveButton("OK", null);
        builder.setNeutralButton("Управление блокировкой", (dialog, which) -> {
            showBlockNetworksDialog();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Настраиваем размер диалога
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
    }
    
    private void showBlockNetworksDialog() {
        if (!checkLocationPermission()) {
            Toast.makeText(this, "Требуется разрешение на определение местоположения", Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSION_REQUEST_CODE);
            return;
        }
        
        // Загружаем кешированные результаты сканирования
        List<RealWifiNetwork> cachedNetworks = loadCachedRealNetworks();
        
        if (cachedNetworks.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Нет данных о сетях")
                .setMessage("Сначала нажмите кнопку \"Сканировать все сети\" чтобы получить список доступных WiFi сетей.")
                .setPositiveButton("Сканировать сейчас", (dialog, which) -> scanAllNetworks())
                .setNegativeButton("Отмена", null)
                .show();
            return;
        }
        
        showRealNetworksSelectionDialog(cachedNetworks);
    }
    
    private List<RealWifiNetwork> loadCachedRealNetworks() {
        List<RealWifiNetwork> networks = new ArrayList<>();
        
        try {
            String json = prefs.getString(KEY_REAL_NETWORKS, null);
            if (json != null) {
                JSONArray jsonArray = new JSONArray(json);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    String ssid = obj.getString("ssid");
                    if (ssid != null && !ssid.isEmpty()) {
                        networks.add(new RealWifiNetwork(
                            ssid,
                            obj.getString("bssid"),
                            obj.getInt("level"),
                            obj.getDouble("frequency")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return networks;
    }
    
    private void saveRealNetworksToCache(List<ScanResult> scanResults) {
        try {
            // Убираем дубликаты по SSID
            List<RealWifiNetwork> uniqueNetworks = new ArrayList<>();
            Set<String> seenSSIDs = new HashSet<>();
            
            for (ScanResult result : scanResults) {
                String ssid = result.SSID;
                if (ssid != null && !ssid.isEmpty() && !seenSSIDs.contains(ssid)) {
                    seenSSIDs.add(ssid);
                    uniqueNetworks.add(new RealWifiNetwork(
                        ssid,
                        result.BSSID,
                        result.level,
                        result.frequency
                    ));
                }
            }
            
            // Сохраняем в JSON
            JSONArray jsonArray = new JSONArray();
            for (RealWifiNetwork network : uniqueNetworks) {
                JSONObject obj = new JSONObject();
                obj.put("ssid", network.ssid);
                obj.put("bssid", network.bssid);
                obj.put("level", network.level);
                obj.put("frequency", network.frequency);
                jsonArray.put(obj);
            }
            
            prefs.edit().putString(KEY_REAL_NETWORKS, jsonArray.toString()).commit(); makePrefsWorldReadable();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showRealNetworksSelectionDialog(List<RealWifiNetwork> uniqueNetworks) {
        boolean whitelistMode = prefs.getBoolean(KEY_WHITELIST_MODE, false);
        
        // Загружаем текущий список заблокированных/разрешенных сетей
        String blockedStr = prefs.getString(KEY_BLOCKED_SSIDS, "");
        Set<String> blockedSet = new HashSet<>();
        if (!blockedStr.isEmpty()) {
            String[] blocked = blockedStr.split(",");
            for (String ssid : blocked) {
                blockedSet.add(ssid.trim());
            }
        }
        
        // Создаем диалог с чекбоксами
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        String title;
        if (whitelistMode) {
            title = "Выберите сети для РАЗРЕШЕНИЯ (" + uniqueNetworks.size() + " найдено)\n" +
                   "⚠️ Все остальные сети будут заблокированы!";
        } else {
            title = "Выберите сети для БЛОКИРОВКИ (" + uniqueNetworks.size() + " найдено)";
        }
        builder.setTitle(title);
        
        // Создаем список с чекбоксами
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);
        
        // Добавляем информационное сообщение
        TextView infoText = new TextView(this);
        if (whitelistMode) {
            infoText.setText("🔒 WHITELIST режим:\nГалочка = разрешена\nБез галочки = заблокирована");
            infoText.setBackgroundColor(0xFFFFEEEE);
        } else {
            infoText.setText("🔓 BLACKLIST режим:\nГалочка = заблокирована\nБез галочки = разрешена");
            infoText.setBackgroundColor(0xFFEEFFEE);
        }
        infoText.setPadding(15, 15, 15, 15);
        infoText.setTextSize(13);
        mainLayout.addView(infoText);
        
        // Добавляем кнопки "Выбрать все" / "Снять все"
        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setPadding(0, 15, 0, 15);
        
        Button selectAllBtn = new Button(this);
        selectAllBtn.setText("Выбрать все");
        selectAllBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        Button deselectAllBtn = new Button(this);
        deselectAllBtn.setText("Снять все");
        deselectAllBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        buttonsLayout.addView(selectAllBtn);
        buttonsLayout.addView(deselectAllBtn);
        mainLayout.addView(buttonsLayout);
        
        // Добавляем ScrollView для списка сетей
        ScrollView scrollView = new ScrollView(this);
        LinearLayout networksLayout = new LinearLayout(this);
        networksLayout.setOrientation(LinearLayout.VERTICAL);
        
        List<CheckBox> checkBoxes = new ArrayList<>();
        
        for (RealWifiNetwork network : uniqueNetworks) {
            LinearLayout networkItem = new LinearLayout(this);
            networkItem.setOrientation(LinearLayout.HORIZONTAL);
            networkItem.setPadding(10, 10, 10, 10);
            
            // Левая часть - чекбокс и текст
            LinearLayout leftLayout = new LinearLayout(this);
            leftLayout.setOrientation(LinearLayout.VERTICAL);
            leftLayout.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(network.ssid);
            checkBox.setTag(network.ssid);
            checkBox.setChecked(blockedSet.contains(network.ssid));
            checkBox.setTextSize(16);
            
            TextView details = new TextView(this);
            details.setText(String.format("  MAC: %s | Сигнал: %d dBm | %.0f MHz",
                network.bssid, network.level, network.frequency));
            details.setTextSize(12);
            details.setPadding(40, 0, 0, 0);
            
            checkBoxes.add(checkBox);
            
            leftLayout.addView(checkBox);
            leftLayout.addView(details);
            
            // Правая часть - кнопка редактирования
            Button editBtn = new Button(this);
            editBtn.setText("✏️");
            editBtn.setLayoutParams(new LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT));
            editBtn.setOnClickListener(v -> showEditBlockedNetworkDialog(network, checkBox));
            
            networkItem.addView(leftLayout);
            networkItem.addView(editBtn);
            networksLayout.addView(networkItem);
            
            // Добавляем разделитель
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFCCCCCC);
            networksLayout.addView(divider);
        }
        
        scrollView.addView(networksLayout);
        mainLayout.addView(scrollView);
        
        // Обработчики для кнопок "Выбрать все" / "Снять все"
        selectAllBtn.setOnClickListener(v -> {
            for (CheckBox cb : checkBoxes) {
                cb.setChecked(true);
            }
        });
        
        deselectAllBtn.setOnClickListener(v -> {
            for (CheckBox cb : checkBoxes) {
                cb.setChecked(false);
            }
        });
        
        builder.setView(mainLayout);
        
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            List<String> selectedSSIDs = new ArrayList<>();
            for (CheckBox cb : checkBoxes) {
                if (cb.isChecked()) {
                    selectedSSIDs.add((String) cb.getTag());
                }
            }
            
            String blockedList = String.join(",", selectedSSIDs);
            prefs.edit().putString(KEY_BLOCKED_SSIDS, blockedList).commit(); makePrefsWorldReadable();
            
            String message;
            if (whitelistMode) {
                if (selectedSSIDs.isEmpty()) {
                    message = "⚠️ ВСЕ сети будут заблокированы (whitelist пуст)!";
                } else {
                    message = "✓ Разрешено сетей: " + selectedSSIDs.size() + "\n" +
                             "⚠️ Все остальные сети будут заблокированы!\n" +
                             "Перезапустите WiFi для применения";
                }
            } else {
                if (selectedSSIDs.isEmpty()) {
                    message = "Все сети разблокированы";
                } else {
                    message = "Заблокировано сетей: " + selectedSSIDs.size() + "\n" +
                             "Перезапустите WiFi для применения";
                }
            }
            
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
        
        builder.setNegativeButton("Отмена", null);
        
        builder.setNeutralButton("Пересканировать", (dialog, which) -> {
            scanAllNetworks();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Настраиваем размер диалога
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
    }
    
    private void showEditBlockedNetworkDialog(RealWifiNetwork network, CheckBox checkBox) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Редактировать сеть");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        TextView info = new TextView(this);
        info.setText("Информация о сети:");
        info.setTextSize(16);
        info.setPadding(0, 0, 0, 15);
        layout.addView(info);
        
        TextView ssidText = new TextView(this);
        ssidText.setText("SSID: " + network.ssid);
        layout.addView(ssidText);
        
        TextView macText = new TextView(this);
        macText.setText("MAC: " + network.bssid);
        layout.addView(macText);
        
        TextView signalText = new TextView(this);
        signalText.setText(String.format("Сигнал: %d dBm", network.level));
        layout.addView(signalText);
        
        TextView freqText = new TextView(this);
        freqText.setText(String.format("Частота: %.0f MHz", network.frequency));
        layout.addView(freqText);
        
        TextView statusText = new TextView(this);
        statusText.setPadding(0, 20, 0, 0);
        statusText.setTextSize(14);
        
        boolean whitelistMode = prefs.getBoolean(KEY_WHITELIST_MODE, false);
        if (whitelistMode) {
            statusText.setText(checkBox.isChecked() ? 
                "Статус: ✓ РАЗРЕШЕНА (в whitelist)" : 
                "Статус: ✗ ЗАБЛОКИРОВАНА (не в whitelist)");
        } else {
            statusText.setText(checkBox.isChecked() ? 
                "Статус: ✗ ЗАБЛОКИРОВАНА" : 
                "Статус: ✓ РАЗРЕШЕНА");
        }
        layout.addView(statusText);
        
        builder.setView(layout);
        
        builder.setPositiveButton("OK", null);
        
        builder.setNeutralButton(checkBox.isChecked() ? "Снять галочку" : "Поставить галочку", 
            (dialog, which) -> checkBox.setChecked(!checkBox.isChecked()));
        
        builder.show();
    }
    
    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        
        if (!checkLocationPermission()) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        
        // WRITE_EXTERNAL_STORAGE больше не нужно!
        // Используем внутреннее хранилище приложения (/data/data/.../files/)
        // которое доступно БЕЗ разрешений на всех Android версиях
        
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                permissionsNeeded.toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение получено", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Разрешение отклонено. Сканирование WiFi не будет работать.", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void loadNetworks() {
        String json = prefs.getString(KEY_NETWORKS, null);
        if (json != null) {
            try {
                JSONArray jsonArray = new JSONArray(json);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    networks.add(new FakeNetwork(
                        obj.getString("ssid"),
                        obj.getString("bssid"),
                        obj.getInt("level"),
                        obj.getInt("frequency"),
                        obj.getString("capabilities")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // Добавляем примеры если пусто
        if (networks.isEmpty()) {
            networks.add(new FakeNetwork("FAKE_Home_5G", generateRandomMAC(), -45, 5180, "[WPA2-PSK-CCMP][ESS]"));
            networks.add(new FakeNetwork("FAKE_Starbucks", generateRandomMAC(), -60, 2437, "[ESS]"));
            saveNetworks();
        }
    }
    
    private void saveNetworks() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (FakeNetwork network : networks) {
                JSONObject obj = new JSONObject();
                obj.put("ssid", network.ssid);
                obj.put("bssid", network.bssid);
                obj.put("level", network.level);
                obj.put("frequency", network.frequency);
                obj.put("capabilities", network.capabilities);
                jsonArray.put(obj);
            }
            prefs.edit().putString(KEY_NETWORKS, jsonArray.toString()).commit();
            makePrefsWorldReadable();
            
            // ВАЖНО: Также сохраняем в файл для модуля
            saveConfigToFile();
            
            Log.d("WiFiSpoofer", "✅ Сохранено сетей: " + networks.size());
            Log.d("WiFiSpoofer", "JSON: " + jsonArray.toString().substring(0, Math.min(200, jsonArray.toString().length())));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Делаем SharedPreferences доступным для чтения модулем
    private void makePrefsWorldReadable() {
        try {
            // Делаем dataDir доступной для чтения
            File dataDir = new File(getApplicationInfo().dataDir);
            if (dataDir.exists()) {
                dataDir.setReadable(true, false);
                dataDir.setExecutable(true, false);
                Log.d("WiFiSpoofer", "✓ DataDir права установлены: " + dataDir.getPath());
            }
            
            // Делаем shared_prefs директорию доступной
            File prefsDir = new File(getApplicationInfo().dataDir + "/shared_prefs");
            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
                Log.d("WiFiSpoofer", "✓ PrefsDir права установлены: " + prefsDir.getPath());
            }
            
            // Делаем файл настроек доступным
            File prefsFile = new File(getApplicationInfo().dataDir + "/shared_prefs/" + PREFS_NAME + ".xml");
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
                Log.d("WiFiSpoofer", "✓ PrefsFile права установлены: " + prefsFile.getPath());
                Log.d("WiFiSpoofer", "✓ Размер файла: " + prefsFile.length() + " bytes");
            } else {
                Log.e("WiFiSpoofer", "✗ Файл настроек не найден: " + prefsFile.getPath());
            }
        } catch (Exception e) {
            Log.e("WiFiSpoofer", "Ошибка установки прав: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Сохраняем настройки в файл для модуля (Android 10+ блокирует XSharedPreferences)
    private void saveConfigToFile() {
        try {
            // Собираем все настройки в JSON
            JSONObject config = new JSONObject();
            
            // Добавляем фейковые сети
            String networksJson = prefs.getString(KEY_NETWORKS, "[]");
            config.put(KEY_NETWORKS, new JSONArray(networksJson));
            
            // Добавляем настройки
            config.put(KEY_HIDE_REAL, prefs.getBoolean(KEY_HIDE_REAL, false));
            config.put(KEY_RANDOM_SIGNAL, prefs.getBoolean(KEY_RANDOM_SIGNAL, true));
            config.put(KEY_DYNAMIC_MODE, prefs.getBoolean(KEY_DYNAMIC_MODE, false));
            config.put(KEY_MODULE_ENABLED, prefs.getBoolean(KEY_MODULE_ENABLED, false));
            config.put(KEY_BLOCKED_SSIDS, prefs.getString(KEY_BLOCKED_SSIDS, ""));
            config.put(KEY_WHITELIST_MODE, prefs.getBoolean(KEY_WHITELIST_MODE, false));
            
            // Шаг 1: Сохраняем во внутреннем хранилище (можем писать БЕЗ root)
            File internalFile = new File(INTERNAL_CONFIG);
            
            // Создаем директорию если нужно
            File parentDir = internalFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Записываем JSON
            java.io.FileOutputStream fos = new java.io.FileOutputStream(internalFile);
            fos.write(config.toString(2).getBytes());
            fos.close();
            
            Log.d("WiFiSpoofer", "✓ Сохранено во внутреннем хранилище: " + INTERNAL_CONFIG);
            Log.d("WiFiSpoofer", "✓ Размер: " + internalFile.length() + " bytes");
            
            // Шаг 2: Копируем в /data/local/tmp/ через ROOT (откуда модуль может читать)
            try {
                // Копируем файл
                Process suProcess = Runtime.getRuntime().exec("su");
                java.io.DataOutputStream os = new java.io.DataOutputStream(suProcess.getOutputStream());
                
                os.writeBytes("cp " + INTERNAL_CONFIG + " " + SHARED_CONFIG + "\n");
                os.writeBytes("chmod 666 " + SHARED_CONFIG + "\n");  // Все могут читать/писать
                os.writeBytes("exit\n");
                os.flush();
                
                int exitCode = suProcess.waitFor();
                
                if (exitCode == 0) {
                    Log.d("WiFiSpoofer", "✓ Скопировано в общую директорию: " + SHARED_CONFIG);
                    Log.d("WiFiSpoofer", "✓ Права установлены: 666 (rw-rw-rw-)");
                } else {
                    Log.e("WiFiSpoofer", "✗ Ошибка копирования через root (exit code: " + exitCode + ")");
                    Log.e("WiFiSpoofer", "⚠ Модуль будет использовать дефолтные сети");
                }
            } catch (Exception rootErr) {
                Log.e("WiFiSpoofer", "✗ Root недоступен: " + rootErr.getMessage());
                Log.e("WiFiSpoofer", "⚠ Модуль будет использовать дефолтные сети");
            }
        } catch (Exception e) {
            Log.e("WiFiSpoofer", "✗ Ошибка сохранения конфигурации в файл: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Копируем файл конфигурации в /data/local/tmp/ при КАЖДОМ запуске приложения
    // /data/local/tmp/ очищается при перезагрузке, поэтому нужно копировать заново!
    private void copyConfigToSharedLocation() {
        try {
            File internalFile = new File(INTERNAL_CONFIG);
            
            // Если внутренний файл не существует - ничего не делаем
            if (!internalFile.exists()) {
                Log.d("WiFiSpoofer", "⚠ Внутренний файл еще не создан, пропускаем копирование");
                return;
            }
            
            Log.d("WiFiSpoofer", "📋 Копируем конфигурацию в /data/local/tmp/...");
            
            // Копируем через ROOT
            Process suProcess = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(suProcess.getOutputStream());
            
            os.writeBytes("cp " + INTERNAL_CONFIG + " " + SHARED_CONFIG + "\n");
            os.writeBytes("chmod 666 " + SHARED_CONFIG + "\n");
            os.writeBytes("exit\n");
            os.flush();
            
            int exitCode = suProcess.waitFor();
            
            if (exitCode == 0) {
                Log.d("WiFiSpoofer", "✓ Автоматически скопировано в: " + SHARED_CONFIG);
                Log.d("WiFiSpoofer", "✓ Модуль теперь может читать конфигурацию!");
            } else {
                Log.e("WiFiSpoofer", "✗ Ошибка копирования (exit: " + exitCode + ")");
            }
        } catch (Exception e) {
            Log.e("WiFiSpoofer", "✗ Не удалось скопировать файл: " + e.getMessage());
        }
    }
    
    private String generateRandomMAC() {
        Random random = new Random();
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
            random.nextInt(256), random.nextInt(256), random.nextInt(256),
            random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }
    
    private String getCapabilities(int position) {
        switch (position) {
            case 0: return "[ESS]";
            case 1: return "[WPA2-PSK-CCMP][ESS]";
            case 2: return "[WPA-PSK-CCMP][ESS]";
            case 3: return "[WEP][ESS]";
            default: return "[ESS]";
        }
    }
    
    private void showFakeNetworksManagementDialog() {
        if (networks.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Нет фейковых сетей")
                .setMessage("У вас пока нет добавленных фейковых сетей.\n\nНажмите кнопку \"+\" чтобы добавить новую сеть.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Управление фейковыми сетями (" + networks.size() + " шт.)");
        
        // Создаем прокручиваемый список с галочками
        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);
        
        // Набор выбранных для удаления сетей
        final Set<Integer> selectedForDeletion = new HashSet<>();
        
        // Кнопка "Выбрать все"
        Button selectAllButton = new Button(this);
        selectAllButton.setText("☑️ Выбрать все");
        selectAllButton.setOnClickListener(v -> {
            if (selectedForDeletion.size() == networks.size()) {
                // Снять все галочки
                selectedForDeletion.clear();
                selectAllButton.setText("☑️ Выбрать все");
            } else {
                // Поставить все галочки
                selectedForDeletion.clear();
                for (int i = 0; i < networks.size(); i++) {
                    selectedForDeletion.add(i);
                }
                selectAllButton.setText("☐ Снять все");
            }
            // Обновляем чекбоксы
            for (int i = 0; i < mainLayout.getChildCount(); i++) {
                View child = mainLayout.getChildAt(i);
                if (child instanceof CheckBox) {
                    CheckBox cb = (CheckBox) child;
                    cb.setChecked(selectedForDeletion.contains((Integer) cb.getTag()));
                }
            }
        });
        mainLayout.addView(selectAllButton);
        
        // Разделитель
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(0xFFCCCCCC);
        mainLayout.addView(divider);
        
        // Создаем чекбокс для каждой сети
        for (int i = 0; i < networks.size(); i++) {
            final int position = i;
            FakeNetwork network = networks.get(i);
            
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(String.format(
                "📡 %s\nMAC: %s | %d dBm | %.0f MHz",
                network.ssid, network.bssid, network.level, (double)network.frequency
            ));
            checkBox.setTag(position);
            checkBox.setPadding(10, 15, 10, 15);
            checkBox.setTextSize(14);
            
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedForDeletion.add(position);
                } else {
                    selectedForDeletion.remove(position);
                }
                // Обновляем текст кнопки "Выбрать все"
                if (selectedForDeletion.size() == networks.size()) {
                    selectAllButton.setText("☐ Снять все");
                } else {
                    selectAllButton.setText("☑️ Выбрать все");
                }
            });
            
            mainLayout.addView(checkBox);
            
            // Разделитель между сетями
            View netDivider = new View(this);
            netDivider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
            netDivider.setBackgroundColor(0xFFEEEEEE);
            mainLayout.addView(netDivider);
        }
        
        scrollView.addView(mainLayout);
        builder.setView(scrollView);
        
        // Кнопка удаления выбранных
        builder.setPositiveButton("🗑️ Удалить выбранные", (dialog, which) -> {
            if (selectedForDeletion.isEmpty()) {
                Toast.makeText(this, "Не выбрано ни одной сети для удаления", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Подтверждение удаления
            new AlertDialog.Builder(this)
                .setTitle("Подтверждение удаления")
                .setMessage("Удалить " + selectedForDeletion.size() + " сетей?")
                .setPositiveButton("Удалить", (d, w) -> {
                    // Удаляем сети (в обратном порядке чтобы не сбились индексы)
                    List<Integer> sortedPositions = new ArrayList<>(selectedForDeletion);
                    sortedPositions.sort((a, b) -> b.compareTo(a)); // Сортируем по убыванию
                    
                    int deletedCount = 0;
                    for (int pos : sortedPositions) {
                        if (pos < networks.size()) {
                            networks.remove(pos);
                            deletedCount++;
                        }
                    }
                    
                    adapter.notifyDataSetChanged();
                    saveNetworks();
                    
                    // ЗАКРЫВАЕМ ДИАЛОГ УПРАВЛЕНИЯ
                    dialog.dismiss();
                    
                    Toast.makeText(this, 
                        "✅ Удалено сетей: " + deletedCount + "\n\nОсталось фейковых сетей: " + networks.size(), 
                        Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
        });
        
        builder.setNegativeButton("Закрыть", null);
        
        final AlertDialog dialog = builder.create();
        dialog.show();
        
        // Настраиваем размер диалога
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
    }
    
    // Адаптер для списка
    private class NetworkAdapter extends BaseAdapter {
        private Context context;
        private List<FakeNetwork> networks;
        
        public NetworkAdapter(Context context, List<FakeNetwork> networks) {
            this.context = context;
            this.networks = networks;
        }
        
        @Override
        public int getCount() {
            return networks.size();
        }
        
        @Override
        public Object getItem(int position) {
            return networks.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(
                    android.R.layout.simple_list_item_2, parent, false);
            }
            
            FakeNetwork network = networks.get(position);
            
            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);
            
            text1.setText(network.ssid);
            text2.setText(String.format("MAC: %s | Сигнал: %d dBm | %s MHz",
                network.bssid, network.level, network.frequency));
            
            return convertView;
        }
    }
    
    // Класс для хранения сети
    private static class FakeNetwork {
        String ssid;
        String bssid;
        int level;
        int frequency;
        String capabilities;
        
        FakeNetwork(String ssid, String bssid, int level, int frequency, String capabilities) {
            this.ssid = ssid;
            this.bssid = bssid;
            this.level = level;
            this.frequency = frequency;
            this.capabilities = capabilities;
        }
    }
    
    // Класс для хранения реальных WiFi сетей
    private static class RealWifiNetwork {
        String ssid;
        String bssid;
        int level;
        double frequency;
        
        RealWifiNetwork(String ssid, String bssid, int level, double frequency) {
            this.ssid = ssid;
            this.bssid = bssid;
            this.level = level;
            this.frequency = frequency;
        }
    }
}
