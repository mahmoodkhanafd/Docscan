package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DocScanApplication
import com.example.ads.AdManager
import com.example.data.db.DocumentEntity
import com.example.ocr.OcrEngine
import com.example.pdf.CompressionLevel
import com.example.pdf.PdfProcessor
import com.example.scanner.ImageProcessor
import com.example.scanner.ScanFilter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object CameraScan : Screen("camera_scan")
    object IdCardScan : Screen("id_card_scan")
    object PdfTools : Screen("pdf_tools")
    object FileManager : Screen("file_manager")
    object Ocr : Screen("ocr")
    object Viewer : Screen("viewer")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as DocScanApplication).repository

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val documents: StateFlow<List<DocumentEntity>> = combine(
        _selectedCategory,
        _searchQuery,
        repository.allDocuments
    ) { category, query, allDocs ->
        var list = if (category == "All") {
            allDocs
        } else if (category == "Favorites") {
            allDocs.filter { it.isFavorite }
        } else {
            allDocs.filter { it.category == category }
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.tags.contains(query, ignoreCase = true) ||
                it.extractedText.contains(query, ignoreCase = true)
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dark Theme State (null = system default, true = Dark/Night, false = Light/Day)
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    fun toggleDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
    }

    // Currently viewed or selected document
    private val _selectedDocument = MutableStateFlow<DocumentEntity?>(null)
    val selectedDocument: StateFlow<DocumentEntity?> = _selectedDocument.asStateFlow()

    // Recently saved document for Save & Share dialog flow
    private val _recentlySavedDocument = MutableStateFlow<DocumentEntity?>(null)
    val recentlySavedDocument: StateFlow<DocumentEntity?> = _recentlySavedDocument.asStateFlow()

    // OCR extracted text state
    private val _ocrText = MutableStateFlow("")
    val ocrText: StateFlow<String> = _ocrText.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectDocument(doc: DocumentEntity?) {
        _selectedDocument.value = doc
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearRecentlySavedDocument() {
        _recentlySavedDocument.value = null
    }

    private fun handlePostSaveAction(doc: DocumentEntity, activity: Activity?) {
        _recentlySavedDocument.value = doc
        _selectedDocument.value = doc
        activity?.let { act ->
            AdManager.showInterstitialAd(act) {
                shareDocument(act, doc)
            }
        } ?: run {
            shareDocument(getApplication(), doc)
        }
    }

    // Save single scanned image with filter
    fun saveScannedImage(
        bitmap: Bitmap,
        filter: ScanFilter,
        title: String,
        category: String = "Scanned Docs",
        activity: Activity? = null
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val enhancedBitmap = ImageProcessor.applyFilter(bitmap, filter)
                val text = OcrEngine.recognizeText(enhancedBitmap)
                val doc = repository.saveScannedImage(enhancedBitmap, title, category, text)
                _statusMessage.value = "Document saved to DocScan!"
                handlePostSaveAction(doc, activity)
            } catch (e: Exception) {
                _statusMessage.value = "Error saving document: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Save ID Card dual side
    fun saveIdCardScan(
        front: Bitmap,
        back: Bitmap,
        title: String,
        activity: Activity? = null
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val a4Sheet = ImageProcessor.createIdCardA4Sheet(front, back, "ID CARD: $title")
                val textFront = OcrEngine.recognizeText(front)
                val textBack = OcrEngine.recognizeText(back)
                val text = "$textFront\n$textBack"

                val doc = repository.saveScannedImage(a4Sheet, title, "ID Cards", text)
                _statusMessage.value = "ID Card scan created & saved to DocScan!"
                handlePostSaveAction(doc, activity)
            } catch (e: Exception) {
                _statusMessage.value = "Error creating ID card scan: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Convert Bitmaps to PDF
    fun convertImagesToPdf(
        bitmaps: List<Bitmap>,
        title: String,
        watermark: String? = null,
        signature: Bitmap? = null,
        activity: Activity? = null
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val pdfFile = PdfProcessor.imagesToPdf(getApplication(), bitmaps, title, watermark, signature)
                val text = OcrEngine.recognizeTextFromBitmaps(bitmaps)
                val doc = repository.savePdfDocument(
                    pdfFile = pdfFile,
                    title = title,
                    category = "Converted PDFs",
                    pageCount = bitmaps.size,
                    thumbnailBitmap = bitmaps.firstOrNull(),
                    extractedText = text
                )
                _statusMessage.value = "PDF created successfully!"
                handlePostSaveAction(doc, activity)
            } catch (e: Exception) {
                _statusMessage.value = "Error creating PDF: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Process OCR on bitmap
    fun processOcr(bitmap: Bitmap) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val text = OcrEngine.recognizeText(bitmap)
                _ocrText.value = text
            } catch (e: Exception) {
                _statusMessage.value = "OCR Failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Merge multiple PDFs
    fun mergePdfs(pdfFiles: List<File>, title: String, activity: Activity? = null) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val mergedFile = PdfProcessor.mergePdfs(getApplication(), pdfFiles, title)
                val doc = repository.savePdfDocument(
                    pdfFile = mergedFile,
                    title = title,
                    category = "Converted PDFs"
                )
                _statusMessage.value = "PDFs merged successfully!"
                handlePostSaveAction(doc, activity)
            } catch (e: Exception) {
                _statusMessage.value = "Merge failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Compress PDF
    fun compressPdf(pdfFile: File, compressionLevel: CompressionLevel, title: String, activity: Activity? = null) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val compressed = PdfProcessor.compressPdf(getApplication(), pdfFile, compressionLevel, title)
                val doc = repository.savePdfDocument(
                    pdfFile = compressed,
                    title = title,
                    category = "Converted PDFs"
                )
                _statusMessage.value = "PDF compressed successfully!"
                handlePostSaveAction(doc, activity)
            } catch (e: Exception) {
                _statusMessage.value = "Compression failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Rotate PDF
    fun rotatePdf(pdfFile: File, degrees: Float, title: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val rotated = PdfProcessor.rotatePdfPages(getApplication(), pdfFile, degrees, title)
                val doc = repository.savePdfDocument(
                    pdfFile = rotated,
                    title = title,
                    category = "Converted PDFs"
                )
                _statusMessage.value = "PDF pages rotated!"
            } catch (e: Exception) {
                _statusMessage.value = "Rotation failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(doc)
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            _statusMessage.value = "Document deleted."
        }
    }

    // Native Android ACTION_SEND Share Intent
    fun shareDocument(context: Context, doc: DocumentEntity) {
        try {
            val file = File(doc.filePath)
            if (!file.exists()) {
                _statusMessage.value = "File not found"
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = when {
                    doc.filePath.endsWith(".pdf") -> "application/pdf"
                    doc.filePath.endsWith(".docx") || doc.filePath.endsWith(".doc") -> "application/msword"
                    doc.filePath.endsWith(".pptx") || doc.filePath.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
                    doc.filePath.endsWith(".xlsx") || doc.filePath.endsWith(".xls") -> "application/vnd.ms-excel"
                    doc.filePath.endsWith(".txt") -> "text/plain"
                    else -> "image/jpeg"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Share Document via DocScan"))
        } catch (e: Exception) {
            e.printStackTrace()
            _statusMessage.value = "Error sharing file: ${e.message}"
        }
    }

    // Universal Document Importer (PDF, DOCX, PPTX, XLSX, TXT, Images)
    fun importDocumentFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val contentResolver = context.contentResolver
                var fileName = "Imported_Doc_${System.currentTimeMillis() % 10000}"

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }

                val lowerName = fileName.lowercase()
                val ext = when {
                    lowerName.endsWith(".pdf") -> "pdf"
                    lowerName.endsWith(".docx") || lowerName.endsWith(".doc") -> "doc"
                    lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") -> "ppt"
                    lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") -> "xls"
                    lowerName.endsWith(".txt") -> "txt"
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") -> "image"
                    else -> "other"
                }

                val category = when (ext) {
                    "pdf" -> "Converted PDFs"
                    "doc", "ppt", "xls" -> "Office Docs"
                    "txt" -> "Scanned Docs"
                    "image" -> "Scanned Docs"
                    else -> "Office Docs"
                }

                val targetFile = File(repository.getDocScanDir(), "${System.currentTimeMillis()}_$fileName")

                contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                var pageCount = 1
                var extractedText = ""
                var thumbnailBitmap: Bitmap? = null

                if (ext == "pdf") {
                    val images = PdfProcessor.pdfToImages(targetFile)
                    pageCount = images.size.coerceAtLeast(1)
                    thumbnailBitmap = images.firstOrNull()
                    if (thumbnailBitmap != null) {
                        extractedText = OcrEngine.recognizeText(thumbnailBitmap)
                    }
                } else if (ext == "txt") {
                    extractedText = targetFile.readText()
                    val lines = extractedText.lines().size
                    pageCount = (lines / 40).coerceAtLeast(1)
                } else if (ext == "image") {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(targetFile.absolutePath)
                    if (bitmap != null) {
                        thumbnailBitmap = bitmap
                        extractedText = OcrEngine.recognizeText(bitmap)
                    }
                } else {
                    extractedText = "Office Document ($fileName) copied to local library."
                }

                val doc = repository.savePdfDocument(
                    pdfFile = targetFile,
                    title = fileName.substringBeforeLast("."),
                    category = category,
                    pageCount = pageCount,
                    thumbnailBitmap = thumbnailBitmap,
                    extractedText = extractedText
                )

                _selectedDocument.value = doc
                _statusMessage.value = "Imported $fileName into DocScan!"
                _currentScreen.value = Screen.Viewer
            } catch (e: Exception) {
                _statusMessage.value = "Failed to import document: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Rewarded Ad unlock prompt helper
    fun showRewardedAdForFeature(activity: Activity, featureName: String, onUnlocked: () -> Unit) {
        AdManager.showRewardedAd(
            activity,
            onRewardEarned = {
                _statusMessage.value = "$featureName Unlocked!"
                onUnlocked()
            },
            onAdDismissed = {}
        )
    }
}
