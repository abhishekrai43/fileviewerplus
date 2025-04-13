package com.arapps.fileviewplus.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.utils.NotificationUtils
import com.arapps.fileviewplus.utils.isOnWifi
import com.arapps.ftpserver.FtpServerController
import com.arapps.fileviewplus.server.HttpFileServer
import com.arapps.fileviewplus.utils.getLocalIpAddress

@Composable
fun ServerControlPanel() {
    val context = LocalContext.current
    var serverStarted by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("") }
    var useFtp by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf(0) }
    var protocol by remember { mutableStateOf("http") }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!serverStarted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Use FTP Server",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(checked = useFtp, onCheckedChange = { useFtp = it })
            }

            Button(
                onClick = {
                    if (!isOnWifi(context)) {
                        Toast.makeText(context, "Please connect to Wi-Fi", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    val ip = getLocalIpAddress()
                    NotificationUtils.createNotificationChannel(context)

                    protocol = if (useFtp) "FTP" else "HTTP"
                    port = if (useFtp) 2121 else 8080

                    if (useFtp) {
                        FtpServerController.start(context)
                        NotificationUtils.showServerRunningNotification(context, ip, port)
                    } else {
                        HttpFileServer.start()
                        NotificationUtils.showServerRunningNotification(context, ip, port)
                    }

                    ipAddress = ip
                    serverStarted = true
                    showDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Start Local File Sharing Server")
            }
        } else {
            val currentProtocol = if (useFtp) "ftp" else "http"
            val currentPort = if (useFtp) 2121 else 8080

            AssistChip(
                onClick = { showDialog = true },
                label = { Text("Server Running") },
                leadingIcon = {
                    Icon(Icons.Default.CloudDone, contentDescription = null)
                },
                modifier = Modifier
                    .padding(start = 16.dp, bottom = 12.dp)
                    .align(Alignment.Start)
            )

            Text(
                "🌐 Connect via $currentProtocol://$ipAddress:$currentPort",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )

            Text(
                "No username or password is required.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
            )

            Button(
                onClick = {
                    if (useFtp) FtpServerController.stop() else HttpFileServer.stop()
                    NotificationUtils.cancelServerNotification(context)

                    ipAddress = ""
                    serverStarted = false
                    showDialog = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Stop Server")
            }
        }
    }

    if (showDialog) {
        ServerStartedDialog(
            protocol = protocol,
            ip = ipAddress,
            port = port,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun ServerStartedDialog(
    protocol: String,
    ip: String,
    port: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Started ✅") },
        text = {
            Text(
                "Protocol: $protocol\n" +
                        "IP Address: $ip\n" +
                        "Port: $port\n\n" +
                        "No username/password needed.\n" +
                        "For FTP use Filezilla, or for HTTP simply type $ip:$port in Chrome or safari.\n" +
                        "You might not see all files in folders protected by Android (WhatsApp/data etc)"
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
