package org.example.utilities

import kotlinx.serialization.json.Json
// import kotlinx.serialization.json.JsonDecodingException // Add this import for more specific error handling
import org.slf4j.LoggerFactory

// --- Create a single, reusable Json instance ---
private val json = Json { ignoreUnknownKeys = true }

// Get a logger instance for this file (or a specific utility logger)
private val logger = LoggerFactory.getLogger("org.example.utilities.analyzeFile")

/**
 * Executes the ffprobe command on a given file and parses the JSON output.
 *
 * @param filePath The absolute path to the file to analyze.
 * @param ffprobeExecutablePath The absolute path to the ffprobe executable.
 * @return An FFprobeOutput object containing the parsed file information, or null if ffprobe fails or output cannot be parsed.
 */
fun analyzeFile(filePath: String, ffprobeExecutablePath: String): FFprobeOutput? {
    // Construct the ffprobe command to get format and stream info in JSON format
    val ffprobeCommand = listOf(
        ffprobeExecutablePath,
        "-hide_banner",
        "-of", "json",
        "-show_format",
        "-show_streams",
        filePath
    )

    // Use logger.info() for informational messages
    logger.info("Analyzing file with the following command: {}", ffprobeCommand.joinToString(" "))

    // Execute ffprobe command with executeCommand function (assuming it's accessible)
    val ffprobeResult = executeCommand(ffprobeCommand) // Make sure executeCommand is defined and accessible

    // if ffprobe didn't execute properly
    if (!ffprobeResult.isSuccess) {
        // Use logger.error() for failures
        logger.error(
            "FFprobe's attempt at reading the file failed. Exit code: {}. FFprobe's error output: \n{}",
            ffprobeResult.exitCode,
            ffprobeResult.error
        )
        return null
    }

    // Get the JSON output from the standard output
    val ffprobeJsonOutput = ffprobeResult.output

    // parse the JSON output into the data classes w/ the kotlinx.serialization library
    return try {
        json.decodeFromString<FFprobeOutput>(ffprobeJsonOutput)
    } catch (e: Exception) { // switch to JSON decoding exception to catch specific JSON decoding exceptions using import
        // Log JSON parsing errors as ERROR
        logger.error("Failed to parse FFprobe JSON output for file {}. Error: {}. Raw Output:\n{}", filePath, e.message, ffprobeResult.output, e)
        return null
    } catch (e: Exception) { // Catch any other unexpected exceptions during parsing
        // Log general parsing errors as ERROR
        logger.error("An unexpected error occurred while parsing FFprobe output for file {}. Error: {}. Raw Output:\n{}", filePath, e.message, ffprobeResult.output, e)
        return null
    }
}