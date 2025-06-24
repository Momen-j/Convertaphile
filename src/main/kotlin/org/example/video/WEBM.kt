package org.example.video

import org.example.utilities.ConversionResult
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
import java.io.File

class WEBM(override val inputFilePath: String): FFmpegConvertibleType {
    /**
     * Overrides the default convertTo implementation for WEBM files.
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
                // Stream copy + web optimization (WebM to MP4)
                logger.info("Converting WebM to MP4 with optimized H.264")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("23")           // Good quality for web
                command.add("-preset")
                command.add("medium")       // Balanced speed/quality
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("160k")         // Higher audio quality
                command.add("-movflags")
                command.add("faststart")
                command.add("-threads")
                command.add("3")
            }

            "mov" -> {
                logger.info("Converting WebM to MOV with optimized H.264")
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("23")
                command.add("-preset")
                command.add("medium")
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("160k")
                command.add("-movflags")
                command.add("faststart")
                command.add("-threads")
                command.add("3")
            }

            "mkv" -> {
                // Stream copy if possible (WebM and MKV are both Matroska-based)
                logger.info("Converting WebM to MKV with stream copy attempt")
                command.add("-c")
                command.add("copy")         // Try stream copy first
                // Fallback: if stream copy fails, this will re-encode
                command.add("-c:v")
                command.add("libx264")
                command.add("-crf")
                command.add("21")           // Higher quality for MKV
                command.add("-c:a")
                command.add("aac")
                command.add("-b:a")
                command.add("192k")
            }

            "avi" -> {
                logger.info("Converting WebM to AVI with optimized settings")
                command.add("-c:v")
                command.add("libx264")      // H.264 instead of mpeg4 for better quality
                command.add("-crf")
                command.add("24")           // Good quality
                command.add("-preset")
                command.add("fast")
                command.add("-bsf:v")
                command.add("h264_mp4toannexb")  // AVI compatibility
                command.add("-c:a")
                command.add("ac3")          // Better AVI audio than MP3
                command.add("-b:a")
                command.add("192k")
                command.add("-threads")
                command.add("2")
            }

            "wmv" -> {
                logger.info("Converting WebM to WMV with high quality settings")
                command.add("-c:v")
                command.add("wmv2")
                command.add("-b:v")
                command.add("3M")           // Much higher bitrate
                command.add("-maxrate")
                command.add("4M")           // Rate control
                command.add("-bufsize")
                command.add("8M")           // Larger buffer
                command.add("-c:a")
                command.add("wmav2")
                command.add("-b:a")
                command.add("192k")         // Higher audio quality
                command.add("-threads")
                command.add("3")
                command.add("-g")
                command.add("300")          // Keyframe interval
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