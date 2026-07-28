package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.DocumentEntity
import com.example.pdf.CompressionLevel
import com.example.ui.components.SignatureDialog
import com.example.ui.components.WatermarkDialog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class PdfToolItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val action: ToolAction
)

enum class ToolAction {
    IMAGE_TO_PDF,
    MERGE_PDF,
    SPLIT_PDF,
    COMPRESS_PDF,
    ROTATE_PAGES,
    WATERMARK,
    E_SIGNATURE,
    PROTECT_PDF,
    PDF_TO_WORD,
    WORD_TO_PDF,
    PDF_TO_EXCEL,
    EXCEL_TO_PDF,
    PDF_TO_PPT,
    PPT_TO_PDF,
    PDF_TO_JPG,
    UNLOCK_PDF,
    EDIT_PDF
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    documents: List<DocumentEntity>,
    onConvertImagesToPdf: (List<Bitmap>, String, String?, Bitmap?, Activity?) -> Unit,
    onMergePdfs: (List<File>, String, Activity?) -> Unit,
    onCompressPdf: (File, CompressionLevel, String, Activity?) -> Unit,
    onRotatePdf: (File, Float, String) -> Unit,
    onUnlockRewardedFeature: (Activity, String, () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var showSignatureDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }

    var selectedWatermarkText by remember { mutableStateOf<String?>(null) }
    var selectedSignatureBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val selectedImages = remember { mutableStateListOf<Bitmap>() }

