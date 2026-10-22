package com.aras.client.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aras.client.dto.GroupMapItem
import com.aras.client.ui.compose.ThemeManager
import com.aras.client.dto.entities.ServersCache
import kotlinx.coroutines.flow.StateFlow

/**
 * Group tabs rendered as tappable pill buttons (selected = filled),
 * replacing the old underline-style tab row.
 */
@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEachIndexed { index, group ->
            GroupTabItem(
                group = group,
                selected = index == selectedTabIndex,
                serverFlowProvider = { mainViewModel.serversForGroup(group.id) },
                onClick = { onTabClick(index) }
            )
        }
    }
}

@Composable
private fun GroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    serverFlowProvider: () -> StateFlow<List<ServersCache>>,
    onClick: () -> Unit
) {
    val serverFlow = remember(group.id) { serverFlowProvider() }
    val servers by serverFlow.collectAsStateWithLifecycle()
    val uiTheme by ThemeManager.uiTheme.collectAsStateWithLifecycle()
    val isSurvival = uiTheme == "survival"
    val text = if (group.id.isEmpty()) {
        group.remarks
    } else {
        "${group.remarks} (${servers.size})"
    }
    val shape = RoundedCornerShape(if (isSurvival) 4.dp else 20.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    isSurvival && selected -> Color(0xFF354531)
                    isSurvival -> Color(0xFF111811)
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .border(
                width = if (isSurvival) 1.dp else 0.dp,
                color = if (selected && isSurvival) Color(0xFF9BCB72)
                    else if (isSurvival) Color(0xFF354531) else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected && isSurvival) Color(0xFFE1E8D6)
            else if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
