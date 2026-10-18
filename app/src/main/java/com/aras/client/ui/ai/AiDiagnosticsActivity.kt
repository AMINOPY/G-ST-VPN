package com.aras.client.ui.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aras.client.R
import com.aras.client.ui.base.BaseComponentActivity

private data class NetworkSnapshot(
    val connection: String,
    val internet: String,
    val validated: Boolean,
    val vpn: Boolean,
    val metered: Boolean,
    val dns: String,
    val carrier: String,
    val radio: String,
    val advice: String,
)

class AiDiagnosticsActivity : BaseComponentActivity() {
    private var snapshot by mutableStateOf<NetworkSnapshot?>(null)
    private var scanMessage by mutableStateOf<Int?>(R.string.ai_scan_hint)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        scanNetwork()
    }

    @Composable
    override fun ScreenContent() {
        AiDiagnosticsScreen(
            snapshot = snapshot,
            scanMessage = scanMessage,
            onBack = { finish() },
            onScan = { scanNetwork() },
            onSimPermission = {
                requestPhonePermission.launch(Manifest.permission.READ_PHONE_STATE)
            },
            onLibreSpeed = { openExternal("https://librespeed.org") },
            onOpenSpeedTest = { openExternal("https://openspeedtest.com") },
            onNetworkChecker = { openExternal("https://github.com/mirarr-app/network-checker") },
        )
    }

    private val requestPhonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scanNetwork()
            else scanMessage = R.string.ai_sim_permission_denied
        }

    private fun openExternal(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { scanMessage = R.string.ai_open_link_failed }
    }

    @Suppress("DEPRECATION")
    private fun scanNetwork() {
        try {
            val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivity.activeNetwork
            } else null
            val caps = if (network != null) connectivity.getNetworkCapabilities(network) else null
            val link = if (network != null) connectivity.getLinkProperties(network) else null

            val connection = when {
                caps == null -> getString(R.string.ai_network_none)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> getString(R.string.ai_network_vpn)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> getString(R.string.ai_network_wifi)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getString(R.string.ai_network_mobile)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> getString(R.string.ai_network_ethernet)
                else -> getString(R.string.ai_network_other)
            }
            val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val internet = if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                getString(R.string.ai_internet_capable)
            } else getString(R.string.ai_internet_no_capability)
            val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val metered = connectivity.isActiveNetworkMetered
            val dns = link?.dnsServers?.take(4)?.joinToString { it.hostAddress ?: "" }
                ?.takeIf { it.isNotBlank() } ?: getString(R.string.ai_value_unknown)

            val hasPhonePermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            val telephony = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val carrier = if (hasPhonePermission) {
                telephony.networkOperatorName.takeIf { it.isNotBlank() }
                    ?: getString(R.string.ai_value_unknown)
            } else getString(R.string.ai_sim_permission_needed)
            val radio = if (hasPhonePermission) {
                radioLabel(telephony.dataNetworkType)
            } else getString(R.string.ai_sim_permission_needed)

            val advice = when {
                caps == null -> getString(R.string.ai_advice_no_network)
                !validated -> getString(R.string.ai_advice_no_internet)
                vpn -> getString(R.string.ai_advice_vpn)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    getString(R.string.ai_advice_mobile)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    getString(R.string.ai_advice_wifi)
                else -> getString(R.string.ai_advice_connected)
            }
            snapshot = NetworkSnapshot(
                connection, internet, validated, vpn, metered, dns, carrier, radio, advice
            )
            scanMessage = R.string.ai_scan_complete
        } catch (_: Exception) {
            scanMessage = R.string.ai_scan_failed
        }
    }

    private fun radioLabel(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN -> getString(R.string.ai_radio_2g)
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> getString(R.string.ai_radio_3g)
        TelephonyManager.NETWORK_TYPE_LTE -> getString(R.string.ai_radio_4g)
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            type == TelephonyManager.NETWORK_TYPE_NR
        ) getString(R.string.ai_radio_5g) else getString(R.string.ai_radio_unknown)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiDiagnosticsScreen(
    snapshot: NetworkSnapshot?,
    scanMessage: Int?,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSimPermission: () -> Unit,
    onLibreSpeed: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onNetworkChecker: () -> Unit,
) {
    val context = LocalContext.current
    val hasPhonePermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ai_hero_title), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.ai_hero_subtitle), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ai_scan_now))
                    }
                    scanMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                }
            }

            Text(stringResource(R.string.ai_report_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportRow(stringResource(R.string.ai_connection_type), snapshot?.connection ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_internet_status), snapshot?.internet ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_internet_validation), snapshot?.let {
                        if (it.validated) stringResource(R.string.ai_status_validated)
                        else stringResource(R.string.ai_status_not_validated)
                    } ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_vpn_status), snapshot?.let {
                        if (it.vpn) stringResource(R.string.ai_status_active) else stringResource(R.string.ai_status_inactive)
                    } ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_metered), snapshot?.let {
                        if (it.metered) stringResource(R.string.ai_metered_yes) else stringResource(R.string.ai_metered_no)
                    } ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_dns_servers), snapshot?.dns ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_carrier), snapshot?.carrier ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_mobile_radio), snapshot?.radio ?: stringResource(R.string.ai_value_not_scanned))
                    if (!hasPhonePermission) {
                        OutlinedButton(onClick = onSimPermission, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ai_allow_sim_info))
                        }
                    }
                }
            }

            Text(stringResource(R.string.ai_analysis_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Text(
                    snapshot?.advice?.let { it } ?: stringResource(R.string.ai_analysis_not_ready),
                    modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge
                )
            }

            Text(stringResource(R.string.ai_speed_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ai_speed_note), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onLibreSpeed, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_speed_librespeed))
            }
            OutlinedButton(onClick = onOpenSpeedTest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_speed_openspeedtest))
            }
            OutlinedButton(onClick = onNetworkChecker, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_network_checker))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}
