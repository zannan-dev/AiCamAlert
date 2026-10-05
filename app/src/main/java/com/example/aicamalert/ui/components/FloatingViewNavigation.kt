package com.example.aicamalert.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Floating view tabs; the caller supplies safe-area padding and overlay placement. */
@Composable
fun FloatingViewNavigation(
    isListView: Boolean,
    onSelectView: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionProgress by animateFloatAsState(
        targetValue = if (isListView) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f),
        label = "Sliding navigation pill",
    )
    val selectionColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.widthIn(max = 328.dp).fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.selectableGroup().padding(6.dp).drawBehind {
                val gap = 4.dp.toPx()
                val tabWidth = (size.width - gap) / 2
                val progress = if (layoutDirection == LayoutDirection.Rtl) 1f - selectionProgress
                    else selectionProgress
                drawRoundRect(
                    color = selectionColor,
                    topLeft = Offset(progress * (tabWidth + gap), 0f),
                    size = Size(tabWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(false, true).forEach { listView ->
                val selected = isListView == listView
                val foreground by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(220),
                    label = "Navigation selection content",
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelectView(listView) },
                        )
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (listView) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.Map,
                        contentDescription = null,
                        tint = foreground,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (listView) "List" else "Map",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = foreground,
                    )
                }
            }
        }
    }
}
