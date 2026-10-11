package com.mdkdw1.mypdf

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import com.mdkdw1.mypdf.PdfEditor

class VisionTranslateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra("pdf_path") ?: run { finish(); return }
        val pageIndex = intent.getIntExtra("page_index", 0)

        val composeView = ComposeView(this).apply {
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
                    VisionTranslateScreen(
                        pdfPath = path,
                        initialPage = pageIndex,
                        onBack = { finish() }
                    )
                }
            }
        }
        setContentView(composeView)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionTranslateScreen(
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
    var loadingPage by remember { mutableStateOf(true) }
    var translating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var targetLang by remember { mutableStateOf(prefs.targetLang) }
    var mode by remember { mutableStateOf("layout") }  // text_only, layout, summary
    var totalPages by remember { mutableStateOf(1) }

    // 페이지 이미지 로드
    LaunchedEffect(pageIndex) {
        loadingPage = true
        result = ""
        error = null
        pageBitmap = PdfPageCapture.renderPage(context, file, pageIndex, 1200)
        loadingPage = false
    }

    // 총 페이지 수
    LaunchedEffect(file.absolutePath) {
        PdfEditor.getPageCount(file).onSuccess { totalPages = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("통번역 (Vision)", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (pageBitmap == null || translating) return@FloatingActionButton
                    val apiKey = prefs.groqApiKey
                    if (apiKey.isNullOrBlank()) {
                        error = "설정에서 Groq API 키를 입력하세요"
                        return@FloatingActionButton
                    }
                    translating = true
                    error = null
                    result = ""
                    scope.launch {
                        val b64 = PdfPageCapture.toBase64Jpeg(pageBitmap!!)
                        val r = GroqVisionTranslator.translateImage(
                            apiKey, b64, targetLang, mode
                        )
                        translating = false
                        r.onSuccess { result = it }
                            .onFailure { error = it.message }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                if (translating) CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                else Icon(Icons.Filled.Translate, "번역")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── 페이지 선택 바 ──
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
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
            }

            // ── 언어 선택 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("→ ", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
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

            // ── 번역 모드 선택 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("모드: ", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp)
                listOf(
                    "text_only" to "📝 텍스트만",
                    "layout" to "📄 레이아웃 유지",
                    "summary" to "📋 요약"
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = mode == code,
                        onClick = { mode = code },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ── 컨텐츠 (페이지 이미지 + 번역 결과) ──
            Row(modifier = Modifier.fillMaxSize()) {
                // 좌: 페이지 이미지
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF21262D)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loadingPage -> CircularProgressIndicator()
                        pageBitmap != null -> Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "PDF 페이지",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                        else -> Text("페이지 로드 실패",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 우: 번역 결과
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp)
                ) {
                    when {
                        translating -> Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Vision 번역 중...",
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        error != null -> Column(
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Error, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("오류", color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(error ?: "", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        result.isNotBlank() -> SelectionContainer {
                            Text(
                                text = result,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                        else -> Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Translate, null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("🌐 버튼을 눌러 번역",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("(이미지·표·손글씨까지 번역)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
