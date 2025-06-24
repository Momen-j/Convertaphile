package org.example.video

import org.example.utilities.ConversionResult
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
import java.io.File

class MOV(override val inputFilePath: String): FFmpegConvertibleType {
    /**
     * Overrides the default convertTo implementation for MOV files.
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
                // Stream copy + web optimization (MOV to MP4 is very similar)
                logger.info("Converting MOV to MP4 with stream copy and faststart")
                command.add("-c")
                command.add("copy")
                command.add("-movflags")
                command.add("faststart")
            }

            "webm" -> {
                logger.info("Converting MOV to WebM with optimized VP9 + Opus")
                command.add("-c:v")
                command.add("libvpx-vp9")
                command.add("-crf")
                command.add("27")           // Good quality for web
                command.add("-b:v")
                command.add("0")            // Use CRF mode
                command.add("-cpu-used")
                command.add("4")            // Balanced speed/quality
                command.add("-threads")
                command.add("4")
                command.add("-c:a")
                command.add("libopus")
                command.add("-b:a")
                command.add("128k")
            }

            "mkv" -> {
                // Stream copy for MKV (very compatible container)
                logger.info("Converting MOV to MKV with stream copy")
                command.add("-c")
                command.add("copy")
            }

            "avi" -> {
                logger.info("Converting MOV to AVI with optimized H.264 + AC3")
                command.add("-c:v")
                command.add("libx264")      // H.264 for better quality than mpeg4
                command.add("-crf")
                command.add("24")           // Good quality
                command.add("-preset")
                command.add("fast")         // Balanced speed
                command.add("-bsf:v")
                command.add("h264_mp4toannexb")  // AVI compatibility
                command.add("-c:a")
                command.add("ac3")          // Better AVI audio compatibility
                command.add("-b:a")
                command.add("192k")
                command.add("-ar")
                command.add("48000")        // Standard sample rate
                command.add("-threads")
                command.add("2")
            }

            "wmv" -> {
                logger.info("Converting MOV to WMV with high quality settings")
                command.add("-c:v")
                command.add("wmv2")
                command.add("-b:v")
                command.add("4M")           // High bitrate for quality
                command.add("-maxrate")
                command.add("5M")           // Rate control
                command.add("-bufsize")
                command.add("10M")          // Buffer size
                command.add("-c:a")
                command.add("wmav2")
                command.add("-b:a")
                command.add("192k")         // High audio quality
                command.add("-threads")
                command.add("3")
                command.add("-g")
                command.add("300")          // Keyframe interval for seeking
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
                logger.error("No specific codecs defined for .{} when converting from MOV. Attempting default conversion.", targetExtension)
            }
        }

        command.add(outputFilePath)

        logger.info("Converting {} to {} using custom MOV command: {}", inputFilePath, outputFilePath, command.joinToString(" "))

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