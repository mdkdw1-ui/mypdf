package com.mdkdw1.mypdf.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdkdw1.mypdf.CrashLogger
import com.mdkdw1.mypdf.FilePickerActivity
import com.mdkdw1.mypdf.LogActivity
import com.mdkdw1.mypdf.ReadingPrefs
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenReader: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ReadingPrefs(context) }
    var sortMode by remember { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    // fade-in 애니메이션
    LaunchedEffect(Unit) { visible = true }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val path = result.data?.getStringExtra("pdf_path") ?: return@rememberLauncherForActivityResult
            val src = File(path)
            if (src.exists()) {
                val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
                val dst = File(dir, src.name)
                try {
                    src.inputStream().use { input ->
                        FileOutputStream(dst).use { out -> input.copyTo(out) }
                    }
                    CrashLogger.log(context, "LIB", "PDF 추가: ${src.name}")
                    refresh++
                } catch (e: Exception) {
                    CrashLogger.e(context, "LIB", "저장 실패", e)
                }
            }
        }
    }

    val allFiles = remember(sortMode, refresh) {
        val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val list = dir.listFiles { f -> f.extension.lowercase() == "pdf" }?.toList() ?: emptyList()
        when (sortMode) {
            0 -> list.sortedByDescending { prefs.getLastOpened(it.name) }
            1 -> list.sortedBy { it.name.lowercase() }
            2 -> list.sortedByDescending {
                (if (prefs.isFavorite(it.name)) 1_000_000_000L else 0L) +
                        prefs.getLastOpened(it.name)
            }
            3 -> list.sortedByDescending { it.length() }
            else -> list
        }
    }

    val files = remember(allFiles, searchQuery) {
        if (searchQuery.isBlank()) allFiles
        else allFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "My PDF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Text(
                            "for HJ  ·  ${allFiles.size}개",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, "정렬")
                    }
                    IconButton(onClick = {
                        try {
                            launcher.launch(Intent(context, FilePickerActivity::class.java))
                        } catch (e: Exception) {
                            CrashLogger.e(context, "LIB", "탐색기 오류", e)
                        }
                    }) {
                        Icon(Icons.Filled.Add, "PDF 추가")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, LogActivity::class.java))
                    }) {
                        Icon(Icons.Filled.BugReport, "로그")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    launcher.launch(Intent(context, FilePickerActivity::class.java))
                },
                icon = { Icon(Icons.Filled.Add, "추가") },
                text = { Text("PDF 추가") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 검색 바
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("PDF 검색...") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, "지우기")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp)
            )

            // 정렬 상태 표시
            val sortLabel = when (sortMode) {
                0 -> "🕐 최근 열람순"
                1 -> "🔤 이름순"
                2 -> "⭐ 즐겨찾기순"
                3 -> "📦 용량순"
                else -> ""
            }
            Text(
                sortLabel,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            AnimatedVisibility(visible = visible, enter = fadeIn()) {
                if (files.isEmpty()) {
                    EmptyState(searchQuery.isNotEmpty())
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(files, key = { it.absolutePath }) { file ->
                            PdfCard(
                                file = file,
                                prefs = prefs,
                                onClick = { onOpenReader(file.absolutePath) },
                                onLongClick = { refresh++ }
                            )
                        }
                    }
                }
            }
        }
    }

    // 정렬 메뉴
    if (showSortMenu) {
        AlertDialog(
            onDismissRequest = { showSortMenu = false },
            title = { Text("정렬") },
            text = {
                Column {
                    listOf(
                        0 to "🕐 최근 열람순",
                        1 to "🔤 이름순",
                        2 to "⭐ 즐겨찾기순",
                        3 to "📦 용량순"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sortMode = mode
                                    showSortMenu = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sortMode == mode,
                                onClick = { sortMode = mode; showSortMenu = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortMenu = false }) { Text("닫기") }
            }
        )
    }
}

@Composable
fun PdfCard(
    file: File,
    prefs: ReadingPrefs,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val fav = prefs.isFavorite(file.name)
    val lastOpened = prefs.getLastOpened(file.name)
    val lastPage = prefs.getPage(file.name)
    val dateStr = if (lastOpened > 0) {
        val sdf = SimpleDateFormat("yy.MM.dd HH:mm", Locale.getDefault())
        sdf.format(Date(lastOpened))
    } else "열람 전"
    val sizeMb = "%.1f MB".format(file.length() / 1024.0 / 1024.0)

    // PDF별 고유 색상 (파일명 해시 기반)
    val colors = listOf(
        Color(0xFFFF6B9D), Color(0xFF9C6BFF), Color(0xFF6BB8FF),
        Color(0xFF6BFFB8), Color(0xFFFFB86B), Color(0xFFFF6B6B)
    )
    val colorIdx = (file.name.hashCode() and 0x7FFFFFFF) % colors.size
    val cardColor = colors[colorIdx]

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 상단 색상 배너 + 아이콘
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(cardColor, cardColor.copy(alpha = 0.6f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    if (fav) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "즐겨찾기",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                }

                // 파일 정보
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        file.nameWithoutExtension,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            sizeMb,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            dateStr,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (lastPage > 0) {
                        Text(
                            "📖 ${lastPage + 1}페이지",
                            fontSize = 10.sp,
                            color = cardColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 우상단 메뉴 버튼
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    "메뉴",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(file.name, fontSize = 14.sp) },
            text = {
                Column {
                    MenuItem("⭐ 즐겨찾기 ${if (fav) "해제" else "추가"}") {
                        prefs.toggleFavorite(file.name)
                        onLongClick()
                        showMenu = false
                    }
                    MenuItem("🗑 삭제") {
                        file.delete()
                        onLongClick()
                        showMenu = false
                    }
                    MenuItem("📤 공유") {
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "PDF 공유"))
                        } catch (e: Exception) {
                            CrashLogger.e(context, "LIB", "공유 실패", e)
                        }
                        showMenu = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) { Text("닫기") }
            }
        )
    }
}

@Composable
fun MenuItem(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
    }
}

@Composable
fun EmptyState(isSearch: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                if (isSearch) Icons.Filled.SearchOff else Icons.Filled.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isSearch) "검색 결과가 없습니다" else "저장된 PDF가 없습니다",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isSearch) "다른 검색어를 시도해보세요"
                else "[+ PDF 추가] 버튼을 눌러 시작하세요",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
