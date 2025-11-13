package com.example.wifispoofer;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WifiSpoofer implements IXposedHookLoadPackage {

    private static final String PREFS_NAME = "WifiSpooferPrefs";
    private static final String KEY_NETWORKS = "fake_networks";
    private static final String KEY_REAL_NETWORKS = "real_networks_cache";
    private static final String KEY_BYPASS_SPOOF = "bypass_spoof_mode";
    private static final String KEY_BLOCKED_SSIDS = "blocked_ssids";
    private static final String KEY_HIDE_REAL = "hide_real_networks";
    private static final String KEY_WHITELIST_MODE = "whitelist_mode";
    private static final String KEY_MODULE_ENABLED = "module_enabled";
    private static final String KEY_SCANNING_MODE = "scanning_mode";
    
    // Дефолтные сети на случай если настройки не загрузились
    private static final FakeNetwork[] DEFAULT_NETWORKS = {
        new FakeNetwork("Home_Network_5G", "12:34:56:78:9A:BC", -45, 5180, "[WPA2-PSK-CCMP][ESS]"),
        new FakeNetwork("Starbucks WiFi", "AA:BB:CC:DD:EE:FF", -60, 2437, "[ESS]"),
        new FakeNetwork("CoffeeShop_Guest", "11:22:33:44:55:66", -55, 2412, "[WPA-PSK-CCMP][ESS]"),
        new FakeNetwork("Mall_Public", "FF:EE:DD:CC:BB:AA", -70, 2462, "[ESS]"),
        new FakeNetwork("Hotel_Lobby", "AB:CD:EF:12:34:56", -65, 5200, "[WPA2-PSK-CCMP][ESS]")
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Логируем загрузку модуля
        XposedBridge.log("═══════════════════════════════════════");
        XposedBridge.log("WiFiSpoofer: Модуль загружен!");
        XposedBridge.log("WiFiSpoofer: VERSION 3.0 FINAL");
        XposedBridge.log("WiFiSpoofer: Пакет = " + lpparam.packageName);
        
        // ВАЖНО: НЕ перехватываем вызовы от самого WiFiSpoofer!
        if ("com.example.wifispoofer".equals(lpparam.packageName)) {
            XposedBridge.log("WiFiSpoofer: ⚠️⚠️⚠️ Это наше приложение - хуки НЕ устанавливаются! ⚠️⚠️⚠️");
            XposedBridge.log("WiFiSpoofer: 🎉🎉🎉 Приложение будет видеть РЕАЛЬНЫЕ сети! 🎉🎉🎉");
            XposedBridge.log("═══════════════════════════════════════");
            return; // НЕ устанавливаем хуки!
        }
        
        XposedBridge.log("WiFiSpoofer: Это другое приложение - устанавливаем хуки для спуфинга");
        XposedBridge.log("═══════════════════════════════════════");
        
        // Хук для всех ДРУГИХ приложений, использующих WiFi
        hookWifiManager(lpparam);
    }

    private void hookWifiManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log("WiFiSpoofer: Начинаем установку хуков...");
            
            // Хук getScanResults() - основной метод получения списка WiFi сетей
            XposedHelpers.findAndHookMethod(
                WifiManager.class,
                "getScanResults",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("WiFiSpoofer: ★★★★ getScanResults() перехвачен! ★★★★");
                        
                        List<ScanResult> originalResults = (List<ScanResult>) param.getResult();
                        XposedBridge.log("WiFiSpoofer: Оригинальных сетей: " + (originalResults != null ? originalResults.size() : 0));
                        
                        // Сохраняем реальные результаты в кеш ВСЕГДА
                        saveRealNetworks(originalResults);
                        
                        // Проверяем флаг - если приложение сканирует, пропускаем
                        if (isScanning()) {
                            XposedBridge.log("WiFiSpoofer: РЕЖИМ СКАНИРОВАНИЯ - возвращаем оригинальные результаты");
                            return; // Возвращаем оригинальные результаты
                        }
                        
                        // Проверяем включен ли модуль
                        if (!isModuleEnabled()) {
                            XposedBridge.log("WiFiSpoofer: МОДУЛЬ ВЫКЛЮЧЕН - возвращаем оригинальные результаты");
                            return;
                        }
                        
                        XposedBridge.log("WiFiSpoofer: Модуль включен, применяем фильтры");
                        
                        // Получаем настройки
                        boolean hideRealNetworks = getHideRealNetworksSetting();
                        boolean whitelistMode = getWhitelistMode();
                        List<String> blockedSSIDs = getBlockedSSIDs();
                        
                        List<ScanResult> finalResults = new ArrayList<>();
                        
                        // Обрабатываем реальные сети
                        if (originalResults != null && !hideRealNetworks) {
                            for (ScanResult result : originalResults) {
                                boolean shouldBlock = false;
                                
                                if (whitelistMode) {
                                    // Whitelist режим: блокируем все КРОМЕ выбранных
                                    shouldBlock = !blockedSSIDs.contains(result.SSID);
                                } else {
                                    // Blacklist режим: блокируем только выбранные
                                    shouldBlock = blockedSSIDs.contains(result.SSID);
                                }
                                
                                if (!shouldBlock) {
                                    finalResults.add(result);
                                    XposedBridge.log("WiFiSpoofer:   ✓ Разрешена реальная сеть: " + result.SSID);
                                } else {
                                    XposedBridge.log("WiFiSpoofer:   ✗ Заблокирована реальная сеть: " + result.SSID);
                                }
                            }
                        } else if (hideRealNetworks) {
                            XposedBridge.log("WiFiSpoofer: Все реальные сети скрыты (hide_real_networks = true)");
                        }
                        
                        // Добавляем фейковые сети
                        List<ScanResult> fakeScanResults = createFakeScanResults();
                        finalResults.addAll(fakeScanResults);
                        
                        param.setResult(finalResults);
                        XposedBridge.log("WiFiSpoofer: ✓ Итого сетей: " + finalResults.size() + 
                            " (реальных: " + (finalResults.size() - fakeScanResults.size()) + 
                            ", фейковых: " + fakeScanResults.size() + ")");
                    }
                }
            );
            XposedBridge.log("WiFiSpoofer: ✓ Хук getScanResults() установлен");

            // Хук для startScan() - убеждаемся что сканирование "успешно"
            XposedHelpers.findAndHookMethod(
                WifiManager.class,
                "startScan",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("WiFiSpoofer: ★ startScan() перехвачен");
                        param.setResult(true);
                    }
                }
            );
            XposedBridge.log("WiFiSpoofer: ✓ Хук startScan() установлен");

            // Хук для getConfiguredNetworks() если нужно
            XposedHelpers.findAndHookMethod(
                WifiManager.class,
                "getConfiguredNetworks",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("WiFiSpoofer: getConfiguredNetworks() вызван");
                        // Возвращаем пустой список или оригинальный
                        // param.setResult(new ArrayList<>());
                    }
                }
            );
            XposedBridge.log("WiFiSpoofer: ✓ Хук getConfiguredNetworks() установлен");
            
            XposedBridge.log("WiFiSpoofer: ✓✓✓ ВСЕ ХУКИ УСПЕШНО УСТАНОВЛЕНЫ ✓✓✓");

        } catch (Throwable t) {
            XposedBridge.log("WiFiSpoofer ERROR: " + t.getMessage());
            XposedBridge.log("WiFiSpoofer ERROR Stack: " + android.util.Log.getStackTraceString(t));
        }
    }

    private List<ScanResult> createFakeScanResults() {
        List<ScanResult> fakeResults = new ArrayList<>();
        
        XposedBridge.log("WiFiSpoofer: Создаем фейковые сети...");
        
        // Попытка загрузить сети из SharedPreferences
        List<FakeNetwork> networks = loadNetworksFromPrefs();
        
        // Если не удалось загрузить, используем дефолтные
        if (networks == null || networks.isEmpty()) {
            XposedBridge.log("WiFiSpoofer: ⚠ Используем дефолтные сети (настройки не загружены)");
            for (FakeNetwork network : DEFAULT_NETWORKS) {
                try {
                    ScanResult result = createScanResult(
                        network.ssid,
                        network.bssid,
                        network.level,
                        network.frequency,
                        network.capabilities
                    );
                    fakeResults.add(result);
                    XposedBridge.log("WiFiSpoofer:   + Добавлена сеть: " + network.ssid + " (" + network.bssid + ")");
                } catch (Exception e) {
                    XposedBridge.log("WiFiSpoofer: ✗ Ошибка создания " + network.ssid + ": " + e.getMessage());
                }
            }
        } else {
            XposedBridge.log("WiFiSpoofer: ✓ Загружено " + networks.size() + " сетей из настроек");
            for (FakeNetwork network : networks) {
                try {
                    ScanResult result = createScanResult(
                        network.ssid,
                        network.bssid,
                        network.level,
                        network.frequency,
                        network.capabilities
                    );
                    fakeResults.add(result);
                    XposedBridge.log("WiFiSpoofer:   + " + network.ssid + " (" + network.bssid + ", " + network.level + "dBm)");
                } catch (Exception e) {
                    XposedBridge.log("WiFiSpoofer: ✗ Ошибка: " + e.getMessage());
                }
            }
        }
        
        XposedBridge.log("WiFiSpoofer: Всего создано сетей: " + fakeResults.size());
        return fakeResults;
    }
    
    private List<FakeNetwork> loadNetworksFromPrefs() {
        List<FakeNetwork> networks = new ArrayList<>();
        
        try {
            // Используем XSharedPreferences для чтения настроек из WiFiSpoofer
            XposedBridge.log("WiFiSpoofer: 📖 Загрузка настроек через XSharedPreferences...");
            
            XSharedPreferences prefs = new XSharedPreferences("com.example.wifispoofer", PREFS_NAME);
            prefs.makeWorldReadable(); // Делаем файл доступным для чтения
            prefs.reload(); // Перезагружаем данные
            
            String json = prefs.getString(KEY_NETWORKS, null);
            
            if (json == null || json.isEmpty()) {
                XposedBridge.log("WiFiSpoofer: ⚠ Фейковые сети не найдены в настройках");
                return networks;
            }
            
            XposedBridge.log("WiFiSpoofer: ✓ JSON настроек загружен, длина: " + json.length());
            
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                networks.add(new FakeNetwork(
                    jsonObject.getString("ssid"),
                    jsonObject.getString("bssid"),
                    jsonObject.getInt("level"),
                    jsonObject.getInt("frequency"),
                    jsonObject.getString("capabilities")
                ));
            }
            
            XposedBridge.log("WiFiSpoofer: ✅ Успешно загружено фейковых сетей: " + networks.size());
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: ✗ Ошибка загрузки настроек: " + e.getMessage());
            XposedBridge.log("WiFiSpoofer: ✗ Stack trace: " + android.util.Log.getStackTraceString(e));
        }
        
        return networks;
    }
    
    // Универсальный метод для получения XSharedPreferences
    private XSharedPreferences getPrefs() {
        XSharedPreferences prefs = new XSharedPreferences("com.example.wifispoofer", PREFS_NAME);
        prefs.makeWorldReadable();
        prefs.reload();
        return prefs;
    }
    
    private void saveRealNetworks(List<ScanResult> realResults) {
        try {
            Context context = AndroidAppHelper.currentApplication();
            if (context == null) return;
            
            Context moduleContext = context.createPackageContext(
                "com.example.wifispoofer",
                Context.CONTEXT_IGNORE_SECURITY
            );
            
            SharedPreferences prefs = moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            if (realResults != null && !realResults.isEmpty()) {
                JSONArray jsonArray = new JSONArray();
                for (ScanResult result : realResults) {
                    JSONObject obj = new JSONObject();
                    obj.put("ssid", result.SSID);
                    obj.put("bssid", result.BSSID);
                    obj.put("level", result.level);
                    obj.put("frequency", result.frequency);
                    obj.put("capabilities", result.capabilities);
                    jsonArray.put(obj);
                }
                prefs.edit().putString(KEY_REAL_NETWORKS, jsonArray.toString()).apply();
            }
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: Ошибка сохранения реальных сетей: " + e.getMessage());
        }
    }
    
    private boolean isScanning() {
        try {
            boolean scanning = getPrefs().getBoolean(KEY_SCANNING_MODE, false);
            if (scanning) {
                XposedBridge.log("WiFiSpoofer: Флаг SCANNING_MODE = true, пропускаем перехват");
            }
            return scanning;
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: Ошибка проверки scanning_mode: " + e.getMessage());
            return false;
        }
    }
    
    private boolean isModuleEnabled() {
        try {
            return getPrefs().getBoolean(KEY_MODULE_ENABLED, false);
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: Ошибка проверки module_enabled: " + e.getMessage());
            return false;
        }
    }
    
    private boolean getHideRealNetworksSetting() {
        try {
            return getPrefs().getBoolean(KEY_HIDE_REAL, false);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean getWhitelistMode() {
        try {
            return getPrefs().getBoolean(KEY_WHITELIST_MODE, false);
        } catch (Exception e) {
            return false;
        }
    }
    
    private List<String> getBlockedSSIDs() {
        List<String> blocked = new ArrayList<>();
        try {
            String blockedStr = getPrefs().getString(KEY_BLOCKED_SSIDS, "");
            
            if (!blockedStr.isEmpty()) {
                String[] ssids = blockedStr.split(",");
                for (String ssid : ssids) {
                    String trimmed = ssid.trim();
                    if (!trimmed.isEmpty()) {
                        blocked.add(trimmed);
                    }
                }
            }
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: Ошибка загрузки blocked SSIDs: " + e.getMessage());
        }
        return blocked;
    }

    private ScanResult createScanResult(String ssid, String bssid, int level, int frequency, String capabilities) throws Exception {
        ScanResult scanResult = new ScanResult();
        
        // Устанавливаем основные поля
        scanResult.SSID = ssid;
        scanResult.BSSID = bssid;
        scanResult.level = level;
        scanResult.frequency = frequency;
        scanResult.capabilities = capabilities;
        
        // Устанавливаем timestamp
        scanResult.timestamp = System.currentTimeMillis() * 1000; // в микросекундах
        
        // Для Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Field channelWidth = ScanResult.class.getDeclaredField("channelWidth");
                channelWidth.setAccessible(true);
                channelWidth.set(scanResult, 2); // 80 MHz
                
                Field centerFreq0 = ScanResult.class.getDeclaredField("centerFreq0");
                centerFreq0.setAccessible(true);
                centerFreq0.set(scanResult, frequency);
                
                Field centerFreq1 = ScanResult.class.getDeclaredField("centerFreq1");
                centerFreq1.setAccessible(true);
                centerFreq1.set(scanResult, 0);
            } catch (NoSuchFieldException e) {
                // Игнорируем, если поля не существуют
            }
        }
        
        // Для Android 9.0+ (Pie)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                // Добавляем информацию о стандарте WiFi
                Field wifiStandard = ScanResult.class.getDeclaredField("wifiStandard");
                wifiStandard.setAccessible(true);
                wifiStandard.set(scanResult, 5); // WiFi 5 (802.11ac)
            } catch (NoSuchFieldException e) {
                // Игнорируем
            }
        }
        
        return scanResult;
    }

    // Класс для хранения конфигурации фейковой сети
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
}
