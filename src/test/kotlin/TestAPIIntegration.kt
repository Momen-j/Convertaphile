package org.example.test

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.* // Import Ktor's testing utilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

//import org.example.Application
import org.example.utilities.ConversionRouteConfig
import org.example.routes.conversionRoutes
import org.example.utilities.analyzeFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

// MAKE AN UTILITY FUNCTION
val determineContentType: (String) -> ContentType = { fileExtension ->
    when (fileExtension.lowercase()) {
        "png" -> ContentType.Image.PNG
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "webp" -> ContentType("image", "webp")
        "gif" -> ContentType.Image.GIF
        "bmp" -> ContentType("image", "bmp")
        "tiff" -> ContentType("image", "tiff")
        "ico" -> ContentType("image", "x-icon")
        "avif" -> ContentType("image", "avif")

        "mp3" -> ContentType.Audio.MPEG
        "aac" -> ContentType("audio", "aac")
        "wav" -> ContentType("audio", "wav")
        "flac" -> ContentType("audio", "flac")
        "ogg" -> ContentType.Audio.OGG
        "m4a" -> ContentType.Audio.MPEG

        "mp4" -> ContentType.Video.MP4
        "avi" -> ContentType("video", "x-msvideo")
        "mov" -> ContentType("video", "quicktime")
        "webm" -> ContentType("video", "webm")
        "mkv" -> ContentType("video", "x-matroska")
        "wmv" -> ContentType("video", "x-ms-wmv")

        else -> ContentType.Application.OctetStream // Default for unknown types
    }
}

// Define the list of supported image formats (extensions) for API testing
val API_TEST_IMAGE_FORMATS = listOf(
    "jpg", "png", "webp", "gif" // Keep this list smaller for faster API tests initially. add later -> ("avif", "bmp", "tiff")
)
// Define the list of supported video formats for API testing
val API_TEST_VIDEO_FORMATS = listOf(
    "mp4", "webm" // Focus on a couple of common video formats for API tests add later -> ("avi", "mkv", "wmv", "mov")
)
// Define the list of supported audio formats for API testing
val API_TEST_AUDIO_FORMATS = listOf(
    "mp3", "wav" // Focus on a couple of common audio formats for API tests add later -> ("aac", "flac", "m4a", "ogg")
)

