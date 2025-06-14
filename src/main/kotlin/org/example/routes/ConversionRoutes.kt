package org.example.routes


import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.ContentType
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

import org.example.utilities.*
import org.example.photo.*
import org.example.audio.*

import java.io.File
import java.util.UUID // to generate unique filenames
import java.nio.file.Files // for temp dir creation

import org.example.utilities.ConversionRouteConfig
import org.example.utilities.ConversionStatsResponse
import org.example.video.AVI
import org.example.video.MKV
import org.example.video.MOV
import org.example.video.MP4
import org.example.video.WEBM
import org.example.video.WMV
import java.io.InputStream

// extension function on Routing Class
// handles all conversion related endpoints
fun Routing.conversionRoutes(config: ConversionRouteConfig) {
    // destructure ConversionRouteConfig object
    val ffmpegExecutablePath = config.ffmpegExecutablePath
    val ffprobeExecutablePath = config.ffprobeExecutablePath
    val tempFilesBaseDir = config.tempFilesBaseDir
    val redisClient = config.jedisPool

    // either make into an exportable separate util function, use ffprobe (why am i not???), or make a service class
    // for time being we are using separate util function defined within extension function
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

    // handles GET requests to /health
    get("/health") {
        call.respondText("Fine & Dandy!")
    }

    // handles POST requests to /conversion
    // File upload & conversion endpoint
    post("/conversion") {
        // vars needed in order to create final file output
        var tempInputFile: File? = null
        var outputFilePath: String? = null
        var inputFileName: String? = null
        var targetExtension: String? = null

        try {
            // call.receiveMultipart() reads the incoming HTTP request body as a multipart stream.
            val multipart = call.receiveMultipart()

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        inputFileName = part.originalFileName
                        // USE FFPROBE OR SIMILAR UTIL FUNCTION HERE
                        val fileExtension = inputFileName?.substringAfterLast(".", "")
                        val contentType = part.contentType?.contentType // Main part of MIME Type (ex. image)
                        val contentSubType = part.contentType?.contentSubtype // Subtype of MIME Type (ex. jpeg)

                        println("Received file: $inputFileName, Type: $contentType/$contentSubType")

                        // create a temporary file on server to store uploaded content
                        tempInputFile = File.createTempFile(
                            "uploaded_", if (!fileExtension.isNullOrEmpty()) ".$fileExtension"
                            else null, tempFilesBaseDir
                        )
                        // delete file on JVM exit (will add deletion logic after converion)
                        tempInputFile.deleteOnExit()

                        // copy the content of the uploaded file part to the temp file
                        // part.streamProvider(): Returns a lambda that provides an InputStream.
                        // .use { inputStream -> ... }: Ensures the InputStream is closed after use.
                        // inputStream.copyTo(outputStream): Efficiently copies bytes from input to output.
                        // streamProvider is deprecated, but used as a workaround.
                        @Suppress("DEPRECATION") // Suppress deprecation warning for streamProvider
                        tempInputFile.outputStream().use { outputStream ->
                            part.streamProvider().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        println("Saved uploaded file to: ${tempInputFile.absolutePath}")
                    }

                    // handles form field data like when a user specifies
                    // which type of file they want to convert theirs into
                    is PartData.FormItem -> {
                        // extract form data
                        if (part.name == "targetFormat") {
                            targetExtension = part.value.lowercase()
                            println("Requested target format: $targetExtension")
                        }
                    }

                    else -> {
                        println("Skipping unknown part type ${part.name}")
                    }
                }
                part.dispose() // release resources allocated to that specific part, allowing it to be garbage collected
            }

            // UPLOAD VALIDATION
            // validate that tempinput file isn't empty or null/exists,
            // inputFileName isn't null, & targetExtension isn't null
            if (tempInputFile == null || !tempInputFile.exists() || inputFileName == null || targetExtension == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing file or target format")
                return@post
            }

            // analyze the file with ffprobe to determine actual type
            println("\n--- Analyzing Input File with FFprobe ---")
            val ffprobeData = analyzeFile(tempInputFile.absolutePath, ffprobeExecutablePath)

            // If ffprobe analysis fails, send a 415 Unsupported Media Type response.
            if (ffprobeData == null) {
                call.respond(HttpStatusCode.UnsupportedMediaType, "Could not analyze input file type. Is it a valid media file?")
                return@post
            }

            // determine File Type and Instantiate the Correct Class
            // MAKE INTO ITS OWN FUNCTION
            val formatName = ffprobeData?.format?.formatName
            val hasVideoStream = ffprobeData?.streams?.any { it.codecType == "video" } == true
            val hasAudioStream = ffprobeData?.streams?.any { it.codecType == "audio" } == true
            val fileExtension = tempInputFile.extension.lowercase() // use file extension as fallback check w ffprobe

            val fileToConvert: FFmpegConvertibleType? = when {
                formatName?.contains("jpeg") == true || fileExtension == "jpeg" || fileExtension == "jpg" -> { // CHECK IF I NEED TO HAVE THIS ALSO CHECK IF IT CONTAINS jpg
                    println("\n~~ Detected file type: .JPEG ~~")
                    JPEG(tempInputFile.absolutePath)
                }
                formatName?.contains("png") == true || fileExtension == "png"-> {
                    println("\n~~ Detected file type: .PNG ~~")
                    PNG(tempInputFile.absolutePath)
                }
                formatName?.contains("webp") == true || fileExtension == "webp" -> {
                    println("\n~~ Detected file type: .WEBP ~~")
                    WEBP(tempInputFile.absolutePath)
                }
                formatName?.contains("gif") == true || fileExtension == "gif" -> {
                    println("\n~~ Detected file type: .GIF ~~")
                    GIF(tempInputFile.absolutePath)
                }
                formatName?.contains("avif") == true || fileExtension == "avif" -> {
                    println("\n~~ Detected file type: .AVIF ~~")
                    AVIF(tempInputFile.absolutePath)
                }
                formatName?.contains("bmp") == true || fileExtension == "bmp" -> {
                    println("\n~~ Detected file type: .BMP ~~")
                    BMP(tempInputFile.absolutePath)
                }
                formatName?.contains("tiff") == true || fileExtension == "tiff" -> {
                    println("\n~~ Detected file type: .TIFF ~~")
                    TIFF(tempInputFile.absolutePath)
                }
                hasVideoStream -> {
                    println("\n~~ Detected a VIDEO file")
                    if (formatName?.contains("mp4") == true || fileExtension == "mp4") {
                        println("\n~~ Detected file type: .MP4 ~~")
                        MP4(tempInputFile.absolutePath)
                    } else if (formatName?.contains("mkv") == true) {
                        println("\n~~ Detected file type: .MKV ~~")
                        MKV(tempInputFile.absolutePath)
                    } else if (formatName?.contains("mov") == true) {
                        println("\n~~ Detected file type: .MOV ~~")
                        MOV(tempInputFile.absolutePath)
                    } else if (formatName?.contains("avi") == true) {
                        println("\n~~ Detected file type: .AVI ~~")
                        AVI(tempInputFile.absolutePath)
                    } else if (formatName?.contains("webm") == true) {
                        println("\n~~ Detected file type: .WEBM ~~")
                        WEBM(tempInputFile.absolutePath)
                    } else if (formatName?.contains("wmv") == true) {
                        println("\n~~ Detected file type: .WMV ~~")
                        WMV(tempInputFile.absolutePath)
                    } else {
                        // Fallback for other video containers, or throw error if not supported
                        System.err.println("Detected unsupported video container: $formatName")
                        null
                    }
                }
                hasAudioStream -> {
                    println("\n~~ Detected an AUDIO file")
                    if (formatName?.contains("mp3") == true || ffprobeData.streams?.any { it.codecType == "audio" && it.codecName == "mp3" } == true) {
                        MP3(tempInputFile.absolutePath)
                    } else if (formatName?.contains("aac") == true || ffprobeData.streams?.any { it.codecType == "audio" && it.codecName == "aac" } == true) {
                        AAC(tempInputFile.absolutePath)
                    } else if (formatName?.contains("flac") == true || ffprobeData.streams?.any { it.codecType == "audio" && it.codecName == "flac" } == true) {
                        FLAC(tempInputFile.absolutePath)
                    } else if (formatName?.contains("m4a") == true || ffprobeData.streams?.any { it.codecType == "audio" && it.codecName == "m4a" } == true) {
                        M4A(tempInputFile.absolutePath)
                    } else if (formatName?.contains("ogg") == true || ffprobeData.streams?.any { it.codecType == "audio" && it.codecName == "ogg" } == true) {
                        OGG(tempInputFile.absolutePath)
                    } else if (formatName?.contains("wav") == true || ffprobeData.streams?.any { it.codecType == "audio" && it.codecName == "wav" } == true) {
                        WAV(tempInputFile.absolutePath)
                    } else {
                        // Fallback for other audio containers, or throw error if not supported
                        System.err.println("Detected unsupported audio container: $formatName")
                        null
                    }
                }
                else -> {
                    System.err.println("!! Detected unknown/unsupported file type !!")
                    null
                }
            }

            // if no supported file type was created, send a 400 bad request
            if (fileToConvert == null) {
                call.respond(HttpStatusCode.BadRequest, "Unsupported input file type detected.")
                return@post
            }

            // generate output file path
            val outputFileName = "convertaphile_${UUID.randomUUID()}.$targetExtension"
            val outputDir = tempFilesBaseDir
            outputDir.mkdirs()
            outputFilePath = File(outputDir, outputFileName).absolutePath

            // execute conversion
            println("\n !!! Executing Conversion !!!")
            val conversionResult = fileToConvert.convertTo(outputFilePath!!, ffmpegExecutablePath)

            // if conversion failed, send a 500 internal server error
            if (!conversionResult.isSuccess) {
                call.respond(HttpStatusCode.InternalServerError, "File conversion failed: ${conversionResult.error}")
                return@post
            }

            // setup file to be returned to the user
            println("\n Sending converted file to user")
            val convertedFile = File(outputFilePath)

            // if the converted file doesn't exist/the length of file is 0
            if (!convertedFile.exists() || convertedFile.length() == 0L) {
                call.respond(HttpStatusCode.InternalServerError, "Converted file not found or is empty")
                return@post
            }

            // properly setup HTTP Headers (metadata about message being returned and its content)

            // call.response.header(name, value): Sets an HTTP header.
            // HttpHeaders.ContentDisposition: Standard header name for content disposition.
            // ContentDisposition.Attachment: Value indicating the content should be downloaded.
            // .withParameter(ContentDisposition.Parameters.FileName, convertedFile.name): Adds filename parameter.
            // .toString(): Converts the Ktor object to the required String format for the header value.
            call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, convertedFile.name).toString())

            // HttpHeaders.ContentType: Standard header name for content type.
            // determineContentType(...).toString(): Gets the MIME type string (e.g., "image/png").
            call.response.header(HttpHeaders.ContentType, determineContentType(convertedFile.extension).toString())

            // call.respondFile(file): Ktor function to send the content of a File as the response body.
            // It automatically sets the status to 200 OK if successful.
            // call.respondFile((convertedFile))

            // Create a permanent storage directory (separate from temp files)
            val permanentStorageDir = File(tempFilesBaseDir.parent, "converted_files")
            permanentStorageDir.mkdirs()

            // Generate a unique ID for this conversion
            val conversionId = UUID.randomUUID().toString()
            val storedFileName = "${conversionId}_${inputFileName?.substringBeforeLast(".")}.${targetExtension}"
            val permanentFilePath = File(permanentStorageDir, storedFileName)

            // Move the converted file to permanent storage
            convertedFile.copyTo(permanentFilePath, overwrite = true)

            // Get file metadata
            val fileSizeBytes = permanentFilePath.length()
            val fileSizeMB = fileSizeBytes / (1024.0 * 1024.0)

            // Redis tracking - increment conversion statistics
            redisClient.hincrby("conversion_stats", "total_files", 1)
            redisClient.hincrbyfloat("conversion_stats", "total_size_mb", fileSizeMB)

            // Return conversion metadata instead of the file
            // Return conversion metadata instead of the file
            call.respond(ConversionResponse(
                conversionId = conversionId,
                originalFileName = inputFileName ?: "unknown",
                convertedFileName = storedFileName,
                targetFormat = targetExtension,
                fileSizeBytes = fileSizeBytes,
                fileSizeMB = String.format("%.2f", fileSizeMB),
                downloadUrl = "/download/${conversionId}",
                message = "File converted successfully"
            ))

            println("✅ File stored at: ${permanentFilePath.absolutePath}")


        } catch (e: Exception) {
            System.err.println("Error occurred during file conversion at /convert endpoint: \n${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Unexpected error occurred during conversion: \n${e.message}")
        } finally {
            // THIS BLOCK WILL ALWAYS EXECUTE
            // Ensures temp files are deleted to manage privacy and disk space
            // clean up the temp input file
            tempInputFile?.delete()
            outputFilePath?.let {File(it).delete()}
            println("Temp file cleanup completed")
        }
    }

    get("/download/{conversionId}") {
        val conversionId = call.parameters["conversionId"]

        if (conversionId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Missing conversion ID")
            return@get
        }

        try {
            // Create path to permanent storage directory
            val permanentStorageDir = File(tempFilesBaseDir.parent, "converted_files")

            // Find the file with this conversion ID
            val files = permanentStorageDir.listFiles { file ->
                file.name.startsWith("${conversionId}_")
            }

            if (files.isNullOrEmpty()) {
                call.respond(HttpStatusCode.NotFound, "File not found or has expired")
                return@get
            }

            val convertedFile = files.first()

            if (!convertedFile.exists() || convertedFile.length() == 0L) {
                call.respond(HttpStatusCode.NotFound, "File not found or is empty")
                return@get
            }

            // Set proper headers for file download
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    convertedFile.name.substringAfter("_") // Remove the UUID prefix
                ).toString()
            )

            call.response.header(
                HttpHeaders.ContentType,
                determineContentType(convertedFile.extension).toString()
            )

            // Track download in Redis
            redisClient.hincrby("conversion_stats", "total_downloads", 1)

            // Send the file
            call.respondFile(convertedFile)

            println("✅ File downloaded: ${convertedFile.name}")

            // CLEANUP: Delete the file after successful download
            try {
                if (convertedFile.delete()) {
                    println("🗑️ Cleaned up downloaded file: ${convertedFile.name}")
                } else {
                    System.err.println("⚠️ Failed to delete file after download: ${convertedFile.name}")
                }
            } catch (deleteException: Exception) {
                System.err.println("⚠️ Error deleting file after download: ${deleteException.message}")
            }

        } catch (e: Exception) {
            System.err.println("Error serving download: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Error retrieving file")
        }
    }

    get("/stats") {
        try {
            val stats = redisClient.hgetall("conversion_stats")

            val response = ConversionStatsResponse(
                totalFiles = stats["total_files"]?.toLongOrNull() ?: 0L,
                totalSizeMB = stats["total_size_mb"]?.toDoubleOrNull() ?: 0.0,
                totalDownloads = stats["total_downloads"]?.toLongOrNull() ?: 0L,
                message = "Statistics retrieved successfully"
            )

            call.respond(response)

        } catch (e: Exception) {
            System.err.println("Error retrieving stats: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Error retrieving statistics")
        }
    }
}
