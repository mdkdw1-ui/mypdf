package com.mdkdw1.mypdf

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "pdf_path"
    }

    private lateinit var pdfFile: File
    private lateinit var tvInfo: TextView
    private lateinit var etPageIndex: EditText
    private lateinit var etText: EditText

    private val scope = CoroutineScope(Dispatchers.Main)
    private var pageCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        pdfFile = File(path)
        if (!pdfFile.exists()) { finish(); return }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        root.addView(TextView(this).apply {
            text = "📝 PDF 편집기"
            textSize = 22f
            setPadding(0, 0, 0, 16)
        })

        tvInfo = TextView(this).apply {
            text = "파일: ${pdfFile.name}"
            textSize = 12f
            setPadding(0, 0, 0, 16)
        }
        root.addView(tvInfo)

        root.addView(TextView(this).apply { text = "페이지 번호 (0부터):" })
        etPageIndex = EditText(this).apply {
            hint = "0"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(etPageIndex)

        root.addView(TextView(this).apply { text = "텍스트 (워터마크/메모/스탬프):" })
        etText = EditText(this).apply { hint = "텍스트 입력" }
        root.addView(etText)

        // ⭐ 텍스트 편집 (핵심!)
        val btnEditText = Button(this).apply {
            text = "✏ 텍스트 편집 (원본 위치에 덮어쓰기)"
            setBackgroundColor(android.graphics.Color.parseColor("#FF69B4"))
        }
        root.addView(btnEditText)

        // 기존 기능들
        val btnRotate = Button(this).apply { text = "🔄 페이지 회전 (90°)" }
        val btnDeletePage = Button(this).apply { text = "🗑 페이지 삭제" }
        val btnWatermark = Button(this).apply { text = "💧 워터마크 추가" }
        val btnAddBlank = Button(this).apply { text = "➕ 빈 페이지 추가" }
        val btnHighlight = Button(this).apply { text = "🖍 하이라이트 추가 (좌상단)" }
        val btnSticky = Button(this).apply { text = "📌 스티키 노트 (좌상단)" }
        val btnExtractText = Button(this).apply { text = "📄 텍스트 추출 (미리보기)" }
        val btnClose = Button(this).apply { text = "✕ 닫기" }

        listOf(
            btnRotate, btnDeletePage, btnWatermark, btnAddBlank,
            btnHighlight, btnSticky, btnExtractText, btnClose
        ).forEach { root.addView(it) }

        scroll.addView(root)
        setContentView(scroll)

        scope.launch {
            PdfEditor.getPageCount(pdfFile).onSuccess {
                pageCount = it
                tvInfo.text = "파일: ${pdfFile.name}\n페이지 수: $pageCount"
            }.onFailure {
                tvInfo.text = "파일: ${pdfFile.name}\n로드 실패: ${it.message}"
            }
        }

        // ⭐ 텍스트 편집 다이얼로그 (Compose 기반이지만 Activity에서 DialogFragment로 띄움)
        btnEditText.setOnClickListener {
            val pageIdx = etPageIndex.text.toString().toIntOrNull() ?: 0
            showTextEditDialog(pageIdx)
        }

        btnRotate.setOnClickListener {
            val idx = etPageIndex.text.toString().toIntOrNull() ?: 0
            runEdit("페이지 $idx 회전") { PdfEditor.rotatePage(pdfFile, idx, 90) }
        }
        btnDeletePage.setOnClickListener {
            val idx = etPageIndex.text.toString().toIntOrNull() ?: 0
            AlertDialog.Builder(this).setMessage("페이지 $idx 삭제?")
                .setPositiveButton("실행") { _, _ ->
                    runEdit("페이지 $idx 삭제") { PdfEditor.deletePage(pdfFile, idx) }
                }.setNegativeButton("취소", null).show()
        }
        btnWatermark.setOnClickListener {
            val text = etText.text.toString().ifBlank { "MyPDF" }
            runEdit("워터마크 추가") { PdfEditor.addWatermark(pdfFile, text) }
        }
        btnAddBlank.setOnClickListener {
            runEdit("빈 페이지 추가") { PdfEditor.addBlankPage(pdfFile) }
        }
        btnHighlight.setOnClickListener {
            val idx = etPageIndex.text.toString().toIntOrNull() ?: 0
            runEdit("하이라이트") { PdfEditor.addHighlight(pdfFile, idx, 100f, 500f, 200f, 20f) }
        }
        btnSticky.setOnClickListener {
            val idx = etPageIndex.text.toString().toIntOrNull() ?: 0
            val text = etText.text.toString().ifBlank { "메모" }
            runEdit("스티키 노트") { PdfEditor.addStickyNote(pdfFile, idx, 100f, 500f, text) }
        }
        btnExtractText.setOnClickListener {
            scope.launch {
                tvInfo.text = "텍스트 추출 중..."
                val result = withContext(Dispatchers.IO) { PdfEditor.extractText(pdfFile) }
                result.onSuccess { text ->
                    AlertDialog.Builder(this@EditorActivity)
                        .setTitle("텍스트 (${text.length}자)")
                        .setMessage(text.take(2000))
                        .setPositiveButton("확인", null)
                        .show()
                    tvInfo.text = "추출: ${text.length}자"
                }.onFailure {
                    Toast.makeText(this@EditorActivity, "실패: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        btnClose.setOnClickListener { finish() }
    }

    private fun showTextEditDialog(pageIdx: Int) {
        // Compose 다이얼로그를 위해 ComposeView 사용
        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                androidx.compose.material3.MaterialTheme {
                    com.mdkdw1.mypdf.ui.TextEditDialog(
                        pdfFile = pdfFile,
                        pageIndex = pageIdx,
                        onDismiss = { /* ComposeView가 Dialog로 처리 */ },
                        onSaved = { tvInfo.text = "✅ 텍스트 편집 저장됨" }
                    )
                }
            }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(composeView)
            .create()
        dialog.show()
    }

    private fun runEdit(label: String, block: suspend () -> Result<File>) {
        scope.launch {
            tvInfo.text = "$label 처리 중..."
            block().onSuccess {
                tvInfo.text = "✅ $label 완료"
                Toast.makeText(this@EditorActivity, "$label 완료", Toast.LENGTH_SHORT).show()
            }.onFailure {
                tvInfo.text = "❌ $label 실패: ${it.message}"
                Toast.makeText(this@EditorActivity, "실패: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
