package org.example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.example.routes.conversionRoutes

// import routing module
import org.example.utilities.ConversionRouteConfig

import java.io.File
import java.nio.file.Files

// Config constants to be passed to routing module
private val FFMPEG_PATH: String = System.getenv("FFMPEG_PATH")
    ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffmpeg.exe"
private val FFPROBE_PATH: String = System.getenv("FFPROBE_PATH")
    ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffprobe.exe"

// Temp directory for temporary uploaded & converted files
private val TEMP_FILES_BASE_DIR: File = Files.createTempDirectory("convertaphile").toFile().apply{ deleteOnExit() }

// Ktor App Module
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        // install KTOR plugins
        install(ContentNegotiation) {
            json()
        }

        // configure configuration object for application routes
        val routeConfig = ConversionRouteConfig(
            ffmpegExecutablePath = FFMPEG_PATH,
            ffprobeExecutablePath = FFPROBE_PATH,
            tempFilesBaseDir = TEMP_FILES_BASE_DIR
        )

        // use routing logic and call extension function from class to register routes
        routing {
            conversionRoutes(routeConfig)
        }
    }.start(wait = true)
}
