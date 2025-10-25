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
    // read status bar inset once
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Modern premium gradient header with rounded bottom - REDUCED HEIGHT
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // REDUCED from 92.dp to 64.dp for compact layout
            .height(64.dp)
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
                .padding(horizontal = 16.dp)
                // push content down by statusBarTop so it sits below the status bar
                .padding(top = statusBarTop)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onGoHome() }
            ) {
                // simple circular logo pill - REDUCED SIZE
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onPrimary)
                ) {
                    // small emoji/letter centered
                    Text(
                        text = "FF",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "FileFlow Plus",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    // Compact subtitle
                    Text(
                        text = if (isScanning) "Refreshing..." else "Tap Refresh to rescan storage",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Theme toggle - SMALLER SIZE
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = onToggleTheme,
                    modifier = Modifier.height(24.dp)
                )

                // Refresh button - SMALLER
                IconButton(
                    onClick = onRefresh,
                    enabled = !isScanning,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
