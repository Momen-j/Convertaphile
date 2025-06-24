package org.example.video

import org.example.photo.GIF
import org.example.utilities.ConversionResult
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
import org.slf4j.LoggerFactory
import java.io.File

class AVI(override val inputFilePath: String): FFmpegConvertibleType {
    // define companion object for logger that is a single instance shared across all AVI instances
    companion object {
        private val logger = LoggerFactory.getLogger(AVI::class.java)
    }

    /**
     * Overrides the default convertTo implementation for AVI files.
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
                // Stream copy + web optimization (if codecs are compatible)
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

            "webm" -> {
                logger.info("Using RE-ENCODING for WEBM (VP9 + Opus)")
                command.add("-c:v")
                command.add("libvpx-vp9")
                command.add("-crf")
                command.add("27")
                command.add("-b:v")
                command.add("0")
                command.add("-cpu-used")
                command.add("4")
                command.add("-threads")
                command.add("4")
                command.add("-c:a")
                command.add("libopus")
                command.add("-b:a")
                command.add("128k")
            }

            "mkv" -> {
                // Stream copy for MKV (very compatible container)
                logger.info("Using STREAM COPY for MKV")
                command.add("-c")
                command.add("copy")
            }

            "wmv" -> {
                logger.info("Using RE-ENCODING for WMV (High Quality)")
                command.add("-c:v")
                command.add("wmv2")
                command.add("-b:v")
                command.add("5M")
                command.add("-maxrate")
                command.add("6M")
                command.add("-bufsize")
                command.add("12M")
                command.add("-c:a")
                command.add("wmav2")
                command.add("-b:a")
                command.add("192k")
                command.add("-threads")
                command.add("2")
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
                logger.error("No specific codecs defined for .{} when converting from AVI. Attempting default conversion.", targetExtension)
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