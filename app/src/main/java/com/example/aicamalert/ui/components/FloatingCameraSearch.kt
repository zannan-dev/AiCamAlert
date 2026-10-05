package com.example.aicamalert.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

/** Bottom search control morphs into a panel above the navigation and keyboard. */
@Composable
fun FloatingCameraSearch(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedDistrict: String,
    onDistrictChange: (String) -> Unit,
    districts: List<String>,
    districtCounts: Map<String, Int>,
    selectedDistance: String,
    onDistanceChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchStateHolder = rememberSaveableStateHolder()
    val hasSearch = searchQuery.isNotBlank() || selectedDistrict != "All Districts" || selectedDistance != "All"
    fun closeSearch() {
        focusManager.clearFocus()
        keyboard?.hide()
        onExpandedChange(false)
    }
    BackHandler(enabled = expanded) { closeSearch() }
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
        val panelWidth = maxWidth.coerceAtMost(520.dp)
        AnimatedContent(
            targetState = expanded,
            contentAlignment = Alignment.BottomEnd,
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(100)))
                    .using(SizeTransform(clip = true) { _, _ ->
                        spring(dampingRatio = 1f, stiffness = 240f)
                    })
            },
            label = "Floating search expansion",
        ) { open ->
            if (open) {
                searchStateHolder.SaveableStateProvider("search_controls") {
                    Surface(
                        modifier = Modifier.width(panelWidth),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 10.dp,
                    ) {
                        Column(Modifier.padding(top = 4.dp, bottom = 16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Search & filters", style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f))
                                IconButton(onClick = { closeSearch() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            }
                            SearchAndFilters(searchQuery, onSearchChange, selectedDistrict, onDistrictChange,
                                districts, districtCounts, selectedDistance, onDistanceChange, autoFocus = true)
                        }
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = { onExpandedChange(true) },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    BadgedBox(badge = { if (hasSearch) Badge() }) {
                        Icon(Icons.Default.Search,
                            contentDescription = if (hasSearch) "Search cameras, filters active" else "Search cameras")
                    }
                }
            }
        }
    }
}
