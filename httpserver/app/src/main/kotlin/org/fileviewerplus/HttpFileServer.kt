package com.fileviewplus.httpserver


import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import java.io.File

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            jackson()
        }
        routing {
            get("/") {
                call.respondText("Ktor server is running!")
            }
            get("/file") {
                val file = File("/sdcard/Download/sample.txt") // change this!
                if (file.exists()) {
                    call.respondFile(file)
                } else {
                    call.respondText("File not found", status = io.ktor.http.HttpStatusCode.NotFound)
                }
            }
        }
    }.start(wait = true)
}
