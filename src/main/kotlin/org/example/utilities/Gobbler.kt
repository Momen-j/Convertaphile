package org.example.utilities

import org.slf4j.LoggerFactory
import java.io.BufferedReader

private val logger = LoggerFactory.getLogger("org.example.utilities.Gobbler") // Give it a meaningful name


// INSTANCE OF A TASK TO BE EXECUTED W/IN A THREAD (implements runnable interface)
// Helper class to read a process' input stream in a separate thread
// Helps to prevent ffmpeg from blocking if it's output buffer fills up
class Gobbler(private val reader: BufferedReader): Runnable {
    // Mutable list to store the lines read from a stream
    val lines = mutableListOf<String>();

    // run method executes once the thread is started
    override fun run() {
        try {
            // nullable string that will store lines read from character input stream through bufferedReader
            var line: String?

            // read lines from the reader until the end of the stream where readLine() returns null
            // .also performs a side effect, in this case assigning the currently read line to "line"
            // essentially the same as reader.readLine() != null
            while (reader.readLine().also { line = it } != null) {
                // add the currently read line to lines
                // since it shouldn't be null, !! was added
                lines.add(line!!);
            }
        } catch (e: Exception) {
            // WHERE TO INTEGRATE LOG ROCKET/SENTRY USE TO LOG ERRORS
            logger.error("Error reading FFmpeg process stream {}", e.message)
        } finally {
            // close reader to release resources
            reader.close();
        }
    }
}