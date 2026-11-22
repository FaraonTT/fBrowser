@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.fbrowser.app.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Screen { Browser, Tabs, Bookmarks, History, Settings }

@Composable
fun BrowserRoot(vm: BrowserViewModel) {
    if (!vm.ready) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }
    val manager = vm.manager
    val settings by vm.settings.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Browser) }
    val motion = MaterialTheme.motionScheme
    val view = LocalView.current
    val window = LocalContext.current.findActivity()?.window

    // Private tabs: block screenshots and the recent-apps thumbnail.
    val isPrivate = manager.active?.isPrivate == true
    DisposableEffect(isPrivate, window) {
        if (isPrivate) window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }

    // Fullscreen video hides the system bars.
    val fullscreen = manager.fullscreen
    DisposableEffect(fullscreen, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler(enabled = screen != Screen.Browser) { screen = Screen.Browser }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.Browser -> BrowserScreen(vm, settings) { screen = it }
                Screen.Tabs -> TabsScreen(manager) { screen = Screen.Browser }
                Screen.Bookmarks -> BookmarksScreen(
                    library = vm.library,
                    onBack = { screen = Screen.Browser },
                    onOpen = { url ->
                        manager.active?.let { manager.load(it, url) }
                        screen = Screen.Browser
                    },
                )
                Screen.History -> HistoryScreen(
                    library = vm.library,
                    onBack = { screen = Screen.Browser },
                    onOpen = { url ->
                        manager.active?.let { manager.load(it, url) }
                        screen = Screen.Browser
                    },
                )
                Screen.Settings -> SettingsScreen(vm, settings) { screen = Screen.Browser }
            }
        }

        BackHandler(enabled = fullscreen) {
            manager.exitFullscreen()
        }
    }
}