class ApiIntegrationTest {
    companion object {
        private lateinit var ffmpegExecutablePath: String
        private lateinit var ffprobeExecutablePath: String
        private lateinit var testFilesDir: File // Temporary directory for test resources for API tests

        @BeforeAll
        @JvmStatic
        fun setUpAll() {
            ffmpegExecutablePath = System.getenv("FFMPEG_PATH")
                ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffmpeg.exe"
            ffprobeExecutablePath = System.getenv("FFPROBE_PATH")
                ?: "C:\\ffmpeg\\ffmpeg-7.0.2-full_build\\ffmpeg-7.0.2-full_build\\bin\\ffprobe.exe"

            // create temp dir for API test files
            testFilesDir = Files.createTempDirectory("api_conversion_tests").toFile()
            testFilesDir.deleteOnExit()

            val classLoader = this::class.java.classLoader

            // copy test images into test file dir
            for (format in API_TEST_IMAGE_FORMATS) {
                val resourceName = "image/sample.$format"
                val resourceUrl = classLoader.getResource(resourceName)
                if (resourceUrl == null) {
                    System.err.println("Warning: Image test resource '$resourceName' not found for API tests. Skipping.")
                    continue
                }
                // keep subdir in temp dir
                val destinationFile = File(testFilesDir, "image/sample.$format")
                destinationFile.parentFile?.mkdirs()
                Files.copy(resourceUrl.openStream(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                // println("Test image copied into ${destinationFile.absolutePath}") ADD IF I NEED TO LOG
            }

            // copy test videos into test file dir
            for (format in API_TEST_VIDEO_FORMATS) {
                val resourceName = "video/sample.$format"
                val resourceUrl = classLoader.getResource(resourceName)
                if (resourceUrl == null) {
                    System.err.println("Warning: Image test resource '$resourceName' not found for API tests. Skipping.")
                    continue
                }
                // keep subdir in temp dir
                val destinationFile = File(testFilesDir, "video/sample.$format")
                destinationFile.parentFile?.mkdirs()
                Files.copy(resourceUrl.openStream(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                // println("Test image copied into ${destinationFile.absolutePath}") ADD IF I NEED TO LOG
            }

            // copy test audios into test file dir
            for (format in API_TEST_AUDIO_FORMATS) {
                val resourceName = "audio/sample.$format"
                val resourceUrl = classLoader.getResource(resourceName)
                if (resourceUrl == null) {
                    System.err.println("Warning: Image test resource '$resourceName' not found for API tests. Skipping.")
                    continue
                }
                // keep subdir in temp dir
                val destinationFile = File(testFilesDir, "audio/sample.$format")
                destinationFile.parentFile?.mkdirs()
                Files.copy(resourceUrl.openStream(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                // println("Test image copied into ${destinationFile.absolutePath}") ADD IF I NEED TO LOG
            }
        }

        // Test Cases for /conversion endpoint
        @JvmStatic
        fun conversionApiTestCases(): List<Arguments> {
            val testCases = mutableListOf<Arguments>()

            // Image to Image conversions
            for (sourceFormat in API_TEST_IMAGE_FORMATS) {
                for (targetFormat in API_TEST_IMAGE_FORMATS) {
                    if (sourceFormat != targetFormat) {
                        testCases.add(Arguments.of("image/sample.$sourceFormat", targetFormat, "image"))
                    } else {
                        continue
                    }
                }
            }

            // Video to Video conversions
            for (sourceFormat in API_TEST_VIDEO_FORMATS) {
                for (targetFormat in API_TEST_VIDEO_FORMATS) {
                    if (sourceFormat != targetFormat) {
                        testCases.add(Arguments.of("video/sample.$sourceFormat", targetFormat, "video"))
                    } else {
                        continue
                    }
                }
            }

            // Video to Audio conversions
            for (sourceFormat in API_TEST_VIDEO_FORMATS) {
                for (targetFormat in API_TEST_AUDIO_FORMATS) {
                    testCases.add(Arguments.of("video/sample.$sourceFormat", targetFormat, "audio"))
                }
            }

            // Audio to Audio conversions
            for (sourceFormat in API_TEST_AUDIO_FORMATS) {
                for (targetFormat in API_TEST_AUDIO_FORMATS) {
                    if (sourceFormat != targetFormat) {
                        // EXCLUDE LOSSLESS TO LOSSY CONVERSIONS (like wav to mp3)
                        if (LOSSLESS_AUDIO_FORMATS.contains(sourceFormat) && LOSSY_AUDIO_FORMATS.contains(targetFormat)) {
                            println("Skipping API test: Audio conversion from .$sourceFormat (lossless) to .$targetFormat (lossy).")
                            continue
                        }
                        testCases.add(Arguments.of("audio/sample.$sourceFormat", targetFormat, "audio"))
                    } else {
                        continue
                    }
                }
            }

            return testCases
        }
    }

    // test the /health endpoint
    @Test
    fun testHealthEndpoint() = testApplication {
        // configure application module for test
        application {
            // pass config needed by routes
            val routeConfig = ConversionRouteConfig(
                ffmpegExecutablePath = ffmpegExecutablePath,
                ffprobeExecutablePath = ffprobeExecutablePath,
                testFilesDir
            )

            // install plugins for routes
            this.install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }

            // install routes
            this.routing {
                conversionRoutes(routeConfig)
            }
        }

        // make a get request to /health
        val response = client.get("/health")

        // assert appropriate response
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Fine & Dandy!", response.bodyAsText())
    }

    // Parameterized test for /conversion endpoint
    @ParameterizedTest(name = "API Convert {0} to .{1} ({2})")
    @MethodSource("conversionApiTestCases")
    fun testConversionEndpoint(sourceFileNameWithDir: String, targetExtension: String, mediaType: String) =
        testApplication {
            // configure application
            application {
                // pass config needed by routes
                val routeConfig = ConversionRouteConfig(
                    ffmpegExecutablePath = ffmpegExecutablePath,
                    ffprobeExecutablePath = ffprobeExecutablePath,
                    testFilesDir
                )

                // install plugins for routes
                this.install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json()
                }

                // install routes
                this.routing {
                    conversionRoutes(routeConfig)
                }
            }

            // construct full path to input test file
            val inputTestFile = File(testFilesDir, sourceFileNameWithDir)
            assertTrue(inputTestFile.exists(), "Input test file doesn't exist: ${inputTestFile.absolutePath}")

            // make a post request to /conversion with the file and target format
            val response = client.post("/conversion") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            // Add the file portion of part
                            append("file", inputTestFile.readBytes(), Headers.build {
                                append(
                                    HttpHeaders.ContentType,
                                    ContentType.parse(determineContentType(inputTestFile.extension).toString())
                                )
                                append(HttpHeaders.ContentDisposition, "filename=\"${inputTestFile.name}\"")
                            })
                            // add the target format part
                            append("targetFormat", targetExtension)
                        }
                    )
                )
            }

            // assert the response status
            assertEquals(
                HttpStatusCode.OK,
                response.status,
                "API conversion failed for $sourceFileNameWithDir to .$targetExtension. Response: ${response.bodyAsText()}"
            )

            // assert content-disposition header
            val contentDispositionHeader = response.headers[HttpHeaders.ContentDisposition]
            assertNotNull(contentDispositionHeader, "Content-Disposition header is missing")
            assertTrue(
                contentDispositionHeader!!.contains("attachment"),
                "Content-Disposition header is not 'attachment'."
            )

            // assert content type header
            val contentTypeHeader = response.headers[HttpHeaders.ContentType]
            assertNotNull(contentTypeHeader, "Content-Type header is missing")
            assertEquals(
                determineContentType(targetExtension).toString(),
                contentTypeHeader,
                "Content type header is incorrect"
            )

            // check if response body isn't empty (essentially checking if a file was returned
            val responseBytes = response.body<ByteArray>()
            assertTrue(responseBytes.isNotEmpty(), "Response body for converted file is empty")
        }

    // TODO: Add tests for error cases (e.g., missing file, unsupported format, FFmpeg failure)
    @Test
    fun testConversionBadRequestMissingFile() = testApplication {
        application {
            val routeConfig = ConversionRouteConfig(ffmpegExecutablePath, ffprobeExecutablePath, testFilesDir)
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { conversionRoutes(routeConfig) }
        }
        val response = client.post("/conversion") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // Missing 'file' part
                        append("targetFormat", "png")
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Missing file or target format", response.bodyAsText())
    }

    @Test
    fun testConversionBadRequestMissingTargetFormat() = testApplication {
        application {
            val routeConfig = ConversionRouteConfig(ffmpegExecutablePath, ffprobeExecutablePath, testFilesDir)
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
            routing { conversionRoutes(routeConfig) }
        }
        val inputTestFile = File(testFilesDir, "image/sample.jpg")
        val response = client.post("/conversion") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", inputTestFile.readBytes(), Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"${inputTestFile.name}\"")
                        })
                        // Missing 'targetFormat' part
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Missing file or target format", response.bodyAsText())
    }

}