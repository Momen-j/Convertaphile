package org.example.photo

import org.example.utilities.FFmpegConvertibleType

class BMP(override val inputFilePath: String): FFmpegConvertibleType {
    // override convertTo function in case I need to specify flags
}