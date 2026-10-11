package com.mdkdw1.mypdf.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdkdw1.mypdf.CrashLogger
import com.mdkdw1.mypdf.FilePickerActivity
import com.mdkdw1.mypdf.LogActivity
import com.mdkdw1.mypdf.PdfThumbnail
import com.mdkdw1.mypdf.ReadingPrefs
import kotlinx.coroutines.launch
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
    var viewMode by remember { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }

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
                (if (prefs.isFavorite(it.name)) 1_000_000_000L else 0L) + prefs.getLastOpened(it.name)
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
                        Text("My PDF", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("for HJ  ·  ${allFiles.size}개", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { showViewMenu = true }) {
                        Icon(
                            when (viewMode) {
                                1 -> Icons.Filled.GridView
                                2 -> Icons.Filled.Article
                                else -> Icons.Filled.ViewList
                            },
                            "보기"
                        )
                    }
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
                        Icon(Icons.Filled.Add, "추가")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, LogActivity::class.java))
                    }) {
                        Icon(Icons.Filled.BugReport, "로그")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("PDF 검색...") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(28.dp)
            )

            if (files.isEmpty()) {
                EmptyState(searchQuery.isNotEmpty())
            } else {
                when (viewMode) {
                    1 -> ThumbnailGrid(files, prefs, onOpenReader, { refresh++ })
                    2 -> CompactList(files, prefs, onOpenReader, { refresh++ })
                    else -> IconList(files, prefs, onOpenReader, { refresh++ })
                }
            }
        }
    }

    if (showSortMenu) {
        AlertDialog(
            onDismissRequest = { showSortMenu = false },
            title = { Text("정렬") },
            text = {
                Column {
                    listOf(
                        0 to "🕐 최근 열람순", 1 to "🔤 이름순",
                        2 to "⭐ 즐겨찾기순", 3 to "📦 용량순"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { sortMode = mode; showSortMenu = false }
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

    if (showViewMenu) {
        AlertDialog(
            onDismissRequest = { showViewMenu = false },
            title = { Text("보기 모드") },
            text = {
                Column {
                    listOf(
                        0 to "📋 아이콘 리스트",
                        1 to "🎨 썸네일 그리드",
                        2 to "📄 컴팩트 (글자만)"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { viewMode = mode; showViewMenu = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = viewMode == mode,
                                onClick = { viewMode = mode; showViewMenu = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewMenu = false }) { Text("닫기") }
            }
        )
    }
}

// ═══════════════ 아이콘 리스트 (썸네일) ═══════════════

@Composable
fun IconList(
    files: List<File>, prefs: ReadingPrefs,
    onOpenReader: (String) -> Unit, onRefresh: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.absolutePath }) { file ->
            PdfIconRow(file, prefs, { onOpenReader(file.absolutePath) }, onRefresh)
        }
    }
}

@Composable
fun PdfIconRow(
    file: File, prefs: ReadingPrefs,
    onClick: () -> Unit, onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    val fav = prefs.isFavorite(file.name)
    val lastOpened = prefs.getLastOpened(file.name)
    val lastPage = prefs.getPage(file.name)
    val dateStr = if (lastOpened > 0)
        SimpleDateFormat("yy.MM.dd", Locale.getDefault()).format(Date(lastOpened))
    else "-"
    val sizeMb = "%.1f MB".format(file.length() / 1024.0 / 1024.0)

    val colors = listOf(
        Color(0xFFFF6B9D), Color(0xFF9C6BFF), Color(0xFF6BB8FF),
        Color(0xFF6BFFB8), Color(0xFFFFB86B), Color(0xFFFF6B6B)
    )
    val cardColor = colors[(file.name.hashCode() and 0x7FFFFFFF) % colors.size]

    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath) {
        thumbnail = PdfThumbnail.get(context, file, 200)
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                .background(
                    if (thumbnail == null)
                        Brush.linearGradient(listOf(cardColor, cardColor.copy(alpha = 0.7f)))
                    else
                        Brush.linearGradient(listOf(Color(0xFFF5F5F5), Color(0xFFEEEEEE)))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White,
                    modifier = Modifier.size(28.dp))
            }
            if (fav) {
                Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700),
                    modifier = Modifier.align(Alignment.TopEnd).size(14.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.nameWithoutExtension, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row {
                Text("$sizeMb  ·  $dateStr", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lastPage > 0) {
                    Text("  ·  📖${lastPage + 1}p", fontSize = 11.sp, color = cardColor)
                }
            }
        }
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, "메뉴", modifier = Modifier.size(20.dp))
        }
    }

    HorizontalDivider(modifier = Modifier.padding(start = 76.dp),
        color = MaterialTheme.colorScheme.surfaceVariant)

    if (showMenu) {
        FileMenuDialog(file, prefs, { showMenu = false }, onRefresh)
    }
}

