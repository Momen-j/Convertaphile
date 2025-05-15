package org.example

import java.io.File
import org.example.photo.JPEG
import org.example.photo.WEBP
import org.example.utilities.ConversionResult
import org.example.utilities.analyzeFile

// --- Example Usage ---
fun main() {
    // --- Configuration: Get FFmpeg & FFprobe path from environment variable ---
    val ffmpegExecutablePath = System.getenv("FFMPEG_PATH")
        ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffmpeg.exe" // Default path

    val ffprobeExecutablePath = System.getenv("FFPROBE_PATH")
        ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffprobe.exe"

    // --- End Configuration ---


    // Test File paths
    //val inputPath = "C:\\Users\\moses\\Documents\\test files\\Rockstar_Games_Logo.jpg"
    //val outputPath = "C:\\Users\\moses\\Documents\\test files\\Rockstar_Games_Logo.png"

    val inputPath = "C:\\Users\\moses\\Documents\\test files\\bluebird.webp"
    val outputPath = "C:\\Users\\moses\\Documents\\test files\\bluebird.png"

    // Basic check to see if the input file exists before attempting conversion.
    val inputFile = File(inputPath)
    if (!inputFile.exists()) {
        System.err.println("Error: Input file not found at $inputPath. Please update the path.")
        // FRONT END CODE SHOULD HAVE TINY POP UP APPEAR
        return
    }

    // -- Analyze the file with analzeFile that uses FFprobe
    println("-- Analyzing Input File --")
    val ffprobeData = analyzeFile(inputPath, ffprobeExecutablePath)

    if (ffprobeData == null) {
        System.err.println("! Failed to analyze input file - Conversion will not continue !")
        // FRONT END CODE SHOULD HAVE ERROR RESPONSE APPEAR
        return
    }

    // -- Determine File Type and init the correct class based on analyzeFile
    println("\n--Determining File Type --")
    val formatName = ffprobeData.format?.formatName
    val hasVideoStream = ffprobeData.streams?.any {it.codecType == "video"} == true
    val hasAudioStream = ffprobeData.streams?.any {it.codecType == "audio"} == true
    println(ffprobeData.format)

    // When statement to init input file as correct file class
    val fileToConvert = when {
        formatName?.contains("jpeg") == true -> { // CHECK IF I NEED TO HAVE THIS ALSO CHECK IF IT CONTAINS jpg
            println("\n~~ Detected file type: JPEG ~~")
            JPEG(inputPath)
        }
        formatName?.contains("png") == true -> {
            println("\n~~ Detected file type: PNG ~~")
            //PNG(inputPath)
        }
        formatName?.contains("webp") == true -> {
            println("\n~~ Detected file type: WEBP ~~")
            JPEG(inputPath)
        }
        hasVideoStream -> {
            println("\n~~ Detected file type: VIDEO ~~ WILL BE CHANGNING TO SPECIFIC VIDEO TYPES")
            null
        }
        hasAudioStream -> {
            println("\n~~ Detected file type: AUDIO ~~ WILL BE CHANGNING TO SPECIFIC AUDIO TYPES")
            null
        }
        else -> {
            System.err.println("!! Detected unknown/unsupported file type !!")
            null
        }
    }

    if (fileToConvert == null) {
        System.err.println("Cannot proceed with conversion due to unsupported file type.")
        // FRONTEND: RETURN ERROR RESPONSE
        return
    }

    println("\n-- Converting File... ---")
    // Determine target format
    // This is where the front end comes in and calls a specific function to run depending on what the use clicks
    // For now I'll hard code in order to test

    val conversionResult: ConversionResult = when (fileToConvert) {
        is JPEG -> fileToConvert.convertTo(outputPath, ffmpegExecutablePath)
        is WEBP -> fileToConvert.convertTo(outputPath, ffmpegExecutablePath)
        null -> {
            System.err.println("Internal Error: fileToConvert is null after initial check.")
            ConversionResult(
                isSuccess = false,
                exitCode = -1,
                output = "",
                error = "Internal error during file type handling."
            )
        }
        // Handle any other unexpected types that might slip through
        else -> {
            System.err.println("Conversion logic not implemented for this file type or target format.")
            ConversionResult(
                isSuccess = false,
                exitCode = -1,
                output = "",
                error = "Unsupported file type or missing conversion logic."
            )
        }
    }

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

    // QUESTIONS:
    // How do I make the output path a new name everytime, even if someone tries to convert the same file
    // to the above, seems like having temp files solves that

    // --- Cleanup (In a real application, this would happen after streaming the output) ---
    // println("\n--- Cleaning up temporary files ---")
    // inputFile.delete() // Delete the temporary input file
    // File(outputPath).delete() // Delete the temporary output file
    // println("Temporary files deleted.")
    // --- End Cleanup ---
}
