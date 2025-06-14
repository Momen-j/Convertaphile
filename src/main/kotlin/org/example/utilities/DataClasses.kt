package org.example.utilities

// Import kotlinx.serialization related classes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import redis.clients.jedis.JedisPool
import java.io.File

// Data class to encapsulate the result of an external command execution.
// This makes it easy to check for success, get the exit code, and see
// what was printed to standard output and standard error.

data class ConversionResult(
    val isSuccess: Boolean, // True if the command exited with code 0, false otherwise or if an exception occurred/timeout
    val exitCode: Int,      // The exit code of the process (0 usually means success)
    val output: String,     // Content printed to the command's standard output
    val error: String       // Content printed to the command's standard error (FFmpeg often uses this for progress/errors)
)

data class ConversionRouteConfig(
    val ffmpegExecutablePath: String,
    val ffprobeExecutablePath: String,
    val tempFilesBaseDir: File,
    val jedisPool: JedisPool
)

// --- Data classes to match ffprobe JSON structure (simplified) ---
// These classes define the structure of the JSON output we expect from ffprobe.
// We use @Serializable annotation for kotlinx.serialization to automatically
// convert JSON to these objects.
@Serializable
data class FFprobeOutput(
    val format: Format? = null, // Information about the container format
    val streams: List<Stream>? = null // List of streams (video, audio, etc.)
)

@Serializable
data class Format(
    // Use @SerialName to map the JSON key "format_name" to field name
    @SerialName("format_name")
    val formatName: String? = null, // e.g., "jpeg", "png", "mov,mp4,m4a,3gp", "mp3"
    val duration: String? = null // Duration in seconds (as a string)
    // Add other format-level fields if needed (e.g., size, bit_rate)
)

@Serializable
data class Stream(
    // Use @SerialName to map the JSON key "codec_name" to field name
    @SerialName("codec_name")
    val codecName: String? = null, // e.g., "mjpeg", "png", "h264", "aac", "mp3", "vp9"
    // Use @SerialName to map the JSON key "codec_type" to field name
    @SerialName("codec_type")
    val codecType: String? = null, // "video", "audio", "data", "subtitle"
    val width: Int? = null,         // Video width
    val height: Int? = null         // Video height
    // Add other stream-level fields if needed (e.g., bit_rate, sample_rate, channels)
)
// --- End of ffprobe JSON data classes ---

// API Response from stored redis data
@Serializable
data class ConversionStatsResponse(
    val totalFiles: Long,
    val totalSizeMB: Double,
    val totalDownloads: Long,
    val message: String
)

// response from conversion endpoint on metadata about converted file
@Serializable
data class ConversionResponse(
    val conversionId: String,
    val originalFileName: String,
    val convertedFileName: String,
    val targetFormat: String,
    val fileSizeBytes: Long,
    val fileSizeMB: String,
    val downloadUrl: String,
    val message: String
)