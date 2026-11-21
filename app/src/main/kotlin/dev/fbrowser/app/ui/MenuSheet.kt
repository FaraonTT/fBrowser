@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fbrowser.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.fbrowser.app.R
import dev.fbrowser.app.data.BrowserSettings
import dev.fbrowser.app.engine.Tab
import dev.fbrowser.app.engine.UrlUtils

@Composable
fun MenuSheet(
    tab: Tab,
    vm: BrowserViewModel,
    settings: BrowserSettings,
    bookmarked: Boolean,
    navigate: (Screen) -> Unit,
    onDismiss: () -> Unit,
) {
    val manager = vm.manager
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val siteKey = UrlUtils.siteKey(tab.host)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            MenuItem(Icons.Filled.Add, R.string.new_tab) {
                manager.newTab()
                onDismiss()
            }
            MenuItem(Icons.Filled.Lock, R.string.new_private_tab) {
                if (manager.newTab(private = true) == null) {
                    manager.toast(context.getString(R.string.private_unsupported))
                }
                onDismiss()
            }
            HorizontalDivider()

            if (!tab.isStartPage) {
                MenuItem(
                    Icons.Filled.Star,
                    if (bookmarked) R.string.remove_bookmark else R.string.add_bookmark,
                ) {
                    vm.library.toggleBookmark(tab.url, tab.title)
                    onDismiss()
                }
                MenuItem(Icons.Filled.Share, R.string.share) {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, tab.url)
                    context.startActivity(Intent.createChooser(send, null))
                    onDismiss()
                }
                MenuItem(Icons.Filled.Create, R.string.copy_link) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("url", tab.url))
                    manager.toast(context.getString(R.string.link_copied))
                    onDismiss()
                }
                MenuItem(Icons.Filled.Search, R.string.find_in_page) {
                    onDismiss()
                    manager.openFind()
                }
                SwitchItem(Icons.Filled.Info, R.string.desktop_site, tab.desktopSite) {
                    manager.setDesktop(tab, it)
                    onDismiss()
                }
                if (settings.blockTrackers && siteKey.isNotEmpty()) {
                    SwitchItem(
                        Icons.Filled.Lock,
                        R.string.site_protection,
                        siteKey !in settings.allowlist,
                    ) { enabled ->
                        vm.update { s ->
                            s.copy(allowlist = if (enabled) s.allowlist - siteKey else s.allowlist + siteKey)
                        }
                        manager.reload()
                        onDismiss()
                    }
                }
                HorizontalDivider()
            }

            MenuItem(Icons.Filled.Favorite, R.string.bookmarks) {
                onDismiss()
                navigate(Screen.Bookmarks)
            }
            MenuItem(Icons.Filled.DateRange, R.string.history) {
                onDismiss()
                navigate(Screen.History)
            }
            MenuItem(Icons.Filled.Settings, R.string.settings) {
                onDismiss()
                navigate(Screen.Settings)
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    @StringRes label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
