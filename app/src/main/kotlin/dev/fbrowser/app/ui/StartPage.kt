@file:OptIn(ExperimentalLayoutApi::class)

package dev.fbrowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fbrowser.app.R
import dev.fbrowser.app.data.Bookmark
import dev.fbrowser.app.data.SearchEngine
import dev.fbrowser.app.engine.UrlUtils

@Composable
fun StartPage(
    isPrivate: Boolean,
    engine: SearchEngine,
    bookmarks: List<Bookmark>,
    onSearch: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            text = "fBrowser",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPrivate) colors.tertiary else colors.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(engine.label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))

        Surface(
            onClick = onSearch,
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isPrivate) colors.tertiaryContainer else colors.secondaryContainer,
                contentColor = if (isPrivate) colors.onTertiaryContainer else colors.onSecondaryContainer,
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(if (isPrivate) R.string.start_private_title else R.string.start_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (isPrivate) R.string.start_private_body else R.string.start_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (!isPrivate && bookmarks.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            FlowRow(
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                bookmarks.take(8).forEach { bookmark ->
                    val label = bookmark.title.ifBlank { UrlUtils.displayHost(bookmark.url) }
                    Column(
                        Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpen(bookmark.url) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SiteBadge(label)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
