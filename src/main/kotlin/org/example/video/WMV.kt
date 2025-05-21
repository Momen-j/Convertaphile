package org.example.video

import org.example.utilities.FFmpegConvertibleType

class WMV(override val inputFilePath: String): FFmpegConvertibleType {
    // override convertTo function in case I need to specify flags
}