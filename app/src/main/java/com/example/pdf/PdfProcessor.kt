package com.example.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.db.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfProcessor {

    /**
     * Converts a list of Bitmaps into a single PDF file
     */
    suspend fun imagesToPdf(
        context: Context,
        bitmaps: List<Bitmap>,
        outputTitle: String,
        watermarkText: String? = null,
        signatureBitmap: Bitmap? = null
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val repository = DocumentRepository(context, com.example.data.db.AppDatabase.getDatabase(context).documentDao())
        val outputDir = repository.getDocScanDir()
        val outputFile = File(outputDir, "${outputTitle}_${System.currentTimeMillis()}.pdf")

        try {
            bitmaps.forEachIndexed { index, originalBitmap ->
                // A4 dimensions at 72 DPI (595 x 842 pt)
                val pageWidth = 595
                val pageHeight = 842

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Fill background white
                canvas.drawColor(Color.WHITE)

                // Scale image to fit within A4 margins
                val margin = 20
                val targetW = pageWidth - (margin * 2)
                val targetH = pageHeight - (margin * 2)

                val scale = Math.min(targetW.toFloat() / originalBitmap.width, targetH.toFloat() / originalBitmap.height)
                val scaledW = (originalBitmap.width * scale).toInt()
                val scaledH = (originalBitmap.height * scale).toInt()

                val left = margin + (targetW - scaledW) / 2f
                val top = margin + (targetH - scaledH) / 2f

                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledW, scaledH, true)
                canvas.drawBitmap(scaledBitmap, left, top, null)

                // Draw Watermark if provided
                if (!watermarkText.isNull_orEmpty()) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.RED
                        alpha = 70 // 28% opacity
                        textSize = 36f
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    canvas.save()
                    canvas.rotate(-35f, pageWidth / 2f, pageHeight / 2f)
                    canvas.drawText(watermarkText!!, pageWidth / 4f, pageHeight / 2f, paint)
                    canvas.restore()
                }

                // Draw Signature Overlay if provided
                if (signatureBitmap != null) {
                    val sigW = 160
                    val sigH = (signatureBitmap.height * (sigW.toFloat() / signatureBitmap.width)).toInt()
                    val sigScaled = Bitmap.createScaledBitmap(signatureBitmap, sigW, sigH, true)
                    canvas.drawBitmap(sigScaled, (pageWidth - sigW - 30).toFloat(), (pageHeight - sigH - 40).toFloat(), null)
                }

                pdfDocument.finishPage(page)
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        outputFile
    }

    private fun String?.isNull_orEmpty(): Boolean = this == null || this.trim().isEmpty()

    /**
     * Renders pages of a PDF file as Bitmaps
     */
    suspend fun pdfToImages(pdfFile: File): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }

            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bitmaps
    }

    /**
     * Merges multiple PDF files into one PDF
     */
    suspend fun mergePdfs(context: Context, pdfFiles: List<File>, outputTitle: String): File = withContext(Dispatchers.IO) {
        val allBitmaps = mutableListOf<Bitmap>()
        for (file in pdfFiles) {
            val pages = pdfToImages(file)
            allBitmaps.addAll(pages)
        }
        imagesToPdf(context, allBitmaps, outputTitle)
    }

    /**
     * Splits a PDF file by extracting selected page indices (0-indexed)
     */
    suspend fun splitPdf(context: Context, pdfFile: File, selectedPages: List<Int>, outputTitle: String): File = withContext(Dispatchers.IO) {
        val allBitmaps = pdfToImages(pdfFile)
        val selectedBitmaps = selectedPages.filter { it in allBitmaps.indices }.map { allBitmaps[it] }
        imagesToPdf(context, selectedBitmaps, outputTitle)
    }

    /**
     * Compresses PDF pages by re-encoding rendered bitmaps at requested compression level
     */
    suspend fun compressPdf(
        context: Context,
        pdfFile: File,
        compressionLevel: CompressionLevel,
        outputTitle: String
    ): File = withContext(Dispatchers.IO) {
        val originalBitmaps = pdfToImages(pdfFile)
        val compressedBitmaps = originalBitmaps.map { original ->
            val quality = when (compressionLevel) {
                CompressionLevel.EXTREME -> 30
                CompressionLevel.RECOMMENDED -> 60
                CompressionLevel.LOW -> 85
            }
            val scaleFactor = when (compressionLevel) {
                CompressionLevel.EXTREME -> 0.5f
                CompressionLevel.RECOMMENDED -> 0.75f
                CompressionLevel.LOW -> 0.95f
            }

            val newW = (original.width * scaleFactor).toInt()
            val newH = (original.height * scaleFactor).toInt()
            val resized = Bitmap.createScaledBitmap(original, newW, newH, true)

            val stream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val byteArray = stream.toByteArray()
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }

        imagesToPdf(context, compressedBitmaps, outputTitle)
    }

    /**
     * Rotates pages of a PDF by specified angle (90, 180, 270)
     */
    suspend fun rotatePdfPages(
        context: Context,
        pdfFile: File,
        degrees: Float,
        outputTitle: String
    ): File = withContext(Dispatchers.IO) {
        val originalBitmaps = pdfToImages(pdfFile)
        val rotatedBitmaps = originalBitmaps.map { src ->
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        }
        imagesToPdf(context, rotatedBitmaps, outputTitle)
    }
}

enum class CompressionLevel {
    EXTREME, // High compression, smaller size
    RECOMMENDED, // Good quality and compression balance
    LOW // High quality, low compression
}
