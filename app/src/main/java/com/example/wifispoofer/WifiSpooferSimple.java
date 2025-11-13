package com.example.wifispoofer;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * WiFi Spoofer v10.0 - ГЛОБАЛЬНЫЙ РЕЖИМ
 * 
 * ═══════════════════════════════════════════════════════════════
 * ВАЖНО: Scope в arrays.xml теперь ТОЛЬКО "android"!
 * ═══════════════════════════════════════════════════════════════
 * 
 * Преимущества глобального режима:
 * ✅ Работает для ВСЕХ приложений автоматически
 * ✅ НЕ нужно добавлять каждое приложение в scope
 * ✅ Settings, Maps, Chrome - все видят фейковые сети
 * ✅ WiFiSpoofer приложение видит РЕАЛЬНЫЕ сети (для сканирования)
 * 
 * Архитектура:
 * - Хукаем WifiManager на уровне System Framework (android)
 * - Исключаем пакет "com.example.wifispoofer" из хуков
 * - Все остальные приложения получают фейковые сети
 * 
 * Конфигурация:
 * - Приложение: /data/data/com.example.wifispoofer/files/wifispoofer_config.json
 * - Модуль: /data/local/tmp/wifispoofer_config.json (автокопирование при запуске)
 */
public class WifiSpooferSimple implements IXposedHookLoadPackage {

    // Используем файл вместо SharedPreferences (Android 10+ блокирует XSharedPreferences)
    // /data/local/tmp/ доступен ВСЕМ процессам без ограничений SELinux!
    // System_server может читать, приложение может писать БЕЗ разрешений
    private static final String CONFIG_FILE = "/data/local/tmp/wifispoofer_config.json";
    
    private static Random random = new Random();
    private static long lastUpdateTime = 0;
    private static Set<String> hiddenNetworks = new HashSet<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // ═══════════════════════════════════════════════════════════════
        // v10.0 ГЛОБАЛЬНЫЙ РЕЖИМ - КРИТИЧЕСКАЯ ПРОВЕРКА!
        // ═══════════════════════════════════════════════════════════════
        
        // НЕ хукаем само приложение WiFiSpoofer!
        // Это позволяет приложению видеть РЕАЛЬНЫЕ сети для сканирования
        if ("com.example.wifispoofer".equals(lpparam.packageName)) {
            XposedBridge.log("════════════════════════════════════════════════");
            XposedBridge.log("WiFiSpoofer: ПРОПУСКАЕМ WiFiSpoofer приложение");
            XposedBridge.log("WiFiSpoofer: Оно будет видеть реальные сети!");
            XposedBridge.log("════════════════════════════════════════════════");
            return; // Выходим БЕЗ установки хуков!
        }
        
        // ═══════════════════════════════════════════════════════════════
        // Все остальные приложения ХУКАЕМ
        // ═══════════════════════════════════════════════════════════════
        
        XposedBridge.log("════════════════════════════════════════════════");
        XposedBridge.log("WiFiSpoofer: МОДУЛЬ ЗАГРУЖЕН!");
        XposedBridge.log("WiFiSpoofer: Пакет: " + lpparam.packageName);
        XposedBridge.log("WiFiSpoofer: Режим: ГЛОБАЛЬНЫЙ (v10.0)");
        XposedBridge.log("════════════════════════════════════════════════");
        
