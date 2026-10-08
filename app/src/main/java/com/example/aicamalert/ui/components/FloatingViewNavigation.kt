package com.example.aicamalert.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
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
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var previewListView by remember { mutableStateOf<Boolean?>(null) }
    val currentOnSelectView by rememberUpdatedState(onSelectView)
    val currentIsListView by rememberUpdatedState(isListView)
    val layoutDirection = LocalLayoutDirection.current
    val isPressed = dragProgress != null
    val activeListView = previewListView ?: isListView
    val selectionProgress by animateFloatAsState(
        targetValue = dragProgress ?: if (isListView) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = if (isPressed) 2000f else Spring.StiffnessMediumLow,
        ),
        label = "Sliding navigation pill",
    )
    val pillScale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "Navigation pill press scale",
    )
    val selectionColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.widthIn(max = 328.dp).fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.selectableGroup().padding(6.dp)
                .pointerInput(layoutDirection) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        down.consume()
                        val gap = 4.dp.toPx()
                        val tabWidth = (size.width - gap) / 2f
                        fun preview(x: Float) {
                            val physicalProgress = ((x - tabWidth / 2f) / (tabWidth + gap))
                                .coerceIn(0f, 1f)
                            dragProgress = if (layoutDirection == LayoutDirection.Rtl)
                                1f - physicalProgress else physicalProgress
                            val rightTab = x >= size.width / 2f
                            previewListView = if (layoutDirection == LayoutDirection.Rtl)
                                !rightTab else rightTab
                        }
                        try {
                            preview(down.position.x)
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (change.isConsumed) break
                                preview(change.position.x)
                                change.consume()
                                if (!change.pressed) {
                                    previewListView?.let { selected ->
                                        if (selected != currentIsListView) currentOnSelectView(selected)
                                    }
                                    break
                                }
                            }
                        } finally {
                            dragProgress = null
                            previewListView = null
                        }
                    }
                }
                .drawBehind {
                val gap = 4.dp.toPx()
                val tabWidth = (size.width - gap) / 2
                val progress = if (layoutDirection == LayoutDirection.Rtl) 1f - selectionProgress
                    else selectionProgress
                drawRoundRect(
                    color = selectionColor,
                    topLeft = Offset(
                        progress * (tabWidth + gap) - tabWidth * (pillScale - 1f) / 2f,
                        -size.height * (pillScale - 1f) / 2f,
                    ),
                    size = Size(tabWidth * pillScale, size.height * pillScale),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(false, true).forEach { listView ->
                val selected = activeListView == listView
                val contentScale by animateFloatAsState(
                    targetValue = if (selected) 1.05f else 0.96f,
                    animationSpec = spring(dampingRatio = 0.75f),
                    label = "Navigation item scale",
                )
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
                            selected = isListView == listView,
                            role = Role.Tab,
                            onClick = { onSelectView(listView) },
                        )
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .graphicsLayer { scaleX = contentScale; scaleY = contentScale },
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
