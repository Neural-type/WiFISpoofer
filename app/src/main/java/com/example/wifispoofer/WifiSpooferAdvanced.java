package com.example.wifispoofer;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Расширенная версия WiFi Spoofer с дополнительными возможностями:
 * - Динамическая генерация сетей
 * - Симуляция изменения силы сигнала
 * - Поддержка географической привязки
 * - Более реалистичное поведение
 */
public class WifiSpooferAdvanced implements IXposedHookLoadPackage {

    private static final String PREFS_NAME = "WifiSpooferPrefs";
    private static final String KEY_NETWORKS = "fake_networks";
    private static final String KEY_DYNAMIC_MODE = "dynamic_mode";
    private static final String KEY_SIGNAL_VARIATION = "signal_variation";
    
    private static boolean dynamicMode = false;
    private static boolean signalVariation = true;
    private static Random random = new Random();
    private static long lastScanTime = 0;
    
    // Дефолтные сети
    private static final FakeNetwork[] DEFAULT_NETWORKS = {
        new FakeNetwork("Starbucks WiFi", generateRandomMAC(), -60, 2437, "[ESS]"),
        new FakeNetwork("Airport_Public", generateRandomMAC(), -70, 5180, "[ESS]"),
        new FakeNetwork("Hotel_Guest", generateRandomMAC(), -55, 2412, "[WPA2-PSK-CCMP][ESS]"),
        new FakeNetwork("CafeLibre", generateRandomMAC(), -65, 2462, "[WPA-PSK-CCMP][ESS]"),
        new FakeNetwork("Mall_WiFi", generateRandomMAC(), -75, 5200, "[ESS]")
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // КРИТИЧЕСКИ ВАЖНО: НЕ устанавливаем хуки для самого WiFiSpoofer!
        if ("com.example.wifispoofer".equals(lpparam.packageName)) {
            return; // НЕ устанавливаем хуки!
        }
        
        // Для всех остальных приложений устанавливаем хуки
        XposedBridge.log("WiFiSpoofer: ✓ Хукаем приложение: " + lpparam.packageName);
        hookWifiManager(lpparam);
        hookLocationServices(lpparam);
    }

