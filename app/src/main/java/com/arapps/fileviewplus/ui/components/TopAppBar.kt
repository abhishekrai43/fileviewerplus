package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FileViewTopAppBar(
    showMenu: Boolean = false,
    isDarkMode: Boolean,
    onGoHome: () -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onMenuClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .shadow(1.dp),

        color = MaterialTheme.colorScheme.surface


    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp) // fixed height for app bar itself
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "📁 FileFlow Plus", // <- kept your emoji branding
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isDarkMode) "Dark" else "Light",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = onToggleTheme,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}


