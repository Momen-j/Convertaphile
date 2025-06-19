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
                // For MP4, typically use H.264 (libx264) for video and AAC for audio
                command.add("-c:v")
                command.add("libx264")
                command.add("-c:a")
                command.add("aac")
                command.add("-crf")
                command.add("23")
                command.add("-b:a")
                command.add("128k")
            }
            "webm" -> {
                command.add("-c:v")
                command.add("libvpx") // Use VP8 codec
                command.add("-c:a")
                command.add("libopus")
                command.add("-crf")
                command.add("10")   // A good quality setting for VP8 (range 4-63, lower is better quality for VP8)
                command.add("-b:a")
                command.add("128k") // Audio bitrate
            }
            "mov" -> {
                // For MOV, typically use H.264 (libx264) for video and AAC for audio
                command.add("-c:v")
                command.add("libx264")
                command.add("-c:a")
                command.add("aac")
                command.add("-crf")
                command.add("23")
                command.add("-b:a")
                command.add("128k")
            }
            "mkv" -> {
                // MKV is flexible, re-encode to H.264/AAC for consistency
                command.add("-c:v")
                command.add("libx264")
                command.add("-c:a")
                command.add("aac")
                command.add("-crf")
                command.add("23")
                command.add("-b:a")
                command.add("128k")
            }
            "wmv" -> {
                // WMV, re-encode to wmv2/wma2
                command.add("-c:v")
                command.add("wmv2")
                command.add("-c:a")
                command.add("wmav2")
                command.add("-b:v")
                command.add("1M")
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