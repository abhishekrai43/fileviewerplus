package com.arapps.fileviewplus.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arapps.fileviewplus.ui.theme.FileFlowPlusTheme
import java.io.File
import com.google.gson.GsonBuilder
import org.xml.sax.InputSource
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

class JsonXmlPreviewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PATH = "path"
        fun launch(context: Context, path: String) {
            val i = Intent(context, JsonXmlPreviewActivity::class.java).apply { putExtra(EXTRA_PATH, path) }
            if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "No file to preview", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val previewText = try {
            val f = File(path)
            val ext = f.extension.lowercase()
            val raw = f.readText()
            when (ext) {
                "json" -> prettyJson(raw)
                "xml" -> prettyXml(raw)
                else -> raw
            }
        } catch (e: Exception) {
            "Unable to preview: ${e.localizedMessage}"
        }

        setContent {
            FileFlowPlusTheme {
                JsonXmlPreviewScreen(previewText = previewText)
            }
        }
    }

    private fun prettyJson(raw: String): String {
        return try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val obj = com.google.gson.JsonParser.parseString(raw)
            gson.toJson(obj)
        } catch (e: Exception) { raw }
    }

    private fun prettyXml(raw: String): String {
        return try {
            val tf = TransformerFactory.newInstance()
            val transformer = tf.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            val sw = StringWriter()
            val sr = StreamSource(java.io.StringReader(raw))
            val result = StreamResult(sw)
            transformer.transform(sr, result)
            sw.toString()
        } catch (e: Exception) { raw }
    }
}

@Composable
fun JsonXmlPreviewScreen(previewText: String) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Text(text = previewText, style = MaterialTheme.typography.bodyMedium)
    }
}

