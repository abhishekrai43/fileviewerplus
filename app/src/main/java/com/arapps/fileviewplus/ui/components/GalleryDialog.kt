package com.arapps.fileviewplus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.arapps.fileviewplus.model.FileNode
import java.io.File

@Composable
fun GalleryDialog(files: List<FileNode>, startIndex: Int = 0, onClose: () -> Unit) {
    val cfg = LocalConfiguration.current
    val screenWidth = cfg.screenWidthDp.dp
    val listState = rememberLazyListState(startIndex)

    Dialog(onDismissRequest = onClose) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center
            ) {
                itemsIndexed(files) { _, f ->
                    Box(modifier = Modifier
                        .width(screenWidth)
                        .fillMaxHeight()) {
                        val painter = rememberAsyncImagePainter(File(f.path))
                        Image(
                            painter = painter,
                            contentDescription = f.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(f.name, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onClose() }) { Icon(Icons.Default.Close, contentDescription = "Close") }
                        }
                    }
                }
            }

            // page indicator
            val idx = (listState.firstVisibleItemIndex + 1).coerceIn(1, files.size)
            Text(text = "$idx / ${files.size}", modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

