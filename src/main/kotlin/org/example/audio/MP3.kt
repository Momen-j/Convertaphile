package org.example.audio

import org.example.utilities.FFmpegConvertibleType

class MP3(override val inputFilePath: String): FFmpegConvertibleType {
    // override convertTo function in case I need to specify flags
}