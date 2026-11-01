package com.mdkdw1.mypdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HwpViewerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PATH = "hwp_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }

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
                HwpViewerScreen(path, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HwpViewerScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val file = remember { File(path) }
    var loading by remember { mutableStateOf(true) }
    var blocks by remember { mutableStateOf<List<HwpxParser.Block>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var plainText by remember { mutableStateOf("") }
    var rawMode by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            if (file.extension.equals("hwpx", ignoreCase = true)) {
                HwpxParser.parse(context, file).map { it.blocks to it.plainText }
            } else {
                Hwp5Parser.parseTextOnly(file).map { text ->
                    listOf(HwpxParser.Block(HwpxParser.Block.Type.PARAGRAPH, text)) to text
                }
            }
        }
        loading = false
        result.fold(
            onSuccess = { (b, t) -> blocks = b; plainText = t },
            onFailure = { error = it.message }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.nameWithoutExtension, fontSize = 15.sp,
                            fontWeight = FontWeight.Medium)
                        Text("${file.extension.uppercase()} \u00b7 ${blocks.size}\uac1c \ube14\ub85d",
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
                    TextButton(onClick = { rawMode = !rawMode }) {
                        Text(if (rawMode) "서식" else "원문", fontSize = 11.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                loading -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("문서 분석 중...", fontSize = 13.sp)
                }
                error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text("HWP 5.0 은 지원되지 않을 수 있습니다",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rawMode -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    item {
                        Text(plainText,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp, lineHeight = 22.sp)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(blocks) { block ->
                        when (block.type) {
                            HwpxParser.Block.Type.HEADING -> Text(
                                block.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            HwpxParser.Block.Type.PARAGRAPH -> Text(
                                block.text,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                            HwpxParser.Block.Type.TABLE -> TableView(block.rows)
                        }
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }
}

@Composable
fun TableView(rows: List<List<String>>) {
    if (rows.isEmpty()) return
    val colCount = rows.maxOf { it.size }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(4.dp))
    ) {
        rows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (rowIdx == 0) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface
                    )
            ) {
                for (colIdx in 0 until colCount) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(0.5.dp, MaterialTheme.colorScheme.surfaceVariant)
                            .padding(6.dp)
                    ) {
                        Text(
                            row.getOrElse(colIdx) { "" },
                            fontSize = 12.sp,
                            fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
