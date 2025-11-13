# WiFi Spoofer v10.0 - ГЛОБАЛЬНЫЙ РЕЖИМ 🌐

Android приложение для глобального спуфинга WiFi сетей с использованием Xposed Framework.

## ✨ Что нового в v10.0?

**РЕВОЛЮЦИОННОЕ ИЗМЕНЕНИЕ:** Теперь фейковые сети видят **ВСЕ** приложения автоматически!

### Преимущества глобального режима:
- ✅ **Простота**: Настройка один раз - работает везде
- ✅ **Универсальность**: Settings, Maps, Chrome - все видят фейковые сети
- ✅ **Удобство**: Не нужно добавлять каждое приложение в LSPosed
- ✅ **Стабильность**: Один хук на уровне System Framework
- ✅ **Функциональность**: WiFiSpoofer видит реальные сети для сканирования

### Как это работает?

```
┌─────────────────────────────────┐
│  System Framework (android)     │
│  WifiManager.getScanResults()   │
│            ↓                     │
│  Проверка: это WiFiSpoofer?     │
│    ДА → Реальные сети           │
│    НЕТ → Фейковые сети          │
└─────────────────────────────────┘
       ↓           ↓           ↓
   Settings    Maps      Любое приложение
 (фейковые) (фейковые)    (фейковые)
       
       ↓
 WiFiSpoofer (реальные)
```

---

## 🚀 Быстрая установка (5 минут)

### Шаг 1: Установка APK

**Вариант A: Через GitHub Actions (рекомендуется)**
1. Откройте вкладку **Actions** в репозитории
2. Выберите последний успешный запуск (зеленая галочка ✓)
3. Скачайте **wifi-spoofer-v10.0** из раздела Artifacts
4. Или перейдите в **Releases** для скачивания последней версии

**Вариант B: Ручная сборка**
```bash
git clone https://github.com/Neural-type/WiFISpoofer.git
cd WiFISpoofer
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Шаг 2: Настройка LSPosed ⚠️ КРИТИЧНО!

**ВАЖНО:** Scope должен быть **ТОЛЬКО `android`**!

1. Откройте **LSPosed Manager**
2. Перейдите в **Модули**
3. Найдите **WiFi Spoofer**
4. **Включите** модуль
5. Нажмите на **"Настройка области видимости"** (Scope)
6. **ОСТАВЬТЕ ТОЛЬКО:**
   ```
   ✅ android (System Framework)
   ```
7. **УДАЛИТЕ ВСЕ ОСТАЛЬНЫЕ** приложения из scope!
   ```
   ❌ com.android.chrome
   ❌ com.google.android.apps.maps
   ❌ com.android.settings
   ❌ Любые другие приложения
   ```

**Визуальная схема правильной настройки:**
```
╔═══════════════════════════════════╗
║  LSPosed - Область видимости      ║
╠═══════════════════════════════════╣
║                                   ║
║  ☑️ android (System Framework)   ║  ← ТОЛЬКО ЭТО!
║  ☐ com.android.settings          ║  ← НЕ выбирать
║  ☐ com.google.android.apps.maps  ║  ← НЕ выбирать
║  ☐ com.example.wifispoofer       ║  ← НЕ выбирать
║  ☐ ...                            ║
║                                   ║
╚═══════════════════════════════════╝
```

### Шаг 3: Перезагрузка
```bash
adb reboot
```

**ГОТОВО!** Все приложения теперь видят фейковые сети! 🎉

---

## 📱 Использование

### 1. Добавление фейковой сети

1. Откройте **WiFi Spoofer**
2. Нажмите **"+"** (Add Network)
3. Введите:
   - **SSID**: Название сети (например, "Home_WiFi")
   - **BSSID**: MAC адрес (генерируется автоматически)
   - **Signal Level**: Уровень сигнала от -100 до 0 dBm
   - **Frequency**: 2.4 GHz или 5 GHz
   - **Security**: Тип защиты (WPA2, Open, и т.д.)
4. Нажмите **"Save"**

### 2. Проверка работы

**В WiFi Spoofer:**
- Нажмите **"Scan Real Networks"** → Видите **реальные** сети ✅

**В Settings → WiFi:**
- Откройте настройки WiFi → Видите **только фейковые** сети ✅

**В Google Maps:**
- Откройте Maps → Видите **только фейковые** сети ✅

**В любом другом приложении:**
- Все видят **только фейковые** сети ✅

### 3. Настройки модуля

- **Hide Real Networks**: Скрыть реальные сети
- **Random Signal**: Случайное изменение уровня сигнала (±5 dBm)
- **Dynamic Mode**: Динамическое появление/исчезновение сетей
- **Block SSIDs**: Заблокировать конкретные реальные сети (через запятую)

---

## 🏗️ Архитектура v10.0

### Файловая структура:

```
/data/data/com.example.wifispoofer/files/
  └── wifispoofer_config.json          ← Основное хранилище (приложение)
  
/data/local/tmp/
  └── wifispoofer_config.json          ← Временная копия (модуль читает)
