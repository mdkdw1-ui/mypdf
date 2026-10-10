package com.mdkdw1.mypdf.ui

import android.graphics.Color as AndroidColor
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
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.mdkdw1.mypdf.ReadingPrefs
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EbookReaderScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ReadingPrefs(context) }
    val file = remember { File(path) }

    var darkMode by remember { mutableStateOf(prefs.darkMode) }
    var toolbarVisible by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(prefs.getPage(file.name)) }
    var pageCount by remember { mutableStateOf(0) }
    var isHorizontal by remember { mutableStateOf(false) }

    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── PDF 뷰어 ──
        AndroidView(
            factory = { ctx ->
                PDFView(ctx).apply {
                    setBackgroundColor(
                        if (darkMode) AndroidColor.BLACK else AndroidColor.WHITE
                    )
                    fromFile(file)
                        .enableSwipe(true)
                        .swipeHorizontal(isHorizontal)
                        .enableDoubletap(true)
                        .enableAntialiasing(true)
                        .spacing(if (isHorizontal) 0 else 10)
                        .defaultPage(prefs.getPage(file.name))
                        .scrollHandle(DefaultScrollHandle(ctx))
                        .onLoad { pages ->
                            pageCount = pages
                        }
                        .onPageChange { page, _ ->
                            currentPage = page
                            prefs.savePage(file.name, page)
                        }
                        .load()
                    pdfViewRef = this
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
        if (toolbarVisible) {
            TopAppBar(
                title = {
                    Text(
                        "${file.name}  ${currentPage + 1}/${pageCount}",
                        maxLines = 1
                    )
                },
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
                        isHorizontal = !isHorizontal
                        pdfViewRef?.let { v ->
                            v.fromFile(file)
                                .enableSwipe(true)
                                .swipeHorizontal(isHorizontal)
                                .enableDoubletap(true)
                                .spacing(if (isHorizontal) 0 else 10)
                                .defaultPage(currentPage)
                                .scrollHandle(DefaultScrollHandle(context))
                                .onLoad { pageCount = it }
                                .onPageChange { page, _ ->
                                    currentPage = page
                                    prefs.savePage(file.name, page)
                                }
                                .load()
                        }
                    }) {
                        Icon(Icons.Filled.MenuBook, "이북 모드")
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

        // ── 하단 페이지 네비게이션 ──
        if (toolbarVisible && pageCount > 0) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (darkMode) Color(0xCC000000) else Color(0xCCFFFFFF)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = {
                        if (currentPage > 0) pdfViewRef?.jumpTo(currentPage - 1)
                    }) {
                        Icon(Icons.Filled.ChevronLeft, "이전")
                    }
                    Text(
                        "${currentPage + 1} / $pageCount",
                        color = if (darkMode) Color.White else Color.Black
                    )
                    IconButton(onClick = {
                        if (currentPage < pageCount - 1) pdfViewRef?.jumpTo(currentPage + 1)
                    }) {
                        Icon(Icons.Filled.ChevronRight, "다음")
                    }
                    IconButton(onClick = { toolbarVisible = false }) {
                        Icon(Icons.Filled.VisibilityOff, "숨김")
                    }
                }
            }
        }

        // ── 툴바 복귀용 (숨김 상태일 때) ──
        if (!toolbarVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { toolbarVisible = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (darkMode)
                            Color(0x88FFFFFF) else Color(0x88000000)
                    )
                ) {
                    Icon(Icons.Filled.Menu, "메뉴")
                }
            }
        }
    }
}
