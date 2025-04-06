package com.arapps.fileviewplus.server

import android.os.Environment
import com.arapps.fileviewplus.logic.FileScanner
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

object HttpFileServer {

    private var isRunning = false
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        if (isRunning) return
        isRunning = true

        server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            routing {
                get("/") {
                    val categories = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                    val html = buildHtml {
                        h1("\uD83D\uDCC1 FileViewPlus - Categories")
                        for (cat in categories) {
                            val encoded = URLEncoder.encode(cat.name, "UTF-8")
                            link("/browse?path=$encoded", "\uD83D\uDCC2 ${cat.name}")
                        }
                    }
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/browse") {
                    val categoryName = call.request.queryParameters["path"]
                        ?: return@get call.respondText("Missing path", status = HttpStatusCode.BadRequest)

                    val categories = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                    val category = categories.find { it.name == categoryName }

                    if (category == null) {
                        return@get call.respondText("Category not found", status = HttpStatusCode.NotFound)
                    }

                    val html = buildHtml {
                        h1("\uD83D\uDCC2 Category: $categoryName")
                        link("/", "\u2B05 Back to Categories")
                        for (month in category.months) {
                            val encodedCat = URLEncoder.encode(categoryName, "UTF-8")
                            val encodedMonth = URLEncoder.encode(month.name, "UTF-8")
                            val totalFiles = month.days.sumOf { it.files.size }
                            link(
                                "/browseMonth?category=$encodedCat&month=$encodedMonth",
                                "\uD83D\uDCC5 ${month.name} ($totalFiles files)"
                            )
                        }
                    }
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/browseDay") {
                    val category = call.request.queryParameters["category"]
                    val month = call.request.queryParameters["month"]
                    val day = call.request.queryParameters["day"]

                    if (category == null || month == null || day == null) {
                        return@get call.respondText("Missing parameters", status = HttpStatusCode.BadRequest)
                    }

                    val categories = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                    val dayFolder = categories
                        .find { it.name == category }
                        ?.months?.find { it.name == month }
                        ?.days?.find { it.name == day }

                    if (dayFolder == null) {
                        return@get call.respondText("Day folder not found", status = HttpStatusCode.NotFound)
                    }

                    val html = buildHtml {
                        h2("\uD83D\uDDC2\uFE0F Files on $day")
                        val backLink = URLEncoder.encode("/browseMonth?category=$category&month=$month", "UTF-8")
                        link("/browseMonth?category=$category&month=$month", "\u2B05 Back to $month")
                        this.br()
                        for (file in dayFolder.files) {
                            val rawPath = "${file.path}"
                            val encodedPath = URLEncoder.encode(rawPath, "UTF-8")
                            link("/file?path=$encodedPath", file.name)
                        }
                    }

                    call.respondText(html, ContentType.Text.Html)
                }

                get("/browseMonth") {
                    val categoryName = call.request.queryParameters["category"]
                    val monthName = call.request.queryParameters["month"]

                    if (categoryName == null || monthName == null) {
                        call.respondText("Missing parameters", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    val categories = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                    val category = categories.find { it.name == categoryName }
                    val month = category?.months?.find { it.name == monthName }

                    if (category == null || month == null) {
                        call.respondText("Folder not found", status = HttpStatusCode.NotFound)
                        return@get
                    }

                    val html = buildHtml {
                        h1("\uD83D\uDCCB Month: $monthName")
                        val catEncoded = URLEncoder.encode(categoryName, "UTF-8")
                        link("/browse?path=$catEncoded", "\u2B05 Back to $categoryName")

                        for (day in month.days) {
                            h2("\uD83D\uDCC5 ${day.name}")
                            for (file in day.files) {
                                val path = file.path
                                val encoded = URLEncoder.encode(path, "UTF-8")
                                link("/file?path=$encoded", "\uD83D\uDCC4 ${file.name}")
                            }
                        }
                    }
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/file") {
                    val encodedPath = call.request.queryParameters["path"]
                        ?: return@get call.respondText("Missing file path", status = HttpStatusCode.BadRequest)

                    val decodedPath = URLDecoder.decode(encodedPath, "UTF-8")
                    val file = File(decodedPath)

                    if (!file.exists() || file.isDirectory) {
                        return@get call.respondText("File not found: ${file.absolutePath}", status = HttpStatusCode.NotFound)
                    }

                    call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.name}\"")
                    call.respondFile(file)
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        isRunning = false
    }
}

private fun loaderHtml(): String {
    return """
        <html><head><meta charset='UTF-8'/>
        <style>
        body { font-family: sans-serif; background: #f4f6f9; color: #333; display: flex; align-items: center; justify-content: center; height: 100vh; }
        .loader { text-align: center; }
        .spinner { border: 6px solid #eee; border-top: 6px solid #007bff; border-radius: 50%; width: 50px; height: 50px; animation: spin 1s linear infinite; margin: auto; }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        </style>
        <script>
            let messages = ["Scanning storage...", "Organizing folders...", "Indexing categories..."];
            let i = 0;
            setInterval(() => {
                document.getElementById("status-text").innerText = messages[i % messages.length];
                i++;
            }, 2000);
        </script>
        </head><body>
        <div class='loader'>
            <div class='spinner'></div>
            <p id='status-text' style='margin-top: 16px;'>Initializing...</p>
        </div>
        </body></html>
    """.trimIndent()
}

private fun buildHtml(block: HtmlBuilder.() -> Unit): String {
    return buildString {
        append(
            """
            <html>
              <head>
                <meta charset=\"UTF-8\"/>
                <title>FileViewPlus</title>
                <style>
                  body { font-family: sans-serif; padding: 1.5em; background: #f4f6f9; color: #222; }
                  a { text-decoration: none; color: #0056d6; }
                  .link { margin: 6px 0; }
                  .icon { margin-right: 8px; }
                  h1, h2, h3 { color: #1a1a1a; }
                  .back { display: inline-block; margin-bottom: 16px; }
                  .file-explorer { max-width: 700px; margin: auto; background: white; padding: 24px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
                </style>
              </head>
              <body>
                <div class=\"file-explorer\">
            """.trimIndent()
        )

        HtmlBuilder(this).block()
        append("</div></body></html>")
    }
}

private class HtmlBuilder(private val out: StringBuilder) {
    fun h1(text: String) = out.append("<h1>$text</h1>\n")
    fun h2(text: String) = out.append("<h2>$text</h2>\n")
    fun link(href: String, label: String) = out.append("<div><a href=\"$href\">$label</a></div>\n")
    fun br() = out.append("<br/>")
}
