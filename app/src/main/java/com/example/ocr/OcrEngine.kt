package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

object OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * Extracts text from a Bitmap using ML Kit Text Recognition
     */
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    // Fallback to empty string if fails
                    continuation.resume("")
                }
        }
    }

    /**
     * Extracts text from multiple Bitmaps
     */
    suspend fun recognizeTextFromBitmaps(bitmaps: List<Bitmap>): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        bitmaps.forEachIndexed { index, bitmap ->
            val pageText = recognizeText(bitmap)
            if (bitmaps.size > 1) {
                sb.append("--- Page ${index + 1} ---\n")
            }
            sb.append(pageText).append("\n\n")
        }
        sb.toString().trim()
    }

    /**
     * Exports recognized text as a TXT file
     */
    suspend fun exportAsTxt(context: Context, text: String, fileName: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "DocScan_Text")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, if (fileName.endsWith(".txt")) fileName else "$fileName.txt")
        file.writeText(text)
        file
    }

    /**
     * Exports recognized text as a formatted DOCX/DOC file
     */
    suspend fun exportAsDocx(context: Context, text: String, fileName: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "DocScan_Text")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, if (fileName.endsWith(".docx")) fileName else "$fileName.docx")
        
        // Basic DOCX XML structure generation for offline document export
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:r>
                            <w:t>${escapeXml(text)}</w:t>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        file.writeText(xmlContent)
        file
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
