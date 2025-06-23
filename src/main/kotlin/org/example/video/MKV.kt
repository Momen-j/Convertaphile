package org.example.video

import org.example.utilities.ConversionResult
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
import java.io.File

class MKV(override val inputFilePath: String): FFmpegConvertibleType {
    /**
     * Overrides the default convertTo implementation for MKV files.
     * Specifies appropriate video and audio codecs based on the output format.
     */
    override fun convertTo(outputFilePath: String, ffmpegExecutablePath: String): ConversionResult {
        val targetExtension = File(outputFilePath).extension.lowercase()

        val command = mutableListOf(
            ffmpegExecutablePath,
            "-i", inputFilePath
        )

        when (targetExtension) {
            "mp4" -> {
                // Stream copy + web optimization
                logger.info("Using STREAM COPY for MP4 (with faststart)")
                command.add("-c")
                command.add("copy")
                command.add("-movflags")
                command.add("faststart")
            }

            "mov" -> {
                // Stream copy + web optimization
                logger.info("Using STREAM COPY for MOV (with faststart)")
                command.add("-c")
                command.add("copy")
                command.add("-movflags")
                command.add("faststart")
            }

            "avi" -> {
                // Stream copy (no faststart - AVI doesn't support it)
                logger.info("Using STREAM COPY for AVI")
                command.add("-c")
                command.add("copy")
            }

            "webm" -> {
                // Use VP8 instead of VP9 - much faster encoding
                logger.info("Using RE-ENCODING for WEBM (VP8 + Vorbis - fast preset)")
                command.add("-c:v")
                command.add("libvpx")  // VP8 instead of libvpx-vp9
                command.add("-crf")
                command.add("10")      // Higher quality, faster than CRF 30
                command.add("-b:v")
                command.add("2M")      // Set target bitrate
                command.add("-cpu-used")
                command.add("5")       // Faster encoding (0=slowest, 16=fastest)
                command.add("-c:a")
                command.add("libvorbis") // Vorbis instead of Opus (faster)
                command.add("-q:a")
                command.add("5")       // Quality-based audio encoding
            }

            "wmv" -> {
                // Optimized WMV settings
                logger.info("Using RE-ENCODING for WMV (fast preset)")
                command.add("-c:v")
                command.add("libx264")
                command.add("-preset")
                command.add("fast")    // Much faster than "medium"
                command.add("-crf")
                command.add("25")      // Slightly lower quality but much faster
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("128k")
            }

            "mp3" -> {
                command.add("-c:a")
                command.add("libmp3lame") // MP3 encoder
                command.add("-b:a")
                command.add("192k")      // Example bitrate for audio
                command.add("-vn")       // Crucial: Tells FFmpeg to disable video stream
            }
            "aac" -> {
                command.add("-c:a")
                command.add("aac")       // AAC encoder
                command.add("-b:a")
                command.add("192k")
                command.add("-vn")
            }
            "wav" -> {
                command.add("-c:a")
                command.add("pcm_s16le") // PCM S16 LE (uncompressed audio)
                command.add("-vn")
            }
            "flac" -> {
                command.add("-c:a")
                command.add("flac")      // FLAC encoder
                command.add("-vn")
            }
            "ogg" -> {
                command.add("-c:a")
                command.add("libvorbis") // Vorbis encoder for OGG
                command.add("-q:a")
                command.add("5")         // Quality scale for Vorbis
                command.add("-vn")
            }
            "m4a" -> {
                command.add("-c:a")
                command.add("aac")       // M4A usually contains AAC
                command.add("-b:a")
                command.add("192k")
                command.add("-vn")
            }
            else -> {
                logger.error("No specific codecs defined for .{} when converting from MKV. Attempting default conversion.", targetExtension)
            }
        }

        command.add(outputFilePath)

        logger.info("Converting {} to {} using custom AVI command: {}", inputFilePath, outputFilePath, command.joinToString(" "))

        val result = executeCommand(command, timeoutSeconds = 180L) // wait for 3 minutes before terminating process

        if (result.isSuccess) {
            logger.info("Successfully converted {} to {}", inputFilePath, outputFilePath)
        } else {
            logger.error("Failed to convert {} to {}. Exit Code: {}", inputFilePath, outputFilePath, result.exitCode)
            logger.error("FFmpeg Error Output:\n{}", result.error)
        }
        return result
    }
}