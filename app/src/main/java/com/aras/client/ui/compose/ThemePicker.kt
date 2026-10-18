package com.aras.client.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aras.client.R

private data class ThemeChoice(
    val value: String,
    val title: String,
    val description: String,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val foreground: Color
)

@Composable
fun ThemePicker(
    selectedValue: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val patternIntensity by ThemeManager.patternIntensity.collectAsState()
    val reducedMotion by ThemeManager.reducedMotionEnabled.collectAsState()

    val choices = listOf(
        ThemeChoice(
            value = "0",
            title = stringResource(R.string.theme_system_title),
            description = stringResource(R.string.theme_system_summary),
            background = MaterialTheme.colorScheme.background,
            surface = MaterialTheme.colorScheme.surfaceContainerHigh,
            accent = MaterialTheme.colorScheme.primary,
            foreground = MaterialTheme.colorScheme.onSurface
        ),
        ThemeChoice(
            value = "3",
            title = stringResource(R.string.theme_cultural_night_title),
            description = stringResource(R.string.theme_cultural_night_summary),
            background = Color(0xFF0B1420),
            surface = Color(0xFF16222F),
            accent = Color(0xFF4FB3B0),
            foreground = Color(0xFFF2E8D5)
        ),
        ThemeChoice(
            value = "4",
            title = stringResource(R.string.theme_ancient_paper_title),
            description = stringResource(R.string.theme_ancient_paper_summary),
            background = Color(0xFFF4ECD8),
            surface = Color(0xFFFFF8E9),
            accent = Color(0xFF2A7F7A),
            foreground = Color(0xFF1E3650)
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.title_pref_ui_mode_night),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            choices.forEach { choice ->
                val selected = if (choice.value == "0") {
                    selectedValue !in setOf("3", "4")
                } else selectedValue == choice.value
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(choice.value) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(choice.background)
                                .padding(7.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.52f)
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(choice.accent)
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(13.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(choice.surface)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(choice.accent)
                                    )
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .align(Alignment.CenterVertically)
                                            .clip(CircleShape)
                                            .background(choice.foreground.copy(alpha = 0.45f))
                                    )
                                }
                            }
                            if (selected) {
                                Text(
                                    text = "✓",
                                    color = choice.accent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                )
                            }
                        }
                        Text(
                            text = choice.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            text = choice.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2
                        )
                    }
                }
            }
        }
        if (selectedValue == "3") {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.theme_pattern_intensity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$patternIntensity%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = patternIntensity.toFloat(),
                onValueChange = { ThemeManager.setPatternIntensity(it.toInt()) },
                valueRange = 0f..100f,
                steps = 99
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.theme_reduced_motion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.theme_reduced_motion_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = reducedMotion,
                    onCheckedChange = { ThemeManager.setReducedMotionEnabled(it) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
