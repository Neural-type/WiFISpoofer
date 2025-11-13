# WiFi Spoofer v10.0 - CHANGELOG

## 🎉 v10.0 Global Mode (November 2024)

### 🌟 ГЛАВНОЕ ИЗМЕНЕНИЕ:

**ГЛОБАЛЬНАЯ ПОДМЕНА для ВСЕХ приложений автоматически!**

Теперь не нужно добавлять каждое приложение в LSPosed scope отдельно.

---

## ✨ Что изменилось?

### 1. Scope в LSPosed

**Было (v9.6):**
```xml
<string-array name="xposed_scope">
    <item>android</item>
    <item>system</item>
    <item>com.google.android.gms</item>
    <item>com.android.chrome</item>
    <item>com.google.android.apps.maps</item>
    <item>com.android.settings</item>
    <!-- + нужно было добавлять каждое приложение -->
</string-array>
```

**Стало (v10.0):**
```xml
<string-array name="xposed_scope">
    <item>android</item>  <!-- ТОЛЬКО android! -->
</string-array>
```

### 2. Исключение WiFiSpoofer из хука

**Добавлена проверка в WifiSpooferSimple.java:**

```java
@Override
public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
    // НЕ хукаем само приложение WiFiSpoofer!
    if ("com.example.wifispoofer".equals(lpparam.packageName)) {
        XposedBridge.log("WiFiSpoofer: ПРОПУСКАЕМ WiFiSpoofer приложение");
        return; // Выходим БЕЗ установки хуков!
    }
    
    // Все остальные приложения ХУКАЕМ
    ...
}
```

**Результат:**
- ✅ WiFiSpoofer видит **реальные** сети (для сканирования)
- ✅ ВСЕ остальные приложения видят **фейковые** сети

---

## 🎯 Преимущества v10.0

| Параметр | v9.6 | v10.0 |
|----------|------|-------|
| **Scope настройка** | Каждое приложение | Только `android` |
| **Количество настроек** | 10+ приложений | 1 раз |
| **Settings** | Нужно добавлять | ✅ Работает |
| **Google Maps** | Нужно добавлять | ✅ Работает |
| **Chrome** | Нужно добавлять | ✅ Работает |
| **Любое приложение** | Нужно добавлять | ✅ Работает |
| **WiFiSpoofer** | Видит фейковые | ✅ Видит реальные |

---

## 📋 Инструкция по обновлению

### Шаг 1: Удалите старую версию (опционально)
```bash
adb uninstall com.example.wifispoofer
```

### Шаг 2: Установите v10.0
```bash
adb install WiFiSpoofer-v10.0.apk
```

### Шаг 3: Настройте LSPosed

**КРИТИЧНО:** Удалите все приложения из scope кроме `android`!

1. LSPosed Manager → Модули → WiFi Spoofer
2. Scope → **Удалите ВСЁ** кроме:
   ```
   ✅ android (System Framework)
   ```
3. Должно остаться **ТОЛЬКО** `android`!

### Шаг 4: Перезагрузка
```bash
adb reboot
```

**ГОТОВО!** Все ваши настройки и сети сохранились! 🎉

---

## 🔍 Как проверить что всё работает?

### 1. В WiFi Spoofer:
```bash
Открыть → "Scan Real Networks"
→ Видите реальные сети ✅
```

### 2. В Settings:
```bash
Settings → WiFi
→ Видите ТОЛЬКО фейковые сети ✅
```

### 3. В других приложениях:
```bash
Google Maps, Chrome, WhatsApp, и т.д.
→ Все видят ТОЛЬКО фейковые сети ✅
```

### 4. В логах:
```bash
adb logcat | grep "WiFiSpoofer"

# Должно быть:
WiFiSpoofer: ПРОПУСКАЕМ WiFiSpoofer приложение
WiFiSpoofer: МОДУЛЬ ЗАГРУЖЕН! Пакет: com.android.settings
WiFiSpoofer: Режим: ГЛОБАЛЬНЫЙ (v10.0)
WiFiSpoofer: ✓ Успешно добавлено фейковых сетей: X
```

---

## 🛠️ Технические детали

### Изменённые файлы:

1. **arrays.xml** - Scope теперь только `android`
2. **WifiSpooferSimple.java** - Добавлена проверка package name
3. **build.gradle** - versionCode 10, versionName "10.0"
4. **README.md** - Полностью обновлён

### Архитектура:

```
┌──────────────────────────────────────┐
│   System Framework (android)         │
│   WifiManager.getScanResults()       │
│              ↓                        │
│   Проверка package name:             │
│   • com.example.wifispoofer?         │
│     → ДА: return (без хуков)         │
│     → НЕТ: hook + фейковые сети      │
└──────────────────────────────────────┘
        ↓              ↓              ↓
    Settings       Maps          WiFiSpoofer
  (фейковые)   (фейковые)       (реальные)
```

---

## 🚀 Дальнейшие планы

Возможные улучшения в следующих версиях:

- [ ] Экспорт/импорт конфигураций
- [ ] Профили сетей (домашний, офисный, путешествие)
- [ ] Автоматическое переключение профилей по времени
- [ ] Генератор реалистичных BSSID по геолокации
- [ ] Режим "невидимости" (скрыть само приложение из списка)

---

## 📞 Обратная связь

Нашли баг? Есть предложения?

1. Откройте Issue на GitHub
2. Приложите логи: `adb logcat | grep "WiFiSpoofer"`
3. Укажите версию Android и LSPosed

---

## 📜 История версий

- **v10.0** (Nov 2024) - Глобальный режим
- **v9.6** (Nov 2024) - Автокопирование файла
- **v9.0-9.5** (Nov 2024) - Файловое хранилище
- **v8.0** (2024) - Первая публичная версия

---

**WiFi Spoofer v10.0** - Глобальная подмена WiFi сетей! 🌐
