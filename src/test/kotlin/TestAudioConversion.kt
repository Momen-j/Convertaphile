package org.example.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.Arguments

import org.example.audio.* // Import all your audio classes
import org.example.utilities.FFmpegConvertibleType
import java.io.File
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Define the list of supported audio formats (extensions)
val SUPPORTED_AUDIO_FORMATS = listOf(
    "mp3", "aac", "flac", "ogg", "wav", "m4a"
)

// Define which formats are considered lossy and lossless for exclusion logic
val LOSSY_AUDIO_FORMATS = listOf("mp3", "aac", "ogg", "m4a")
val LOSSLESS_AUDIO_FORMATS = listOf("flac", "wav")

class AudioConversionTest {

    companion object {
        private lateinit var ffmpegExecutablePath: String
        private lateinit var testFilesDir: File

        @BeforeAll
        @JvmStatic
        fun setUpAll() {
            ffmpegExecutablePath = System.getenv("FFMPEG_PATH")
                ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffmpeg.exe"

            testFilesDir = Files.createTempDirectory("audio_conversion_tests").toFile()
            testFilesDir.deleteOnExit()

            println("FFmpeg path for audio tests: $ffmpegExecutablePath")
            println("Audio test files directory: ${testFilesDir.absolutePath}")

            val classLoader = this::class.java.classLoader

            for (format in SUPPORTED_AUDIO_FORMATS) {
                val resourceName = "audio/sample.$format" // Assuming audio files are in src/test/resources/audio/
                val resourceUrl = classLoader.getResource(resourceName)

                if (resourceUrl == null) {
                    System.err.println("Warning: Audio test resource '$resourceName' not found in src/test/resources. Skipping tests for this format. Make sure it's in src/test/resources/audio/")
                    continue
                }

                val inputStream = resourceUrl.openStream()
                val destinationFile = File(testFilesDir, resourceName)

                destinationFile.parentFile?.mkdirs() // Create the 'audio/' subdirectory if it doesn't exist

                try {
                    Files.copy(inputStream, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    println("Copied audio test file: $resourceName to ${destinationFile.absolutePath}")
                } catch (e: Exception) {
                    System.err.println("Error copying audio test file '$resourceName': ${e.message}")
                } finally {
                    inputStream.close()
                }
            }
        }

        @JvmStatic
        fun conversionTestCases(): List<Arguments> {
            val testCases = mutableListOf<Arguments>()
            for (sourceFormat in SUPPORTED_AUDIO_FORMATS) {
                val sourceFileInTemp = File(testFilesDir, "audio/sample.$sourceFormat")
                if (!sourceFileInTemp.exists()) {
                    System.err.println("Skipping audio test cases for source format '$sourceFormat' because test file was not found in temp directory.")
                    continue
                }

                for (targetFormat in SUPPORTED_AUDIO_FORMATS) {
                    // Skip converting a format to itself unless specifically needed
                    if (sourceFormat == targetFormat) {
                        continue
                    }

                    // --- EXCLUDE LOSSY TO LOSSLESS CONVERSIONS ---
                    // Prevent converting a lossy source format to a lossless target format
                    if (LOSSY_AUDIO_FORMATS.contains(sourceFormat) && LOSSLESS_AUDIO_FORMATS.contains(targetFormat)) {
                        println("Skipping test: Converting from .$sourceFormat (lossy) to .$targetFormat (lossless) as per test requirements.")
                        continue
                    }
                    // --- END EXCLUSION ---

                    testCases.add(Arguments.of("audio/sample.$sourceFormat", targetFormat))
                }
            }
            return testCases
        }
    }

    // Helper function to get the correct FFmpegConvertibleType instance for a given audio file path
    private fun getConvertibleFileInstance(filePath: String): FFmpegConvertibleType {
        val extension = File(filePath).extension.lowercase()
        return when (extension) {
            "mp3" -> MP3(filePath)
            "aac" -> AAC(filePath)
            "flac" -> FLAC(filePath)
            "ogg" -> OGG(filePath)
            "wav" -> WAV(filePath)
            "m4a" -> M4A(filePath)
            else -> throw IllegalArgumentException("Unsupported audio source format for testing: $extension")
        }
    }

    @ParameterizedTest(name = "Convert audio from {0} to .{1}")
    @MethodSource("conversionTestCases")
    fun testAudioConversion(sourceFileName: String, targetExtension: String) {
        println("\n--- Testing Audio Conversion: $sourceFileName to .$targetExtension ---")

        val inputFilePath = File(testFilesDir, sourceFileName).absolutePath
        val inputFile = File(inputFilePath)
        assertTrue(inputFile.exists(), "Input audio test file does not exist: $inputFilePath")

        val sourceFile = getConvertibleFileInstance(inputFilePath)

        val outputFileName = "${UUID.randomUUID()}.$targetExtension"
        val outputFilePath = File(testFilesDir, outputFileName).absolutePath
        val outputFile = File(outputFilePath)
        assertFalse(outputFile.exists(), "Output audio file already exists before conversion: $outputFilePath")

        val conversionResult = sourceFile.convertTo(outputFilePath, ffmpegExecutablePath)

        assertTrue(conversionResult.isSuccess, "Audio conversion failed for $sourceFileName to .$targetExtension. FFmpeg Error:\n${conversionResult.error}")
        assertEquals(0, conversionResult.exitCode, "Audio conversion exited with non-zero code for $sourceFileName to .$targetExtension. FFmpeg Error:\n${conversionResult.error}")

        assertTrue(outputFile.exists(), "Output audio file was not created after conversion: $outputFilePath")
        assertTrue(outputFile.length() > 0, "Output audio file is empty after conversion: $outputFilePath")

        println("Audio conversion successful: $sourceFileName to $outputFileName")

        assertTrue(outputFile.delete(), "Failed to delete output audio test file: $outputFilePath")
        println("Cleaned up output audio file: $outputFilePath")
    }
}
