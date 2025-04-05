package com.example.fileviewplus.server

import android.os.Environment
import com.example.fileviewplus.logic.FileScanner
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import java.io.File

object HttpFileServer {

    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            routing {
                // Root: Show top-level categories
                get("/") {
                    val categories = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                    val html = buildHtml {
                        h1("📁 FileViewPlus")
                        for (cat in categories) {
                            link("/browse?path=${cat.name}", cat.name)
                        }
                    }
                    call.respondText(html, ContentType.Text.Html)
                }

                // Browse subfolders
                get("/browse") {
                    val path = call.request.queryParameters["path"] ?: return@get call.respondText(
                        "Missing path",
                        status = HttpStatusCode.BadRequest
                    )
                    val base = Environment.getExternalStorageDirectory()
                    val folder = File(base, path)

                    if (!folder.exists() || !folder.isDirectory) {
                        return@get call.respondText("Folder not found", status = HttpStatusCode.NotFound)
                    }

                    val children = folder.listFiles()?.sortedBy { it.name } ?: emptyList()
                    val html = buildHtml {
                        h2("📂 $path")
                        link("/", "⬅ Back")
                        for (file in children) {
                            val subPath = "$path/${file.name}".replace("//", "/")
                            if (file.isDirectory) {
                                link("/browse?path=$subPath", "📁 ${file.name}")
                            } else {
                                link("/file?path=$subPath", "📄 ${file.name}")
                            }
                        }
                    }
                    call.respondText(html, ContentType.Text.Html)
                }

                // Serve actual file
                get("/file") {
                    val path = call.request.queryParameters["path"] ?: return@get call.respondText(
                        "Missing file path",
                        status = HttpStatusCode.BadRequest
                    )
                    val file = File(Environment.getExternalStorageDirectory(), path)
                    if (!file.exists() || file.isDirectory) {
                        return@get call.respondText("File not found", status = HttpStatusCode.NotFound)
                    }
                    call.respondFile(file)
                }
            }
        }.start(wait = false)
    }

    private fun buildHtml(block: HtmlBuilder.() -> Unit): String {
        return buildString {
            append("<html><body style='font-family:sans-serif;padding:2em;'>")
            HtmlBuilder(this).block()
            append("</body></html>")
        }
    }

    private class HtmlBuilder(private val out: StringBuilder) {
        fun h1(text: String) = out.append("<h1>$text</h1>")
        fun h2(text: String) = out.append("<h2>$text</h2>")
        fun link(href: String, label: String) =
            out.append("<div style='margin: 8px 0;'><a href=\"$href\">$label</a></div>")
    }
}
