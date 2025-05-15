package org.example.photo

import org.example.utilities.ConvertibleImageType

class BMP(override val inputFilePath: String): ConvertibleImageType {
    // override convertTo function in case I need to specify flags
}