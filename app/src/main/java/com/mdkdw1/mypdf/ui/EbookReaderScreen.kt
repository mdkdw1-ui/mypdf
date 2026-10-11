package com.mdkdw1.mypdf.ui

import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.mdkdw1.mypdf.DictionaryHelper
import com.mdkdw1.mypdf.GroqTranslator
import com.mdkdw1.mypdf.PdfTextPicker
import com.mdkdw1.mypdf.ReadingPrefs
import com.mdkdw1.mypdf.TtsHelper
import com.mdkdw1.mypdf.VisionTranslateActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EbookReaderScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ReadingPrefs(context) }
    val scope = rememberCoroutineScope()
    val file = remember { File(path) }

    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var toolbarVisible by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(prefs.getPage(file.name)) }
    var pageCount by remember { mutableStateOf(0) }
    var isHorizontal by remember { mutableStateOf(false) }
    var showEyeCareDialog by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showDictDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var detectedText by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var lastTapX by remember { mutableStateOf(0f) }
    var lastTapY by remember { mutableStateOf(0f) }
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }

    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    LaunchedEffect(prefs.eyeCareEnabled) {
        if (prefs.eyeCareEnabled) {
            while (true) {
                delay(prefs.eyeCareIntervalMin * 60_000L)
                showEyeCareDialog = true
            }
        }
    }

    val (bgColor, textColor) = when (themeMode) {
        1 -> Color(0xFFF5E6C8) to Color(0xFF3E2A14)
        2 -> Color(0xFF121212) to Color(0xFFE0E0E0)
        3 -> Color(0xFF000000) to Color(0xFFCFA050)
        else -> Color(0xFFFAFAFA) to Color(0xFF000000)
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(com.mdkdw1.mypdf.R.layout.view_pdf, null, false) as PDFView
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
                pdfViewRef = view
                view
            },
            update = { view ->
                if (selectionMode) {
                    view.setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                            lastTapX = event.x
                            lastTapY = event.y
                        }
                        false
                    }
                    view.setOnLongClickListener {
                        scope.launch {
                            val words = PdfTextPicker.extractWords(
                                context, file, currentPage
                            ).getOrNull()
                            if (words.isNullOrEmpty()) {
                                android.widget.Toast.makeText(
                                    context, "텍스트 없음",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                            val found = PdfTextPicker.findWordAt(
                                words, lastTapX, lastTapY,
                                view.width, view.height, 595f, 842f
                            )
                            if (found != null) {
                                detectedText = found.text
                                showTranslateDialog = true
                            } else {
                                android.widget.Toast.makeText(
                                    context, "감지 안 됨",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        true
                    }
                } else {
                    view.setOnTouchListener(null)
                    view.setOnLongClickListener(null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (themeMode == 1) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x33FFCC66)))
        } else if (themeMode == 3) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x55FF8800)))
        }

        if (toolbarVisible) {
            TopAppBar(
                title = {
                    Text("${file.name}  ${currentPage + 1}/${pageCount}", maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
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
                    IconButton(onClick = {
                        selectionMode = !selectionMode
                        android.widget.Toast.makeText(
                            context,
                            if (selectionMode) "선택 모드 ON" else "OFF",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            if (selectionMode) Icons.Filled.TextFields
                            else Icons.Filled.TouchApp,
                            "선택"
                        )
                    }
                    IconButton(onClick = {
                        context.startActivity(
                            android.content.Intent(
                                context, VisionTranslateActivity::class.java
                            ).apply {
                                putExtra("pdf_path", file.absolutePath)
                                putExtra("page_index", currentPage)
                            }
                        )
                    }) {
                        Icon(Icons.Filled.DocumentScanner, "통번역")
                    }
                    IconButton(onClick = { showTranslateDialog = true }) {
                        Icon(Icons.Filled.Translate, "번역")
                    }
                    IconButton(onClick = { showDictDialog = true }) {
                        Icon(Icons.Filled.MenuBook, "사전")
                    }
                    IconButton(onClick = {
                        if (tts.isReady()) {
                            tts.speak("페이지 ${currentPage + 1}")
                        }
                    }) {
                        Icon(Icons.Filled.VolumeUp, "TTS")
                    }
                    IconButton(onClick = {
                        prefs.eyeCareEnabled = !prefs.eyeCareEnabled
                    }) {
                        Icon(
                            if (prefs.eyeCareEnabled) Icons.Filled.RemoveRedEye
                            else Icons.Filled.Visibility,
                            "눈건강"
                        )
                    }
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

        if (toolbarVisible && pageCount > 0) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = bgColor.copy(alpha = 0.92f)
                )
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
                                .enableDoubletap(true)
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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                FloatingActionButton(onClick = { toolbarVisible = true }) {
                    Icon(Icons.Filled.Menu, "메뉴")
                }
            }
        }
    }

    if (showEyeCareDialog) {
        AlertDialog(
            onDismissRequest = { showEyeCareDialog = false },
            icon = { Icon(Icons.Filled.RemoveRedEye, null) },
            title = { Text("눈 휴식 시간") },
            text = { Text("20초간 먼 곳을 바라보세요.\n(20-20-20 규칙)") },
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

    if (showDictDialog) {
        DictionaryDialog(onDismiss = { showDictDialog = false })
    }

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

@Composable
fun TranslateDialog(
    prefs: ReadingPrefs,
    initialText: String = "",
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialText) }
    var output by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var targetLang by remember { mutableStateOf(prefs.targetLang) }

    val wordCount = input.trim().split(" ").filter { it.isNotBlank() }.size
    val autoMode = when {
        input.isBlank() -> "auto"
        wordCount == 1 -> "word"
        wordCount <= 10 -> "sentence"
        else -> "doc"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Translate, null) },
        title = { Text("번역 (Groq)") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("텍스트") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6
                )
                val modeLabel = when (autoMode) {
                    "word" -> "📖 단어"
                    "sentence" -> "📝 문장"
                    "doc" -> "📄 문서"
                    else -> "자동"
                }
                Text("모드: $modeLabel", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (output.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )) {
                        SelectionContainer {
                            Text(output, modifier = Modifier.padding(8.dp),
                                fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("오류: $error",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = input.isNotBlank() && !loading,
                onClick = {
                    val apiKey = prefs.groqApiKey
                    if (apiKey.isNullOrBlank()) {
                        error = "설정에서 API 키를 입력하세요"
                        return@TextButton
                    }
                    loading = true
                    error = null
                    output = ""
                    scope.launch {
                        val r = GroqTranslator.translate(
                            apiKey, input, targetLang, autoMode
                        )
                        loading = false
                        r.onSuccess { output = it }
                            .onFailure { error = it.message }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp))
                else Text("번역")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

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
                Text("설치된 사전 앱에서 검색", fontSize = 12.sp)
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
                Text("console.groq.com/keys", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(12.dp))
                Text("눈 건강", fontWeight = FontWeight.Bold)
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
