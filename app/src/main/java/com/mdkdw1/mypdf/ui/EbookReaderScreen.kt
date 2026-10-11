package com.mdkdw1.mypdf.ui

import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.mdkdw1.mypdf.*
import com.mdkdw1.mypdf.PdfTextPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import com.mdkdw1.mypdf.GroqTranslator
import com.mdkdw1.mypdf.DictionaryHelper
import com.mdkdw1.mypdf.TtsHelper
import com.mdkdw1.mypdf.ReadingPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EbookReaderScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ReadingPrefs(context) }
    val scope = rememberCoroutineScope()
    val file = remember { File(path) }

    var themeMode by remember { mutableStateOf(prefs.themeMode) } // 0=일반 1=세피아 2=다크 3=야간
    var toolbarVisible by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(prefs.getPage(file.name)) }
    var pageCount by remember { mutableStateOf(0) }
    var isHorizontal by remember { mutableStateOf(false) }
    var showEyeCareDialog by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var detectedText by remember { mutableStateOf("") }
    var showDictDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }

    // TTS
    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    // 눈 건강 알림 타이머
    LaunchedEffect(prefs.eyeCareEnabled) {
        if (prefs.eyeCareEnabled) {
            while (true) {
                delay(prefs.eyeCareIntervalMin * 60_000L)
                showEyeCareDialog = true
            }
        }
    }

    // 테마 색상
    val (bgColor, textColor) = when (themeMode) {
        1 -> Color(0xFFF5E6C8) to Color(0xFF3E2A14)   // 세피아
        2 -> Color(0xFF121212) to Color(0xFFE0E0E0)   // 다크
        3 -> Color(0xFF000000) to Color(0xFFCFA050)   // 야간 (앰버)
        else -> Color(0xFFFAFAFA) to Color(0xFF000000) // 일반
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // ── PDF 뷰어 ──
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(com.mdkdw1.mypdf.R.layout.view_pdf, null, false) as PDFView
                view.setBackgroundColor(bgColor.value.toLong().let { AndroidColor.rgb(
                    ((it shr 16) and 0xFF).toInt(),
                    ((it shr 8) and 0xFF).toInt(),
                    (it and 0xFF).toInt()
                )})
                view.fromFile(file)
                    .enableSwipe(true)
                    .swipeHorizontal(isHorizontal)
                    .enableDoubletap(true)
                    .enableAntialiasing(true)
                    .spacing(if (isHorizontal) 0 else 10)
                    .defaultPage(prefs.getPage(file.name))
                    .scrollHandle(DefaultScrollHandle(ctx))
                    .onLoad { pages -> pageCount = pages }
                    .onPageChange { page, _ ->
                        currentPage = page
                        prefs.savePage(file.name, page)
                    }
                    .load()
                // PDF 롱프레스 → 좌표의 텍스트 자동 감지 → 번역 다이얼로그
                var lastTapX = 0f
                var lastTapY = 0f
                view.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                        lastTapX = event.x
                        lastTapY = event.y
                    }
                    false
                }
                view.setOnLongClickListener {
                    scope.launch {
                        val words = PdfTextPicker.extractWords(file, currentPage).getOrNull()
                        if (words.isNullOrEmpty()) {
                            android.widget.Toast.makeText(
                                context, "텍스트를 찾을 수 없습니다",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }
                        val pageW = 595f
                        val pageH = 842f
                        val found = PdfTextPicker.findWordAt(
                            words, lastTapX, lastTapY,
                            view.width, view.height, pageW, pageH
                        )
                        if (found != null) {
                            detectedText = found.text
                            showTranslateDialog = true
                            android.widget.Toast.makeText(
                                context, "감지: ${found.text}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            android.widget.Toast.makeText(
                                context, "텍스트가 감지되지 않았습니다",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    true
                }
                pdfViewRef = view
                view
            },
            modifier = Modifier.fillMaxSize()
        )

        // 세피아/야간 오버레이 (색조 필터)
        if (themeMode == 1) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x33FFCC66)))
        } else if (themeMode == 3) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x55FF8800)))
        }

        // ── 상단 툴바 ──
        if (toolbarVisible) {
            TopAppBar(
                title = { Text("${file.name}  ${currentPage + 1}/${pageCount}", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    // 테마 순환
                    IconButton(onClick = {
                        themeMode = (themeMode + 1) % 4
                        prefs.themeMode = themeMode
                    }) {
                        val icon = when (themeMode) {
                            0 -> Icons.Filled.LightMode
                            1 -> Icons.Filled.WbSunny
                            2 -> Icons.Filled.DarkMode
                            else -> Icons.Filled.NightsStay
                        }
                        Icon(icon, "테마")
                    }
                    // 번역
                    IconButton(onClick = { showTranslateDialog = true }) {
                        Icon(Icons.Filled.Translate, "번역")
                    }
                    // 사전
                    IconButton(onClick = { showDictDialog = true }) {
                        Icon(Icons.Filled.MenuBook, "사전")
                    }
                    // TTS 읽기
                    IconButton(onClick = {
                        if (tts.isReady()) {
                            tts.speak("페이지 ${currentPage + 1}. ${file.nameWithoutExtension}")
                        }
                    }) {
                        Icon(Icons.Filled.VolumeUp, "읽기")
                    }
                    // 눈 건강
                    IconButton(onClick = {
                        prefs.eyeCareEnabled = !prefs.eyeCareEnabled
                        android.widget.Toast.makeText(
                            context,
                            if (prefs.eyeCareEnabled) "눈 건강 알림 ON" else "OFF",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            if (prefs.eyeCareEnabled) Icons.Filled.RemoveRedEye
                            else Icons.Filled.Visibility,
                            "눈 건강"
                        )
                    }
                    // 공유
                    IconButton(onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_SEND
                        ).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM,
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                            )
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "PDF 공유")
                        )
                    }) {
                        Icon(Icons.Filled.Share, "공유")
                    }
                    // 설정
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, "설정")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor.copy(alpha = 0.92f),
                    titleContentColor = textColor,
                    actionIconContentColor = textColor,
                    navigationIconContentColor = textColor,
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // ── 하단 네비게이션 ──
        if (toolbarVisible && pageCount > 0) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.92f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = {
                        if (currentPage > 0) pdfViewRef?.jumpTo(currentPage - 1)
                    }) { Icon(Icons.Filled.ChevronLeft, "이전", tint = textColor) }

                    Text("${currentPage + 1} / $pageCount", color = textColor)

                    IconButton(onClick = {
                        if (currentPage < pageCount - 1) pdfViewRef?.jumpTo(currentPage + 1)
                    }) { Icon(Icons.Filled.ChevronRight, "다음", tint = textColor) }

                    IconButton(onClick = {
                        isHorizontal = !isHorizontal
                        pdfViewRef?.let { v ->
                            v.fromFile(file)
                                .enableSwipe(true)
                                .swipeHorizontal(isHorizontal)
                                .spacing(if (isHorizontal) 0 else 10)
                                .defaultPage(currentPage)
                                .scrollHandle(DefaultScrollHandle(context))
                                .onLoad { pageCount = it }
                                .onPageChange { page, _ -> currentPage = page }
                                .load()
                        }
                    }) { Icon(Icons.Filled.SwapHoriz, "가로/세로", tint = textColor) }

                    IconButton(onClick = { toolbarVisible = false }) {
                        Icon(Icons.Filled.VisibilityOff, "숨김", tint = textColor)
                    }
                }
            }
        }

        if (!toolbarVisible) {
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                FloatingActionButton(onClick = { toolbarVisible = true }) {
                    Icon(Icons.Filled.Menu, "메뉴")
                }
            }
        }
    }

    // ── 눈 건강 다이얼로그 ──
    if (showEyeCareDialog) {
        AlertDialog(
            onDismissRequest = { showEyeCareDialog = false },
            icon = { Icon(Icons.Filled.RemoveRedEye, null) },
            title = { Text("눈 휴식 시간") },
            text = { Text("20초간 먼 곳을 바라보며 눈을 쉬게 해주세요.\n\n20-20-20 규칙: 20분마다, 20피트(6m) 먼 곳을, 20초간") },
            confirmButton = {
                TextButton(onClick = { showEyeCareDialog = false }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.eyeCareEnabled = false
                    showEyeCareDialog = false
                }) { Text("알림 끄기") }
            }
        )
    }

    // ── 번역 다이얼로그 ──
    if (showTranslateDialog) {
        TranslateDialog(
            prefs = prefs,
            initialText = detectedText,
            onDismiss = {
                showTranslateDialog = false
                detectedText = ""
            }
        )
    }

    // ── 사전 다이얼로그 ──
    if (showDictDialog) {
        DictionaryDialog(
            onDismiss = { showDictDialog = false }
        )
    }

    // ── 설정 다이얼로그 ──
    if (showSettingsDialog) {
        SettingsDialog(
            prefs = prefs,
            onDismiss = {
                showSettingsDialog = false
                themeMode = prefs.themeMode
            }
        )
    }
}

