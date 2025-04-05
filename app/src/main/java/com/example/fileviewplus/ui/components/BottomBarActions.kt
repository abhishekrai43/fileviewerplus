package com.example.fileviewplus.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BottomBarActions() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // avoids overlay by system nav bar
            .padding(16.dp)
    ) {
        // Top: Share + Hint
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Check out FileViewPlus!")
                }
                context.startActivity(Intent.createChooser(intent, "Share via"))
            }) {
                Text("📤 Share App")
            }

            Text(
                text = "💡 Need to Rename or Delete files?\nStart FTP and connect from a PC/Mac.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }

        // Bottom: Divider + FTP button
        HorizontalDivider(modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                // TODO: Start FTP service here
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text("Start FTP Server")
        }
    }
}
