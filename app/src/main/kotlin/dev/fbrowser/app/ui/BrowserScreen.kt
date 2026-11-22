@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.fbrowser.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fbrowser.app.R
import dev.fbrowser.app.data.BrowserSettings
import dev.fbrowser.app.data.Library
import dev.fbrowser.app.data.SearchEngine
import dev.fbrowser.app.engine.ErrorKind
import dev.fbrowser.app.engine.Tab
import dev.fbrowser.app.engine.TabError
import dev.fbrowser.app.engine.TabManager
import org.mozilla.geckoview.GeckoView
import dev.fbrowser.app.engine.UrlUtils

@Composable
fun BrowserScreen(vm: BrowserViewModel, settings: BrowserSettings, navigate: (Screen) -> Unit) {
    val manager = vm.manager
    val tab = manager.active ?: return
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val focusManager = LocalFocusManager.current
    val bookmarks by vm.library.bookmarks.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val addressState = rememberTextFieldState()

    fun startEditing() {
        addressState.setTextAndSelectAll(if (tab.isStartPage) "" else tab.url)
        editing = true
    }

    fun stopEditing() {
        editing = false
        focusManager.clearFocus()
    }

    fun submit(text: String) {
        stopEditing()
        manager.go(tab, text)
    }

    BackHandler(enabled = editing) { stopEditing() }
    BackHandler(enabled = !editing && tab.canGoBack) { manager.goBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab.isStartPage) {
                StartPage(
                    isPrivate = tab.isPrivate,
                    engine = settings.searchEngine,
                    bookmarks = bookmarks,
                    onSearch = { startEditing() },
                    onOpen = { manager.load(tab, it) },
                )
            } else {
                key(tab.id) { WebContent(tab, manager) }
            }
            tab.error?.let { ErrorPane(it) { manager.reload() } }
            if (editing) {
                SuggestionsPane(
                    state = addressState,
                    library = vm.library,
                    engine = settings.searchEngine,
                    onPick = { submit(it) },
                )
            }
        }

        AnimatedVisibility(
            visible = manager.barsVisible || editing || tab.isStartPage || tab.error != null,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            BottomBar(
                tab = tab,
                manager = manager,
                editing = editing,
                addressState = addressState,
                onStartEditing = { startEditing() },
                onSubmit = { submit(it) },
                onTabs = { navigate(Screen.Tabs) },
                onMenu = { menuOpen = true },
            )
        }
    }

    if (menuOpen) {
        val bookmarked = bookmarks.any { it.url == tab.url }
        MenuSheet(
            tab = tab,
            vm = vm,
            settings = settings,
            bookmarked = bookmarked,
            navigate = navigate,
            onDismiss = { menuOpen = false },
        )
    }
}

/**
 * Attaches the selected tab's GeckoSession to a GeckoView. A session belongs to its tab,
 * not to the Compose tree, so it is detached from any old view before being attached here.
 */
@Composable
private fun WebContent(tab: Tab, manager: TabManager) {
    val sessionVersion = tab.sessionVersion
    key(tab.id, sessionVersion) {
        AndroidView(
            factory = { context -> GeckoView(context) },
            update = { geckoView ->
                val session = manager.sessionFor(tab)
                if (geckoView.session !== session) {
                    (geckoView.parent as? android.view.ViewGroup)?.removeView(geckoView)
                    geckoView.setSession(session)
                }
            },
            onRelease = { geckoView ->
                geckoView.releaseSession()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BottomBar(
    tab: Tab,
    manager: TabManager,
    editing: Boolean,
    addressState: TextFieldState,
    onStartEditing: () -> Unit,
    onSubmit: (String) -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (tab.isPrivate) colors.tertiaryContainer else colors.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 6.dp)) {
            // Fixed-height slot so the bar does not jump when loading starts or ends.
            Box(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                if (tab.loading && tab.progress in 1..99) {
                    LinearWavyProgressIndicator(
                        progress = { tab.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (editing) {
                AddressEditor(addressState, stringResource(R.string.search_hint), onSubmit)
            } else {
                AddressPill(tab, manager, onStartEditing)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { manager.goBack() }, enabled = tab.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                IconButton(onClick = { manager.goForward() }, enabled = tab.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward))
                }
                IconButton(onClick = { manager.newTab() }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.new_tab))
                }
                IconButton(onClick = onTabs) {
                    Box(
                        Modifier.size(24.dp).border(2.dp, LocalContentColor.current, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (manager.tabs.size > 99) ":)" else manager.tabs.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.menu))
                }
            }
        }
    }
}

@Composable
private fun AddressPill(tab: Tab, manager: TabManager, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            val secure = tab.isStartPage || tab.isSecure
            Icon(
                imageVector = when {
                    tab.isStartPage -> Icons.Filled.Search
                    secure -> Icons.Filled.Lock
                    else -> Icons.Filled.Warning
                },
                contentDescription = null,
                tint = if (secure) colors.primary else colors.error,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = if (tab.isStartPage) stringResource(R.string.search_hint) else UrlUtils.displayHost(tab.url),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = if (tab.isStartPage) colors.onSurfaceVariant else colors.onSurface,
        )
        if (tab.blockedCount > 0) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.secondaryContainer) {
                Text(
                    text = tab.blockedCount.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (!tab.isStartPage) {
            IconButton(onClick = { if (tab.loading) manager.stop() else manager.reload() }) {
                Icon(
                    imageVector = if (tab.loading) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = stringResource(if (tab.loading) R.string.stop else R.string.reload),
                )
            }
        }
    }
}

@Composable
private fun AddressEditor(state: TextFieldState, hint: String, onSubmit: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surfaceContainerHighest)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            state = state,
            modifier = Modifier.weight(1f).focusRequester(focus),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            onKeyboardAction = { onSubmit(state.text.toString()) },
            decorator = TextFieldDecorator { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) {
                        Text(hint, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                    inner()
                }
            },
        )
        if (state.text.isNotEmpty()) {
            IconButton(onClick = { state.clearText() }) {
                Icon(Icons.Filled.Close, stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun SuggestionsPane(
    state: TextFieldState,
    library: Library,
    engine: SearchEngine,
    onPick: (String) -> Unit,
) {
    val query = state.text.toString() // read here so only this pane recomposes while typing
    val suggestions = remember(query) { library.suggest(query) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isNotBlank()) {
                item(key = "search") {
                    ListItem(
                        headlineContent = { Text(query.trim(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(stringResource(R.string.search_with, engine.label)) },
                        leadingContent = { Icon(Icons.Filled.Search, null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable { onPick(query) },
                    )
                }
            }
            items(suggestions, key = { it.url }) { s ->
                ListItem(
                    headlineContent = { Text(s.title.ifBlank { s.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(s.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { SiteBadge(s.title.ifBlank { s.url }, size = 36.dp) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { onPick(s.url) },
                )
            }
        }
    }
}


@Composable
private fun ErrorPane(error: TabError, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val title: Int
    val body: String
    when (error.kind) {
        ErrorKind.Certificate -> {
            title = R.string.error_cert_title
            body = stringResource(R.string.error_cert_body)
        }
        ErrorKind.Crash -> {
            title = R.string.error_crash_title
            body = stringResource(R.string.error_crash_body)
        }
        ErrorKind.Network -> {
            title = R.string.error_network_title
            body = error.detail
        }
    }
    Surface(Modifier.fillMaxSize(), color = colors.surface) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Warning, null, tint = colors.error, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                UrlUtils.displayHost(error.url),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
