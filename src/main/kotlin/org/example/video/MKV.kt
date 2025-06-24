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
                logger.info("Using RE-ENCODING for WEBM (VP9 + Opus - faster preset)")
                command.add("-c:v")
                command.add("libvpx-vp9")
                command.add("-crf")
                command.add("27")          // Back to faster CRF
                command.add("-b:v")
                command.add("0")
                command.add("-cpu-used")
                command.add("4")           // Much faster (was probably 2)
                command.add("-threads")
                command.add("4")           // Use more threads
                command.add("-c:a")
                command.add("libopus")
                command.add("-b:a")
                command.add("128k")
            }

            "wmv" -> {
                logger.info("Using RE-ENCODING for WMV (memory-optimized preset)")
                command.add("-c:v")
                command.add("libx264")
                command.add("-preset")
                command.add("ultrafast")   // Fastest preset to avoid timeout
                command.add("-crf")
                command.add("28")          // Balanced quality/speed
                command.add("-maxrate")
                command.add("1M")          // Limit bitrate to reduce memory usage
                command.add("-bufsize")
                command.add("2M")          // Buffer size control
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("96k")         // Lower audio bitrate
                command.add("-threads")
                command.add("2")           // Limit thread usage
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

        logger.info("Converting {} to {} using custom MKV command: {}", inputFilePath, outputFilePath, command.joinToString(" "))

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