package org.example.photo

import org.example.utilities.ConversionResult
import org.example.utilities.ConvertibleImageType
import org.example.utilities.executeCommand
import java.io.File

// Represents an instance of a GIF file
class GIF(override val inputFilePath: String): ConvertibleImageType {
    // override convertTo function in case I need to specify flags
    /**
     * Overrides the default convertTo implementation for GIF files.
     * Adds the -frames:v 1 flag when converting to single-image formats
     * to handle GIF animations.
     */
    override fun convertTo(outputFilePath: String, ffmpegExecutablePath: String): ConversionResult {
        // Determine the target format extension from the file path
        val targetExtension = File(outputFilePath).extension.lowercase()

        // Check if the target format is a single image format
        // POTENTIALLY ADDING LATER
        // "ico", "svg"
        val isSingleFormat = when (targetExtension) {
            "jpg", "jpeg", "png", "webp", "bmp", "svg", "tiff", "avif" -> true
            else -> false
        }

        // construct ffmpeg command
        val command = mutableListOf(
            ffmpegExecutablePath,
            "-i", inputFilePath
        )

        // add '-framse:v 1' flag if converting gif into single image output
        if (isSingleFormat) {
            command.add("-frames:v")
            command.add("1")
            println("Adding -frames:v 1 flag for GIF to single image conversion")
        }

        // add the output file path
        command.add(outputFilePath)

        // execute the command
        val result = executeCommand(command)

        // Log whether conversion was a success
        // SECTION TO USE LOGGING TOOLS
        if (result.isSuccess) {
            println("Successfully converted $inputFilePath to $outputFilePath")
        } else {
            System.err.println("Failed to convert $inputFilePath to $outputFilePath. Exit Code: ${result.exitCode}")
            System.err.println("FFmpeg Error Output:\n${result.error}")
        }

        return result
    }
}