package com.mdkdw1.mypdf

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

class OcrOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("pdf_path") ?: run { finish(); return }
        val pageIndex = intent.getIntExtra("page_index", 0)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF6B9D),
                    background = Color(0xFF0D1117),
                    surface = Color(0xFF161B22),
                    onSurface = Color(0xFFF0F6FC),
                    surfaceVariant = Color(0xFF21262D),
                    onSurfaceVariant = Color(0xFF8B949E),
                )
            ) {
                OcrOverlayScreen(
                    pdfPath = path,
                    initialPage = pageIndex,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrOverlayScreen(
    pdfPath: String,
    initialPage: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { ReadingPrefs(context) }
    val scope = rememberCoroutineScope()
    val file = remember { File(pdfPath) }

    var pageIndex by remember { mutableStateOf(initialPage) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var blocks by remember { mutableStateOf<List<PdfOcrOverlay.TextBlock>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var translatingAll by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var targetLang by remember { mutableStateOf(prefs.targetLang) }
    var totalPages by remember { mutableStateOf(1) }
    var showOriginalText by remember { mutableStateOf(true) }

    // 페이지 로드 + OCR
    LaunchedEffect(pageIndex) {
        loading = true
        error = null
        blocks = emptyList()
        val r = PdfOcrOverlay.renderAndExtract(context, file, pageIndex, 1600)
        loading = false
        r.onSuccess { (bmp, blks) ->
            pageBitmap = bmp
            blocks = blks
        }.onFailure {
            error = it.message
        }
    }

    LaunchedEffect(file.absolutePath) {
        PdfEditor.getPageCount(file).onSuccess { totalPages = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("오버레이 번역", fontSize = 16.sp)
                        Text("페이지 ${pageIndex + 1} / $totalPages · ${blocks.count { it.translated != null }}/${blocks.size}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    // 원본 텍스트 표시 토글
                    IconButton(onClick = { showOriginalText = !showOriginalText }) {
                        Icon(
                            if (showOriginalText) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            "원본"
                        )
                    }
                    // 전체 번역
                    IconButton(
                        enabled = blocks.isNotEmpty() && !translatingAll,
                        onClick = {
                            val apiKey = prefs.groqApiKey
                            if (apiKey.isNullOrBlank()) {
                                error = "설정에서 API 키를 입력하세요"
                                return@IconButton
                            }
                            translatingAll = true
                            progress = 0 to blocks.size
                            scope.launch {
                                val updated = PdfOcrOverlay.translateAll(
                                    apiKey, blocks, targetLang
                                ) { i, _ ->
                                    progress = (i + 1) to blocks.size
                                }
                                blocks = updated
                                translatingAll = false
                            }
                        }
                    ) {
                        if (translatingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Translate, "전체 번역")
                        }
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
            // 페이지 네비
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { if (pageIndex > 0) pageIndex-- },
                    enabled = pageIndex > 0
                ) { Icon(Icons.Filled.ChevronLeft, "이전") }

                Text("페이지 ${pageIndex + 1} / $totalPages",
                    fontWeight = FontWeight.Medium)

                IconButton(
                    onClick = { if (pageIndex < totalPages - 1) pageIndex++ },
                    enabled = pageIndex < totalPages - 1
                ) { Icon(Icons.Filled.ChevronRight, "다음") }
            }

            // 언어 선택
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("→ ", fontWeight = FontWeight.Bold)
                listOf(
                    "ko" to "한국어", "en" to "English", "ja" to "日本語",
                    "zh" to "中文", "es" to "Español", "fr" to "Français"
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = targetLang == code,
                        onClick = { targetLang = code; prefs.targetLang = code },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }

            // 진행률
            if (translatingAll) {
                LinearProgressIndicator(
                    progress = if (progress.second > 0)
                        progress.first.toFloat() / progress.second
                    else 0f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("번역 중... ${progress.first} / ${progress.second}",
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // 오버레이 캔버스
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF000000))
            ) {
                when {
                    loading -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("페이지 로드 + OCR 중...")
                    }
                    error != null -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Error, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("오류", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(4.dp))
                        Text(error ?: "", fontSize = 11.sp)
                    }
                    pageBitmap != null -> OverlayCanvas(
                        bitmap = pageBitmap!!,
                        blocks = blocks,
                        showOriginal = showOriginalText
                    )
                }
            }
        }
    }
}

@Composable
fun OverlayCanvas(
    bitmap: Bitmap,
    blocks: List<PdfOcrOverlay.TextBlock>,
    showOriginal: Boolean
) {
    // 이미지 크기 vs 캔버스 크기 비율
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // 핀치 줌
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset += pan
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            canvasSize = size

            val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val canvasAspect = size.width / size.height

            // 이미지 fit
            val fitScale = if (canvasAspect > bitmapAspect) {
                size.width / bitmap.width
            } else {
                size.height / bitmap.height
            }

            val drawW = bitmap.width * fitScale * scale
            val drawH = bitmap.height * fitScale * scale
            val drawX = (size.width - drawW) / 2 + offset.x
            val drawY = (size.height - drawH) / 2 + offset.y

            // 원본 이미지
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    drawX.toInt(), drawY.toInt()
                ),
                dstSize = androidx.compose.ui.unit.IntSize(
                    drawW.toInt(), drawH.toInt()
                )
            )

            // 각 블록 오버레이
            for (block in blocks) {
                val bx = block.boundingBox.left * fitScale * scale + drawX
                val by = block.boundingBox.top * fitScale * scale + drawY
                val bw = block.boundingBox.width() * fitScale * scale
                val bh = block.boundingBox.height() * fitScale * scale

                // 배경 (원본 텍스트 덮기)
                if (showOriginal && block.translated != null) {
                    // 흰색 반투명 배경
                    drawRect(
                        color = Color(0xEEFFFFFF),
                        topLeft = Offset(bx, by),
                        size = Size(bw, bh)
                    )
                }

                // 번역 텍스트
                val displayText = block.translated ?: block.text
                val textColor = if (block.translated != null) Color(0xFF000000)
                                else Color(0xFFFF6B9D)

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = textColor.toArgb()
                        textSize = (bh * 0.85f).coerceIn(8f, 40f)
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.LEFT
                    }
                    val lines = wrapText(displayText, paint, bw)
                    lines.forEachIndexed { idx, line ->
                        canvas.nativeCanvas.drawText(
                            line,
                            bx + 2f,
                            by + paint.textSize * (idx + 1),
                            paint
                        )
                    }
                }
            }
        }
    }
}

/** 텍스트를 폭에 맞게 줄바꿈 */
fun wrapText(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    val words = text.split(" ")
    var current = StringBuilder()

    for (word in words) {
        val test = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(test) <= maxWidth) {
            if (current.isNotEmpty()) current.append(" ")
            current.append(word)
        } else {
            if (current.isNotEmpty()) result.add(current.toString())
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) result.add(current.toString())
    return result.ifEmpty { listOf(text) }
}
