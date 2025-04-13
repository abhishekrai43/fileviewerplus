package com.arapps.fileviewplus.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun BottomBarActions(
    modifier: Modifier = Modifier,
    onSearch: () -> Unit,
    onToggleView: () -> Unit,
    onToggleServer: () -> Unit,
    onShareApp: () -> Unit,
    onVaultClick: () -> Unit,
    isServerRunning: Boolean,
    serverTypeToggle: @Composable () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Use FTP", style = MaterialTheme.typography.labelSmall)
            serverTypeToggle()
        }

        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color.Red)
        ) {
            NavigationBarItem(
                icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") },
                label = { Text("Vault") },
                selected = false,
                onClick = onVaultClick
            )

            NavigationBarItem(
                icon = { Icon(Icons.Filled.StarRate, contentDescription = "Rate") },
                label = { Text("Rate") },
                selected = false,
                onClick = {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            NavigationBarItem(
                icon = { Icon(Icons.Filled.Cloud, contentDescription = "Server") },
                label = { Text(if (isServerRunning) "Stop" else "Serve") },
                selected = false,
                onClick = onToggleServer
            )

            NavigationBarItem(
                icon = { Icon(Icons.Filled.Search, contentDescription = "Browse") },
                label = { Text("Browse") },
                selected = false,
                onClick = onSearch
            )

            NavigationBarItem(
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = "Update App") },
                label = { Text("Update") },
                selected = false,
                onClick = { onUpdateApp(context) } // ✅ Pass context here
            )

            NavigationBarItem(
                icon = { Icon(Icons.Filled.Share, contentDescription = "Share") },
                label = { Text("Share") },
                selected = false,
                onClick = onShareApp
            )
        }
    }
}

fun onUpdateApp(context: Context) {
    try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${context.packageName}")
        ).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        val fallbackIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallbackIntent)
    }
}
