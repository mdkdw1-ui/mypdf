package com.mdkdw1.mypdf

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewerActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener {

    companion object {
        const val EXTRA_PATH = "pdf_path"
    }

    private lateinit var pdfView: PDFView
    private lateinit var tvPageInfo: TextView
    private lateinit var tvFileName: TextView
    private lateinit var prefs: ReadingPrefs
    private lateinit var file: File

    private var pageCount = 0
    private var currentPage = 0
    private var selectionMode = false

    private var lastTapX = 0f
    private var lastTapY = 0f

    // ⭐ PDFView 커스텀: 터치 좌표 기록 + 이벤트 통과
    private inner class RecordingPdfView(context: android.content.Context) : PDFView(context, null) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastTapX = event.x
                lastTapY = event.y
            }
            return super.onTouchEvent(event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        file = File(path)
        if (!file.exists()) { finish(); return }

        prefs = ReadingPrefs(this)

        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // ── 루트 레이아웃 ──
        val root = FrameLayout(this)

        // ⭐ PDFView — 커스텀 RecordingPdfView 사용
        pdfView = RecordingPdfView(this)
        val pdfLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        pdfView.layoutParams = pdfLp
        root.addView(pdfView)

        // ── 상단 툴바 ──
        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.setBackgroundColor(0xCC000000.toInt())
        toolbar.setPadding(8, 8, 8, 8)
        toolbar.gravity = Gravity.CENTER_VERTICAL
        val toolbarLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        toolbarLp.gravity = Gravity.TOP
        toolbar.layoutParams = toolbarLp

        val btnClose = Button(this)
        btnClose.text = "✕"
        btnClose.setTextColor(0xFFFFFFFF.toInt())
        btnClose.setBackgroundColor(0)
        btnClose.setOnClickListener { finish() }

        tvFileName = TextView(this)
        tvFileName.setTextColor(0xFFFFFFFF.toInt())
        tvFileName.textSize = 12f
        tvFileName.maxLines = 1
        tvFileName.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        tvFileName.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tvFileName.setPadding(16, 0, 8, 0)

        val btnSelect = Button(this)
        btnSelect.text = "✏"
        btnSelect.setTextColor(0xFFFFFFFF.toInt())
        btnSelect.setBackgroundColor(0)
        btnSelect.setOnClickListener {
            selectionMode = !selectionMode
            btnSelect.setBackgroundColor(if (selectionMode) 0x88FF6B9D.toInt() else 0)
            Toast.makeText(
                this@PdfViewerActivity,
                if (selectionMode) "선택 모드 ON — 길게 눌러 번역" else "선택 모드 OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        val btnOverlay = Button(this)
        btnOverlay.text = "🎨"
        btnOverlay.setTextColor(0xFFFFFFFF.toInt())
        btnOverlay.setBackgroundColor(0)
        btnOverlay.setOnClickListener {
            val intent = Intent(this@PdfViewerActivity, OcrOverlayActivity::class.java)
            intent.putExtra("pdf_path", file.absolutePath)
            intent.putExtra("page_index", currentPage)
            startActivity(intent)
        }

        val btnSettings = Button(this)
        btnSettings.text = "⚙"
        btnSettings.setTextColor(0xFFFFFFFF.toInt())
        btnSettings.setBackgroundColor(0)
        btnSettings.setOnClickListener {
            startActivity(Intent(this@PdfViewerActivity, LogActivity::class.java))
        }

        toolbar.addView(btnClose)
        toolbar.addView(tvFileName)
        toolbar.addView(btnSelect)
        toolbar.addView(btnOverlay)
        toolbar.addView(btnSettings)
        root.addView(toolbar)

        // ── 하단 바 ──
        val bottomBar = LinearLayout(this)
        bottomBar.orientation = LinearLayout.HORIZONTAL
        bottomBar.setBackgroundColor(0xCC000000.toInt())
        bottomBar.setPadding(16, 12, 16, 12)
        bottomBar.gravity = Gravity.CENTER
        val bottomLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        bottomLp.gravity = Gravity.BOTTOM
        bottomBar.layoutParams = bottomLp

        val btnPrev = Button(this)
        btnPrev.text = "◀"
        btnPrev.setOnClickListener {
            if (currentPage > 0) pdfView.jumpTo(currentPage - 1)
        }

        tvPageInfo = TextView(this)
        tvPageInfo.setTextColor(0xFFFFFFFF.toInt())
        tvPageInfo.textSize = 14f
        tvPageInfo.setPadding(24, 0, 24, 0)
        tvPageInfo.text = "0 / 0"

        val btnNext = Button(this)
        btnNext.text = "▶"
        btnNext.setOnClickListener {
            if (currentPage < pageCount - 1) pdfView.jumpTo(currentPage + 1)
        }

        bottomBar.addView(btnPrev)
        bottomBar.addView(tvPageInfo)
        bottomBar.addView(btnNext)
        root.addView(bottomBar)

        setContentView(root)

        // ⭐ PDFView 초기화 (터치 설정 없음!)
        loadPdf()

        // ⭐ 롱프레스만 PDFView에 설정 (clickListener 없이)
        pdfView.setOnLongClickListener {
            if (selectionMode) {
                handleLongPress()
                true
            } else false
        }
    }

    private fun loadPdf() {
        tvFileName.text = file.name

        // ⭐ 핀치 줌 + 스크롤 완전 활성
        pdfView.fromFile(file)
            .enableSwipe(true)              // 스와이프 활성
            .swipeHorizontal(false)         // 세로 스크롤
            .enableDoubletap(true)          // 더블탭 줌
            .enableAntialiasing(true)       // 안티앨리어싱
            .spacing(10)                    // 페이지 간격
            .defaultPage(prefs.getPage(file.name))
            .scrollHandle(DefaultScrollHandle(this))
            .onLoad(this)
            .onPageChange(this)
            .load()

        // ⭐ 줌 범위
        pdfView.setMaxZoom(5.0f)
        pdfView.setMinZoom(0.5f)
        pdfView.useBestQuality(true)
    }

    private fun handleLongPress() {
        val apiKey = prefs.groqApiKey
        if (apiKey.isNullOrBlank()) {
            Toast.makeText(this, "설정에서 Groq API 키를 입력하세요", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "텍스트 감지 중...", Toast.LENGTH_SHORT).show()

        val tapX = lastTapX
        val tapY = lastTapY
        val viewW = pdfView.width
        val viewH = pdfView.height
        val page = currentPage

        lifecycleScope.launch {
            val words = withContext(Dispatchers.IO) {
                PdfTextPicker.extractWords(this@PdfViewerActivity, file, page).getOrNull()
            }
            if (words.isNullOrEmpty()) {
                Toast.makeText(this@PdfViewerActivity, "텍스트 없음", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val found = PdfTextPicker.findWordAt(
                words, tapX, tapY, viewW, viewH, 595f, 842f
            )

            if (found != null) {
                showTranslateDialog(found.text)
            } else {
                Toast.makeText(this@PdfViewerActivity, "텍스트 감지 안 됨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTranslateDialog(sourceText: String) {
        val input = EditText(this)
        input.setText(sourceText)
        input.setSelection(sourceText.length)

        val output = TextView(this)
        output.textSize = 14f
        output.setPadding(24, 24, 24, 24)
        output.setTextColor(0xFF333333.toInt())

        val label1 = TextView(this)
        label1.setText("텍스트:")
        label1.setTextColor(0xFF888888.toInt())
        label1.textSize = 11f

        val label2 = TextView(this)
        label2.setText("번역:")
        label2.setTextColor(0xFF888888.toInt())
        label2.textSize = 11f
        label2.setPadding(0, 16, 0, 8)

        val scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )
        scroll.addView(output)

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(24, 16, 24, 16)
        container.addView(label1)
        container.addView(input)
        container.addView(label2)
        container.addView(scroll)

        AlertDialog.Builder(this)
            .setTitle("번역 (Groq)")
            .setView(container)
            .setPositiveButton("번역") { _, _ ->
                val apiKey = prefs.groqApiKey ?: return@setPositiveButton
                output.setText("번역 중...")
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        GroqTranslator.translate(
                            apiKey, input.text.toString(), prefs.targetLang, "auto"
                        )
                    }
                    result.fold(
                        onSuccess = { output.setText(it) },
                        onFailure = { output.setText("오류: ${it.message}") }
                    )
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    override fun loadComplete(nbPages: Int) {
        pageCount = nbPages
        runOnUiThread {
            tvPageInfo.text = "1 / $nbPages"
        }
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        currentPage = page
        prefs.savePage(file.name, page)
        runOnUiThread {
            tvPageInfo.text = "${page + 1} / $pageCount"
        }
    }
}
