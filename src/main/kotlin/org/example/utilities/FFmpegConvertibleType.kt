package org.example.utilities

import java.io.File

interface FFmpegConvertibleType {
    val inputFilePath: String // property all implementing classes must provide

    /**
     * Converts the file to a specified file format by the user
     * @param outputFilePath The desired path for the output PNG file
     * @param ffmpegExecutablePath The absolute path to FFmpeg.exe (configured through env var)
     * @return A ConversionResult object indicating the status of the conversion as well as captured stdout & stderr
     */
    fun convertTo(outputFilePath: String, ffmpegExecutablePath: String): ConversionResult {
        // Construct FFmpeg command as list of strings
        // command structure to convert image formats:
        // ffmpegPath -i input_file output_file
        val targetExtension = File(outputFilePath).extension.lowercase()

        val command = mutableListOf(
            ffmpegExecutablePath,
            "-i", inputFilePath, // "-i" specifies the input file followed by the input file path
        )

        when (targetExtension) {
            "avif" -> {
                // Specific flags for AVIF output (using libaom-av1 encoder)
                command.add("-c:v")
                command.add("libaom-av1")
                command.add("-crf")
                command.add("23") // A common good quality setting for AV1 (lower is better, e.g., 23-35)
                // Explicitly set pixel format for AVIF, often yuv420p or yuva420p (if transparency)
                command.add("-pix_fmt")
                command.add("yuv420p")
                // For very large images, sometimes downscaling is necessary for AV1 to complete reliably
                // command.add("-vf")
                // command.add("scale=1920:-1") // Example: Scale to 1920px width, maintain aspect ratio
            }
            else -> {

            }
        }

        command.add(outputFilePath) // Add the output file path at the end

        println("Converting $inputFilePath to $outputFilePath using command: ${command.joinToString(" | ")}")

        // Execute the FFmpeg command using executeCommand helper function and assign the result
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