package org.example.test // It's good practice to put tests in a separate package

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import org.example.photo.*
import org.example.utilities.FFmpegConvertibleType
import org.junit.jupiter.params.provider.Arguments
import java.io.File
import java.util.UUID // For generating unique filenames
import java.nio.file.Files // For creating temporary directories
import java.nio.file.StandardCopyOption

// Define the list of supported image formats (extensions)
// This helps define the test matrix
/**
 * Potentially to be supported formats
 * .ico
 * .svg
 */
val SUPPORTED_IMAGE_FORMATS = listOf(
    "jpg", "jpeg", "png", "webp", "bmp", "tiff", "avif", "gif"
)

class ImageConversionTest {

    // Companion object to hold properties/functions shared by all test instances
    companion object {
        // Property to hold the FFmpeg executable path, read once before all tests
        private lateinit var ffmpegExecutablePath: String
        // Property to hold a temporary directory for test files
        private lateinit var testFilesDir: File

        // This method runs once before any test method in this class
        @BeforeAll
        @JvmStatic // Use @JvmStatic for methods in companion objects used with @BeforeAll
        fun setUpAll() {
            // Read the FFmpeg path from an environment variable for tests
            // Use a different environment variable for tests if needed, or the same one
            ffmpegExecutablePath = System.getenv("FFMPEG_PATH")
                ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffmpeg.exe"

            // Create a temporary directory for test input and output files
            // This ensures tests don't clutter your actual file system
            testFilesDir = Files.createTempDirectory("image_conversion_tests").toFile()
            testFilesDir.deleteOnExit() // Schedule deletion on JVM exit (safety net)

            println("FFmpeg path for tests: $ffmpegExecutablePath")
            println("Test files directory: ${testFilesDir.absolutePath}")

            // --- Copy actual test image files from src/test/resources into testFilesDir ---
            val classLoader = this::class.java.classLoader // Get the classloader

            for (format in SUPPORTED_IMAGE_FORMATS) {
                val resourceName = "image/sample.$format" // Assuming files are named like sample.jpg, sample.png, etc.
                val resourceUrl = classLoader.getResource(resourceName) // Get the URL of the resource

                if (resourceUrl == null) {
                    System.err.println("Warning: Test resource '$resourceName' not found in src/test/resources. Skipping tests for this format.")
                    // In a real scenario, you might want to fail the test setup if a crucial resource is missing.
                    continue // Skip this format if the resource is not found
                }

                // Get the InputStream for the resource
                val inputStream = resourceUrl.openStream()
                // Define the destination file in the temporary directory
                val destinationFile = File(testFilesDir, resourceName)

                // --- MODIFIED: Create parent directories if they don't exist ---
                destinationFile.parentFile?.mkdirs() // Create the 'video/' subdirectory if it doesn't exist

                // Copy the resource stream to the temporary file
                try {
                    Files.copy(inputStream, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    println("Copied test file: $resourceName to ${destinationFile.absolutePath}")
                } catch (e: Exception) {
                    System.err.println("Error copying test file '$resourceName': ${e.message}")
                    // You might want to fail the test setup here
                } finally {
                    inputStream.close() // Ensure the input stream is closed
                }
            }
        }

        // Method to provide test data for parameterized tests
        // We want to test converting FROM each supported format TO each supported format
        @JvmStatic
        fun conversionTestCases(): List<Arguments> {
            val testCases = mutableListOf<Arguments>()
            for (sourceFormat in SUPPORTED_IMAGE_FORMATS) {
                // Ensure the source file path for checking existence includes the subdirectory
                val sourceFileInTemp = File(testFilesDir, "image/sample.$sourceFormat") // MODIFIED
                if (!sourceFileInTemp.exists()) {
                    System.err.println("Skipping video test cases for source format '$sourceFormat' because test file was not found in temp directory.")
                    continue
                }

                for (targetFormat in SUPPORTED_IMAGE_FORMATS) {
                    if (sourceFormat != targetFormat) {
                        testCases.add(Arguments.of("image/sample.$sourceFormat", targetFormat)) // MODIFIED: Pass full resourceName
                    }
                }
            }
            return testCases
        }
    }

    // Helper function to get the correct FFmpegConvertibleType instance for a given file path
    // You'll need to expand this to handle all your image classes
    private fun getConvertibleFileInstance(filePath: String): FFmpegConvertibleType {
        // In a real test, you might use ffprobe here to reliably determine the type,
        // but for simplicity in tests, we'll assume the file extension is correct
        val extension = File(filePath).extension.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> JPEG(filePath)
            "png" -> PNG(filePath) // Assuming you have a PNG class
            "webp" -> WEBP(filePath) // Assuming you have a WEBP class
            "gif" -> GIF(filePath) // Assuming you have a GIF class
            "bmp" -> BMP(filePath) // Assuming you have a BMP class
            "tiff" -> TIFF(filePath) // Assuming you have a TIFF class
            //"ico" -> ICO(filePath) // Assuming you have an ICO class
            "avif" -> AVIF(filePath) // Assuming you have an AVIF class
            else -> throw IllegalArgumentException("Unsupported source format for testing: $extension")
        }
    }


