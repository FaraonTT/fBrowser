# API and dependency notes

Researched on 2026-11-22. Versions are the latest stable ones found at that date.

## Build

- **AGP 9.3.3**, requires Gradle 9.5.0 and JDK 17; AGP 9 has built-in Kotlin, so `org.jetbrains.kotlin.android` is not applied. Only `org.jetbrains.kotlin.plugin.compose` is needed.
  - https://developer.android.com/build/releases/agp-9-3-0-release-notes
- **compileSdk DSL** (AGP 9): `compileSdk { version = release(37) { minorApiLevel = 1 } }`.
  - https://developer.android.com/build/gradle-build-overview
- **Kotlin 2.4.10** (2.4.0 stable, 2.4.10 patch).
  - https://kotlinlang.org/docs/whatsnew24.html
- **Compose BOM 2026.11.01** (Compose 1.13, Material 3 1.5.0). Requires Kotlin 2.2+ and compileSdk 37.1. AGP 9.3 lists API 37 as its maximum, so `android.suppressUnsupportedCompileSdk=37.1` is set in `gradle.properties`.
  - https://developer.android.com/blog/posts/jetpack-compose-november-2026-release

## Material 3 Expressive

- Theme entry point: `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive(), ...)`.
- Used components: `LinearWavyProgressIndicator`, `ModalBottomSheet`, `ListItem`, `TopAppBar`, `Card`, `Surface(onClick)`, `SmallFloatingActionButton`.
- Motion specs from `MaterialTheme.motionScheme` (`defaultSpatialSpec()`, `defaultEffectsSpec()`, `fastSpatialSpec()`, `fastEffectsSpec()`).
- Text input uses the state-based `BasicTextField(state = TextFieldState)`, as recommended for Material 3 1.5.0.
- Icons: `material-icons-core:1.7.8` (the extended set is no longer updated).
  - https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary

## WebView

- **AndroidX WebKit 1.18.0**: `ProfileStore.getInstance().getOrCreateProfile / deleteProfile`, `WebViewCompat.setProfile(webView, name)` (must be called before the WebView is used), `Profile.cookieManager`, `WebSettingsCompat.setAlgorithmicDarkeningAllowed`. Always guard with `WebViewFeature.isFeatureSupported(...)` (`MULTI_PROFILE`, `ALGORITHMIC_DARKENING`).
  - https://developer.android.com/reference/androidx/webkit/ProfileStore
  - https://developer.android.com/jetpack/androidx/releases/webkit
- `WebViewCompat.navigate` (1.17+) is a possible future replacement for `loadUrl`; the app currently uses `loadUrl(url, headers)`.
  - https://developer.android.com/develop/ui/views/layout/webapps/navigate
- **Hardening** (`TabManager.applySettings`): `allowFileAccess=false`, `allowContentAccess=false`, `MIXED_CONTENT_NEVER_ALLOW`, Safe Browsing enabled (manifest `android.webkit.WebView.EnableSafeBrowsing`).
  - https://developer.android.com/privacy-and-security/risks/cross-app-scripting

## Storage

- **DataStore Preferences 1.2.1** for settings. Bookmarks, history and session are small JSON files written through `android.util.AtomicFile`.
  - https://developer.android.com/jetpack/androidx/releases/datastore

## Other AndroidX versions

`activity-compose 1.13.0`, `lifecycle 2.11.0`, `core-ktx 1.19.1`.
  - https://developer.android.com/jetpack/androidx/versions

## Not verified

The project was not compiled by the author. Items most likely to need a tweak on first build: the exact `compileSdk` minor-version handling with AGP 9.3, `expressiveLightColorScheme()` availability, and experimental opt-in annotations for Material 3 1.5.0.
