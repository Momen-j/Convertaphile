package org.example.photo

import org.example.utilities.ConversionResult
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
import org.slf4j.LoggerFactory
import java.io.File

// Represents an instance of a GIF file
class GIF(override val inputFilePath: String): FFmpegConvertibleType {
    // define companion object for logger that is a single instance shared across all GIF instances
    companion object {
        private val logger = LoggerFactory.getLogger(GIF::class.java)
    }

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
        }

        // add the output file path
        command.add(outputFilePath)

        // execute the command
        val result = executeCommand(command)

        if (result.isSuccess) {
            logger.info("Successfully converted $inputFilePath to $outputFilePath")
        } else {
            logger.error("Failed to convert {} to {}. Exit Code: {}", inputFilePath, outputFilePath, result.exitCode)
            logger.error("FFmpeg Error Output:\n{}", result.error)
        }

        return result
    }
}