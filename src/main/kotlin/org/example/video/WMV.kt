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
            "mov" -> {
                // For MOV, typically use H.264 (libx264) for video and AAC for audio
                command.add("-c:v")
                command.add("libx264")
                command.add("-c:a")
                command.add("aac")
                command.add("-crf")
                command.add("23") // Common good quality for H.264
                command.add("-b:a")
                command.add("128k")
            }
            "mkv" -> {
                // MKV is a flexible container; can often stream copy if codecs are compatible,
                // or re-encode to common codecs like H.264/AAC
                // For simplicity, we'll re-encode to H.264/AAC for now
                command.add("-c:v")
                command.add("libx264")
                command.add("-c:a")
                command.add("aac")
                command.add("-crf")
                command.add("23")
                command.add("-b:a")
                command.add("128k")
            }
            "avi" -> {
                // For AVI, typically use MPEG-4 (DivX/Xvid compatible) for video and MP3 for audio
                command.add("-c:v")
                command.add("mpeg4") // or libxvid if enabled in FFmpeg build
                command.add("-c:a")
                command.add("libmp3lame") // MP3 audio codec
                // Add quality/bitrate flags
                command.add("-b:v")
                command.add("1M") // 1 Mbps for video
                command.add("-b:a")
                command.add("128k") // 128 kbps for audio
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
                System.err.println("No specific codecs defined for .$targetExtension when converting from WMV. Attempting default conversion.")
            }
        }

        command.add(outputFilePath)

        println("Converting $inputFilePath to $outputFilePath using custom WMV command: ${command.joinToString(" ")}")

        val result = executeCommand(command)

        if (result.isSuccess) {
            println("Successfully converted $inputFilePath to $outputFilePath")
        } else {
            System.err.println("Failed to convert $inputFilePath to $outputFilePath. Exit Code: ${result.exitCode}")
            System.err.println("FFmpeg Error Output:\n${result.error}")
        }
        return result
    }
}