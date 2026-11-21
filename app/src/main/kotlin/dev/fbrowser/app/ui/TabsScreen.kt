@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fbrowser.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fbrowser.app.R
import dev.fbrowser.app.engine.Tab
import dev.fbrowser.app.engine.TabManager
import dev.fbrowser.app.engine.UrlUtils

@Composable
fun TabsScreen(manager: TabManager, onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${stringResource(R.string.tabs)} (${manager.tabs.size})") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        manager.closeAll()
                        onClose()
                    }) { Text(stringResource(R.string.close_all)) }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = {
                    if (manager.newTab(private = true) == null) {
                        manager.toast(context.getString(R.string.private_unsupported))
                    } else {
                        onClose()
                    }
                }) { Icon(Icons.Filled.Lock, stringResource(R.string.new_private_tab)) }
                FloatingActionButton(onClick = {
                    manager.newTab()
                    onClose()
                }) { Icon(Icons.Filled.Add, stringResource(R.string.new_tab)) }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 120.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(manager.tabs, key = { it.id }) { tab ->
                TabCard(
                    tab = tab,
                    selected = tab.id == manager.activeId,
                    onOpen = {
                        manager.select(tab.id)
                        onClose()
                    },
                    onClose = { manager.close(tab.id) },
                )
            }
        }
    }
}

@Composable
private fun TabCard(tab: Tab, selected: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        tab.isPrivate -> colors.tertiaryContainer
        selected -> colors.primaryContainer
        else -> colors.surfaceContainerHigh
    }
    val host = if (tab.isStartPage) "" else UrlUtils.displayHost(tab.url)
    val title = tab.title.ifBlank { host.ifEmpty { stringResource(R.string.new_tab) } }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = tab.favicon
                if (icon != null) {
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp).clip(CircleShape),
                    )
                } else {
                    SiteBadge(label = title, size = 28.dp)
                }
                Spacer(Modifier.weight(1f))
                if (tab.isPrivate) {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.private_badge), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (host.isNotEmpty()) {
                Text(
                    host,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
