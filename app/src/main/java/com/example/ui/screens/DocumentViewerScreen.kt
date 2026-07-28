package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.DocumentEntity
import java.io.File
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    doc: DocumentEntity?,
    onShare: (DocumentEntity) -> Unit,
    onDelete: (DocumentEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Offline Night Mode Reader Toggle
    var isNightMode by remember { mutableStateOf(false) }

    // Text Font Size Reader Control
    var fontSizeSp by remember { mutableStateOf(16) }

    // TTS Read Aloud State
    var isSpeaking by remember { mutableStateOf(false) }
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts?.language = Locale.US
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
            ttsState.value = tts
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        onDispose {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    // PDF Pages Bitmap Cache for Offline Native Rendering
    val pdfPages = remember(doc?.filePath) {
        mutableStateListOf<Bitmap>()
    }

    LaunchedEffect(doc?.filePath) {
        pdfPages.clear()
        if (doc != null && doc.filePath.lowercase().endsWith(".pdf")) {
            val file = File(doc.filePath)
            if (file.exists()) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pdfPages.add(bitmap)
                        page.close()
                    }
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val backgroundColor = if (isNightMode) Color(0xFF121212) else MaterialTheme.colorScheme.surface
    val textColor = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSurface

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = doc?.title ?: "Document Reader",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        doc?.let { d ->
                            Text(
                                text = "${d.category} • ${d.pageCount} ${if (d.pageCount == 1) "Page" else "Pages"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    doc?.let { d ->
                        // Night Mode Reader Toggle
                        IconButton(onClick = { isNightMode = !isNightMode }) {
                            Icon(
                                imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Night Mode Toggle"
                            )
                        }

                        // Text-To-Speech Read Aloud Button
                        if (d.extractedText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val tts = ttsState.value
                                    if (tts != null) {
                                        if (isSpeaking) {
                                            tts.stop()
                                            isSpeaking = false
                                        } else {
                                            tts.speak(d.extractedText, TextToSpeech.QUEUE_FLUSH, null, "DOC_READ_ALOUD")
                                            isSpeaking = true
                                            Toast.makeText(context, "Reading document aloud...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Read Aloud",
                                    tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        IconButton(onClick = { onShare(d) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }

                        IconButton(onClick = {
                            onDelete(d)
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        containerColor = backgroundColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("document_viewer_screen"),
            contentAlignment = Alignment.Center
        ) {
            if (doc == null) {
                Text("No document selected.", color = textColor)
            } else {
                val path = doc.filePath.lowercase()

                Column(modifier = Modifier.fillMaxSize()) {

                    // Document Controls Bar (Font Size Controls for Text View)
                    if (doc.extractedText.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Reader Size: ${fontSizeSp}sp",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Row {
                                    IconButton(
                                        onClick = { if (fontSizeSp > 12) fontSizeSp -= 2 },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { if (fontSizeSp < 32) fontSizeSp += 2 },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase Font Size")
                                    }
                                }
                            }
                        }
                    }

                    // Reader Content Area
                    when {
                        // PDF File Native Multi-Page Reader
                        path.endsWith(".pdf") -> {
                            if (pdfPages.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(pdfPages) { index, bitmap ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Page ${index + 1} of ${pdfPages.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textColor,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            ElevatedCard(
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = "PDF Page ${index + 1}",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentScale = ContentScale.FillWidth
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Rendering PDF Document...", color = textColor)
                                    }
                                }
                            }
                        }

                        // Images View
                        path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".jpeg") || path.endsWith(".webp") -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = doc.filePath,
                                    contentDescription = doc.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        // Word / Excel / PowerPoint / TXT / Office Docs Viewer
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Office Doc Banner
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                path.contains("doc") -> Icons.Default.Description
                                                path.contains("ppt") -> Icons.Default.Slideshow
                                                path.contains("xls") -> Icons.Default.TableChart
                                                else -> Icons.Default.Article
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = doc.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${doc.category} • ${doc.fileSizeBytes / 1024} KB",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Document Text Reader View
                                Text(
                                    text = "Document Reader Content",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isNightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (doc.extractedText.isNotBlank()) doc.extractedText else "No preview text layer available for this file format.",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSizeSp.sp),
                                        color = textColor,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