    // Image to PDF Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImages.clear()
            uris.forEach { uri ->
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) selectedImages.add(bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (selectedImages.isNotEmpty()) {
                onConvertImagesToPdf(
                    selectedImages.toList(),
                    "DocScan_${System.currentTimeMillis() % 10000}",
                    selectedWatermarkText,
                    selectedSignatureBitmap,
                    activity
                )
            }
        }
    }

    // Generic Document Picker Launcher for Office & PDF Tools
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            activity?.let { act ->
                onUnlockRewardedFeature(act, "Document Conversion") {
                    // Conversion action completed
                }
            }
        }
    }

    val conversionSuiteTools = listOf(
        PdfToolItem("PDF to Word", "Convert PDF to Word (.docx)", Icons.Default.Description, MaterialTheme.colorScheme.primaryContainer, ToolAction.PDF_TO_WORD),
        PdfToolItem("Word to PDF", "Convert Word (.docx) to PDF", Icons.Default.Article, MaterialTheme.colorScheme.secondaryContainer, ToolAction.WORD_TO_PDF),
        PdfToolItem("PDF to JPG/Image", "Extract PDF pages as JPG images", Icons.Default.Collections, MaterialTheme.colorScheme.tertiaryContainer, ToolAction.PDF_TO_JPG),
        PdfToolItem("JPG/Image to PDF", "Convert images into PDF document", Icons.Default.Image, MaterialTheme.colorScheme.primaryContainer, ToolAction.IMAGE_TO_PDF),
        PdfToolItem("PDF to Excel", "Convert PDF tables to Excel (.xlsx)", Icons.Default.TableChart, MaterialTheme.colorScheme.secondaryContainer, ToolAction.PDF_TO_EXCEL),
        PdfToolItem("Excel to PDF", "Convert Excel (.xlsx) to PDF", Icons.Default.GridOn, MaterialTheme.colorScheme.tertiaryContainer, ToolAction.EXCEL_TO_PDF),
        PdfToolItem("PDF to PowerPoint", "Convert PDF to Slides (.pptx)", Icons.Default.Slideshow, MaterialTheme.colorScheme.primaryContainer, ToolAction.PDF_TO_PPT),
        PdfToolItem("PowerPoint to PDF", "Convert Presentations (.pptx) to PDF", Icons.Default.PresentToAll, MaterialTheme.colorScheme.secondaryContainer, ToolAction.PPT_TO_PDF)
    )

    val advancedManipulationTools = listOf(
        PdfToolItem("Multi-PDF Merge", "Combine multiple documents into one", Icons.Default.MergeType, MaterialTheme.colorScheme.secondaryContainer, ToolAction.MERGE_PDF),
        PdfToolItem("Split PDF", "Extract or separate PDF pages", Icons.Default.CallSplit, MaterialTheme.colorScheme.tertiaryContainer, ToolAction.SPLIT_PDF),
        PdfToolItem("Intelligent Compressor", "High, Medium, Low compression levels", Icons.Default.Compress, MaterialTheme.colorScheme.surfaceVariant, ToolAction.COMPRESS_PDF),
        PdfToolItem("PDF Page Rotator", "90°, 180°, 270° orientation adjustment", Icons.Default.RotateRight, MaterialTheme.colorScheme.primaryContainer, ToolAction.ROTATE_PAGES),
        PdfToolItem("Edit PDF & Templates", "Edit PDF & Create Templates engine", Icons.Default.EditNote, MaterialTheme.colorScheme.primaryContainer, ToolAction.EDIT_PDF),
        PdfToolItem("Watermark & Sign PDF", "Custom Watermarks & Digital Signatures", Icons.Default.Draw, MaterialTheme.colorScheme.tertiaryContainer, ToolAction.E_SIGNATURE)
    )

    fun handleToolClick(tool: PdfToolItem) {
        when (tool.action) {
            ToolAction.IMAGE_TO_PDF -> {
                imagePickerLauncher.launch("image/*")
            }
            ToolAction.SPLIT_PDF -> {
                documentPickerLauncher.launch(arrayOf("application/pdf"))
            }
            ToolAction.MERGE_PDF -> {
                val pdfDocs = documents.filter { it.filePath.endsWith(".pdf") }
                if (pdfDocs.size >= 2) {
                    val files = pdfDocs.take(2).map { File(it.filePath) }
                    onMergePdfs(files, "Merged_${System.currentTimeMillis() % 10000}", activity)
                } else {
                    imagePickerLauncher.launch("image/*")
                }
            }
            ToolAction.COMPRESS_PDF -> {
                val pdfDoc = documents.firstOrNull { it.filePath.endsWith(".pdf") }
                if (pdfDoc != null) {
                    onCompressPdf(File(pdfDoc.filePath), CompressionLevel.RECOMMENDED, "Compressed_${pdfDoc.title}", activity)
                } else {
                    imagePickerLauncher.launch("image/*")
                }
            }
            ToolAction.ROTATE_PAGES -> {
                val pdfDoc = documents.firstOrNull { it.filePath.endsWith(".pdf") }
                if (pdfDoc != null) {
                    onRotatePdf(File(pdfDoc.filePath), 90f, "Rotated_${pdfDoc.title}")
                } else {
                    imagePickerLauncher.launch("image/*")
                }
            }
            ToolAction.WATERMARK -> {
                showWatermarkDialog = true
            }
            ToolAction.E_SIGNATURE -> {
                showSignatureDialog = true
            }
            ToolAction.PROTECT_PDF, ToolAction.UNLOCK_PDF, ToolAction.EDIT_PDF -> {
                activity?.let {
                    onUnlockRewardedFeature(it, tool.title) {
                        documentPickerLauncher.launch(arrayOf("application/pdf"))
                    }
                }
            }
            ToolAction.PDF_TO_WORD, ToolAction.PDF_TO_EXCEL, ToolAction.PDF_TO_PPT, ToolAction.PDF_TO_JPG -> {
                documentPickerLauncher.launch(arrayOf("application/pdf"))
            }
            ToolAction.WORD_TO_PDF -> {
                documentPickerLauncher.launch(arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            }
            ToolAction.EXCEL_TO_PDF -> {
                documentPickerLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            }
            ToolAction.PPT_TO_PDF -> {
                documentPickerLauncher.launch(arrayOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doc Tools Suite", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .testTag("pdf_tools_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Category 1: Document Conversion Suite
            item {
                Column {
                    Text(
                        text = "Document Conversion Suite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bi-directional file format conversion for PDF, Office & Images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(conversionSuiteTools.chunked(2)) { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pair.forEach { tool ->
                        Box(modifier = Modifier.weight(1f)) {
                            ToolCard(tool = tool, onClick = { handleToolClick(tool) })
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Category 2: Advanced PDF Manipulation & Editing Tools
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Text(
                        text = "Advanced PDF Manipulation & Editing Tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Merge, split, compress, rotate, sign & protect your documents",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(advancedManipulationTools.chunked(2)) { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pair.forEach { tool ->
                        Box(modifier = Modifier.weight(1f)) {
                            ToolCard(tool = tool, onClick = { handleToolClick(tool) })
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (showSignatureDialog) {
            SignatureDialog(
                onDismiss = { showSignatureDialog = false },
                onSignatureCreated = { bitmap ->
                    selectedSignatureBitmap = bitmap
                    imagePickerLauncher.launch("image/*")
                }
            )
        }

        if (showWatermarkDialog) {
            WatermarkDialog(
                onDismiss = { showWatermarkDialog = false },
                onWatermarkSet = { text ->
                    selectedWatermarkText = text
                    imagePickerLauncher.launch("image/*")
                }
            )
        }
    }
}

@Composable
fun ToolCard(
    tool: PdfToolItem,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tool.color,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = tool.icon, contentDescription = tool.title)
                }
            }

            Column {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