// ═══════════════ 썸네일 그리드 ═══════════════

@Composable
fun ThumbnailGrid(
    files: List<File>, prefs: ReadingPrefs,
    onOpenReader: (String) -> Unit, onRefresh: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.absolutePath }) { file ->
            PdfThumbCard(file, prefs, { onOpenReader(file.absolutePath) })
        }
    }
}

@Composable
fun PdfThumbCard(
    file: File, prefs: ReadingPrefs,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    val fav = prefs.isFavorite(file.name)
    val sizeMb = "%.1f MB".format(file.length() / 1024.0 / 1024.0)
    val lastPage = prefs.getPage(file.name)

    LaunchedEffect(file.absolutePath) {
        loading = true
        thumbnail = PdfThumbnail.get(context, file, 400)
        loading = false
    }

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    thumbnail != null -> Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                    else -> Icon(Icons.Filled.PictureAsPdf, null,
                        tint = Color.Gray, modifier = Modifier.size(48.dp))
                }
                if (fav) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(file.nameWithoutExtension, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(sizeMb, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lastPage > 0) {
                        Text("  ·  📖${lastPage + 1}p", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ═══════════════ 컴팩트 리스트 ═══════════════

@Composable
fun CompactList(
    files: List<File>, prefs: ReadingPrefs,
    onOpenReader: (String) -> Unit, onRefresh: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.absolutePath }) { file ->
            val fav = prefs.isFavorite(file.name)
            val sizeMb = "%.1f MB".format(file.length() / 1024.0 / 1024.0)
            val lastOpened = prefs.getLastOpened(file.name)
            val dateStr = if (lastOpened > 0)
                SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date(lastOpened))
            else "-"

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onOpenReader(file.absolutePath) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (if (fav) "⭐ " else "") + file.nameWithoutExtension,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(sizeMb, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(dateStr, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

// ═══════════════ 파일 메뉴 다이얼로그 ═══════════════

@Composable
fun FileMenuDialog(
    file: File, prefs: ReadingPrefs,
    onDismiss: () -> Unit, onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val fav = prefs.isFavorite(file.name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name, fontSize = 14.sp) },
        text = {
            Column {
                MenuRow("⭐ 즐겨찾기 ${if (fav) "해제" else "추가"}") {
                    prefs.toggleFavorite(file.name)
                    onRefresh()
                    onDismiss()
                }
                MenuRow("🗑 삭제") {
                    file.delete()
                    onRefresh()
                    onDismiss()
                }
                MenuRow("📤 공유") {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
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
                    onDismiss()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

@Composable
fun MenuRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Text(label)
    }
}

@Composable
fun EmptyState(isSearch: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isSearch) Icons.Filled.SearchOff else Icons.Filled.PictureAsPdf,
                null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isSearch) "검색 결과 없음" else "저장된 PDF 없음",
                fontSize = 16.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isSearch) "다른 검색어를 시도해보세요"
                else "[+ 버튼]을 눌러 추가하세요",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
