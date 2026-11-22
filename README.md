# fBrowser

Быстрый, стабильный и приватный браузер для Android на **Kotlin** + **Jetpack Compose** в стиле **Material 3 Expressive**. Движок — системный WebView (Chromium). Поисковик по умолчанию — **Google** (можно сменить в настройках: DuckDuckGo, Brave, Startpage).

> **Статус: v0.1, ранний прототип.** Код написан без возможности сборки в среде автора (нет доступа к Android SDK), поэтому первая сборка может потребовать мелких правок. Сначала запустите `./gradlew :app:assembleDebug` и проверьте CI.

## Возможности

**Приватность и конфиденциальность**
- Блокировка трекеров и рекламы (встроенный список `assets/blocklist.txt`, поиск по родительским доменам за O(меток)); отключается для отдельного сайта.
- Блокировка сторонних cookie, удаление трекинг-параметров (`utm_*`, `fbclid`, `gclid`, …).
- HTTPS-first: сначала `https://`, откат на `http://` только при невозможности соединиться (не при ошибках сертификата).
- Сигналы `Sec-GPC` и `DNT`.
- Приватные вкладки в отдельном WebView Profile (`androidx.webkit.ProfileStore`): данные удаляются при закрытии последней приватной вкладки и перед первой приватной вкладкой нового запуска; `FLAG_SECURE` запрещает скриншоты.
- Камера, микрофон и геолокация по умолчанию запрещены; доступ к файлам/`content://` отключён; mixed content запрещён.
- Safe Browsing, отключённая телеметрия WebView, без резервного копирования данных.
- «Очищать данные при выходе», ручная очистка.

**Стабильность**
- Восстановление после падения рендерера (`onRenderProcessGone`) с защитой от цикла падений.
- Атомарная запись закладок, истории и сессии (`AtomicFile`), повреждённый файл не роняет приложение.
- Восстановление вкладок; сертификатные ошибки никогда не пропускаются.

**Производительность**
- WebView создаётся лениво, не более 4 живых; остальные «засыпают» (`saveState`) и восстанавливаются при показе; освобождаются по `onTrimMemory`.
- Загрузка списка блокировки в фоне, проверка запросов без аллокаций, запись истории с задержкой, R8 + shrinkResources.

**Интерфейс**
- `MaterialExpressiveTheme`, динамические цвета (Material You), пружинная анимация (`MotionScheme.expressive()`), волнистый индикатор загрузки, нижняя адресная панель, скрытие панели при прокрутке, поиск по странице, сетка вкладок, закладки, история, настройки. Русский и английский языки.

## Стек (актуально на ноябрь 2026)

| Компонент | Версия |
|---|---|
| Android Gradle Plugin | 9.3.3 (Gradle 9.5.0, JDK 17) |
| Kotlin / Compose compiler plugin | 2.4.10 |
| Compose BOM | 2026.11.01 (Material 3 1.5.0) |
| AndroidX WebKit | 1.18.0 |
| DataStore Preferences | 1.2.1 |
| compileSdk / targetSdk / minSdk | 37.1 / 36 / 26 |

Подробности и ссылки на источники — в [docs/API_NOTES.md](docs/API_NOTES.md).

## Сборка

```bash
# Нужны JDK 17 и Android SDK (platform 37.1). Gradle Wrapper не включён:
gradle wrapper --gradle-version 9.5.0   # один раз
./gradlew :app:assembleDebug
```

## Структура

```
app/src/main/kotlin/dev/fbrowser/app/
  data/    SettingsRepository (DataStore), Library (закладки, история)
  engine/  TabManager, Tab, Clients (WebViewClient/ChromeClient), BlockList, UrlUtils
  ui/      BrowserRoot, BrowserScreen, MenuSheet, StartPage, TabsScreen, ...
```

## Ограничения

- Движок — системный WebView: нет расширений и встроенного менеджера паролей.
- Блокировка работает по именам хостов (без косметических фильтров и EasyList-правил).
- Разрешения сайтов (камера/микрофон/геолокация) пока всегда отклоняются.
- Приватные вкладки требуют WebView с поддержкой Multi-Profile; иначе кнопка покажет подсказку.

## Лицензия

MIT, см. [LICENSE](LICENSE).

---

## English summary

fBrowser is a privacy-first Android browser written in Kotlin with Jetpack Compose and Material 3 Expressive, built on the system WebView. Google is the default search engine. It blocks trackers and third-party cookies, upgrades to HTTPS, strips tracking parameters, isolates private tabs in a separate WebView profile, recovers from renderer crashes, hibernates background tabs and writes its data atomically. Early prototype (v0.1): not compiled by the author, expect minor build fixes.
