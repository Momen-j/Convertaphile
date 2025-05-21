package org.example.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.Arguments

import org.example.video.* // Import all your video classes
import org.example.utilities.FFmpegConvertibleType
import org.example.utilities.executeCommand
import java.io.File
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Define the list of supported video formats (extensions)
val SUPPORTED_VIDEO_FORMATS = listOf(
    "mp4", "avi", "mov", "webm", "wmv", "mkv"
)

class VideoConversionTest {

    companion object {
        private lateinit var ffmpegExecutablePath: String
        private lateinit var testFilesDir: File

        @BeforeAll
        @JvmStatic
        fun setUpAll() {
            ffmpegExecutablePath = System.getenv("FFMPEG_PATH")
                ?: throw IllegalStateException("FFMPEG_PATH environment variable not set for tests.")

            testFilesDir = Files.createTempDirectory("video_conversion_tests").toFile()
            testFilesDir.deleteOnExit()

            println("FFmpeg path for video tests: $ffmpegExecutablePath")
            println("Video test files directory: ${testFilesDir.absolutePath}")

            val classLoader = this::class.java.classLoader

            for (format in SUPPORTED_VIDEO_FORMATS) {
                val resourceName = "video/sample.$format" // Assuming files are named like sample.mp4, sample.avi, etc.
                val resourceUrl = classLoader.getResource(resourceName)

                if (resourceUrl == null) {
                    System.err.println("Warning: Video test resource '$resourceName' not found in src/test/resources. Skipping tests for this format.")
                    continue
                }

                val inputStream = resourceUrl.openStream()
                val destinationFile = File(testFilesDir, resourceName)

                try {
                    Files.copy(inputStream, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    println("Copied video test file: $resourceName to ${destinationFile.absolutePath}")
                } catch (e: Exception) {
                    System.err.println("Error copying video test file '$resourceName': ${e.message}")
                } finally {
                    inputStream.close()
                }
            }
        }

        @JvmStatic
        fun conversionTestCases(): List<Arguments> {
            val testCases = mutableListOf<Arguments>()
            for (sourceFormat in SUPPORTED_VIDEO_FORMATS) {
                val sourceFileInTemp = File(testFilesDir, "sample.$sourceFormat")
                if (!sourceFileInTemp.exists()) {
                    System.err.println("Skipping video test cases for source format '$sourceFormat' because test file was not found in temp directory.")
                    continue
                }

                for (targetFormat in SUPPORTED_VIDEO_FORMATS) {
                    if (sourceFormat != targetFormat) {
                        // All these common video formats are lossy, so direct conversion is generally fine.
                        // If you had a specific lossless video format (e.g., FFV1 in MKV) and a lossy target,
                        // you might add a check here to prevent it if that's a rule.
                        testCases.add(Arguments.of("sample.$sourceFormat", targetFormat))
                    }
                }
            }
            return testCases
        }
    }

    // Helper function to get the correct FFmpegConvertibleType instance for a given video file path
    private fun getConvertibleFileInstance(filePath: String): FFmpegConvertibleType {
        val extension = File(filePath).extension.lowercase()
        return when (extension) {
            "mp4" -> MP4(filePath)
            "avi" -> AVI(filePath)
            "mov" -> MOV(filePath) // Assuming MOVVideo class exists
            "webm" -> WEBM(filePath) // Assuming WEBMVideo class exists
            "wmv" -> WMV(filePath) // Assuming WMVVideo class exists
            "mkv" -> MKV(filePath) // Assuming MKVVideo class exists
            else -> throw IllegalArgumentException("Unsupported video source format for testing: $extension")
        }
    }

    @ParameterizedTest(name = "Convert video from {0} to .{1}")
    @MethodSource("conversionTestCases")
    fun testVideoConversion(sourceFileName: String, targetExtension: String) {
        println("\n--- Testing Video Conversion: $sourceFileName to .$targetExtension ---")

        val inputFilePath = File(testFilesDir, sourceFileName).absolutePath
        val inputFile = File(inputFilePath)
        assertTrue(inputFile.exists(), "Input video test file does not exist: $inputFilePath")

        val sourceFile = getConvertibleFileInstance(inputFilePath)

        val outputFileName = "${UUID.randomUUID()}.$targetExtension"
        val outputFilePath = File(testFilesDir, outputFileName).absolutePath
        val outputFile = File(outputFilePath)
        assertFalse(outputFile.exists(), "Output video file already exists before conversion: $outputFilePath")

        val conversionResult = sourceFile.convertTo(outputFilePath, ffmpegExecutablePath)

        assertTrue(conversionResult.isSuccess, "Video conversion failed for $sourceFileName to .$targetExtension. FFmpeg Error:\n${conversionResult.error}")
        assertEquals(0, conversionResult.exitCode, "Video conversion exited with non-zero code for $sourceFileName to .$targetExtension. FFmpeg Error:\n${conversionResult.error}")

        assertTrue(outputFile.exists(), "Output video file was not created after conversion: $outputFilePath")
        assertTrue(outputFile.length() > 0, "Output video file is empty after conversion: $outputFilePath")

        println("Video conversion successful: $sourceFileName to $outputFileName")

        assertTrue(outputFile.delete(), "Failed to delete output video test file: $outputFilePath")
        println("Cleaned up output video file: $outputFilePath")
    }

    // You might add specific tests for video conversions that require custom flags,
    // e.g., testing specific codec changes, resolution changes, or trimming.
    // Example: Test MP4 to WEBM with VP9 codec
    @Test
    fun testMp4ToWebmSpecific() {
        println("\n--- Testing Specific MP4 to WEBM Conversion ---")
        val sourceFileName = "sample.mp4"
        val targetExtension = "webm"

        val inputFilePath = File(testFilesDir, sourceFileName).absolutePath
        val outputFile = File(testFilesDir, "${UUID.randomUUID()}.$targetExtension")
        val outputFilePath = outputFile.absolutePath

        //val mp4Video = MP4(inputFilePath) remove

        val command = listOf(
            ffmpegExecutablePath,
            "-i", inputFilePath,
            "-c:v", "libvpx-vp9", // VP9 video codec
            "-c:a", "libopus",    // Opus audio codec
            outputFilePath
        )
        println("Custom MP4 to WEBM command: ${command.joinToString(" ")}")
        val conversionResult = executeCommand(command) // Use executeCommand directly for custom test

        assertTrue(conversionResult.isSuccess, "MP4 to WEBM conversion failed. FFmpeg Error:\n${conversionResult.error}")
        assertTrue(outputFile.exists(), "Output WEBM file was not created.")
        assertTrue(outputFile.length() > 0, "Output WEBM file is empty.")

        println("Specific MP4 to WEBM conversion successful.")
        assertTrue(outputFile.delete(), "Failed to delete output test file: $outputFilePath")
    }

    // TODO: Add more specific tests for video conversions (e.g., trimming, resolution change, audio extraction)
}