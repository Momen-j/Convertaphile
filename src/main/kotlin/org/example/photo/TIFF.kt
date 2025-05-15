package org.example.photo

import org.example.utilities.ConvertibleImageType

class TIFF(override val inputFilePath: String): ConvertibleImageType {
    // override convertTo function in case I need to specify flags
}