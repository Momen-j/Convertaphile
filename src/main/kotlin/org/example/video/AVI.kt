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
                // Try stream copy first, but with fallback logic
                logger.info("Converting AVI to MP4 with optimized H.264")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("20")           // Higher quality than 23
                command.add("-preset")
                command.add("medium")       // Good balance of speed/quality
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("192k")         // Higher audio quality
                command.add("-movflags")
                command.add("faststart")
            }

            "mov" -> {
                logger.info("Converting AVI to MOV with optimized H.264")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("20")
                command.add("-preset")
                command.add("medium")
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("192k")
                command.add("-movflags")
                command.add("faststart")
            }

            "webm" -> {
                logger.info("Using RE-ENCODING for WEBM (VP9 + Opus)")
                command.add("-c:v")
                command.add("libvpx-vp9")
                command.add("-crf")
                command.add("25")           // Slightly better quality for AVI source
                command.add("-b:v")
                command.add("0")
                command.add("-cpu-used")
                command.add("2")            // Slower but better quality
                command.add("-threads")
                command.add("4")
                command.add("-c:a")
                command.add("libopus")
                command.add("-b:a")
                command.add("128k")
            }

            "mkv" -> {
                // Re-encode for AVI sources to ensure compatibility
                logger.info("Converting AVI to MKV with H.264/AAC")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("20")
                command.add("-preset")
                command.add("medium")
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("192k")
            }

            "wmv" -> {
                logger.info("Using RE-ENCODING for WMV (High Quality)")
                command.add("-c:v")
                command.add("wmv2")
                command.add("-b:v")
                command.add("6M")           // Even higher bitrate
                command.add("-maxrate")
                command.add("8M")
                command.add("-bufsize")
                command.add("16M")
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