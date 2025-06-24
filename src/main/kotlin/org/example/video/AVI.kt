package org.example.video

import org.example.photo.GIF
import org.example.utilities.ConversionResult
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
import org.slf4j.LoggerFactory
import java.io.File

class AVI(override val inputFilePath: String): FFmpegConvertibleType {
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
                logger.info("Converting AVI to MP4 with deployment-optimized settings")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("28")           // Faster encoding, smaller file
                command.add("-preset")
                command.add("ultrafast")    // Much faster encoding
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("128k")         // Lower audio bitrate
                command.add("-movflags")
                command.add("faststart")
                // Memory and performance optimizations
                command.add("-threads")
                command.add("2")            // Limit thread usage
                command.add("-bufsize")
                command.add("1M")           // Smaller buffer
            }

            "mov" -> {
                logger.info("Converting AVI to MOV with deployment-optimized settings")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("28")
                command.add("-preset")
                command.add("ultrafast")
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("128k")
                command.add("-movflags")
                command.add("faststart")
                command.add("-threads")
                command.add("2")
                command.add("-bufsize")
                command.add("1M")
            }

            "webm" -> {
                logger.info("Converting AVI to WEBM with fast settings")
                command.add("-c:v")
                command.add("libvpx-vp9")
                command.add("-crf")
                command.add("35")           // Lower quality for speed
                command.add("-b:v")
                command.add("0")
                command.add("-cpu-used")
                command.add("8")            // Fastest VP9 setting
                command.add("-threads")
                command.add("2")
                command.add("-c:a")
                command.add("libopus")
                command.add("-b:a")
                command.add("96k")          // Lower audio bitrate
            }

            "mkv" -> {
                logger.info("Converting AVI to MKV with fast H.264")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("28")
                command.add("-preset")
                command.add("ultrafast")
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("128k")
                command.add("-threads")
                command.add("2")
            }

            "wmv" -> {
                logger.info("Converting AVI to WMV with fast settings")
                command.add("-c:v")
                command.add("wmv2")
                command.add("-b:v")
                command.add("2M")           // Lower bitrate for speed
                command.add("-c:a")
                command.add("wmav2")
                command.add("-b:a")
                command.add("128k")
                command.add("-threads")
                command.add("1")            // Single thread for WMV
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