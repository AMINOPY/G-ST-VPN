package com.aras.client.ui.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material3.TextButton
import com.aras.client.ui.radar.RadarConfigActivity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.aras.client.R
import com.aras.client.ui.compose.AppTopBar

@Composable
fun MainTopBar(
    isRdrX: Boolean = false,
    isLoading: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    autoScrollEnabled: Boolean,
    onToggleAutoScroll: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onAction: (MainAction) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit
) {
    val context = LocalContext.current
    var showImportMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    AnchoredDropdown(
        expanded = showImportMenu,
        onDismiss = { showImportMenu = false }
    ) {
        ImportMenuContent(
            onAction = { action ->
                showImportMenu = false
                onAction(action)
            }
        )
        TextButton(onClick = {
            showImportMenu = false
            context.startActivity(Intent(context, RadarConfigActivity::class.java))
        }) {
            Text("رادار کانفیگ")
        }
    }
    AnchoredDropdown(
        expanded = showMenu,
        onDismiss = { showMenu = false }
    ) {
        MoreMenuContent { action ->
            showMenu = false
            onMoreMenuAction(action)
        }
    }

    AppTopBar(
        title = stringResource(R.string.app_name),
        titleContent = {
            Column {
                Text(
                    text = if (isRdrX) "RDR X" else stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = if (isRdrX) Color(0xFF39FF88) else MaterialTheme.colorScheme.onSurface,
                    letterSpacing = if (isRdrX) 0.8.sp else 0.sp
                )
                if (isRdrX) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "INTELLIGENT TUNNEL CONTROL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8AA0B5),
                        letterSpacing = 1.1.sp
                    )
                }
            }
        },
        onBackClick = {},
        isLoading = isLoading,
        isSearchActive = showSearch,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        searchPlaceholder = stringResource(R.string.menu_item_search),
        navigationIcon = {
            if (showSearch) {
                IconButton(onClick = onSearchClose) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = stringResource(R.string.acc_back), tint = if (isRdrX) Color(0xFF39FF88) else MaterialTheme.colorScheme.onSurface)
                }
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(painterResource(R.drawable.ic_menu_24dp), contentDescription = stringResource(R.string.acc_open_menu), tint = if (isRdrX) Color(0xFF39FF88) else MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        actions = {
            if (!showSearch) {
                IconButton(onClick = { onToggleAutoScroll(!autoScrollEnabled) }) {
                    Icon(
                        painterResource(R.drawable.ic_autoscroll_24dp),
                        contentDescription = stringResource(R.string.acc_auto_scroll),
                        tint = if (autoScrollEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onSearchToggle(true) }) {
                    Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = stringResource(R.string.acc_search), tint = if (isRdrX) Color(0xFF39FF88) else MaterialTheme.colorScheme.onSurface)
                }
            }
            IconButton(onClick = { showImportMenu = true }) {
                Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.acc_add), tint = if (isRdrX) Color(0xFF39FF88) else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = stringResource(R.string.acc_more), tint = if (isRdrX) Color(0xFF39FF88) else MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}
