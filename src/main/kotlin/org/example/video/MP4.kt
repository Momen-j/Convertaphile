package org.example.video

import org.example.utilities.ConversionResult
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
import java.io.File

class MP4(override val inputFilePath: String): FFmpegConvertibleType {
    /**
     * Overrides the default convertTo implementation for MP4 files.
     * Specifies appropriate video and audio codecs based on the output format.
     */
    override fun convertTo(outputFilePath: String, ffmpegExecutablePath: String): ConversionResult {
        val targetExtension = File(outputFilePath).extension.lowercase()

        val command = mutableListOf(
            ffmpegExecutablePath,
            "-i", inputFilePath,
            "-threads", "0" // Use all available CPU threads for encoding
        )

        when (targetExtension) {
            "mov" -> {
                // Stream copy + web optimization (MP4 to MOV is very similar)
                logger.info("Using STREAM COPY for MOV (with faststart)")
                command.add("-c")
                command.add("copy")
                command.add("-movflags")
                command.add("faststart")
            }

            "avi" -> {
                // Copy video with bitstream filter, use AC3 audio
                logger.info("Using VIDEO COPY + BSF + AC3 AUDIO for AVI")
                command.add("-c:v")
                command.add("copy")
                command.add("-bsf:v")
                command.add("h264_mp4toannexb")
                command.add("-c:a")
                command.add("ac3")
                command.add("-b:a")
                command.add("192k")
                command.add("-ar")
                command.add("48000")
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
                logger.info("Using RE-ENCODING for WMV")
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
                // fallback to generic conversion command to give to FFmpeg.exe
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