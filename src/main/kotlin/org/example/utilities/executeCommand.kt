package org.example.utilities

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

// Function gives arguments to FFmpeg to execute based on input & output file type
// Executes an external command using ProcessBuilder
fun executeCommand(command: List<String>, timeoutSeconds: Long = 60): ConversionResult {
    try {
        // Create instance of ProcessBuiilder:
        // an object that configures how the external process will be started
        val processBuilder = ProcessBuilder(command);

        // Configure stream redirection
        // control how the external process' (FFmpeg) I/O streams are connected
        processBuilder.redirectErrorStream(false); // Keep stdout & stderr separated
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE) // Tell OS to create a pipe for stdout
        processBuilder.redirectError(ProcessBuilder.Redirect.PIPE) // Tell OS to create a pipe for stderr

        // Begin the process (start FFmpeg.exe)
        // Executes the command given to ProcessBuilder
        // Program waits briefly while the OS starts the new process
        // Returns a proces object representing the running process
        val process = processBuilder.start();

        // -- READ OUTPUT & ERROR STREAMS CONCURRENTLY --
        // Set up Input/Error Stream Readers
        // Connect InputStreamReader (byte to char) & BufferedReader to the process'
        // stdout & error streams
        val outputReader = BufferedReader(InputStreamReader(process.inputStream));
        val errorReader = BufferedReader(InputStreamReader(process.errorStream));

        // Create Gobbler Tasks
        // Create instances of the Gobbler class to read from each of the streams
        val outputGobbler = Gobbler(outputReader);
        val errorGobbler = Gobbler(errorReader);

        // Create & Start Gobbler Threads
        // Create new thread objects, give these threads Gobbler tasks, and start them
        // Threads will run in background, reading both of the streams
        val outputThread = Thread(outputGobbler).apply { start() }
        val errorThread = Thread(errorGobbler).apply { start() }
        // -- END OF CONCURRENT STREAM READING --

        // Wait for completion of FFmpeg process
        // Main thread waits for external process to finish OR timeout limit is reached
        // exited will be true IF process finished within timeout limit
        val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

        // Join Gobbler Threads
        // Once main thread finished, we need to ensure Gobbler tasks have read all available output
        // FFmpeg process may finish but there may still be data in our buffers not read yet
        // join() makes the main thread wait until threads have completed their run() method
        // indicated when the reader closes
        outputThread.join()
        errorThread.join()

        // Handle timeout
        // If exited is false, process timed out
        if (!exited) {
            return ConversionResult(
                isSuccess = false,
                exitCode = -1, // timeout code
                output = outputGobbler.lines.joinToString("\n"), // include any output read
                error = errorGobbler.lines.joinToString("\n")
            )
        }

        // If the process finished, get the exit code and full stdout & stderr
        val exitCode = process.exitValue();
        val stdOut = outputGobbler.lines.joinToString("\n");
        val stdErr = errorGobbler.lines.joinToString("\n");

        // Return the ConversionResult
        return ConversionResult(
            isSuccess = exitCode == 0,
            exitCode = exitCode,
            output = stdOut,
            error = stdErr
        )

    } catch (e: Exception) {
        // catch any exceptions that may have occured
        System.err.println("Exception during FFmpeg Command Execution: ${e.message}")

        return ConversionResult(
            false,
            -1,
            "",
            "Exception during FFmpeg Command Execution: ${e.message}"
        )
    }
}