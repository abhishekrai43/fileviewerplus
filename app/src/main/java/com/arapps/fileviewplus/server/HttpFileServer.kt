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
import java.util.concurrent.atomic.AtomicBoolean

object HttpFileServer {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val isRunning = AtomicBoolean(false)

    fun start() {
        if (isRunning.get()) return

        server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            routing {
                get("/") {
                    val categories = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                    val html = buildHtml {
                        h1("📁 FileViewPlus - Categories")
                        for (cat in categories) {
                            val encoded = URLEncoder.encode(cat.name, "UTF-8")
                            link("/browse?category=$encoded", "📂 ${cat.name}")
                        }
                    }
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/browse") {
                    val categoryName = call.request.queryParameters["category"]
                        ?: return@get call.respondText("Missing category", status = HttpStatusCode.BadRequest)

                    val categories = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                    val category = categories.find { it.name == categoryName }
                        ?: return@get call.respondText("Category not found", status = HttpStatusCode.NotFound)

                    val html = buildHtml {
                        h1("📂 Category: $categoryName")
                        link("/", "⬅ Back to Categories")

                        for (year in category.years.sortedByDescending { it.name.toIntOrNull() ?: 0 }) {
                            for (month in year.months) {
                                val encCat = URLEncoder.encode(categoryName, "UTF-8")
                                val encYear = URLEncoder.encode(year.name, "UTF-8")
                                val encMonth = URLEncoder.encode(month.name, "UTF-8")
                                val totalFiles = month.days.sumOf { it.files.size }

                                link(
                                    "/browseMonth?category=$encCat&year=$encYear&month=$encMonth",
                                    "📅 ${month.name} $encYear ($totalFiles files)"
                                )
                            }
                        }
                    }

                    call.respondText(html, ContentType.Text.Html)
                }

                get("/browseMonth") {
                    val categoryName = call.parameters["category"]
                    val yearName = call.parameters["year"]
                    val monthName = call.parameters["month"]

                    if (categoryName == null || yearName == null || monthName == null) {
                        call.respondText("Missing parameters", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    val categories = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                    val category = categories.find { it.name == categoryName }
                    val year = category?.years?.find { it.name == yearName }
                    val month = year?.months?.find { it.name == monthName }

                    if (month == null) {
                        call.respondText("Month not found", status = HttpStatusCode.NotFound)
                        return@get
                    }

                    val html = buildHtml {
                        h1("📅 $monthName $yearName")
                        val encCat = URLEncoder.encode(categoryName, "UTF-8")
                        link("/browse?category=$encCat", "⬅ Back to $categoryName")

                        for (day in month.days) {
                            val encDay = URLEncoder.encode(day.name, "UTF-8")
                            link(
                                "/browseDay?category=$encCat&year=$yearName&month=$monthName&day=$encDay",
                                "📆 ${day.name} (${day.files.size} files)"
                            )
                        }
                    }

                    call.respondText(html, ContentType.Text.Html)
                }

                get("/browseDay") {
                    val categoryName = call.parameters["category"]
                    val yearName = call.parameters["year"]
                    val monthName = call.parameters["month"]
                    val dayName = call.parameters["day"]

                    if (categoryName == null || yearName == null || monthName == null || dayName == null) {
                        call.respondText("Missing parameters", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    val categories = FileScanner.scanStorage(Environment.getExternalStorageDirectory())
                    val category = categories.find { it.name == categoryName }
                    val year = category?.years?.find { it.name == yearName }
                    val month = year?.months?.find { it.name == monthName }
                    val day = month?.days?.find { it.name == dayName }

                    if (day == null) {
                        call.respondText("Day not found", status = HttpStatusCode.NotFound)
                        return@get
                    }

                    val html = buildHtml {
                        h2("📂 Files on $dayName")
                        val encMonth = URLEncoder.encode(monthName, "UTF-8")
                        link(
                            "/browseMonth?category=$categoryName&year=$yearName&month=$encMonth",
                            "⬅ Back to $monthName $yearName"
                        )
                        br()
                        for (file in day.files) {
                            val encodedPath = URLEncoder.encode(file.path, "UTF-8")
                            link("/file?path=$encodedPath", "📄 ${file.name}")
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

        isRunning.set(true)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        isRunning.set(false)
    }

    fun isRunning(): Boolean = isRunning.get()
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
