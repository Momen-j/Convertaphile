package org.example.photo

import org.example.utilities.FFmpegConvertibleType

class AVIF(override val inputFilePath: String): FFmpegConvertibleType {
    // override convertTo function in case I need to specify flags
}