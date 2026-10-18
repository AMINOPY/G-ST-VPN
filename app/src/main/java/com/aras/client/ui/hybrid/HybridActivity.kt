package com.aras.client.ui.hybrid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aras.client.R
import com.aras.client.ui.base.BaseComponentActivity

class HybridActivity : BaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        HybridScreen(onBack = { finish() })
    }
}

private enum class ChainDirection { PsiphonFirst, V2RayFirst }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HybridScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var configText by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf(ChainDirection.PsiphonFirst) }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hybrid_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.hybrid_status_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.hybrid_backend_notice),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(stringResource(R.string.hybrid_config_label), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = configText,
                onValueChange = {
                    configText = it
                    validationMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10,
                label = { Text(stringResource(R.string.hybrid_config_hint)) },
                placeholder = { Text("vless://…  /  vmess://…  /  trojan://…") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    configText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    validationMessage = null
                }) { Text(stringResource(R.string.hybrid_paste)) }
                OutlinedButton(onClick = {
                    configText = ""
                    validationMessage = null
                }) { Text(stringResource(R.string.hybrid_clear)) }
            }

            Text(stringResource(R.string.hybrid_direction_label), style = MaterialTheme.typography.titleMedium)
            DirectionOption(
                selected = direction == ChainDirection.PsiphonFirst,
                title = stringResource(R.string.hybrid_psiphon_first),
                onClick = { direction = ChainDirection.PsiphonFirst }
            )
            DirectionOption(
                selected = direction == ChainDirection.V2RayFirst,
                title = stringResource(R.string.hybrid_v2ray_first),
                onClick = { direction = ChainDirection.V2RayFirst }
            )

            Button(
                onClick = {
                    val trimmed = configText.trim()
                    val accepted = trimmed.lineSequence().any { line ->
                        val value = line.trim().lowercase()
                        value.startsWith("vless://") ||
                            value.startsWith("vmess://") ||
                            value.startsWith("trojan://") ||
                            value.startsWith("ss://") ||
                            value.startsWith("socks://")
                    }
                    validationMessage = if (accepted) {
                        context.getString(R.string.hybrid_validation_ok)
                    } else {
                        context.getString(R.string.hybrid_validation_error)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = configText.isNotBlank()
            ) {
                Text(stringResource(R.string.hybrid_validate))
            }
            validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.hybrid_not_connected_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun DirectionOption(selected: Boolean, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, modifier = Modifier.padding(start = 4.dp))
    }
}
