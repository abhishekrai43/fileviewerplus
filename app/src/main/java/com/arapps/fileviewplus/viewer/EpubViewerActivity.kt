package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.arapps.fileviewplus.model.FileNode
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.io.InputStream
import java.io.BufferedInputStream
import java.io.FileInputStream

private const val EXTRA_PATH = "extra_path"

class EpubViewerActivity : ComponentActivity() {
    companion object {
        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = Intent(context, EpubViewerActivity::class.java).apply { putExtra(EXTRA_PATH, fileNode.path); putExtra("fromVault", fromVault) }
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private var openedPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathExtra = intent?.getStringExtra(EXTRA_PATH)
        if (pathExtra == null) {
            Toast.makeText(this, "No file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        openedPath = pathExtra

        // Unpack epub to cache and find an HTML entry
        val unpackDir = File(cacheDir, "epub_${System.currentTimeMillis()}")
        unpackDir.mkdirs()
        val entryToLoad = try {
            unzipEpub(File(pathExtra), unpackDir)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        if (entryToLoad == null) {
            Toast.makeText(this, "Unable to open EPUB", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EpubWebView(entryToLoad)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do not aggressively delete cache here; let OS or a periodic cleanup handle caches
    }
}

@Composable
fun EpubWebView(indexFile: File) {
    AndroidView(factory = { ctx ->
        val web = WebView(ctx)
        web.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.loadUrl("file:///${indexFile.absolutePath}")
        web
    }, modifier = Modifier.fillMaxSize())
}

// Simple unzip: extract all files and return the first .xhtml/.html/index.html found, else null
fun unzipEpub(epubFile: File, destDir: File): File? {
    ZipInputStream(BufferedInputStream(FileInputStream(epubFile))).use { zip ->
        var ze: ZipEntry? = zip.nextEntry
        var foundIndex: File? = null
        while (ze != null) {
            val name = ze.name
            val outFile = File(destDir, name)
            if (ze.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { out ->
                    val buffer = ByteArray(4096)
                    var count: Int
                    while (zip.read(buffer).also { count = it } != -1) out.write(buffer, 0, count)
                }
                if (foundIndex == null) {
                    val lower = name.lowercase()
                    if (lower.endsWith("index.html") || lower.endsWith("index.htm") || lower.endsWith(".xhtml") || lower.endsWith(".html")) {
                        foundIndex = outFile
                    }
                }
            }
            ze = zip.nextEntry
        }
        // If not found, try to locate any html/xhtml inside destDir
        if (foundIndex == null) {
            destDir.walkTopDown().forEach { f ->
                if (foundIndex == null && f.isFile && (f.name.endsWith(".html", true) || f.name.endsWith(".xhtml", true))) {
                    foundIndex = f
                }
            }
        }
        return foundIndex
    }
}

