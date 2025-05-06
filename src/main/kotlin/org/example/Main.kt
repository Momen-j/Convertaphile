package org.example

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

// Data class to encapsulate the result of an external command execution.
// This makes it easy to check for success, get the exit code, and see
// what was printed to standard output and standard error.
data class ConversionResult(
    val isSuccess: Boolean, // True if the command exited with code 0, false otherwise or if an exception occurred/timeout
    val exitCode: Int,      // The exit code of the process (0 usually means success)
    val output: String,     // Content printed to the command's standard output
    val error: String       // Content printed to the command's standard error (FFmpeg often uses this for progress/errors)
)

// Helper class to read a process's input stream in a separate thread.
// This is crucial to prevent the process from blocking if its output buffer fills up,
// especially for long-running commands like video conversions.
class Gobbler(private val reader: BufferedReader) : Runnable {
    // Mutable list to store the lines read from the stream.
    val lines = mutableListOf<String>()

    // The run method is executed when the thread starts.
    override fun run() {
        try {
            var line: String?
            // Read lines from the reader until the end of the stream (readLine() returns null).
            while (reader.readLine().also { line = it } != null) {
                // Add each non-null line to our list.
                lines.add(line!!)
            }
        } catch (e: Exception) {
            // In a real application, you would log this error properly.
            // It indicates an issue reading the stream, which might happen if the process
            // is unexpectedly terminated or if there's an I/O issue.
            System.err.println("Error reading process stream: ${e.message}")
        } finally {
            // Always close the reader to release resources.
            reader.close()
        }
    }
}

// Function to execute an external command using ProcessBuilder.
// This is a general utility that can be used for any command-line tool, not just FFmpeg.
fun executeCommand(command: List<String>, timeoutSeconds: Long = 60): ConversionResult {
    try {
        // Create a ProcessBuilder instance with the command and its arguments.
        val processBuilder = ProcessBuilder(command)

        // Configure stream redirection.
        // redirectErrorStream(false): This is important! It keeps standard output and
        // standard error as separate streams. FFmpeg writes progress and detailed errors
        // to standard error, so we need to read it separately.
        processBuilder.redirectErrorStream(false)
        // Redirect standard output and error to pipes so we can read them from our Kotlin code.
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)
        processBuilder.redirectError(ProcessBuilder.Redirect.PIPE)

        // Start the external process.
        val process = processBuilder.start()

        // --- Read output and error streams concurrently ---
        // Use the Gobbler helper to read the streams in separate threads.
        // This prevents the parent process (our Kotlin app) from blocking
        // while waiting for the child process (FFmpeg) to finish, and ensures
        // that the child process doesn't block due to full output buffers.
        val outputReader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))

        val outputGobbler = Gobbler(outputReader)
        val errorGobbler = Gobbler(errorReader)

        // Start the gobbler threads.
        val outputThread = Thread(outputGobbler).apply { start() }
        val errorThread = Thread(errorGobbler).apply { start() }
        // --- End concurrent stream reading ---

        // Wait for the process to complete within the specified timeout.
        // waitFor() returns true if the process exited within the timeout, false otherwise.
        val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

        // Join the gobbler threads. This ensures that we finish reading all output
        // before proceeding, even if the process finished quickly.
        outputThread.join()
        errorThread.join()

        // Check if the process timed out.
        if (!exited) {
            // If it timed out, forcibly destroy the process.
            process.destroyForcibly()
            // Return a ConversionResult indicating failure due to timeout.
            return ConversionResult(
                isSuccess = false,
                exitCode = -1, // Use -1 or another non-zero code to indicate a non-standard exit (like timeout)
                output = outputGobbler.lines.joinToString("\n"),
                error = errorGobbler.lines.joinToString("\n") + "\nProcess timed out after $timeoutSeconds seconds."
            )
        }

        // Get the exit code of the finished process.
        val exitCode = process.exitValue()
        // Join the lines captured by the gobblers into single strings.
        val stdOutput = outputGobbler.lines.joinToString("\n")
        val stdError = errorGobbler.lines.joinToString("\n")

        // Return the result based on the exit code. FFmpeg typically returns 0 for success.
        return ConversionResult(
            isSuccess = exitCode == 0,
            exitCode = exitCode,
            output = stdOutput,
            error = stdError
        )

    } catch (e: Exception) {
        // Catch any exceptions that occur during the process creation or execution itself.
        // This might happen if the command is not found (FFmpeg not in PATH or incorrect path),
        // or if there are permission issues.
        System.err.println("Exception during command execution: ${e.message}")
        // Return a ConversionResult indicating failure due to an exception.
        return ConversionResult(
            isSuccess = false,
            exitCode = -1, // Indicate an exception occurred
            output = "",
            error = "Exception during command execution: ${e.message}"
        )
    }
}


