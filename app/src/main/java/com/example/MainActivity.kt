package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.AdManager
import com.example.ads.BannerAdView
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

data class NavItem(
    val title: String,
    val icon: ImageVector,
    val screen: Screen
)

class MainActivity : ComponentActivity() {

    private var viewModelRef: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Preload Interstitial and Rewarded Ads
        try {
            AdManager.loadInterstitialAd(this)
            AdManager.loadRewardedAd(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        setContent {
            val viewModel: MainViewModel = viewModel()
            viewModelRef = viewModel
            val context = LocalContext.current

            val isDarkThemeState by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = isDarkThemeState ?: systemDark

            MyApplicationTheme(darkTheme = useDarkTheme) {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val documents by viewModel.documents.collectAsStateWithLifecycle()
                val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val ocrText by viewModel.ocrText.collectAsStateWithLifecycle()
                val selectedDocument by viewModel.selectedDocument.collectAsStateWithLifecycle()
                val recentlySavedDocument by viewModel.recentlySavedDocument.collectAsStateWithLifecycle()
                val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

                // Check for incoming intent URI (e.g. opened file from WhatsApp, Chrome, or Files)
                LaunchedEffect(intent) {
                    intent?.data?.let { uri ->
                        viewModel.importDocumentFromUri(context, uri)
                    }
                }

                // Show status toasts
                LaunchedEffect(statusMessage) {
                    statusMessage?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        viewModel.clearStatusMessage()
                    }
                }

                // Dual-Option Save & Share Dialog UX Flow
                recentlySavedDocument?.let { savedDoc ->
                    AlertDialog(
                        onDismissRequest = { viewModel.clearRecentlySavedDocument() },
                        icon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Saved",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        title = {
                            Text(
                                text = "Saved to DocScan",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = "\"${savedDoc.title}\" has been saved into your DocScan folder.\n\nWould you like to share it now or return to your Dashboard?"
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.shareDocument(context, savedDoc)
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Now")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearRecentlySavedDocument()
                                    viewModel.navigateTo(Screen.Home)
                                }
                            ) {
                                Text("Done")
                            }
                        }
                    )
                }

                val navItems = listOf(
                    NavItem("Home", Icons.Default.Home, Screen.Home),
                    NavItem("Scan", Icons.Default.CameraAlt, Screen.CameraScan),
                    NavItem("Doc Tools", Icons.Default.PictureAsPdf, Screen.PdfTools),
                    NavItem("Files", Icons.Default.Folder, Screen.FileManager),
                    NavItem("OCR", Icons.Default.TextFields, Screen.Ocr)
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Column {
                            // Persistent Adaptive Banner Ad at bottom of Main Dashboard screens
                            if (currentScreen == Screen.Home || currentScreen == Screen.FileManager || currentScreen == Screen.PdfTools) {
                                BannerAdView()
                            }

                            // Bottom Navigation Bar
                            NavigationBar {
                                navItems.forEach { item ->
                                    NavigationBarItem(
                                        selected = currentScreen == item.screen,
                                        onClick = { viewModel.navigateTo(item.screen) },
                                        icon = { Icon(item.icon, contentDescription = item.title) },
                                        label = { Text(item.title) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            Screen.Home -> HomeScreen(
                                documents = documents,
                                selectedCategory = selectedCategory,
                                isDarkTheme = useDarkTheme,
                                onToggleDarkTheme = { isDark -> viewModel.toggleDarkTheme(isDark) },
                                onCategorySelected = { viewModel.selectCategory(it) },
                                onNavigate = { viewModel.navigateTo(it) },
                                onImportDocument = { uri -> viewModel.importDocumentFromUri(context, uri) },
                                onDocumentClick = { doc ->
                                    viewModel.selectDocument(doc)
                                    viewModel.navigateTo(Screen.Viewer)
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                onShare = { viewModel.shareDocument(context, it) },
                                onDelete = { viewModel.deleteDocument(it) }
                            )

                            Screen.CameraScan, Screen.IdCardScan -> CameraScanScreen(
                                onSaveSingleScan = { bitmap, filter, title, activity ->
                                    viewModel.saveScannedImage(bitmap, filter, title, "Scanned Docs", activity)
                                },
                                onSaveIdCardScan = { front, back, title, activity ->
                                    viewModel.saveIdCardScan(front, back, title, activity)
                                },
                                onSaveBatchPdf = { bitmaps, title, activity ->
                                    viewModel.convertImagesToPdf(bitmaps, title, null, null, activity)
                                },
                                onBack = { viewModel.navigateTo(Screen.Home) }
                            )

                            Screen.PdfTools -> PdfToolsScreen(
                                documents = documents,
                                onConvertImagesToPdf = { bitmaps, title, watermark, signature, activity ->
                                    viewModel.convertImagesToPdf(bitmaps, title, watermark, signature, activity)
                                },
                                onMergePdfs = { files, title, activity ->
                                    viewModel.mergePdfs(files, title, activity)
                                },
                                onCompressPdf = { file, level, title, activity ->
                                    viewModel.compressPdf(file, level, title, activity)
                                },
                                onRotatePdf = { file, degrees, title ->
                                    viewModel.rotatePdf(file, degrees, title)
                                },
                                onUnlockRewardedFeature = { activity, featureName, onUnlocked ->
                                    viewModel.showRewardedAdForFeature(activity, featureName, onUnlocked)
                                },
                                onBack = { viewModel.navigateTo(Screen.Home) }
                            )

                            Screen.FileManager -> FileManagerScreen(
                                documents = documents,
                                selectedCategory = selectedCategory,
                                searchQuery = searchQuery,
                                onCategorySelected = { viewModel.selectCategory(it) },
                                onSearchQueryChanged = { viewModel.updateSearchQuery(it) },
                                onImportDocument = { uri -> viewModel.importDocumentFromUri(context, uri) },
                                onDocumentClick = { doc ->
                                    viewModel.selectDocument(doc)
                                    viewModel.navigateTo(Screen.Viewer)
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                onShare = { viewModel.shareDocument(context, it) },
                                onDelete = { viewModel.deleteDocument(it) },
                                onBack = { viewModel.navigateTo(Screen.Home) }
                            )

                            Screen.Ocr -> OcrScreen(
                                ocrText = ocrText,
                                onProcessOcr = { viewModel.processOcr(it) },
                                onBack = { viewModel.navigateTo(Screen.Home) }
                            )

                            Screen.Viewer -> DocumentViewerScreen(
                                doc = selectedDocument,
                                onShare = { viewModel.shareDocument(context, it) },
                                onDelete = { viewModel.deleteDocument(it) },
                                onBack = { viewModel.navigateTo(Screen.Home) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            viewModelRef?.importDocumentFromUri(this, uri)
        }
    }
}
