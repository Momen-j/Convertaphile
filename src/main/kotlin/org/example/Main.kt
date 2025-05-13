package org.example

import java.io.File
import org.example.photo.JPEG
import org.example.photo.WEBP

// --- Example Usage ---
fun main() {
    // --- Configuration: Get FFmpeg path from environment variable ---
    // This is where you read the environment variable.
    // System.getenv("FFMPEG_PATH") attempts to read the variable named "FFMPEG_PATH".
    // The Elvis operator (?:) provides a default value if the environment variable is not set.
    // In a production environment, you might want to throw an error if the variable is missing,
    // as FFmpeg is a mandatory dependency.
    val ffmpegExecutablePath = System.getenv("FFMPEG_PATH")
        ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffmpeg.exe" // Default path (common on many Linux systems). Adjust as needed for your server OS.
    // If running on Windows locally for testing, you might set this default to:
    // ?: "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe" // Example Windows path - adjust to your install location

    println("Using FFmpeg executable path: $ffmpegExecutablePath")
    // --- End Configuration ---


    // Replace with actual paths to test files on your system.
    // Ensure these files exist for the example to run.
    //val inputPath = "C:\\Users\\moses\\Documents\\test files\\Rockstar_Games_Logo.jpg"
    //val outputPath = "C:\\Users\\moses\\Documents\\test files\\Rockstar_Games_Logo.png"

    val inputPath = "C:\\Users\\moses\\Documents\\test files\\bluebird.webp"
    val outputPath = "C:\\Users\\moses\\Documents\\test files\\bluebird.png"

    // Basic check to see if the input file exists before attempting conversion.
    val inputFile = File(inputPath)
    if (!inputFile.exists()) {
        System.err.println("Error: Input file not found at $inputPath. Please update the path.")
        // If the input file doesn't exist, we can't proceed.
        return
    }

    // Instantiate the JPEG class with the input file path.
    //val jpegFile = JPEG(inputPath)
    val webpFile = WEBP(inputPath)

    // Call the toPNG function, passing the desired output path and the configured FFmpeg path.
    //val conversionResult = jpegFile.toPNG(outputPath, ffmpegExecutablePath)
    val conversionResult = webpFile.toPNG(outputPath, ffmpegExecutablePath)

    // Check the result of the conversion.
    if (conversionResult.isSuccess) {
        println("Overall conversion process completed successfully!")
        // You might want to verify the output file was actually created and has a non-zero size.
        val outputFile = File(outputPath)
        if (outputFile.exists() && outputFile.length() > 0) {
            println("Output PNG file successfully created at: ${outputFile.absolutePath}")
        } else {
            System.err.println("Conversion reported success, but output file was not found or is empty!")
        }
    } else {
        System.err.println("Overall conversion process failed.")
        // The detailed error from FFmpeg is already printed within the toPNG function.
    }
}
