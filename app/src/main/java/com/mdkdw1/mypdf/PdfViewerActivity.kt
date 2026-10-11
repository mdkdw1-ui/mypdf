package com.mdkdw1.mypdf

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "pdf_path"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PdfPageAdapter
    private lateinit var tvPageInfo: TextView
    private lateinit var tvFileName: TextView
    private lateinit var btnSelect: Button
    private lateinit var prefs: ReadingPrefs
    private lateinit var file: File
    private lateinit var pdfRenderer: PdfRenderer
    private lateinit var pfd: ParcelFileDescriptor
    private var pageCount = 0
    private var currentPage = 0
    private var selectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        file = File(path)
        if (!file.exists()) { finish(); return }

        prefs = ReadingPrefs(this)

        // ⭐ PDFRenderer 열기
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(pfd)
        pageCount = pdfRenderer.pageCount

        CrashLogger.log(this, "PdfViewer", "PDF 열기: $pageCount 페이지")

        val screenWidth = resources.displayMetrics.widthPixels

        // ⭐ 모든 페이지 렌더링
        val pages = mutableListOf<PdfPageAdapter.PageData>()
        for (i in 0 until pageCount) {
            try {
                pdfRenderer.openPage(i).use { page ->
                    val bitmap = renderPage(page, screenWidth)
                    if (bitmap != null) {
                        pages.add(PdfPageAdapter.PageData(
                            index = i,
                            bitmap = bitmap,
                            widthPt = page.width.toFloat(),
                            heightPt = page.height.toFloat()
                        ))
                        CrashLogger.log(this, "PdfViewer",
                            "페이지 $i 렌더: bitmap=${bitmap.width}x${bitmap.height} pt=${page.width}x${page.height}")
                    }
                }
            } catch (e: Exception) {
                CrashLogger.e(this, "PdfViewer", "페이지 $i 렌더 실패", e)
            }
        }

        if (pages.isEmpty()) {
            Toast.makeText(this, "❌ PDF 로드 실패", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ── 루트 (핀치 줌 지원) ──
        val root = ZoomableFrameLayout(this)

        // ── RecyclerView ──
        recyclerView = RecyclerView(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setBackgroundColor(Color.parseColor("#1A1A1A"))
        recyclerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(recyclerView)

        adapter = PdfPageAdapter(pages)
        recyclerView.adapter = adapter

        // ⭐ 전체 페이지 핀치 줌 연결
        root.target = recyclerView

        // ── 상단 툴바 ──
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

        val btnClose = Button(this).apply {
            text = "✕"
            setTextColor(0xFFFFFFFF.toInt())
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
            text = file.name
        }

        toolbar.addView(btnClose)
        toolbar.addView(tvFileName)
        root.addView(toolbar)

        // ── 하단 바 ──
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xDD000000.toInt())
            setPadding(8, 16, 8, 16)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
        }

        // ⭐ 시스템 네비게이션 바 높이만큼 하단 패딩 추가
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(8, 16, 8, 16 + navBars.bottom)
            insets
        }

        tvPageInfo = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = "1 / $pageCount"
        }

        btnSelect = Button(this).apply {
            text = "✏"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(40, 20, 40, 20)
            setOnClickListener {
                selectionMode = !selectionMode
                setBackgroundColor(if (selectionMode) 0xFFFF6B9D.toInt() else 0)
                Toast.makeText(
                    this@PdfViewerActivity,
                    if (selectionMode) "선택 모드 ON — 길게 눌러 번역" else "OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val btnOverlay = Button(this).apply {
            text = "🎨"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(40, 20, 40, 20)
            setOnClickListener {
                val intent = Intent(this@PdfViewerActivity, OcrOverlayActivity::class.java)
                intent.putExtra("pdf_path", file.absolutePath)
                intent.putExtra("page_index", currentPage)
                startActivity(intent)
            }
        }

        bottomBar.addView(tvPageInfo)
        bottomBar.addView(btnSelect)
        bottomBar.addView(btnOverlay)
        root.addView(bottomBar)

        setContentView(root)

        // ⭐ 롱프레스 감지 (RecyclerView OnItemTouchListener)
        setupLongPress(pages)

        // 페이지 추적
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                val first = lm.findFirstVisibleItemPosition()
                if (first >= 0 && first != currentPage) {
                    currentPage = first
                    tvPageInfo.text = "${first + 1} / $pageCount"
                    prefs.savePage(file.name, first)
                }
            }
        })

        // 저장된 페이지로 이동
        val savedPage = prefs.getPage(file.name)
        if (savedPage > 0 && savedPage < pageCount) {
            recyclerView.scrollToPosition(savedPage)
        }
    }

    private fun renderPage(page: PdfRenderer.Page, targetWidth: Int): Bitmap? {
        return try {
            val scale = targetWidth.toFloat() / page.width
            val height = (page.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * ⭐ RecyclerView에 롱프레스 감지 (스크롤과 공존)
     */
    private fun setupLongPress(pages: List<PdfPageAdapter.PageData>) {
        val handler = Handler(Looper.getMainLooper())
        var runnable: Runnable? = null
        var downX = 0f
        var downY = 0f
        var downChild: View? = null
        var downChildX = 0f
        var downChildY = 0f

        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                        downChild = rv.findChildViewUnder(e.x, e.y)
                        downChild?.let { child ->
                            downChildX = e.x - child.left
                            downChildY = e.y - child.top
                        }

                        if (selectionMode && downChild != null) {
                            runnable = Runnable {
                                val child = downChild
                                if (child != null) {
                                    val pos = rv.getChildAdapterPosition(child)
                                    if (pos >= 0 && pos < pages.size) {
                                        val page = pages[pos]
                                        CrashLogger.log(
                                            this@PdfViewerActivity, "PdfViewer",
                                            "LONG PRESS: page=$pos tap=(${"%.1f".format(downChildX)}, ${"%.1f".format(downChildY)}) " +
                                            "view=${child.width}x${child.height} " +
                                            "bitmap=${page.bitmap.width}x${page.bitmap.height} " +
                                            "pt=${page.widthPt}x${page.heightPt}"
                                        )
                                        onPageLongPress(
                                            page,
                                            downChildX, downChildY,
                                            child.width.toFloat(), child.height.toFloat()
                                        )
                                    }
                                }
                            }
                            handler.postDelayed(runnable!!, 800L)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(e.x - downX) > 30f ||
                            kotlin.math.abs(e.y - downY) > 30f) {
                            runnable?.let { handler.removeCallbacks(it) }
                            runnable = null
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        runnable?.let { handler.removeCallbacks(it) }
                        runnable = null
                    }
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(p: Boolean) {}
        })
    }

    /**
     * ⭐ 좌표 변환: 뷰 좌표 → PDF pt (1:1 매핑)
     */
    private fun onPageLongPress(
        page: PdfPageAdapter.PageData,
        tapX: Float, tapY: Float,
        viewW: Float, viewH: Float
    ) {
        val apiKey = prefs.groqApiKey
        if (apiKey.isNullOrBlank()) {
            Toast.makeText(this, "⚙ 설정에서 Groq API 키를 입력하세요", Toast.LENGTH_LONG).show()
            return
        }

        // 뷰 좌표 → PDF pt
        val pdfX = tapX / viewW * page.widthPt
        val pdfY = tapY / viewH * page.heightPt

        CrashLogger.log(this, "PdfViewer",
            "→ pdf=(${"%.1f".format(pdfX)}, ${"%.1f".format(pdfY)})")

        Toast.makeText(this, "🔍 텍스트 감지 중...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                PdfTextPicker.extractWordsWithSize(this@PdfViewerActivity, file, page.index)
            }.getOrNull()

            if (result == null || result.words.isEmpty()) {
                Toast.makeText(this@PdfViewerActivity, "❌ 텍스트 추출 실패", Toast.LENGTH_LONG).show()
                return@launch
            }

            val found = PdfTextPicker.findWordAtDirect(
                context = this@PdfViewerActivity,
                words = result.words,
                pdfX = pdfX,
                pdfY = pdfY
            )

            if (found != null) {
                showTranslateDialog(found.text)
            } else {
                Toast.makeText(this@PdfViewerActivity, "❌ 위치 매칭 실패", Toast.LENGTH_LONG).show()
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

    override fun onDestroy() {
        try { pdfRenderer.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
