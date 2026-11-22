@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fbrowser.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.fbrowser.app.R
import dev.fbrowser.app.data.BrowserSettings
import dev.fbrowser.app.data.SearchEngine

@Composable
fun SettingsScreen(vm: BrowserViewModel, settings: BrowserSettings, onBack: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { SectionTitle(R.string.settings_search) }
            items(SearchEngine.entries) { engine ->
                ListItem(
                    headlineContent = { Text(engine.label) },
                    leadingContent = { RadioButton(selected = settings.searchEngine == engine, onClick = null) },
                    modifier = Modifier.clickable { vm.update { it.copy(searchEngine = engine) } },
                )
            }

            item { SectionTitle(R.string.settings_privacy) }
            item {
                SwitchRow(R.string.set_block_trackers, R.string.set_block_trackers_desc, settings.blockTrackers) { v ->
                    vm.update { it.copy(blockTrackers = v) }
                }
            }
            item {
                SwitchRow(R.string.set_block_cookies, R.string.set_block_cookies_desc, settings.blockThirdPartyCookies) { v ->
                    vm.update { it.copy(blockThirdPartyCookies = v) }
                }
            }
            item {
                SwitchRow(R.string.set_https_first, R.string.set_https_first_desc, settings.httpsFirst) { v ->
                    vm.update { it.copy(httpsFirst = v) }
                }
            }
            item {
                SwitchRow(R.string.set_strip_params, R.string.set_strip_params_desc, settings.stripTrackingParams) { v ->
                    vm.update { it.copy(stripTrackingParams = v) }
                }
            }
            item {
                SwitchRow(R.string.set_signals, R.string.set_signals_desc, settings.sendPrivacySignals) { v ->
                    vm.update { it.copy(sendPrivacySignals = v) }
                }
            }
            item {
                SwitchRow(R.string.set_javascript, R.string.set_javascript_desc, settings.javascript) { v ->
                    vm.update { it.copy(javascript = v) }
                }
            }

            item { SectionTitle(R.string.settings_browsing) }
            item {
                SwitchRow(R.string.set_dark_web, R.string.set_dark_web_desc, settings.darkWebPages) { v ->
                    vm.update { it.copy(darkWebPages = v) }
                }
            }
            item {
                SwitchRow(R.string.set_hide_bars, R.string.set_hide_bars_desc, settings.hideBarsOnScroll) { v ->
                    vm.update { it.copy(hideBarsOnScroll = v) }
                }
            }

            item { SectionTitle(R.string.settings_data) }
            item {
                SwitchRow(R.string.set_clear_exit, R.string.set_clear_exit_desc, settings.clearOnExit) { v ->
                    vm.update { it.copy(clearOnExit = v) }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_data)) },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    modifier = Modifier.clickable { confirmClear = true },
                )
            }

            item { SectionTitle(R.string.settings_about) }
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.about_text, version)) })
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_data_title)) },
            text = { Text(stringResource(R.string.clear_data_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearBrowsingData()
                    confirmClear = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
