package com.aras.client.ui.server

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.aras.client.R
import com.aras.client.enums.AetherIpVersion
import com.aras.client.enums.AetherObfuscation
import com.aras.client.enums.AetherProtocol
import com.aras.client.enums.AetherPsiphon
import com.aras.client.enums.AetherPsiphonMode
import com.aras.client.enums.AetherScanMode
import com.aras.client.enums.AetherTor
import com.aras.client.enums.EConfigType
import com.aras.client.extension.nullIfBlank
import com.aras.client.extension.toastError
import com.aras.client.extension.toastSuccess
import com.aras.client.fmt.AetherFmt
import com.aras.client.handler.MmkvManager
import com.aras.client.ui.compose.FormDropdownField
import com.aras.client.ui.compose.FormTextField
import com.aras.client.ui.compose.FormToggleField

/**
 * A no-import Psiphon profile. The bundled Aether native helper owns Psiphon's
 * signed server-list bootstrap and exposes its local SOCKS listener to Xray.
 */
class ServerPsiphonActivity : BaseServerActivity() {
    override val serverConfigType: EConfigType = EConfigType.AETHER

    @Composable
    override fun ScreenContent() {
        var name by rememberSaveable {
            mutableStateOf(initialConfig.remarks.takeIf { it.isNotBlank() } ?: "Psiphon")
        }
        var mode by rememberSaveable {
            mutableStateOf(AetherPsiphonMode.fromString(initialConfig.aetherPsiphonMode).type)
        }
        var region by rememberSaveable {
            mutableStateOf(initialConfig.aetherPsiphonRegion?.takeIf { it.isNotBlank() } ?: "AUTO")
        }
        var cdnIps by rememberSaveable { mutableStateOf(initialConfig.aetherPsiphonCdnIps.orEmpty()) }
        var cdnSni by rememberSaveable { mutableStateOf(initialConfig.aetherPsiphonCdnSni.orEmpty()) }
        var bundledList by rememberSaveable { mutableStateOf(initialConfig.aetherPsiphonBundledList != false) }

        ServerEditorScaffold(
            title = stringResource(R.string.menu_item_import_config_psiphon),
            onSaveClick = {
                if (name.isBlank()) {
                    toastError(R.string.server_lab_remarks)
                    return@ServerEditorScaffold
                }
                if (mode == AetherPsiphonMode.CDN.type && cdnIps.isBlank() != cdnSni.isBlank()) {
                    toastError(R.string.psiphon_cdn_pair_required)
                    return@ServerEditorScaffold
                }
                val draft = initialConfig.copy().also {
                    it.remarks = name.trim()
                    it.server = null
                    it.serverPort = null
                    it.description = ""
                    it.aetherProtocol = AetherProtocol.WIREGUARD.type
                    it.aetherTransport = null
                    it.aetherScanMode = AetherScanMode.BALANCED.type
                    it.aetherObfuscation = AetherObfuscation.AUTO.type
                    it.aetherIpVersion = AetherIpVersion.V4.type
                    it.aetherWiwOuter = null
                    it.aetherWiwInner = null
                    it.aetherPsiphon = AetherPsiphon.ONLY.type
                    it.aetherPsiphonMode = AetherPsiphonMode.fromString(mode).type
                    it.aetherPsiphonCdnIps = cdnIps.trim().nullIfBlank()
                    it.aetherPsiphonCdnSni = cdnSni.trim().nullIfBlank()
                    it.aetherPsiphonRegion = region.takeUnless { it == "AUTO" }
                    it.aetherPsiphonBundledList = bundledList.takeUnless { it }
                    it.aetherTor = AetherTor.OFF.type
                    it.aetherTorBridges = null
                    it.aetherTorBridgeLines = null
                    it.aetherTorRelays = null
                    it.aetherCommand = null
                }
                val problem = AetherFmt.normalize(draft)
                if (problem != null) {
                    toastError(R.string.toast_failure)
                    return@ServerEditorScaffold
                }
                if (draft.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
                    draft.subscriptionId = subscriptionId.orEmpty()
                }
                val guid = MmkvManager.encodeServerConfig(editGuid, draft)
                toastSuccess(R.string.toast_success)
                ProfileEditorResult.run { finishSaved(guid, isRunning) }
            },
        ) {
            Text(
                stringResource(R.string.psiphon_standalone_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FormTextField(
                label = stringResource(R.string.server_lab_remarks),
                value = name,
                onValueChange = { name = it },
                maxLines = 1,
            )
            FormDropdownField(
                label = stringResource(R.string.server_lab_aether_psiphon_mode),
                value = mode,
                options = stringArrayResource(R.array.aether_psiphon_modes).toList(),
                onValueChange = { mode = AetherPsiphonMode.fromString(it).type },
            )
            PsiphonRegionField(
                selected = region,
                onSelect = { region = it },
            )
            if (mode == AetherPsiphonMode.CDN.type) {
                FormTextField(
                    stringResource(R.string.server_lab_aether_psiphon_cdn_ips),
                    cdnIps,
                    { cdnIps = it },
                )
                FormTextField(
                    stringResource(R.string.server_lab_aether_psiphon_cdn_sni),
                    cdnSni,
                    { cdnSni = it },
                )
            }
            FormToggleField(
                label = stringResource(R.string.server_lab_aether_psiphon_bundled),
                checked = bundledList,
                onCheckedChange = { bundledList = it },
            )
            Text(
                stringResource(R.string.psiphon_standalone_engine_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
