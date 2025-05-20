package org.example.photo

import org.example.utilities.ConversionResult
import org.example.utilities.ConvertibleImageType
import org.example.utilities.analyzeFile
import org.example.utilities.executeCommand
import java.io.File
import java.util.UUID

class ICO(override val inputFilePath: String): ConvertibleImageType {

}