    private void hookWifiManager(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Хук getScanResults()
            XposedHelpers.findAndHookMethod(
                WifiManager.class,
                "getScanResults",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Загружаем настройки
                        loadSettings();
                        
                        // Создаем фейковые сети
                        List<ScanResult> fakeScanResults = createFakeScanResults();
                        
                        if (fakeScanResults.isEmpty()) {
                            XposedBridge.log("WiFiSpoofer [" + lpparam.packageName + "]: ⚠️ Нет фейковых сетей, используем дефолтные");
                            // Если нет сохраненных сетей, используем дефолтные
                            for (FakeNetwork network : DEFAULT_NETWORKS) {
                                try {
                                    fakeScanResults.add(createScanResult(network));
                                } catch (Exception e) {
                                    XposedBridge.log("WiFiSpoofer: Ошибка создания дефолтной сети: " + e.getMessage());
                                }
                            }
                        }
                        
                        // Если динамический режим, добавляем вариацию
                        if (dynamicMode) {
                            fakeScanResults = addDynamicVariation(fakeScanResults);
                        }
                        
                        XposedBridge.log("WiFiSpoofer [" + lpparam.packageName + "]: 🎭 Подменено " + fakeScanResults.size() + " фейковых сетей");
                        param.setResult(fakeScanResults);
                    }
                }
            );

            // Хук startScan()
            XposedHelpers.findAndHookMethod(
                WifiManager.class,
                "startScan",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("WiFiSpoofer [" + lpparam.packageName + "]: startScan перехвачен");
                        lastScanTime = System.currentTimeMillis();
                        param.setResult(true);
                    }
                }
            );

            // Хук isWifiEnabled()
            XposedHelpers.findAndHookMethod(
                WifiManager.class,
                "isWifiEnabled",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Всегда возвращаем true чтобы приложения думали что WiFi включен
                        param.setResult(true);
                    }
                }
            );

            // Хук getWifiState()
            XposedHelpers.findAndHookMethod(
                WifiManager.class,
                "getWifiState",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // WifiManager.WIFI_STATE_ENABLED = 3
                        param.setResult(3);
                    }
                }
            );

        } catch (Throwable t) {
            XposedBridge.log("WiFiSpoofer Error: " + t.getMessage());
        }
    }

    private void hookLocationServices(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Можно добавить хуки для LocationManager чтобы синхронизировать
            // фейковые WiFi сети с фейковой геолокацией
            
            // Пример: хук для getLastKnownLocation
            Class<?> locationManagerClass = XposedHelpers.findClass(
                "android.location.LocationManager",
                lpparam.classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                locationManagerClass,
                "getLastKnownLocation",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Можно подменить локацию чтобы она соответствовала WiFi сетям
                        // Location fakeLocation = createFakeLocation();
                        // param.setResult(fakeLocation);
                    }
                }
            );

        } catch (Throwable t) {
            XposedBridge.log("WiFiSpoofer Location Hook Error: " + t.getMessage());
        }
    }

    private void loadSettings() {
        try {
            Context context = AndroidAppHelper.currentApplication();
            if (context == null) {
                XposedBridge.log("WiFiSpoofer: Контекст не найден в loadSettings");
                return;
            }
            
            SharedPreferences prefs = null;
            
            // Если мы уже внутри нашего приложения, используем напрямую
            if ("com.example.wifispoofer".equals(context.getPackageName())) {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                XposedBridge.log("WiFiSpoofer: Читаем настройки напрямую из нашего приложения");
            } else {
                // Для других приложений создаем PackageContext
                try {
                    Context moduleContext = context.createPackageContext(
                        "com.example.wifispoofer",
                        Context.CONTEXT_IGNORE_SECURITY
                    );
                    prefs = moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
                    XposedBridge.log("WiFiSpoofer: Читаем настройки через PackageContext из " + context.getPackageName());
                } catch (Exception e) {
                    XposedBridge.log("WiFiSpoofer: Ошибка создания PackageContext: " + e.getMessage());
                    return;
                }
            }
            dynamicMode = prefs.getBoolean(KEY_DYNAMIC_MODE, false);
            signalVariation = prefs.getBoolean(KEY_SIGNAL_VARIATION, true);
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: Ошибка загрузки настроек");
        }
    }

    private List<ScanResult> createFakeScanResults() {
        List<ScanResult> fakeResults = new ArrayList<>();
        List<FakeNetwork> networks = loadNetworksFromPrefs();
        
        if (networks == null || networks.isEmpty()) {
            for (FakeNetwork network : DEFAULT_NETWORKS) {
                try {
                    ScanResult result = createScanResult(network);
                    fakeResults.add(result);
                } catch (Exception e) {
                    XposedBridge.log("WiFiSpoofer: Ошибка создания ScanResult: " + e.getMessage());
                }
            }
        } else {
            for (FakeNetwork network : networks) {
                try {
                    ScanResult result = createScanResult(network);
                    fakeResults.add(result);
                } catch (Exception e) {
                    XposedBridge.log("WiFiSpoofer: Ошибка создания ScanResult: " + e.getMessage());
                }
            }
        }
        
        return fakeResults;
    }

    private List<ScanResult> addDynamicVariation(List<ScanResult> results) {
        // Добавляем реалистичные вариации:
        // 1. Небольшие изменения уровня сигнала
        // 2. Иногда сети "исчезают" и "появляются"
        // 3. Добавляем новые случайные сети
        
        List<ScanResult> variedResults = new ArrayList<>();
        
        for (ScanResult result : results) {
            // 90% вероятность что сеть останется
            if (random.nextInt(100) < 90) {
                if (signalVariation) {
                    // Варьируем сигнал на ±5 dBm
                    result.level += (random.nextInt(11) - 5);
                }
                variedResults.add(result);
            }
        }
        
        // 30% вероятность добавить новую случайную сеть
        if (random.nextInt(100) < 30) {
            try {
                FakeNetwork randomNetwork = generateRandomNetwork();
                variedResults.add(createScanResult(randomNetwork));
            } catch (Exception e) {
                // Ignore
            }
        }
        
        return variedResults;
    }

    private FakeNetwork generateRandomNetwork() {
        String[] prefixes = {"Free", "Guest", "Public", "Open", "WiFi", "Network"};
        String[] suffixes = {"Net", "WiFi", "Access", "Zone", "Spot", "Connect"};
        
        String ssid = prefixes[random.nextInt(prefixes.length)] + 
                     "_" + 
                     suffixes[random.nextInt(suffixes.length)];
        
        String bssid = generateRandomMAC();
        int level = -40 - random.nextInt(50); // -40 to -90
        
        int[] frequencies = {2412, 2437, 2462, 5180, 5200, 5745};
        int frequency = frequencies[random.nextInt(frequencies.length)];
        
        String[] caps = {"[ESS]", "[WPA2-PSK-CCMP][ESS]", "[WPA-PSK-CCMP][ESS]"};
        String capabilities = caps[random.nextInt(caps.length)];
        
        return new FakeNetwork(ssid, bssid, level, frequency, capabilities);
    }

    private List<FakeNetwork> loadNetworksFromPrefs() {
        List<FakeNetwork> networks = new ArrayList<>();
        
        try {
            Context context = AndroidAppHelper.currentApplication();
            if (context == null) return networks;
            
            Context moduleContext = context.createPackageContext(
                "com.example.wifispoofer",
                Context.CONTEXT_IGNORE_SECURITY
            );
            
            SharedPreferences prefs = moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
            String json = prefs.getString(KEY_NETWORKS, null);
            
            if (json != null) {
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
            }
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: Ошибка загрузки настроек: " + e.getMessage());
        }
        
        return networks;
    }

    private ScanResult createScanResult(FakeNetwork network) throws Exception {
        return createScanResult(
            network.ssid,
            network.bssid,
            network.level,
            network.frequency,
            network.capabilities
        );
    }

    private ScanResult createScanResult(String ssid, String bssid, int level, int frequency, String capabilities) throws Exception {
        ScanResult scanResult = new ScanResult();
        
        scanResult.SSID = ssid;
        scanResult.BSSID = bssid;
        scanResult.level = level;
        scanResult.frequency = frequency;
        scanResult.capabilities = capabilities;
        scanResult.timestamp = System.currentTimeMillis() * 1000;
        
        // Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                setField(scanResult, "channelWidth", 2);
                setField(scanResult, "centerFreq0", frequency);
                setField(scanResult, "centerFreq1", 0);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Android 9.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                setField(scanResult, "wifiStandard", 5);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        return scanResult;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    private static String generateRandomMAC() {
        StringBuilder mac = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) mac.append(":");
            mac.append(String.format("%02X", random.nextInt(256)));
        }
        return mac.toString();
    }

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
