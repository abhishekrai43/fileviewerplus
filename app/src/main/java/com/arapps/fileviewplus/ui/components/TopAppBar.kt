package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FileViewTopAppBar(
    isDarkMode: Boolean,
    onGoHome: () -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    isScanning: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    // Modern premium gradient header with rounded bottom
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            )
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onGoHome() }
            ) {
                // simple circular logo pill
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onPrimary)
                ) {
                    // small emoji/letter centered
                    Text(
                        text = "FF",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "FileFlow Plus",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Short explanatory subtitle so users know what the refresh pill does
                    Text(
                        text = if (isScanning) "Refreshing..." else "Tap Refresh to rescan storage",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }

                // Add extra spacing so the theme switch isn't visually cramped against the title
                Spacer(modifier = Modifier.width(16.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Theme toggle (label removed — switch only)
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = onToggleTheme,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.secondary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Compact vertical refresh control: icon (or spinner) and caption below
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh files")
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isScanning) "Refreshing..." else "Refresh files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f)
                    )
                }
            }
        }
    }
}
