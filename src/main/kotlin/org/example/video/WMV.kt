package org.example.video

import org.example.utilities.ConversionResult
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
import java.io.File

class WMV(override val inputFilePath: String): FFmpegConvertibleType {
    /**
     * Overrides the default convertTo implementation for WMV files.
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
                // Stream copy + web optimization (WMV to MP4 usually needs re-encoding)
                logger.info("Converting WMV to MP4 with deployment-optimized H.264")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("26")           // Balanced quality/speed
                command.add("-preset")
                command.add("fast")         // Good speed/quality balance
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("128k")
                command.add("-movflags")
                command.add("faststart")
                command.add("-threads")
                command.add("3")            // Moderate thread usage
            }

            "mov" -> {
                logger.info("Converting WMV to MOV with deployment-optimized H.264")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("26")
                command.add("-preset")
                command.add("fast")
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("128k")
                command.add("-movflags")
                command.add("faststart")
                command.add("-threads")
                command.add("3")
            }

            "mkv" -> {
                logger.info("Converting WMV to MKV with optimized H.264")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("25")           // Slightly better quality for MKV
                command.add("-preset")
                command.add("medium")       // Better quality for flexible container
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("160k")         // Higher audio quality
                command.add("-threads")
                command.add("3")
            }

            "avi" -> {
                logger.info("Converting WMV to AVI with MPEG-4 + AC3")
                command.add("-c:v")
                command.add("mpeg4")
                command.add("-b:v")
                command.add("2M")           // Higher bitrate for good quality
                command.add("-c:a")
                command.add("ac3")          // Better AVI audio compatibility
                command.add("-b:a")
                command.add("192k")         // Higher audio quality
                command.add("-threads")
                command.add("2")            // Conservative for older codec
            }

            "webm" -> {
                logger.info("Converting WMV to WEBM with deployment-optimized VP9")
                command.add("-c:v")
                command.add("libvpx-vp9")
                command.add("-crf")
                command.add("32")           // Balanced quality/speed for WMV source
                command.add("-b:v")
                command.add("0")
                command.add("-cpu-used")
                command.add("6")            // Good speed/quality balance
                command.add("-threads")
                command.add("3")
                command.add("-c:a")
                command.add("libopus")
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
                logger.error("No specific codecs defined for .{} when converting from MP4. Attempting default conversion.", targetExtension)
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