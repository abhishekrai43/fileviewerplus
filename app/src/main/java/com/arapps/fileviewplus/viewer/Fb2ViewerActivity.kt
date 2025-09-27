package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import javax.xml.parsers.DocumentBuilderFactory

private const val EXTRA_PATH = "extra_path"

class Fb2ViewerActivity : ComponentActivity() {
    companion object {
        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = Intent(context, Fb2ViewerActivity::class.java).apply { putExtra(EXTRA_PATH, fileNode.path); putExtra("fromVault", fromVault) }
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

        val html = try { convertFb2ToHtml(File(pathExtra)) } catch (e: Exception) { null }

        if (html == null) {
            Toast.makeText(this, "Unable to open FB2", Toast.LENGTH_LONG).show()
            finish(); return
        }

        setContent {
            MaterialTheme {
                Surface {
                    Fb2WebView(html)
                }
            }
        }
    }
}

@Composable
fun Fb2WebView(html: String) {
    AndroidView(factory = { ctx ->
        val web = WebView(ctx)
        web.settings.javaScriptEnabled = false
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        web
    }, modifier = Modifier.fillMaxSize())
}

fun convertFb2ToHtml(file: File): String? {
    val dbf = DocumentBuilderFactory.newInstance()
    val db = dbf.newDocumentBuilder()
    val doc = db.parse(file)
    val bodies = doc.getElementsByTagName("body")
    if (bodies.length == 0) return null
    val sb = StringBuilder()
    sb.append("<html><head><meta charset=\"utf-8\"><style>body{font-family: sans-serif; padding:16px;} p{margin-bottom:12px;}</style></head><body>")
    for (i in 0 until bodies.length) {
        val body = bodies.item(i)
        val paragraphs = body.childNodes
        for (j in 0 until paragraphs.length) {
            val node = paragraphs.item(j)
            val name = node.nodeName.lowercase()
            if (name == "section") {
                val chars = node.textContent
                if (!chars.isNullOrBlank()) sb.append("<p>").append(escapeHtml(chars.trim())).append("</p>")
            } else if (name == "p") {
                val chars = node.textContent
                if (!chars.isNullOrBlank()) sb.append("<p>").append(escapeHtml(chars.trim())).append("</p>")
            }
        }
    }
    sb.append("</body></html>")
    return sb.toString()
}

fun escapeHtml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>")

