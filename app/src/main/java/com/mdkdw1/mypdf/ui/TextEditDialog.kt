package com.mdkdw1.mypdf.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdkdw1.mypdf.PdfFontExtractor
import com.mdkdw1.mypdf.PdfTextOverlay
import kotlinx.coroutines.launch
import java.io.File

/**
 * PDF의 텍스트 목록 → 선택 → 새 텍스트 입력 → 교체
 */
@Composable
fun TextEditDialog(
    pdfFile: File,
    pageIndex: Int,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<PdfFontExtractor.TextItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<PdfFontExtractor.TextItem?>(null) }
    var newText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    // 텍스트 로드
    LaunchedEffect(pageIndex) {
        loading = true
        val result = PdfFontExtractor.extractWords(pdfFile, pageIndex)
        loading = false
        result.onSuccess { items = it }
            .onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Edit, null) },
        title = { Text(if (selected == null) "편집할 텍스트 선택" else "새 텍스트 입력") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                if (loading) {
                    CircularProgressIndicator()
                } else if (error != null) {
                    Text("오류: $error", color = MaterialTheme.colorScheme.error)
                } else if (selected == null) {
                    Text("페이지 $pageIndex 의 텍스트 (${items.size}개)", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(items) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(2.dp),
                                onClick = {
                                    selected = item
                                    newText = item.text
                                }
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(item.text, fontWeight = FontWeight.Medium)
                                    Text(
                                        "폰트: ${item.fontName} / 매칭: ${item.matchedFont} / ${item.fontSize.toInt()}pt",
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val s = selected!!
                    Text("원본: \"${s.text}\"", fontWeight = FontWeight.Bold)
                    Text("위치: (${s.x.toInt()}, ${s.y.toInt()})", fontSize = 11.sp)
                    Text("폰트: ${s.matchedFont} ${s.fontSize.toInt()}pt", fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it },
                        label = { Text("새 텍스트") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "※ 원본 위치에 흰색으로 덮고 새 텍스트를 그립니다",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (selected == null) {
                TextButton(onClick = onDismiss) { Text("닫기") }
            } else {
                TextButton(
                    enabled = newText.isNotBlank() && !saving,
                    onClick = {
                        val s = selected ?: return@TextButton
                        saving = true
                        scope.launch {
                            val op = PdfTextOverlay.ReplaceOp(
                                pageIndex = pageIndex,
                                x = s.x,
                                y = s.y,
                                width = s.width,
                                height = s.height,
                                newText = newText,
                                fontSize = s.fontSize,
                                fontName = s.matchedFont,
                                color = s.color,
                                originalText = s.text,
                                isBold = (s.fontFlags and 16) != 0,
                                isItalic = (s.fontFlags and 64) != 0
                            )
                            val result = PdfTextOverlay.replaceTexts(pdfFile, listOf(op))
                            saving = false
                            result.onSuccess {
                                android.widget.Toast.makeText(
                                    context, "저장됨", android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onSaved()
                                onDismiss()
                            }.onFailure {
                                error = it.message
                            }
                        }
                    }
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(16.dp))
                    else Text("저장")
                }
            }
        },
        dismissButton = {
            if (selected != null) {
                TextButton(onClick = { selected = null }) { Text("← 목록") }
            }
        }
    )
}
