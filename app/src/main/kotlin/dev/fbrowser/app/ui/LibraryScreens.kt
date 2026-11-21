@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fbrowser.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fbrowser.app.R
import dev.fbrowser.app.data.Library

@Composable
fun BookmarksScreen(library: Library, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val items by library.bookmarks.collectAsStateWithLifecycle()
    LibraryScaffold(
        title = R.string.bookmarks,
        emptyText = R.string.empty_bookmarks,
        empty = items.isEmpty(),
        onBack = onBack,
    ) {
        items(items, key = { it.url }) { b ->
            UrlRow(b.title, b.url, onOpen = { onOpen(b.url) }, onDelete = { library.removeBookmark(b.url) })
        }
    }
}

@Composable
fun HistoryScreen(library: Library, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val items by library.history.collectAsStateWithLifecycle()
    LibraryScaffold(
        title = R.string.history,
        emptyText = R.string.empty_history,
        empty = items.isEmpty(),
        onBack = onBack,
        actions = {
            if (items.isNotEmpty()) {
                TextButton(onClick = { library.clearHistory() }) { Text(stringResource(R.string.clear)) }
            }
        },
    ) {
        items(items, key = { it.url + it.visited }) { h ->
            UrlRow(h.title, h.url, onOpen = { onOpen(h.url) }, onDelete = { library.removeHistory(h.url) })
        }
    }
}

@Composable
private fun LibraryScaffold(
    @StringRes title: Int,
    @StringRes emptyText: Int,
    empty: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = actions,
            )
        },
    ) { padding ->
        if (empty) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(emptyText), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) { content() }
        }
    }
}

@Composable
private fun UrlRow(title: String, url: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(title.ifBlank { url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { SiteBadge(title.ifBlank { url }, size = 36.dp) },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}
