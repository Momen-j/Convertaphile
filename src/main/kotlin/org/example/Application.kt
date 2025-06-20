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
import org.slf4j.LoggerFactory
import java.net.URI

// Config constants to be passed to routing module
private val FFMPEG_PATH: String = System.getenv("FFMPEG_PATH")
    ?: "ffmpeg"
    //"C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffmpeg.exe"
private val FFPROBE_PATH: String = System.getenv("FFPROBE_PATH")
    ?: "ffprobe"
    //"C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffprobe.exe"

// Temp directory for temporary uploaded & converted files
//private val TEMP_FILES_BASE_DIR: File = Files.createTempDirectory("convertaphile").toFile().apply{ deleteOnExit() }
private val TEMP_FILES_BASE_DIR: File = setupStorageDirectory()

/**
 * Sets up storage directory for Railway volume or local development
 * Railway volume mount path: /convertaphile-storage
 */
fun setupStorageDirectory(): File {
    // Check if we're on Railway with the volume mounted
    val volumePath = "/convertaphile-storage"
    val volumeDir = File(volumePath)

    val baseDir = if (volumeDir.exists() && volumeDir.canWrite()) {
        // We're on Railway with the volume mounted
        logger.info("Using Railway volume storage at: {}", volumePath)
        volumeDir
    } else {
        // Fallback for local development
        logger.info("Railway volume not found, using temporary directory for local development")
        Files.createTempDirectory("convertaphile").toFile().apply { deleteOnExit() }
    }

    // Create subdirectories
    val tempFilesDir = File(baseDir, "temp_files")
    val convertedFilesDir = File(baseDir, "converted_files")

    // Ensure directories exist
    listOf(tempFilesDir, convertedFilesDir).forEach { dir ->
        if (!dir.exists()) {
            val success = dir.mkdirs()
            if (!success) {
                throw RuntimeException("Failed to create directory: ${dir.absolutePath}")
            }
            logger.info("Created directory: {}", dir.absolutePath)
        }
    }

    // Verify write permissions
    if (!tempFilesDir.canWrite()) {
        throw RuntimeException("Cannot write to temp directory: ${tempFilesDir.absolutePath}")
    }

    logger.info("Storage setup complete:")
    logger.info("  Base directory: {}", baseDir.absolutePath)
    logger.info("  Temp files: {}", tempFilesDir.absolutePath)
    logger.info("  Converted files: {}", convertedFilesDir.absolutePath)

    return tempFilesDir
}

// local redis setup
// private val REDIS_HOST: String = System.getenv("REDIS_HOST") ?: "localhost"
// private val REDIS_PORT: Int = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379

// file cleanup config
private val FILE_EXPIRATION_MINUTES: Long = System.getenv("FILE_EXPIRATION_MINUTES")?.toLongOrNull() ?: 15L
private val CLEANUP_INTERVAL_MINUTES: Long = System.getenv("CLEANUP_INTERVAL_MINUTES")?.toLongOrNull() ?: 15L

private val logger = LoggerFactory.getLogger("org.example.utilities.Application")


/**
 * Starts a background coroutine that periodically cleans up expired files
 */
suspend fun startFileCleanupScheduler(tempFilesBaseDir: File) {
    // Use GlobalScope for application-lifetime coroutines
    GlobalScope.launch(Dispatchers.IO) {
        logger.info("🧹 File cleanup scheduler started - checking every {} minute(s)", CLEANUP_INTERVAL_MINUTES)
    }

    while (true) {
        try {
            delay(TimeUnit.MINUTES.toMillis(CLEANUP_INTERVAL_MINUTES))
            cleanupExpiredFiles(tempFilesBaseDir)
        } catch (e: Exception) {
            logger.error("Error in cleanup scheduler: {}", e.message, e)
        }
    }
}

/**
 * Cleans up files older than FILE_EXPIRATION_MINUTES
 * Creates a cutoff time using current time - FILE_EXPIRATION_MINUTES
 * Any file created before that cutoff time is auto deleted
 */
fun cleanupExpiredFiles(tempFilesBaseDir: File) {
    try {
        val permanentStorageDir = File(tempFilesBaseDir.parent, "converted_files")

        if (!permanentStorageDir.exists()) {
            logger.info("🧹 Storage directory doesn't exist yet, skipping cleanup")
            return
        }

        val expirationTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(FILE_EXPIRATION_MINUTES)
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
                        //logger.info("🗑️ Cleaned up expired file: ${file.name}")
                    } else {
                        logger.error("⚠️ Failed to delete expired file: {}", file.name)
                    }
                } catch (e: Exception) {
                    logger.error("️ Error processing file {}: {}", file.name, e.message)
                }
            }
        }

        if (cleanedCount > 0) {
            val sizeMB = totalSizeCleaned / (1024.0 * 1024.0)
            logger.info("🧹 Cleanup completed: Removed {} files ({} MB)", cleanedCount, String.format("%.2f", sizeMB))
        } else {
            //logger.info("🧹 Cleanup completed: No expired files found")
        }

    } catch (e: Exception) {
        logger.error("Error during file cleanup: {}", e.message, e)
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
    //val jedisPool = JedisPool(REDIS_HOST, REDIS_PORT)

    fun createJedisPool(): JedisPool? {
        val redisUrl = System.getenv("REDIS_URL")

        return if (!redisUrl.isNullOrEmpty()) {
            try {
                val redisUri = URI.create(redisUrl)
                JedisPool(redisUri)
            } catch (e: Exception) {
                logger.error("Failed to create Redis connection: ${e.message}")
                null
            }
        } else {
            logger.info("No REDIS_URL environment variable found")
            null
        }
    }

    val jedisPool = createJedisPool()

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
