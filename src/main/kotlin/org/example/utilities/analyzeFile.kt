package org.example.utilities

import kotlinx.serialization.json.Json
// import kotlinx.serialization.json.JsonDecodingException ADD LATER

// --- Create a single, reusable Json instance ---
// This instance is created once when the application starts.
private val json = Json { ignoreUnknownKeys = true }

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
        "-hide_banner", // Suppress ffprobe version banner (LOOK THIS UP)
        "-of", "json", // Makes output format JSON
        "-show_format", // Include format level info
        "-show_streams", // Include stream-level info
        filePath
    )

    println("Analyzing file with the following command: ${ffprobeCommand.joinToString(" ")}")

    // Execute ffprobe command with executeCommand function
    val ffprobeResult = executeCommand(ffprobeCommand);

    // if ffprobe didn't execute properly
    if (!ffprobeResult.isSuccess) {
        System.err.println("FFprobe's attempt at reading the file failed. Exit code: ${ffprobeResult.exitCode}")
        System.err.println("FFprobe's error output: \n${ffprobeResult.error}")
        return null
    }

    // Get the JSON output from the standard output
    val ffprobeJsonOutput = ffprobeResult.output

    // parse the JSON output into the data classes w/ the kotlinx.serialization library
    return try {
        json.decodeFromString<FFprobeOutput>(ffprobeJsonOutput)
    } catch (e: Exception) { // catch (e: JsonDecodingException) ADD THIS LATER
        System.err.println("FFprobe Raw Output:\n${ffprobeResult.output}")
        System.err.println("FFprobe Raw Output:\n${ffprobeResult.output}")
        return null
    }
}