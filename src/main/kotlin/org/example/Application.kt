package org.example

import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.CORS
import org.example.routes.conversionRoutes
import io.ktor.http.*

// import routing module
import org.example.utilities.ConversionRouteConfig

import java.io.File
import java.nio.file.Files

// redis import
import redis.clients.jedis.JedisPool

// Config constants to be passed to routing module
private val FFMPEG_PATH: String = System.getenv("FFMPEG_PATH")
    ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffmpeg.exe"
private val FFPROBE_PATH: String = System.getenv("FFPROBE_PATH")
    ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffprobe.exe"

// Temp directory for temporary uploaded & converted files
private val TEMP_FILES_BASE_DIR: File = Files.createTempDirectory("convertaphile").toFile().apply{ deleteOnExit() }

// redis setup
private val REDIS_HOST: String = System.getenv("REDIS_HOST") ?: "localhost"
private val REDIS_PORT: Int = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379

fun Application.module() {
    // install KTOR plugins
    install(ContentNegotiation) {
        json()
    }

    // Add CORS support for frontend-backend communication
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        anyHost() // For development only - restrict this in production
    }

    // create redis connection pool
    val jedisPool = JedisPool(REDIS_HOST, REDIS_PORT)

    // configure configuration object for application routes
    val routeConfig = ConversionRouteConfig(
        ffmpegExecutablePath = FFMPEG_PATH,
        ffprobeExecutablePath = FFPROBE_PATH,
        tempFilesBaseDir = TEMP_FILES_BASE_DIR,
        jedisPool = jedisPool,
    )

    // use routing logic and call extension function from class to register routes
    routing {
        conversionRoutes(routeConfig)
    }
}

// Ktor App Module
// embeddedServer creates and starts an HTTP server
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true) // start server & keep main thread alive to wait for requests
}
