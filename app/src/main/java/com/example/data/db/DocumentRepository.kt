package com.example.data.db

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao
) {

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val favoriteDocuments: Flow<List<DocumentEntity>> = documentDao.getFavoriteDocuments()

    fun getDocumentsByCategory(category: String): Flow<List<DocumentEntity>> {
        return if (category == "All") {
            documentDao.getAllDocuments()
        } else if (category == "Favorites") {
            documentDao.getFavoriteDocuments()
        } else {
            documentDao.getDocumentsByCategory(category)
        }
    }

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> {
        return documentDao.searchDocuments(query)
    }

    /**
     * Gets the custom Scoped Storage directory "DocScan"
     */
    fun getDocScanDir(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DocScan")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Saves a bitmap image to DocScan directory and records in DB
     */
    suspend fun saveScannedImage(
        bitmap: Bitmap,
        title: String,
        category: String = "Scanned Docs",
        extractedText: String = ""
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val dir = getDocScanDir()
        val fileName = "${title}_${System.currentTimeMillis()}.jpg"
        val file = File(dir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        val doc = DocumentEntity(
            title = title,
            filePath = file.absolutePath,
            category = category,
            pageCount = 1,
            fileSizeBytes = file.length(),
            createdAt = System.currentTimeMillis(),
            thumbnailPath = file.absolutePath,
            extractedText = extractedText
        )

        val id = documentDao.insertDocument(doc)
        doc.copy(id = id)
    }

    /**
     * Saves a PDF file to DocScan directory and records in DB
     */
    suspend fun savePdfDocument(
        pdfFile: File,
        title: String,
        category: String = "Converted PDFs",
        pageCount: Int = 1,
        thumbnailBitmap: Bitmap? = null,
        extractedText: String = ""
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val dir = getDocScanDir()
        val targetFile = if (pdfFile.parentFile == dir) pdfFile else File(dir, "${title}_${System.currentTimeMillis()}.pdf")
        
        if (pdfFile != targetFile && pdfFile.exists()) {
            pdfFile.copyTo(targetFile, overwrite = true)
        }

        var thumbPath: String? = null
        if (thumbnailBitmap != null) {
            val thumbFile = File(dir, "thumb_${targetFile.nameWithoutExtension}.jpg")
            FileOutputStream(thumbFile).use { out ->
                thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            thumbPath = thumbFile.absolutePath
        } else {
            // Generate thumbnail from first page of PDF using PdfRenderer
            thumbPath = generatePdfThumbnail(targetFile)
        }

        val doc = DocumentEntity(
            title = title,
            filePath = targetFile.absolutePath,
            category = category,
            pageCount = pageCount,
            fileSizeBytes = targetFile.length(),
            createdAt = System.currentTimeMillis(),
            thumbnailPath = thumbPath,
            extractedText = extractedText
        )

        val id = documentDao.insertDocument(doc)
        doc.copy(id = id)
    }

    private fun generatePdfThumbnail(pdfFile: File): String? {
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()

                val thumbFile = File(getDocScanDir(), "thumb_${pdfFile.nameWithoutExtension}.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                thumbFile.absolutePath
            } else {
                renderer.close()
                pfd.close()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun toggleFavorite(doc: DocumentEntity) {
        documentDao.updateFavoriteStatus(doc.id, !doc.isFavorite)
    }

    suspend fun deleteDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(doc.filePath)
            if (file.exists()) {
                file.delete()
            }
            doc.thumbnailPath?.let {
                val thumbFile = File(it)
                if (thumbFile.exists() && thumbFile.absolutePath != doc.filePath) {
                    thumbFile.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        documentDao.deleteDocument(doc)
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    suspend fun updateDocument(doc: DocumentEntity) {
        documentDao.updateDocument(doc)
    }
}