        try {
            // Хук getScanResults() - главный метод получения WiFi сетей
            XposedHelpers.findAndHookMethod(
                WifiManager.class,
                "getScanResults",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("WiFiSpoofer: ★★★★★ getScanResults() ПЕРЕХВАЧЕН! ★★★★★");
                        
                        // Получаем оригинальные результаты
                        List<ScanResult> originalResults = (List<ScanResult>) param.getResult();
                        XposedBridge.log("WiFiSpoofer: Оригинальных сетей: " + 
                            (originalResults != null ? originalResults.size() : 0));
                        
                        // Загружаем настройки из файла
                        JSONObject config = loadConfigFromFile();
                        
                        boolean hideReal = config.optBoolean("hide_real_networks", false);
                        boolean randomSignal = config.optBoolean("random_signal", true);
                        boolean dynamicMode = config.optBoolean("dynamic_mode", false);
                        String blockedSSIDs = config.optString("blocked_ssids", "");
                        
                        XposedBridge.log("WiFiSpoofer: Настройки - Скрыть реальные: " + hideReal + 
                            ", Рандом сигнал: " + randomSignal + ", Динамический: " + dynamicMode);
                        
                        // Создаем финальный список
                        List<ScanResult> finalResults = new ArrayList<>();
                        
                        // Добавляем реальные сети (если не скрываем)
                        if (!hideReal && originalResults != null) {
                            // Фильтруем заблокированные
                            Set<String> blockedSet = parseBlockedSSIDs(blockedSSIDs);
                            for (ScanResult result : originalResults) {
                                if (!blockedSet.contains(result.SSID)) {
                                    finalResults.add(result);
                                } else {
                                    XposedBridge.log("WiFiSpoofer:   ✗ Заблокирована: " + result.SSID);
                                }
                            }
                        }
                        
                        // Добавляем фейковые сети
                        List<FakeNetwork> fakeNetworks = loadNetworksFromConfig(config);
                        XposedBridge.log("WiFiSpoofer: 🎭 Фейковых сетей для добавления: " + fakeNetworks.size());
                        
                        if (dynamicMode) {
                            fakeNetworks = applyDynamicMode(fakeNetworks);
                            XposedBridge.log("WiFiSpoofer: 🎭 После динамического режима: " + fakeNetworks.size());
                        }
                        
                        int addedCount = 0;
                        for (FakeNetwork network : fakeNetworks) {
                            try {
                                int level = network.level;
                                if (randomSignal) {
                                    level = network.level + random.nextInt(11) - 5; // ±5 dBm
                                }
                                
                                ScanResult result = createScanResult(
                                    network.ssid,
                                    network.bssid,
                                    level,
                                    network.frequency,
                                    network.capabilities
                                );
                                finalResults.add(result);
                                addedCount++;
                                XposedBridge.log("WiFiSpoofer:   + " + network.ssid + " (" + level + " dBm)");
                            } catch (Exception e) {
                                XposedBridge.log("WiFiSpoofer: ✗ Ошибка создания " + network.ssid + ": " + e.getMessage());
                            }
                        }
                        
                        XposedBridge.log("WiFiSpoofer: ✓ Успешно добавлено фейковых сетей: " + addedCount);
                        param.setResult(finalResults);
                        XposedBridge.log("WiFiSpoofer: ✓✓✓ ИТОГО СЕТЕЙ: " + finalResults.size() + 
                            " (реальных: " + (finalResults.size() - addedCount) + ", фейковых: " + addedCount + ") ✓✓✓");
                    }
                }
            );
            XposedBridge.log("WiFiSpoofer: ✓ Хук getScanResults установлен");

            // Хук startScan()
            XposedHelpers.findAndHookMethod(
                WifiManager.class,
                "startScan",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(true);
                    }
                }
            );
            XposedBridge.log("WiFiSpoofer: ✓ Хук startScan установлен");
            
            XposedBridge.log("WiFiSpoofer: ✓✓✓ ВСЕ ХУКИ УСТАНОВЛЕНЫ УСПЕШНО ✓✓✓");

        } catch (Throwable t) {
            XposedBridge.log("WiFiSpoofer: ✗✗✗ ОШИБКА: " + t.getMessage());
            XposedBridge.log("WiFiSpoofer: Stack trace: " + android.util.Log.getStackTraceString(t));
        }
    }
    
    private JSONObject loadConfigFromFile() {
        try {
            java.io.File configFile = new java.io.File(CONFIG_FILE);
            if (!configFile.exists()) {
                XposedBridge.log("WiFiSpoofer: ⚠ Файл конфигурации не найден: " + CONFIG_FILE);
                return new JSONObject();
            }
            
            // Читаем файл
            java.io.FileInputStream fis = new java.io.FileInputStream(configFile);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            fis.close();
            
            String json = sb.toString();
            XposedBridge.log("WiFiSpoofer: ✓ Конфигурация загружена, размер: " + json.length() + " bytes");
            
            return new JSONObject(json);
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: ✗ Ошибка чтения конфигурации: " + e.getMessage());
            XposedBridge.log("WiFiSpoofer: Stack: " + android.util.Log.getStackTraceString(e));
            return new JSONObject();
        }
    }
    
    private List<FakeNetwork> loadNetworksFromConfig(JSONObject config) {
        List<FakeNetwork> networks = new ArrayList<>();
        
        try {
            if (config.has("fake_networks")) {
                JSONArray jsonArray = config.getJSONArray("fake_networks");
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
                XposedBridge.log("WiFiSpoofer: ✓ Загружено " + networks.size() + " сетей из конфигурации");
            } else {
                XposedBridge.log("WiFiSpoofer: ⚠ Нет fake_networks в конфигурации");
            }
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: ✗ Ошибка загрузки сетей: " + e.getMessage());
        }
        
        // Если пусто - используем дефолтные
        if (networks.isEmpty()) {
            XposedBridge.log("WiFiSpoofer: ⚠ Список пуст! Добавляем дефолтные сети");
            networks.add(new FakeNetwork("FAKE_Home_5G", generateRandomMAC(), -45, 5180, "[WPA2-PSK-CCMP][ESS]"));
            networks.add(new FakeNetwork("FAKE_Starbucks", generateRandomMAC(), -60, 2437, "[ESS]"));
            networks.add(new FakeNetwork("FAKE_Airport_WiFi", generateRandomMAC(), -55, 2412, "[ESS]"));
            XposedBridge.log("WiFiSpoofer: ✓ Добавлено " + networks.size() + " дефолтных сетей");
        }
        
        return networks;
    }
    
    private List<ScanResult> createDefaultNetworks() {
        List<ScanResult> results = new ArrayList<>();
        
        try {
            results.add(createScanResult("FAKE_Home_5G", generateRandomMAC(), -45, 5180, "[WPA2-PSK-CCMP][ESS]"));
            results.add(createScanResult("FAKE_Starbucks", generateRandomMAC(), -60, 2437, "[ESS]"));
        } catch (Exception e) {
            XposedBridge.log("WiFiSpoofer: Ошибка создания дефолтных сетей: " + e.getMessage());
        }
        
        return results;
    }
    
    private Set<String> parseBlockedSSIDs(String blockedSSIDs) {
        Set<String> blocked = new HashSet<>();
        if (blockedSSIDs != null && !blockedSSIDs.isEmpty()) {
            String[] ssids = blockedSSIDs.split(",");
            for (String ssid : ssids) {
                blocked.add(ssid.trim());
            }
        }
        return blocked;
    }
    
    private List<FakeNetwork> applyDynamicMode(List<FakeNetwork> networks) {
        long currentTime = System.currentTimeMillis();
        
        // Обновляем каждые 30 секунд
        if (currentTime - lastUpdateTime > 30000) {
            lastUpdateTime = currentTime;
            
            // Случайно скрываем/показываем сети
            hiddenNetworks.clear();
            for (FakeNetwork network : networks) {
                if (random.nextInt(100) < 20) { // 20% шанс скрыть
                    hiddenNetworks.add(network.ssid);
                    XposedBridge.log("WiFiSpoofer:   ⊘ Скрыта (динамический режим): " + network.ssid);
                }
            }
        }
        
        // Фильтруем скрытые сети
        List<FakeNetwork> visible = new ArrayList<>();
        for (FakeNetwork network : networks) {
            if (!hiddenNetworks.contains(network.ssid)) {
                visible.add(network);
            }
        }
        
        return visible;
    }

    private ScanResult createScanResult(String ssid, String bssid, int level, int frequency, String capabilities) throws Exception {
        ScanResult scanResult = new ScanResult();
        
        scanResult.SSID = ssid;
        scanResult.BSSID = bssid;
        scanResult.level = level;
        scanResult.frequency = frequency;
        scanResult.capabilities = capabilities;
        scanResult.timestamp = System.currentTimeMillis() * 1000;
        
        // Для Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Field channelWidth = ScanResult.class.getDeclaredField("channelWidth");
                channelWidth.setAccessible(true);
                channelWidth.set(scanResult, 2);
                
                Field centerFreq0 = ScanResult.class.getDeclaredField("centerFreq0");
                centerFreq0.setAccessible(true);
                centerFreq0.set(scanResult, frequency);
                
                Field centerFreq1 = ScanResult.class.getDeclaredField("centerFreq1");
                centerFreq1.setAccessible(true);
                centerFreq1.set(scanResult, 0);
            } catch (NoSuchFieldException e) {
                // Игнорируем
            }
        }
        
        return scanResult;
    }
    
    private static String generateRandomMAC() {
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
            random.nextInt(256), random.nextInt(256), random.nextInt(256),
            random.nextInt(256), random.nextInt(256), random.nextInt(256));
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
