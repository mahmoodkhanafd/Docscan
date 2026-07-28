package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.scanner.ScanFilter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.InputStream
import java.util.concurrent.Executors

enum class ScanMode {
    SINGLE,
    BATCH,
    ID_CARD
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    onSaveSingleScan: (Bitmap, ScanFilter, String, Activity?) -> Unit,
    onSaveIdCardScan: (Bitmap, Bitmap, String, Activity?) -> Unit,
    onSaveBatchPdf: (List<Bitmap>, String, Activity?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraPermissionState = rememberPermissionState(permission = android.Manifest.permission.CAMERA)

    var scanMode by remember { mutableStateOf(ScanMode.SINGLE) }
    var selectedFilter by remember { mutableStateOf(ScanFilter.MAGIC_COLOR) }
    var docTitle by remember { mutableStateOf("Doc_${System.currentTimeMillis() % 10000}") }

    // Batch images storage
    val batchBitmaps = remember { mutableStateListOf<Bitmap>() }

    // ID Card dual side storage
    var idCardFront by remember { mutableStateOf<Bitmap?>(null) }
    var idCardBack by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturingBack by remember { mutableStateOf(false) }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // Gallery Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    when (scanMode) {
                        ScanMode.SINGLE -> onSaveSingleScan(bitmap, selectedFilter, docTitle, activity)
                        ScanMode.BATCH -> batchBitmaps.add(bitmap)
                        ScanMode.ID_CARD -> {
                            if (idCardFront == null) {
                                idCardFront = bitmap
                                isCapturingBack = true
                            } else {
                                idCardBack = bitmap
                                idCardFront?.let { front ->
                                    onSaveIdCardScan(front, bitmap, docTitle, activity)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Smart Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Import Gallery")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (!cameraPermissionState.status.isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission is required to scan documents.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Black)
            ) {
                // CameraX Preview
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                imageCapture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Edge Detection Overlay Box
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    val rectW = width * 0.85f
                    val rectH = if (scanMode == ScanMode.ID_CARD) rectW * 0.63f else height * 0.65f
                    val left = (width - rectW) / 2f
                    val top = (height - rectH) / 3f

                    drawRoundRect(
                        color = Color.Cyan.copy(alpha = 0.8f),
                        topLeft = Offset(left, top),
                        size = Size(rectW, rectH),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }

                // Top Mode Selection Header & ID Card Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mode Selector
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            FilterChip(
                                selected = scanMode == ScanMode.SINGLE,
                                onClick = { scanMode = ScanMode.SINGLE },
                                label = { Text("Single", color = Color.White) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = scanMode == ScanMode.BATCH,
                                onClick = { scanMode = ScanMode.BATCH },
                                label = { Text("Batch (${batchBitmaps.size})", color = Color.White) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = scanMode == ScanMode.ID_CARD,
                                onClick = { scanMode = ScanMode.ID_CARD },
                                label = { Text("ID Card", color = Color.White) }
                            )
                        }
                    }

                    if (scanMode == ScanMode.ID_CARD) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (!isCapturingBack) "Capture FRONT side of ID" else "Capture BACK side of ID",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Bottom Controls Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Filter Selection Row
                    Text(
                        text = "Filter: ${selectedFilter.name.replace("_", " ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ScanFilter.values()) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.name.lowercase().replace("_", " ")) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Shutter Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (scanMode == ScanMode.BATCH && batchBitmaps.isNotEmpty()) {
                            Button(
                                onClick = {
                                    onSaveBatchPdf(batchBitmaps.toList(), docTitle, activity)
                                }
                            ) {
                                Text("Done (${batchBitmaps.size})")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(60.dp))
                        }

                        // Shutter Button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable {
                                    imageCapture?.takePicture(
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                val bitmap = imageProxyToBitmap(image)
                                                image.close()

                                                when (scanMode) {
                                                    ScanMode.SINGLE -> {
                                                        onSaveSingleScan(bitmap, selectedFilter, docTitle, activity)
                                                    }
                                                    ScanMode.BATCH -> {
                                                        batchBitmaps.add(bitmap)
                                                    }
                                                    ScanMode.ID_CARD -> {
                                                        if (idCardFront == null) {
                                                            idCardFront = bitmap
                                                            isCapturingBack = true
                                                        } else {
                                                            idCardBack = bitmap
                                                            idCardFront?.let { front ->
                                                                onSaveIdCardScan(
                                                                    front,
                                                                    bitmap,
                                                                    docTitle,
                                                                    activity
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                exception.printStackTrace()
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }

                        IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
