package org.example.video

import org.example.utilities.ConversionResult
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
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
            else -> {
                System.err.println("No specific codecs defined for .$targetExtension when converting from AVI. Attempting default conversion.")
            }
        }

        command.add(outputFilePath)

        println("Converting $inputFilePath to $outputFilePath using custom AVI command: ${command.joinToString(" ")}")

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