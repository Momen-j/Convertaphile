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
import java.util.concurrent.TimeUnit

// redis import
import redis.clients.jedis.JedisPool

// Coroutines imports for cleanup scheduler
import kotlinx.coroutines.*

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

// file cleanup config
private val FILE_EXPIRATION_HOURS: Long = System.getenv("FILE_EXPIRATION_HOURS")?.toLongOrNull() ?: 2L
private val CLEANUP_INTERVAL_HOURS: Long = System.getenv("CLEANUP_INTERVAL_HOURS")?.toLongOrNull() ?: 1L

/**
 * Starts a background coroutine that periodically cleans up expired files
 */
suspend fun startFileCleanupScheduler(tempFilesBaseDir: File) {
    // Use GlobalScope for application-lifetime coroutines
    GlobalScope.launch(Dispatchers.IO) {
        println("🧹 File cleanup scheduler started - checking every $CLEANUP_INTERVAL_HOURS hour(s)")
    }

    while (true) {
        try {
            delay(TimeUnit.HOURS.toMillis(CLEANUP_INTERVAL_HOURS))
            cleanupExpiredFiles(tempFilesBaseDir)
        } catch (e: Exception) {
            System.err.println("Error in cleanup scheduler: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * Cleans up files older than FILE_EXPIRATION_HOURS
 * Creates a cutoff time using current time - FILE_EXPIRATION_HOURS
 * Any file created before that cutoff time is auto deleted
 */
fun cleanupExpiredFiles(tempFilesBaseDir: File) {
    try {
        val permanentStorageDir = File(tempFilesBaseDir.parent, "converted_files")

        if (!permanentStorageDir.exists()) {
            println("🧹 Storage directory doesn't exist yet, skipping cleanup")
            return
        }

        val expirationTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(FILE_EXPIRATION_HOURS)
        var cleanedCount = 0
        var totalSizeCleaned = 0L

        permanentStorageDir.listFiles()?.forEach { file ->
            if (file.lastModified() < expirationTime) {
                try {
                    val fileSize = file.length()

                    // Delete the file (Redis stats are preserved)
                    if (file.delete()) {
                        cleanedCount++
                        totalSizeCleaned += fileSize
                        println("🗑️ Cleaned up expired file: ${file.name}")
                    } else {
                        System.err.println("⚠️ Failed to delete expired file: ${file.name}")
                    }
                } catch (e: Exception) {
                    System.err.println("⚠️ Error processing file ${file.name}: ${e.message}")
                }
            }
        }

        if (cleanedCount > 0) {
            val sizeMB = totalSizeCleaned / (1024.0 * 1024.0)
            println("🧹 Cleanup completed: Removed $cleanedCount files (${String.format("%.2f", sizeMB)} MB)")
        } else {
            println("🧹 Cleanup completed: No expired files found")
        }

    } catch (e: Exception) {
        System.err.println("Error during file cleanup: ${e.message}")
        e.printStackTrace()
    }
}

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

    // Start the file cleanup scheduler
    GlobalScope.launch {
        startFileCleanupScheduler(TEMP_FILES_BASE_DIR)
    }

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
