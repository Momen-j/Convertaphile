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

    get("/health") {
        call.respondText("Fine & Dandy!")
    }

    // File upload & conversion endpoint
    post("/conversion") {
        // Handle File Upload from User
        val multipart = call.receiveMultipart()
        var tempInputFile: File? = null
        var outputFilePath: String? = null
        var inputFileName: String? = null
        var targetExtension: String? = null

        try {
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        inputFileName = part.originalFileName
                        // USE FFPROBE OR SIMILAR UTIL FUNCTION HERE
                        val fileExtension = inputFileName?.substringAfterLast(".", "")
                        val contentType = part.contentType?.contentType
                        val contentSubType = part.contentType?.contentSubtype

                        println("Received file: $inputFileName, Type: $contentType/$contentSubType")

                        // save uploaded file to temp location
                        tempInputFile = File.createTempFile(
                            "uploaded_", if (!fileExtension.isNullOrEmpty()) ".$fileExtension"
                            else null, tempFilesBaseDir
                        )
                        // delete file on JVM exit (will add deletion logic after converion)
                        tempInputFile.deleteOnExit()

                        // Use part.streamProvider().invoke() and java.io.InputStream.copyTo ---
                        // This is a robust fallback when Ktor's content property/copyTo extension is not resolving.
                        // Acknowledge streamProvider is deprecated, but used as a workaround.
                        @Suppress("DEPRECATION") // Suppress deprecation warning for streamProvider
                        tempInputFile.outputStream().use { outputStream ->
                            part.streamProvider().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        println("Saved uploaded file to: ${tempInputFile.absolutePath}")
                    }

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
                part.dispose()
            }

            // validate that tempinput file isn't empty or null/exists,
            // inputFileName isn't null & targetExtension isn't null
            if (tempInputFile == null || !tempInputFile.exists() || inputFileName == null || targetExtension == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing file or target format")
                return@post
            }

            // analyze the file with ffprobe to determine actual type
            println("\n--- Analyzing Input File with FFprobe ---")
            val ffprobeData = analyzeFile(tempInputFile.absolutePath, ffprobeExecutablePath)

            // determine File Type and Instantiate the Correct Class
            // MAKE INTO ITS OWN FUNCTION
            val formatName = ffprobeData?.format?.formatName
            val hasVideoStream = ffprobeData?.streams?.any { it.codecType == "video" } == true
            val hasAudioStream = ffprobeData?.streams?.any { it.codecType == "audio" } == true
            val fileExtension = tempInputFile.extension.toLowerCase() // Get the file extension

            val fileToConvert: FFmpegConvertibleType? = when {
                formatName?.contains("jpeg") == true || fileExtension == "jpeg" -> { // CHECK IF I NEED TO HAVE THIS ALSO CHECK IF IT CONTAINS jpg
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

            // if file to convert after
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

            if (!conversionResult.isSuccess) {
                call.respond(HttpStatusCode.InternalServerError, "File conversion failed: ${conversionResult.error}")
                return@post
            }

            // return the converted file
            println("\n Sending converted file to user")
            val convertedFile = File(outputFilePath)

            // if the converted file doesn't exist/the length of file is 0
            if (!convertedFile.exists() || convertedFile.length() == 0L) {
                call.respond(HttpStatusCode.InternalServerError, "Converted file not found or is empty")
                return@post
            }

            call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, convertedFile.name).toString())
            call.response.header(HttpHeaders.ContentType, determineContentType(convertedFile.extension).toString())
            call.respondFile((convertedFile))
        } catch (e: Exception) {
            System.err.println("Error occurred during file conversion at /convert endpoint: \n${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Unexpected error occurred during conversion: \n${e.message}")
        } finally {
            // clean up the temp input file
            tempInputFile?.delete()
            outputFilePath?.let {File(it).delete()}
            println("Temp file cleanup completed")
        }
    }}
