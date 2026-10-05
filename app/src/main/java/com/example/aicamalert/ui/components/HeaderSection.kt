package com.example.aicamalert.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** Theme-aware translucent scrim over the map, with primary controls in one row. */
@Composable
fun HeaderSection(
    radarEnabled: Boolean,
    onRadarToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shade = MaterialTheme.colorScheme.background
    val foreground = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(
                shade.copy(alpha = 0.82f),
                shade.copy(alpha = 0.62f),
                Color.Transparent,
            )))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ))
            .padding(start = 16.dp, end = 12.dp, bottom = 24.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("AiCam Alert", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, color = foreground,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = radarEnabled,
            onCheckedChange = onRadarToggle,
            thumbContent = {
                Icon(
                    if (radarEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedIconColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.semantics { contentDescription = "Camera alerts" },
        )
    }
}
