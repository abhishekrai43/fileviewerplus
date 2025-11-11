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
import androidx.compose.ui.unit.sp

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
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Use FTP", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
            serverTypeToggle()
        }

        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            tonalElevation = 3.dp
        ) {
            NavigationBarItem(
                icon = { Icon(Icons.Default.Lock, contentDescription = "Vault", modifier = Modifier.size(20.dp)) },
                label = { Text("Vault", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
                selected = false,
                onClick = onVaultClick
            )

            NavigationBarItem(
                icon = { Icon(Icons.Filled.StarRate, contentDescription = "Rate", modifier = Modifier.size(20.dp)) },
                label = { Text("Rate", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
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
                icon = { Icon(Icons.Filled.Cloud, contentDescription = "Server", modifier = Modifier.size(20.dp)) },
                label = { Text(if (isServerRunning) "Stop" else "Serve", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
                selected = false,
                onClick = onToggleServer
            )

            NavigationBarItem(
                icon = { Icon(Icons.Filled.Search, contentDescription = "Browse", modifier = Modifier.size(20.dp)) },
                label = { Text("Browse", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
                selected = false,
                onClick = onSearch
            )

            NavigationBarItem(
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = "Update App", modifier = Modifier.size(20.dp)) },
                label = { Text("Update", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
                selected = false,
                onClick = { onUpdateApp(context) }
            )

            NavigationBarItem(
                icon = { Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(20.dp)) },
                label = { Text("Share", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp) },
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