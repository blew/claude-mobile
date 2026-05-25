package com.pascal.claudemobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pascal.claudemobile.data.Tab

@Composable
fun TabBar(
    tabs: List<Tab>,
    activeTabId: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            TabChip(
                tab = tab,
                active = tab.id == activeTabId,
                onClick = { onSelect(tab.id) },
                onClose = { onClose(tab.id) },
            )
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onNew, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New tab",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TabChip(
    tab: Tab,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tab.title,
            color = fg,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close tab",
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
