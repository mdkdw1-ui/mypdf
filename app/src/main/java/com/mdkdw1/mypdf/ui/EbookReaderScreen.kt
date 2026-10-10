package com.mdkdw1.mypdf.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    var showToolbar by remember { mutableStateOf(true) }

    val pdfView = remember {
        PdfView(context).apply {
            setBackgroundColor(if (darkMode) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }

    // 파일 로드
    LaunchedEffect(file.absolutePath) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            pdfView.setDocumentUri(uri)
            prefs.saveLastOpened(file.name)
        } catch (e: Exception) {
            // FileProvider 미설정 시 직접 시도
            pdfView.setDocumentUri(android.net.Uri.fromFile(file))
        }
    }

    // 다크 모드 반영
    LaunchedEffect(darkMode) {
        pdfView.setBackgroundColor(if (darkMode) AndroidColor.BLACK else AndroidColor.WHITE)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── PDF 뷰어 ──
        AndroidView(
            factory = { pdfView },
            modifier = Modifier.fillMaxSize()
        )

        // ── 상단 툴바 ──
        if (showToolbar) {
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
                        val ok = prefs.toggleFavorite(file.name)
                        // TODO: 스낵바
                    }) {
                        Icon(
                            if (prefs.isFavorite(file.name)) Icons.Filled.Star else Icons.Filled.StarBorder,
                            "즐겨찾기"
                        )
                    }
                    IconButton(onClick = {
                        // TODO: 목차
                    }) {
                        Icon(Icons.Filled.Menu, "목차")
                    }
                    IconButton(onClick = {
                        // TODO: 검색
                    }) {
                        Icon(Icons.Filled.Search, "검색")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (darkMode)
                        Color(0xCC000000) else Color(0xCCFFFFFF),
                    titleContentColor = if (darkMode) Color.White else Color.Black,
                    actionIconContentColor = if (darkMode) Color.White else Color.Black,
                    navigationIconContentColor = if (darkMode) Color.White else Color.Black,
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // ── 툴바 토글용 하단 탭 영역 (미니멀) ──
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
        ) {
            // 화면 탭하면 툴바 토글
            // AndroidView가 터치를 다 먹어서 Compose에서 직접 감지 어려움
            // 우회: 상단/하단 엣지 탭 영역
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Transparent)
            ) {}
        }
    }
}
