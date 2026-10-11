package com.mdkdw1.mypdf

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
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
    private var toolbarVisible = true
    private var selectionMode = false

    private var lastTapX = 0f
    private var lastTapY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        file = File(path)
        if (!file.exists()) { finish(); return }

        prefs = ReadingPrefs(this)

        // 화면 항상 켜짐
        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // ── 레이아웃 프로그래매틱 구성 ──
        val root = FrameLayout(this)

        // PDFView
        pdfView = PDFView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(pdfView)

        // 상단 툴바
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        }

        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0)
            setOnClickListener { finish() }
        }

        tvFileName = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 0, 8, 0)
        }

        val btnSelect = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundColor(0)
            contentDescription = "선택 모드"
            setOnClickListener {
                selectionMode = !selectionMode
                Toast.makeText(
                    this@PdfViewerActivity,
                    if (selectionMode) "선택 모드 ON — 길게 눌러 번역" else "선택 모드 OFF",
                    Toast.LENGTH_SHORT
                ).show()
                updateSelectionButton(this)
            }
        }

        val btnOverlay = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            setBackgroundColor(0)
            contentDescription = "오버레이 번역"
            setOnClickListener {
                startActivity(Intent(this@PdfViewerActivity, OcrOverlayActivity::class.java).apply {
                    putExtra("pdf_path", file.absolutePath)
                    putExtra("page_index", currentPage)
                })
            }
        }

        val btnSettings = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setBackgroundColor(0)
            contentDescription = "설정"
            setOnClickListener {
                startActivity(Intent(this@PdfViewerActivity, LogActivity::class.java))
            }
        }

        toolbar.addView(btnClose)
        toolbar.addView(tvFileName)
        toolbar.addView(btnSelect)
        toolbar.addView(btnOverlay)
        toolbar.addView(btnSettings)
        root.addView(toolbar)

        // 하단 페이지 바
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
        }

        val btnPrev = Button(this).apply {
            text = "◀"
            setOnClickListener {
                if (currentPage > 0) pdfView.jumpTo(currentPage - 1)
            }
        }

        tvPageInfo = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(24, 0, 24, 0)
            text = "0 / 0"
        }

        val btnNext = Button(this).apply {
            text = "▶"
            setOnClickListener {
                if (currentPage < pageCount - 1) pdfView.jumpTo(currentPage + 1)
            }
        }

        bottomBar.addView(btnPrev)
        bottomBar.addView(tvPageInfo)
        bottomBar.addView(btnNext)
        root.addView(bottomBar)

        setContentView(root)

        // ── PDFView 초기화 (핵심!) ──
        loadPdf()

        // ── 롱프레스 리스너 ──
        pdfView.setOnLongClickListener {
            if (selectionMode) {
                handleLongPress()
                true
            } else false
        }

        // 터치 좌표만 기록 (핀치/스크롤은 PDFView가 처리)
        pdfView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                lastTapX = event.x
                lastTapY = event.y
            }
            false  // ⭐ 이벤트 계속 전달 → PDFView가 핀치/스크롤 처리
        }

        // 툴바 토글 (PDFView 클릭 시)
        pdfView.setOnClickListener {
            toolbarVisible = !toolbarVisible
            val v = if (toolbarVisible) View.VISIBLE else View.GONE
            toolbar.visibility = v
            bottomBar.visibility = v
        }
    }

    private fun updateSelectionButton(btn: ImageButton) {
        btn.setBackgroundColor(
            if (selectionMode) 0x88FF6B9D.toInt() else 0
        )
    }

    private fun loadPdf() {
        tvFileName.text = file.name

        pdfView.fromFile(file)
            .enableSwipe(true)
            .swipeHorizontal(false)          // 세로 스크롤
            .enableDoubletap(true)           // 더블탭 줌
            .enableAntialiasing(true)
            .spacing(10)
            .defaultPage(prefs.getPage(file.name))
            .scrollHandle(DefaultScrollHandle(this))
            .onLoad(this)
            .onPageChange(this)
            .load()

        pdfView.useBestQuality(true)
        pdfView.setMaxZoom(5.0f)
        pdfView.setMinZoom(0.5f)
    }

    private fun handleLongPress() {
        val apiKey = prefs.groqApiKey
        if (apiKey.isNullOrBlank()) {
            Toast.makeText(this, "설정에서 Groq API 키를 입력하세요", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "텍스트 감지 중...", Toast.LENGTH_SHORT).show()

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val words = PdfTextPicker.extractWords(this@PdfViewerActivity, file, currentPage).getOrNull()
            if (words.isNullOrEmpty()) {
                Toast.makeText(this@PdfViewerActivity, "텍스트 없음", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // PDF 뷰 크기 + 페이지 크기로 매칭
            val pageW = pdfView.document?.pages?.getOrNull(currentPage)?.let {
                // 페이지 크기는 pt 단위, PDFView는 픽셀 단위
                595f  // A4 기본값 (필요시 실제 크기)
            } ?: 595f

            val found = PdfTextPicker.findWordAt(
                words, lastTapX, lastTapY,
                pdfView.width, pdfView.height, pageW, 842f
            )

            if (found != null) {
                showTranslateDialog(found.text)
            } else {
                Toast.makeText(this@PdfViewerActivity, "텍스트 감지 안 됨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTranslateDialog(text: String) {
        val input = EditText(this).apply {
            setText(text)
            setSelection(text.length)
        }

        val output = TextView(this).apply {
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setTextColor(0xFF333333.toInt())
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            addView(TextView(this@PdfViewerActivity).apply {
                text = "텍스트:"
                setTextColor(0xFF888888.toInt())
                textSize = 11f
            })
            addView(input)
            addView(TextView(this@PdfViewerActivity).apply {
                text = "번역:"
                setTextColor(0xFF888888.toInt())
                textSize = 11f
                setPadding(0, 16, 0, 8)
            })
            addView(ScrollView(this@PdfViewerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
                addView(output)
            })
        }

        AlertDialog.Builder(this)
            .setTitle("번역 (Groq)")
            .setView(container)
            .setPositiveButton("번역") { _, _ ->
                val apiKey = prefs.groqApiKey ?: return@setPositiveButton
                output.text = "번역 중..."
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        GroqTranslator.translate(apiKey, input.text.toString(), prefs.targetLang, "auto")
                    }
                    result.fold(
                        onSuccess = { output.text = it },
                        onFailure = { output.text = "오류: ${it.message}" }
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
