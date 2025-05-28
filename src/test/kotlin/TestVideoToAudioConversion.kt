package org.example.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.Arguments

import org.example.video.*
import org.example.audio.*
import org.example.utilities.FFmpegConvertibleType
import java.io.File
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Define the list of supported video formats (extensions)
val SUPPORTED_VIDEO_SOURCE_FORMATS = listOf(
    "mp4", "avi", "mov", "webm", "wmv", "mkv"
)

// Define the list of supported audio formats (extensions)
val SUPPORTED_AUDIO_TARGET_FORMATS = listOf(
    "mp3", "aac", "flac", "ogg", "wav", "m4a"
)

class VideoToAudioConversionTest {

    companion object {
        private lateinit var ffmpegExecutablePath: String
        private lateinit var testFilesDir: File
        // private lateinit var ffprobeExecutablePath: String // Not directly used in this test class, but keep if needed for setup

        @BeforeAll
        @JvmStatic
        fun setUpAll() {
            ffmpegExecutablePath = System.getenv("FFMPEG_PATH")
                ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffmpeg.exe"
            // ffprobeExecutablePath = System.getenv("FFPROBE_PATH") // Uncomment if I add ffprobe calls here
            //     ?: throw IllegalStateException("FFPROBE_PATH environment variable not set for tests.")

            testFilesDir = Files.createTempDirectory("video_to_audio_conversion_tests").toFile()
            testFilesDir.deleteOnExit()

            println("FFmpeg path for video-to-audio tests: $ffmpegExecutablePath")
            println("Video-to-audio test files directory: ${testFilesDir.absolutePath}")

            val classLoader = this::class.java.classLoader

            // Copy actual video test files from src/test/resources/video/
            for (format in SUPPORTED_VIDEO_SOURCE_FORMATS) {
                val resourceName = "video/sample.$format"
                val resourceUrl = classLoader.getResource(resourceName)

                if (resourceUrl == null) {
                    System.err.println("Warning: Video test resource '$resourceName' not found. Skipping. Make sure it's in src/test/resources/video/")
                    continue
                }

                val inputStream = resourceUrl.openStream()
                val destinationFile = File(testFilesDir, "sample.$format") // Copy to root of temp dir

                destinationFile.parentFile?.mkdirs() // Ensure parent directory exists

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

        // Method to provide test data for parameterized tests
        @JvmStatic
        fun conversionTestCases(): List<Arguments> {
            val testCases = mutableListOf<Arguments>()
            for (sourceFormat in SUPPORTED_VIDEO_SOURCE_FORMATS) {
                val sourceFileInTemp = File(testFilesDir, "sample.$sourceFormat")
                if (!sourceFileInTemp.exists()) {
                    System.err.println("Skipping test cases for video source format '$sourceFormat' because test file was not found in temp directory.")
                    continue
                }

                for (targetFormat in SUPPORTED_AUDIO_TARGET_FORMATS) {
                    testCases.add(Arguments.of("sample.$sourceFormat", targetFormat))
                }
            }
            return testCases
        }
    }

    // Helper function to get the correct FFmpegConvertibleType instance for a given video file path
    private fun getConvertibleVideoInstance(filePath: String): FFmpegConvertibleType {
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

    @ParameterizedTest(name = "Convert video from {0} to .{1} audio")
    @MethodSource("conversionTestCases")
    fun testVideoToAudioConversion(sourceFileName: String, targetExtension: String) {
        println("\n--- Testing Video to Audio Conversion: $sourceFileName to .$targetExtension ---")

        val inputFilePath = File(testFilesDir, sourceFileName).absolutePath
        val inputFile = File(inputFilePath)
        assertTrue(inputFile.exists(), "Input video test file does not exist: $inputFilePath")

        val sourceFile = getConvertibleVideoInstance(inputFilePath) // Get video file instance

        val outputFileName = "${UUID.randomUUID()}.$targetExtension"
        val outputFilePath = File(testFilesDir, outputFileName).absolutePath
        val outputFile = File(outputFilePath)
        assertFalse(outputFile.exists(), "Output audio file already exists before conversion: $outputFilePath")

        val conversionResult = sourceFile.convertTo(outputFilePath, ffmpegExecutablePath) // Call convertTo

        assertTrue(conversionResult.isSuccess, "Video to Audio conversion failed for $sourceFileName to .$targetExtension. FFmpeg Error:\n${conversionResult.error}")
        assertEquals(0, conversionResult.exitCode, "Video to Audio conversion exited with non-zero code for $sourceFileName to .$targetExtension. FFmpeg Error:\n${conversionResult.error}")

        assertTrue(outputFile.exists(), "Output audio file was not created after conversion: $outputFilePath")
        assertTrue(outputFile.length() > 0, "Output audio file is empty after conversion: $outputFilePath")

        println("Video to Audio conversion successful: $sourceFileName to $outputFileName")

        assertTrue(outputFile.delete(), "Failed to delete output audio test file: $outputFilePath")
        println("Cleaned up output audio file: $outputFilePath")
    }
}
