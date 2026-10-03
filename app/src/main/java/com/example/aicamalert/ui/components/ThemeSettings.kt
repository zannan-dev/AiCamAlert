package com.example.aicamalert.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.aicamalert.ui.theme.AppThemeMode

@Composable
fun ThemeSettings(selectedMode: AppThemeMode, onModeChange: (AppThemeMode) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text("Theme", style = MaterialTheme.typography.titleSmall)
        AppThemeMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = selectedMode == mode, role = Role.RadioButton,
                        onClick = { onModeChange(mode) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selectedMode == mode, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(if (mode == AppThemeMode.SYSTEM) "System (follow phone)" else mode.label)
            }
        }
    }
}
