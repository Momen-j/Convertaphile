package org.example.utilities

// Data class to encapsulate the result of an external command execution.
// This makes it easy to check for success, get the exit code, and see
// what was printed to standard output and standard error.

data class ConversionResult(
    val isSuccess: Boolean, // True if the command exited with code 0, false otherwise or if an exception occurred/timeout
    val exitCode: Int,      // The exit code of the process (0 usually means success)
    val output: String,     // Content printed to the command's standard output
    val error: String       // Content printed to the command's standard error (FFmpeg often uses this for progress/errors)
)