```

### Как работает автокопирование:

1. **Приложение запускается** → Сохраняет настройки в `/data/data/.../files/`
2. **Автоматически копирует** через root в `/data/local/tmp/` (v9.6+)
3. **Модуль читает** из `/data/local/tmp/wifispoofer_config.json`
4. **После reboot** `/data/local/tmp/` очищается → при следующем запуске приложения файл копируется заново

### Формат JSON:

```json
{
  "fake_networks": [
    {
      "ssid": "Home_WiFi",
      "bssid": "AA:BB:CC:DD:EE:FF",
      "level": -50,
      "frequency": 5180,
      "capabilities": "[WPA2-PSK-CCMP][ESS]"
    }
  ],
  "hide_real_networks": false,
  "random_signal": true,
  "dynamic_mode": false,
  "blocked_ssids": "TP-Link,Guest-WiFi"
}
```

---

## 🔍 Устранение проблем

### Проблема: В Settings видны реальные сети

**Причина:** Неправильный scope в LSPosed

**Решение:**
1. LSPosed Manager → Модули → WiFi Spoofer
2. Scope → **Удалите все** кроме `android`
3. Перезагрузите устройство

### Проблема: WiFi Spoofer не видит реальные сети

**Причина:** Отсутствуют разрешения или GPS выключен

**Решение:**
1. Settings → Apps → WiFi Spoofer → Permissions
2. Location: **Allow all the time**
3. Включите GPS/Location
4. Включите WiFi

### Проблема: Модуль не работает после перезагрузки

**Причина:** Файл в `/data/local/tmp/` удалился

**Решение:**
```bash
# Просто ОТКРОЙТЕ приложение WiFi Spoofer!
# Файл автоматически скопируется заново (v9.6+)
```

### Проблема: Логи показывают ошибку чтения файла

**Решение:**
```bash
# Проверьте файл
adb shell cat /data/local/tmp/wifispoofer_config.json

# Если пуст или не существует - откройте приложение
# Оно автоматически создаст и скопирует файл
```

### Просмотр логов:
```bash
adb logcat | grep "WiFiSpoofer"
```

**Правильные логи:**
```
WiFiSpoofer: ПРОПУСКАЕМ WiFiSpoofer приложение
WiFiSpoofer: МОДУЛЬ ЗАГРУЖЕН! Пакет: com.android.settings
WiFiSpoofer: Режим: ГЛОБАЛЬНЫЙ (v10.0)
WiFiSpoofer: ✓ Конфигурация загружена
WiFiSpoofer: ✓ Успешно добавлено фейковых сетей: 3
WiFiSpoofer: [com.android.settings] Returned 3 fake networks
```

---

## 📋 Требования

- **Android:** 7.0+ (API 24+)
- **Root:** Обязательно (для копирования файла)
- **LSPosed/Xposed:** Обязательно
- **Разрешения:**
  - `ACCESS_FINE_LOCATION` (для сканирования реальных сетей)
  - `ACCESS_WIFI_STATE` (для работы с WiFi)
  - `CHANGE_WIFI_STATE` (для управления WiFi)

---

## 📦 Ручная сборка

### На Windows:

```bash
# 1. Установите Android Studio и JDK 11+

# 2. Клонируйте репозиторий
git clone https://github.com/Neural-type/WiFISpoofer.git
cd WiFISpoofer

# 3. Создайте local.properties
echo sdk.dir=C:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk > local.properties

# 4. Соберите
gradlew assembleDebug

# APK будет в: app/build/outputs/apk/debug/app-debug.apk
```

### На Linux/macOS:

```bash
sudo apt-get install -y openjdk-17-jdk android-sdk

git clone https://github.com/Neural-type/WiFISpoofer.git
cd WiFISpoofer
chmod +x gradlew
./gradlew assembleDebug

# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 📝 Changelog

### v10.0 Global Mode (2024)
- ✨ **ГЛОБАЛЬНЫЙ РЕЖИМ**: Работает для всех приложений автоматически
- 🎯 Scope теперь только `android` - не нужно добавлять приложения
- 🔧 WiFiSpoofer видит реальные сети (исключён из хука)
- 📚 Обновлена документация
- ⚡ Улучшенная производительность

### v9.6 Autocopy (2024)
- 🔄 Автоматическое копирование файла при каждом запуске
- 🛠️ Решена проблема с reboot (файл восстанавливается)

### v9.0-v9.5
- 📁 Переход на файловое хранилище вместо SharedPreferences
- 🔐 Решены проблемы с SELinux и правами доступа
- 📦 Хранилище в `/data/local/tmp/`

### v8.0
- 🎨 Первая публичная версия
- 📱 UI для управления сетями
- 🔌 Базовая функциональность спуфинга

---

## 💡 FAQ

**Q: Нужно ли добавлять приложения в scope LSPosed?**  
A: **НЕТ!** В v10.0 scope должен быть **ТОЛЬКО `android`**. Все приложения хукаются автоматически.

**Q: Почему WiFi Spoofer видит реальные сети?**  
A: Это специально! Приложение исключено из хука, чтобы вы могли сканировать реальные сети для настройки.

**Q: Работает ли без root?**  
A: Нет, требуется root для:
  - Копирования файла в `/data/local/tmp/`
  - Работы LSPosed/Xposed Framework

**Q: Файл исчезает после перезагрузки?**  
A: Да, `/data/local/tmp/` очищается при reboot. Но с v9.6+ файл автоматически копируется при запуске приложения!

**Q: Можно ли использовать с другими Xposed модулями?**  
A: Да, совместим со всеми модулями.

---

## ⚠️ Disclaimer

Этот модуль предназначен **только для образовательных целей**. 

Использование для:
- ❌ Незаконного отслеживания
- ❌ Нарушения чужой приватности
- ❌ Мошенничества или обмана сервисов

**Строго запрещено!** Используйте ответственно и в соответствии с законами вашей страны.

---

## 📞 Поддержка

При возникновении проблем:
1. Прочитайте раздел **"Устранение проблем"** выше
2. Проверьте логи: `adb logcat | grep "WiFiSpoofer"`
3. Откройте Issue в GitHub с логами

---

## 📜 Лицензия

MIT License - используйте свободно!

---

**Версия:** 10.0 Global Mode  
**Дата:** November 2024  
**Автор:** Neural-type  
**Репозиторий:** https://github.com/Neural-type/WiFISpoofer

**WiFi Spoofer v10.0** - Глобальная подмена WiFi сетей для всех приложений! 🚀