// ─────────── 번역 다이얼로그 ───────────
@Composable
fun TranslateDialog(prefs: ReadingPrefs, initialText: String = "", onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialText) }
    var output by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var targetLang by remember { mutableStateOf(prefs.targetLang) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Translate, null) },
        title = { Text("번역 (Groq)") },
        text = {
            Column {
                // 언어 선택
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("→ ", fontWeight = FontWeight.Bold)
                    listOf("ko" to "한국어", "en" to "English", "ja" to "日本語", "zh" to "中文")
                        .forEach { (code, label) ->
                            FilterChip(
                                selected = targetLang == code,
                                onClick = { targetLang = code; prefs.targetLang = code },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("번역할 텍스트") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
                if (output.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(output, fontWeight = FontWeight.Medium)
                    }
                }
                if (error != null) {
                    Text("오류: $error", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = input.isNotBlank() && !loading,
                onClick = {
                    val apiKey = prefs.groqApiKey
                    if (apiKey.isNullOrBlank()) {
                        error = "설정에서 Groq API 키를 입력하세요"
                        return@TextButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        val result = GroqTranslator.translate(apiKey, input, targetLang)
                        loading = false
                        result.onSuccess { output = it }
                            .onFailure { error = it.message }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text("번역")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

// ─────────── 사전 다이얼로그 ───────────
@Composable
fun DictionaryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var word by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.MenuBook, null) },
        title = { Text("사전 검색") },
        text = {
            Column {
                Text("설치된 사전 앱에서 검색합니다.\n(ColorDict, GoldenDict, 네이버 사전 등)", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text("단어/문장") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = word.isNotBlank(),
                onClick = {
                    DictionaryHelper.lookup(context, word)
                    onDismiss()
                }
            ) { Text("검색") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

// ─────────── 설정 다이얼로그 ───────────
@Composable
fun SettingsDialog(prefs: ReadingPrefs, onDismiss: () -> Unit) {
    var apiKey by remember { mutableStateOf(prefs.groqApiKey ?: "") }
    var interval by remember { mutableStateOf(prefs.eyeCareIntervalMin.toString()) }
    var eyeCare by remember { mutableStateOf(prefs.eyeCareEnabled) }
    var theme by remember { mutableStateOf(prefs.themeMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Settings, null) },
        title = { Text("설정") },
        text = {
            Column {
                Text("Groq API 키", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = { Text("gsk_...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("https://console.groq.com/keys 에서 발급", fontSize = 11.sp)

                Spacer(Modifier.height(12.dp))
                Text("눈 건강 알림", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = eyeCare, onCheckedChange = { eyeCare = it })
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { interval = it },
                        label = { Text("간격(분)") },
                        modifier = Modifier.width(120.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("테마", fontWeight = FontWeight.Bold)
                Row {
                    listOf(
                        0 to "일반", 1 to "세피아", 2 to "다크", 3 to "야간"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = theme == mode,
                            onClick = { theme = mode },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.groqApiKey = apiKey.trim()
                prefs.eyeCareEnabled = eyeCare
                prefs.eyeCareIntervalMin = interval.toIntOrNull() ?: 20
                prefs.themeMode = theme
                onDismiss()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
