package com.mdkdw1.mypdf.ui

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.pdf.PdfDocument
import androidx.pdf.view.PdfView
import com.mdkdw1.mypdf.ReadingPrefs
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EbookReaderScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ReadingPrefs(context) }
    val file = remember { File(path) }

    var darkMode by remember { mutableStateOf(prefs.darkMode) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // FileProvider URI
    val uri: Uri = remember(file.absolutePath) {
        try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    // ⭐ 문서 로드 (백그라운드에서 PdfDocument 생성)
    val pdfDocument by produceState<PdfDocument?>(initialValue = null, uri) {
        value = try {
            PdfDocument.load(context, uri)
        } catch (e: Exception) {
            loadError = e.message
            null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 에러 표시 ──
        if (loadError != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("PDF를 열 수 없습니다", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(loadError ?: "", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("뒤로") }
            }
            return@Box
        }

        // ── 로딩 중 ──
        if (pdfDocument == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        // ── PDF 뷰어 ──
        AndroidView(
            factory = { ctx ->
                PdfView(ctx).apply {
                    setBackgroundColor(
                        if (darkMode) AndroidColor.BLACK else AndroidColor.WHITE
                    )
                    setDocument(pdfDocument!!)
                }
            },
            update = { view ->
                view.setBackgroundColor(
                    if (darkMode) AndroidColor.BLACK else AndroidColor.WHITE
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── 상단 툴바 ──
        TopAppBar(
            title = { Text(file.name, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                }
            },
            actions = {
                IconButton(onClick = {
                    darkMode = !darkMode
                    prefs.darkMode = darkMode
                }) {
                    Icon(
                        if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        "다크 모드"
                    )
                }
                IconButton(onClick = {
                    prefs.toggleFavorite(file.name)
                }) {
                    Icon(
                        if (prefs.isFavorite(file.name)) Icons.Filled.Star else Icons.Filled.StarBorder,
                        "즐겨찾기"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (darkMode) Color(0xCC000000) else Color(0xCCFFFFFF),
                titleContentColor = if (darkMode) Color.White else Color.Black,
                actionIconContentColor = if (darkMode) Color.White else Color.Black,
                navigationIconContentColor = if (darkMode) Color.White else Color.Black,
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
