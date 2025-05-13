package org.example.photo

import org.example.utilities.ConversionResult
import org.example.utilities.executeCommand

// Represents an instance of a JPEG file
// Functions within class provide conversion capabilities
class JPEG(private val inputFilePath: String) {

    /**
     * Converts the JPEG image to a PNG format
     *
     * @param outputFilePath The desired path for the output PNG file
     * @param ffmpegExecutablePath The absolute path to FFmpeg.exe (configured through env var)
     * @return A ConversionResult object indicating the status of the conversion as well as captured stdout & stderr
     */
    fun toPNG(outputFilePath: String, ffmpegExecutablePath: String): ConversionResult {
        // Construct FFmpeg command as list of strings
        // command structure to convert image formats:
        // ffmpegPath -i input_file output_file
        val command = listOf(
            ffmpegExecutablePath,
            "-i", inputFilePath, // "-i" specifies the input file followed by the input file path
            outputFilePath
        )

        println("Converting JPEG to PNG using command: ${command.joinToString("")}")

        // Execute the FFmpeg command using executeCommand helper function and assign the result
        val result = executeCommand(command);

        // Log whether conversion was a success
        // SECTION TO USE LOGGING TOOLS
        if (result.isSuccess) {
            println("Successfully converted $inputFilePath to $outputFilePath");
        } else {
            System.err.println("Failed to convert $inputFilePath to $outputFilePath. Exit Code: ${result.exitCode}")
            System.err.println("FFmpeg Error Output:\n${result.error}")
        }

        return result
    }
}

// You would add similar functions for other target formats (toWEBP, toGIF, etc.):
/*
fun toWEBP(outputFilePath: String, ffmpegExecutablePath: String): ConversionResult {
    val command = listOf(
        ffmpegExecutablePath,
        "-i", filePath,
        outputFilePath // FFmpeg will create a WebP because of the .webp extension
    )
    println("Attempting to convert JPEG to WEBP. Command: ${command.joinToString(" ")}")
    val result = executeCommand(command)
    // ... handle result and return ...
    return result
}
*/