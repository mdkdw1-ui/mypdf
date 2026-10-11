package com.mdkdw1.mypdf

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    // ⭐ PDFView 커스텀 — dispatchTouchEvent로 롱프레스 감지 (스크롤/핀치 유지)
    private inner class TouchablePdfView(context: android.content.Context) : PDFView(context, null) {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private var longPressRunnable: Runnable? = null
        private var downX = 0f
        private var downY = 0f
        private var longPressFired = false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTapX = event.x
                    lastTapY = event.y
                    downX = event.x
                    downY = event.y
                    longPressFired = false

                    android.util.Log.d("PdfViewer",
                        "DOWN selection=$selectionMode x=${event.x} y=${event.y}")

                    // ⭐ 선택 모드일 때만 롱프레스 타이머 시작
                    if (selectionMode) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        longPressRunnable = Runnable {
                            if (!longPressFired) {
                                longPressFired = true
                                android.util.Log.d("PdfViewer", "⭐ LONG PRESS FIRED!")
                                handleLongPress()
                            }
                        }
                        handler.postDelayed(longPressRunnable!!, 600L)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // 움직임이 크면 롱프레스 취소 (스크롤 의도)
                    val dx = kotlin.math.abs(event.x - downX)
                    val dy = kotlin.math.abs(event.y - downY)
                    if (dx > 20f || dy > 20f) {
                        if (!longPressFired) {
                            longPressRunnable?.let { handler.removeCallbacks(it) }
                            longPressRunnable = null
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!longPressFired) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        longPressRunnable = null
                    }
                }
            }
            // ⭐ PDFView의 원래 터치 처리는 그대로 유지 (스크롤/핀치 작동)
            return super.dispatchTouchEvent(event)
        }
    }

    companion object {
        const val EXTRA_PATH = "pdf_path"
    }

    private lateinit var pdfView: PDFView
    private lateinit var tvPageInfo: TextView
    private lateinit var tvFileName: TextView
    private lateinit var btnSelect: Button
    private lateinit var prefs: ReadingPrefs
    private lateinit var file: File

    private var pageCount = 0
    private var currentPage = 0
    private var selectionMode = false

    private var lastTapX = 0f
    private var lastTapY = 0f

    // ⭐ GestureDetector (롱프레스 감지)
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        file = File(path)
        if (!file.exists()) { finish(); return }

        prefs = ReadingPrefs(this)

        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // ⭐ GestureDetector 초기화
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                lastTapX = e.x
                lastTapY = e.y
                android.util.Log.d("PdfViewer", "onDown x=${e.x} y=${e.y} selection=$selectionMode")
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                android.util.Log.d("PdfViewer", "⭐ onLongPress! selection=$selectionMode")
                if (selectionMode) {
                    lastTapX = e.x
                    lastTapY = e.y
                    handleLongPress()
                }
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                android.util.Log.d("PdfViewer", "onSingleTapUp")
                return false
            }
        })

        val root = FrameLayout(this)

        // ── PDFView (커스텀 클래스) ──
        pdfView = TouchablePdfView(this)
        pdfView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(pdfView)

        // ── 상단 툴바 (최소) ──
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

        val btnSettings = Button(this)
        btnSettings.text = "⚙"
        btnSettings.setTextColor(0xFFFFFFFF.toInt())
        btnSettings.setBackgroundColor(0)
        btnSettings.setOnClickListener {
            startActivity(Intent(this@PdfViewerActivity, LogActivity::class.java))
        }

        toolbar.addView(btnClose)
        toolbar.addView(tvFileName)
        toolbar.addView(btnSettings)
        root.addView(toolbar)

        // ── 하단 바 ──
        val bottomBar = LinearLayout(this)
        bottomBar.orientation = LinearLayout.HORIZONTAL
        bottomBar.setBackgroundColor(0xDD000000.toInt())
        bottomBar.setPadding(8, 16, 8, 16)
        bottomBar.gravity = Gravity.CENTER_VERTICAL
        val bottomLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        bottomLp.gravity = Gravity.BOTTOM
        bottomBar.layoutParams = bottomLp

        // ⭐ 시스템 네비게이션 바 높이만큼 하단 패딩 추가
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(8, 16, 8, 16 + navBars.bottom)
            insets
        }

        val btnPrev = Button(this)
        btnPrev.text = "◀"
        btnPrev.textSize = 18f
        btnPrev.setTextColor(0xFFFFFFFF.toInt())
        btnPrev.setBackgroundColor(0)
        btnPrev.setPadding(40, 20, 40, 20)
        btnPrev.setOnClickListener {
            if (currentPage > 0) pdfView.jumpTo(currentPage - 1)
        }

        tvPageInfo = TextView(this)
        tvPageInfo.setTextColor(0xFFFFFFFF.toInt())
        tvPageInfo.textSize = 13f
        tvPageInfo.gravity = Gravity.CENTER
        tvPageInfo.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tvPageInfo.text = "0 / 0"

        val btnNext = Button(this)
        btnNext.text = "▶"
        btnNext.textSize = 18f
        btnNext.setTextColor(0xFFFFFFFF.toInt())
        btnNext.setBackgroundColor(0)
        btnNext.setPadding(40, 20, 40, 20)
        btnNext.setOnClickListener {
            if (currentPage < pageCount - 1) pdfView.jumpTo(currentPage + 1)
        }

        btnSelect = Button(this)
        btnSelect.text = "✏"
        btnSelect.textSize = 22f
        btnSelect.setTextColor(0xFFFFFFFF.toInt())
        btnSelect.setBackgroundColor(0)
        btnSelect.setPadding(40, 20, 40, 20)
        btnSelect.setOnClickListener {
            selectionMode = !selectionMode
            btnSelect.setBackgroundColor(if (selectionMode) 0xFFFF6B9D.toInt() else 0)
            Toast.makeText(
                this@PdfViewerActivity,
                if (selectionMode) "선택 모드 ON — 길게 눌러 번역" else "선택 모드 OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        val btnOverlay = Button(this)
        btnOverlay.text = "🎨"
        btnOverlay.textSize = 22f
        btnOverlay.setTextColor(0xFFFFFFFF.toInt())
        btnOverlay.setBackgroundColor(0)
        btnOverlay.setPadding(40, 20, 40, 20)
        btnOverlay.setOnClickListener {
            val intent = Intent(this@PdfViewerActivity, OcrOverlayActivity::class.java)
            intent.putExtra("pdf_path", file.absolutePath)
            intent.putExtra("page_index", currentPage)
            startActivity(intent)
        }

        bottomBar.addView(btnPrev)
        bottomBar.addView(tvPageInfo)
        bottomBar.addView(btnNext)
        bottomBar.addView(btnSelect)
        bottomBar.addView(btnOverlay)
        root.addView(bottomBar)

        setContentView(root)

        loadPdf()
    }

    private fun loadPdf() {
        tvFileName.text = file.name

        pdfView.fromFile(file)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .enableAntialiasing(true)
            .spacing(10)
            .defaultPage(prefs.getPage(file.name))
            .scrollHandle(DefaultScrollHandle(this))
            .onLoad(this)
            .onPageChange(this)
            .load()

        pdfView.setMaxZoom(5.0f)
        pdfView.setMinZoom(0.5f)
        pdfView.useBestQuality(true)
    }

    private fun handleLongPress() {
        val apiKey = prefs.groqApiKey
        if (apiKey.isNullOrBlank()) {
            Toast.makeText(this, "⚙ 설정에서 Groq API 키를 입력하세요", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "🔍 텍스트 감지 중...", Toast.LENGTH_SHORT).show()

        val tapX = lastTapX
        val tapY = lastTapY
        val viewW = pdfView.width
        val viewH = pdfView.height
        val page = currentPage

        android.util.Log.d("PdfViewer",
            "handleLongPress: tapX=$tapX tapY=$tapY viewW=$viewW viewH=$viewH page=$page")

        lifecycleScope.launch {
            val words = withContext(Dispatchers.IO) {
                PdfTextPicker.extractWords(this@PdfViewerActivity, file, page).getOrNull()
            }

            if (words.isNullOrEmpty()) {
                Toast.makeText(this@PdfViewerActivity,
                    "❌ 텍스트 추출 실패 (스캔본이거나 빈 페이지)", Toast.LENGTH_LONG).show()
                return@launch
            }

            android.util.Log.d("PdfViewer", "추출 단어 수: ${words.size}")

            val found = PdfTextPicker.findWordAt(
                words, tapX, tapY, viewW, viewH, 595f, 842f
            )

            if (found != null) {
                android.util.Log.d("PdfViewer", "감지: ${found.text}")
                showTranslateDialog(found.text)
            } else {
                Toast.makeText(this@PdfViewerActivity,
                    "❌ 위치 매칭 실패 — 텍스트 근처 다시 눌러보세요", Toast.LENGTH_LONG).show()
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
