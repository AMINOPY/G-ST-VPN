package com.aras.client.ui.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aras.client.R
import com.aras.client.util.CountryResolver
import java.util.Locale

/** Searchable country picker adapted from ZX-VPN's Psiphon country field. */
@Composable
internal fun PsiphonRegionField(
    selected: String,
    onSelect: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedCode = selected.trim().uppercase().takeUnless { it == "AUTO" }.orEmpty()
    val codes = listOf(
        "", "AT", "AU", "BE", "BG", "CA", "CH", "CZ", "DE", "DK", "EE", "ES", "FI",
        "FR", "GB", "HR", "HU", "IE", "IN", "IT", "JP", "KR", "LV", "MX", "NL", "NO",
        "PL", "PT", "RO", "RS", "SE", "SG", "SK", "UA", "US",
    ).let { listOf("") + it.filter { code -> code.isNotBlank() }.sortedBy { code -> Locale("", code).getDisplayCountry(Locale.getDefault()) } }
    fun name(code: String): String = if (code.isBlank()) {
        "AUTO"
    } else {
        Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }
    }
    val filtered = codes.filter { code ->
        query.isBlank() || name(code).contains(query.trim(), ignoreCase = true) ||
            code.contains(query.trim(), ignoreCase = true)
    }

    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                CountryResolver.flagForCountry(selectedCode).ifBlank { "🌐" },
                style = MaterialTheme.typography.titleLarge,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.server_lab_aether_psiphon_region),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(name(selectedCode), style = MaterialTheme.typography.bodyLarge)
            }
            Text("⌄", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.server_lab_aether_psiphon_region)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.menu_item_search)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it }) { code ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(code)
                                        open = false
                                        query = ""
                                    }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RadioButton(
                                    selected = selectedCode == code,
                                    onClick = {
                                        onSelect(code)
                                        open = false
                                        query = ""
                                    },
                                )
                                Text(
                                    CountryResolver.flagForCountry(code).ifBlank { "🌐" },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(name(code), modifier = Modifier.weight(1f))
                                if (code.isNotBlank()) {
                                    Text(code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}