    // --- Parameterized Test for All-to-All Conversions ---
    // This test will run multiple times, once for each set of data provided by conversionTestCases()
    @ParameterizedTest(name = "Convert from {0} to .{1}") // Provides a descriptive name for each test run
    @MethodSource("conversionTestCases") // Specifies the method that provides test data
    fun testImageConversion(sourceFileName: String, targetExtension: String) {
        println("\n--- Testing Conversion: $sourceFileName to .$targetExtension ---")

        // Construct the full path to the input test file in the temporary directory
        val inputFilePath = File(testFilesDir, sourceFileName).absolutePath

        // Check if the input file actually exists (important for @BeforeAll setup)
        val inputFile = File(inputFilePath)
        assertTrue(inputFile.exists(), "Input test file does not exist: $inputFilePath")

        // Get the correct FFmpegConvertibleType instance for the input file
        val sourceFile = getConvertibleFileInstance(inputFilePath)

        // Generate a unique output file path in the temporary directory
        val outputFileName = "${UUID.randomUUID()}.$targetExtension"//"${UUID.randomUUID()}.$targetExtension"
        val outputFilePath = File(testFilesDir, outputFileName).absolutePath

        // Ensure the output file does NOT exist before conversion
        val outputFile = File(outputFilePath)
        assertFalse(outputFile.exists(), "Output file already exists before conversion: $outputFilePath")

        // --- Perform the conversion ---
        val conversionResult = sourceFile.convertTo(outputFilePath, ffmpegExecutablePath)

        // --- Assert the result ---
        // Assert that the conversion was successful (exit code 0)
        assertTrue(conversionResult.isSuccess, "Conversion failed for $sourceFileName to .$targetExtension. FFmpeg Error:\n${conversionResult.error}")
        assertEquals(0, conversionResult.exitCode, "Conversion exited with non-zero code for $sourceFileName to .$targetExtension. FFmpeg Error:\n${conversionResult.error}")

        // Assert that the output file was created and is not empty
        assertTrue(outputFile.exists(), "Output file was not created after conversion: $outputFilePath")
        assertTrue(outputFile.length() > 0, "Output file is empty after conversion: $outputFilePath")

        println("Conversion successful: $sourceFileName to $outputFileName")

        // --- Cleanup ---
        // Delete the generated output file after the test
        assertTrue(outputFile.delete(), "Failed to delete output test file: $outputFilePath")
        println("Cleaned up output file: $outputFilePath")

        // The input files in testFilesDir are deleted by deleteOnExit() of the directory,
        // but you could also delete them here if they are generated per test.
    }

    // You might add specific tests for conversions that require custom flags,
    // e.g., testing the GIF to JPEG conversion specifically to ensure the -frames:v 1 flag is used.
    @Test
    fun testGifToJpegConversionSpecific() {
        println("\n--- Testing Specific GIF to JPEG Conversion ---")

        val sourceFileName = "image/sample.gif" // Assuming you have a sample.gif
        val targetExtension = "jpeg"

        val inputFilePath = File(testFilesDir, sourceFileName).absolutePath
        val outputFile = File(testFilesDir, "${UUID.randomUUID()}.$targetExtension")
        val outputFilePath = outputFile.absolutePath

        val gifFile = GIF(inputFilePath) // Instantiate GIF specifically

        assertFalse(outputFile.exists(), "Output file already exists before conversion: $outputFilePath")

        val conversionResult = gifFile.convertTo(outputFilePath, ffmpegExecutablePath)

        assertTrue(conversionResult.isSuccess, "GIF to JPEG conversion failed. FFmpeg Error:\n${conversionResult.error}")
        assertEquals(0, conversionResult.exitCode, "GIF to JPEG conversion exited with non-zero code. FFmpeg Error:\n${conversionResult.error}")
        assertTrue(outputFile.exists(), "Output JPEG file was not created after GIF to JPEG conversion: $outputFilePath")
        assertTrue(outputFile.length() > 0, "Output JPEG file is empty after GIF to JPEG conversion: $outputFilePath")

        println("Specific GIF to JPEG conversion successful.")
        assertTrue(outputFile.delete(), "Failed to delete output test file: $outputFilePath")
        println("Cleaned up output file: $outputFilePath")
    }

    // TODO: Add more specific tests for conversions with custom flags or expected behavior.
    // For example, test ICO conversion, test SVG conversion (which need different flags),
    // test animated GIF to MP4 (when I add video support).
}