// Represents a JPEG image file and provides conversion capabilities.
class JPEG(private val filePath: String) {

    /**
     * Converts the JPEG image to a PNG format using FFmpeg.
     *
     * @param outputFilePath The desired path for the output PNG file.
     * @param ffmpegExecutablePath The absolute path to the FFmpeg executable.
     * This should be configured externally (e.g., via environment variable).
     * @return A ConversionResult object indicating the success or failure of the conversion
     * and capturing the output and error streams from FFmpeg.
     */
    fun toPNG(outputFilePath: String, ffmpegExecutablePath: String): ConversionResult {
        // Construct the FFmpeg command as a list of strings.
        // The basic command to convert one image format to another is:
        // ffmpeg -i input_file output_file
        // FFmpeg automatically determines the output format from the output file extension.
        val command = listOf(
            ffmpegExecutablePath, // The first element is the path to the executable
            "-i", filePath,       // -i specifies the input file, followed by the input file path
            outputFilePath        // The last argument is the output file path
        )

        println("Attempting to convert JPEG to PNG. Command: ${command.joinToString(" ")}")

        // Execute the constructed FFmpeg command using our helper function.
        val result = executeCommand(command)

        // Provide feedback based on the conversion result.
        if (result.isSuccess) {
            println("Successfully converted $filePath to $outputFilePath")
        } else {
            // Print detailed error information if the conversion failed.
            System.err.println("Failed to convert $filePath to $outputFilePath. Exit Code: ${result.exitCode}")
            System.err.println("FFmpeg Error Output:\n${result.error}")
            // In a real application, you would log this error properly to a logging system.
        }

        // Return the result object for further handling by the caller.
        return result
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
}

// --- Example Usage ---
fun main() {
    // --- Configuration: Get FFmpeg path from environment variable ---
    // This is where you read the environment variable.
    // System.getenv("FFMPEG_PATH") attempts to read the variable named "FFMPEG_PATH".
    // The Elvis operator (?:) provides a default value if the environment variable is not set.
    // In a production environment, you might want to throw an error if the variable is missing,
    // as FFmpeg is a mandatory dependency.
    val ffmpegExecutablePath = System.getenv("FFMPEG_PATH")
        ?: "/usr/bin/ffmpeg" // Default path (common on many Linux systems). Adjust as needed for your server OS.
    // If running on Windows locally for testing, you might set this default to:
    // ?: "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe" // Example Windows path - adjust to your install location

    println("Using FFmpeg executable path: $ffmpegExecutablePath")
    // --- End Configuration ---


    // Replace with actual paths to test files on your system.
    // Ensure these files exist for the example to run.
    val inputJpegPath = "C:\\Users\\moses\\Documents\\test files\\Rockstar_Games_Logo.jpg"
    val outputPngPath = "C:\\Users\\moses\\Documents\\test files\\Rockstar_Games_Logo.png"

    // Basic check to see if the input file exists before attempting conversion.
    val inputFile = File(inputJpegPath)
    if (!inputFile.exists()) {
        System.err.println("Error: Input file not found at $inputJpegPath. Please update the path.")
        // If the input file doesn't exist, we can't proceed.
        return
    }

    // Instantiate the JPEG class with the input file path.
    val jpegFile = JPEG(inputJpegPath)

    // Call the toPNG function, passing the desired output path and the configured FFmpeg path.
    val conversionResult = jpegFile.toPNG(outputPngPath, ffmpegExecutablePath)

    // Check the result of the conversion.
    if (conversionResult.isSuccess) {
        println("Overall conversion process completed successfully!")
        // You might want to verify the output file was actually created and has a non-zero size.
        val outputFile = File(outputPngPath)
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
