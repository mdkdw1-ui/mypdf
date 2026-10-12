package com.mdkdw1.mypdf

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfPagePreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "pdf_path"
        const val EXTRA_PAGE = "page_index"
        const val EXTRA_MODE = "mode"
        const val MODE_PREVIEW = "preview"
        const val MODE_ADD_TEXT = "add_text"
        const val MODE_HIGHLIGHT = "highlight"
        const val MODE_STICKY = "sticky"
        const val MODE_RECTANGLE = "rectangle"
        const val MODE_CIRCLE = "circle"
        const val RESULT_PAGE_INDEX = "result_page_index"
    }

    private lateinit var pdfView: PDFView
    private var pageIndex = 0
    private var mode = MODE_PREVIEW
    private lateinit var file: File
    private var pageWidthPt = 595f
    private var pageHeightPt = 842f
    private var pdfViewWidth = 0
    private var pdfViewHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        pageIndex = intent.getIntExtra(EXTRA_PAGE, 0)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PREVIEW
        file = File(path)
        if (!file.exists()) { finish(); return }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#0D1117"))

        // ⭐ PDFView 코드로 직접 생성
        pdfView = PDFView(this, null).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        root.addView(pdfView)

        // 상단 바
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xDD000000.toInt())
            setPadding(16, 24, 16, 12)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        }
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(16, 24 + statusBar.top, 16, 12)
            insets
        }

        val btnClose = Button(this).apply {
            text = "✕"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setOnClickListener { finish() }
        }

        val tvTitle = TextView(this).apply {
            text = "페이지 ${pageIndex + 1}"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        topBar.addView(btnClose)
        topBar.addView(tvTitle)

        if (mode != MODE_PREVIEW) {
            val hint = TextView(this).apply {
                text = "👆 위치를 탭"
                setTextColor(0xFFFF6B9D.toInt())
                textSize = 11f
                setPadding(8, 0, 0, 0)
            }
            topBar.addView(hint)
        }

        root.addView(topBar)

        // ⭐ 하단 페이지 이동 (필요시)
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
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setOnClickListener {
                if (pageIndex > 0) {
                    pageIndex--
                    pdfView.jumpTo(pageIndex)
                    tvTitle.text = "페이지 ${pageIndex + 1}"
                }
            }
        }

        val tvPageInfo = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(24, 0, 24, 0)
            text = "${pageIndex + 1} / ${pdfView.pageCount}"
        }

        val btnNext = Button(this).apply {
            text = "▶"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setOnClickListener {
                if (pageIndex < pdfView.pageCount - 1) {
                    pageIndex++
                    pdfView.jumpTo(pageIndex)
                    tvTitle.text = "페이지 ${pageIndex + 1}"
                }
            }
        }

        bottomBar.addView(btnPrev)
        bottomBar.addView(tvPageInfo)
        bottomBar.addView(btnNext)
        root.addView(bottomBar)

        setContentView(root)

        // ⭐ PDFView 로드 (fromFile → load)
        pdfView.fromFile(file)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .enableAntialiasing(true)
            .spacing(10)
            .defaultPage(pageIndex)
            .scrollHandle(DefaultScrollHandle(this))
            .onPageChange(object : OnPageChangeListener {
                override fun onPageChanged(page: Int, pageCount: Int) {
                    pageIndex = page
                    tvTitle.text = "페이지 ${page + 1}"
                    tvPageInfo.text = "${page + 1} / $pageCount"
                    lifecycleScope.launch {
                        val (w, h) = withContext(Dispatchers.IO) { getPageSizePt(file, page) }
                        pageWidthPt = w
                        pageHeightPt = h
                    }
                }
            })
            .load()

        pdfView.setMaxZoom(8.0f)
        pdfView.setMinZoom(1.0f)
        pdfView.useBestQuality(true)

        // ⭐ 탭 리스너 (mode일 때만, false 반환 = 이벤트 통과)
        pdfView.setOnTouchListener { _, event ->
            if (mode != MODE_PREVIEW) {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        downTime = System.currentTimeMillis()
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - downTime
                        val dx = kotlin.math.abs(event.x - downX)
                        val dy = kotlin.math.abs(event.y - downY)
                        if (duration < 250 && dx < 15 && dy < 15) {
                            pdfViewWidth = pdfView.width
                            pdfViewHeight = pdfView.height
                            handleTap(event.x, event.y)
                        }
                    }
                }
            }
            false  // ⭐ PDFView가 이벤트 정상 처리
        }

        // 페이지 크기 로드
        lifecycleScope.launch {
            val (w, h) = withContext(Dispatchers.IO) { getPageSizePt(file, pageIndex) }
            pageWidthPt = w
            pageHeightPt = h
        }
    }

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    /**
     * ⭐ 탭 → PDF 좌표
     */
    private fun handleTap(tapX: Float, tapY: Float) {
        try {
            val pageSize = pdfView.getPageSize(pageIndex)
            val pageW = pageSize.width.toFloat()
            val pageH = pageSize.height.toFloat()

            val scaleX = pageW / pageWidthPt
            val scaleY = pageH / pageHeightPt

            // 뷰 중앙 정렬
            val pageLeft = (pdfViewWidth - pageW) / 2f
            val pageTop = (pdfViewHeight - pageH) / 2f

            val localX = tapX - pageLeft
            val localY = tapY - pageTop

            val pdfX = localX / scaleX
            val pdfY = pageHeightPt - (localY / scaleY)

            CrashLogger.log(this, "PdfPagePreview",
                "탭: view=(${"%.1f".format(tapX)}, ${"%.1f".format(tapY)}) " +
                "viewSize=(${pdfViewWidth}x${pdfViewHeight}) " +
                "pageSize=(${"%.0f".format(pageW)}x${"%.0f".format(pageH)}) " +
                "→ pdf=(${"%.1f".format(pdfX)}, ${"%.1f".format(pdfY)})")

            when (mode) {
                MODE_ADD_TEXT -> showAddTextDialog(pdfX, pdfY)
                MODE_HIGHLIGHT -> performHighlight(pdfX, pdfY)
                MODE_STICKY -> showStickyDialog(pdfX, pdfY)
                MODE_RECTANGLE -> showRectangleDialog(pdfX, pdfY)
                MODE_CIRCLE -> showCircleDialog(pdfX, pdfY)
            }
        } catch (e: Exception) {
            CrashLogger.e(this, "PdfPagePreview", "탭 실패", e)
        }
    }

    private fun showAddTextDialog(pdfX: Float, pdfY: Float) {
        val input = makeEditText("텍스트 입력")
        val sizeInput = makeEditText("폰트 크기 (pt)").apply {
            setText("14")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            addView(TextView(this@PdfPagePreviewActivity).apply {
                text = "위치: (${"%.0f".format(pdfX)}, ${"%.0f".format(pdfY)}) pt"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 11f
            })
            addView(input)
            addView(sizeInput)
        }
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("➕ 텍스트 추가")
            .setView(container)
            .setPositiveButton("추가") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                val fontSize = sizeInput.text.toString().toFloatOrNull() ?: 14f
                performAddText(pdfX, pdfY, text, fontSize)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performAddText(x: Float, y: Float, text: String, fontSize: Float) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                PdfTextEditor.addText(this@PdfPagePreviewActivity, file, pageIndex, x, y, text, fontSize, Color.BLACK)
            }
            result.fold(
                onSuccess = { Toast.makeText(this@PdfPagePreviewActivity, "✅ 추가됨", Toast.LENGTH_SHORT).show(); returnResult() },
                onFailure = { Toast.makeText(this@PdfPagePreviewActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show() }
            )
        }
    }

    private fun performHighlight(x: Float, y: Float) {
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("🖍 하이라이트")
            .setItems(arrayOf("노랑", "초록", "핑크", "파랑")) { _, which ->
                val color = when (which) { 0 -> Color.YELLOW; 1 -> Color.GREEN; 2 -> 0xFFFF6B9D.toInt(); else -> Color.CYAN }
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        PdfAnnotation.addHighlight(this@PdfPagePreviewActivity, file, pageIndex, x - 50f, y - 10f, 100f, 20f, color, 0.4f)
                    }
                    result.fold(onSuccess = { returnResult() }, onFailure = { Toast.makeText(this@PdfPagePreviewActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show() })
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showStickyDialog(x: Float, y: Float) {
        val input = makeEditText("메모 내용").apply { minLines = 3 }
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("📌 스티키 노트")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        PdfAnnotation.addStickyNote(this@PdfPagePreviewActivity, file, pageIndex, x, y, text)
                    }
                    result.fold(onSuccess = { returnResult() }, onFailure = { Toast.makeText(this@PdfPagePreviewActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show() })
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showRectangleDialog(x: Float, y: Float) {
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("⬜ 사각형")
            .setItems(arrayOf("작게", "중간", "크게")) { _, which ->
                val (w, h) = when (which) { 0 -> 100f to 50f; 1 -> 200f to 100f; else -> 300f to 150f }
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        PdfAnnotation.addRectangle(this@PdfPagePreviewActivity, file, pageIndex, x - w/2, y - h/2, w, h, Color.RED, 2f)
                    }
                    result.fold(onSuccess = { returnResult() }, onFailure = { Toast.makeText(this@PdfPagePreviewActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show() })
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showCircleDialog(x: Float, y: Float) {
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("⭕ 원")
            .setItems(arrayOf("작게", "중간", "크게")) { _, which ->
                val r = when (which) { 0 -> 30f; 1 -> 60f; else -> 100f }
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        PdfAnnotation.addCircle(this@PdfPagePreviewActivity, file, pageIndex, x, y, r, Color.RED, 2f)
                    }
                    result.fold(onSuccess = { returnResult() }, onFailure = { Toast.makeText(this@PdfPagePreviewActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show() })
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun makeEditText(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(0xFF888888.toInt())
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 15f
        setPadding(24, 20, 24, 20)
        setBackgroundColor(0xFF2A2A2A.toInt())
    }

    private fun returnResult() {
        setResult(Activity.RESULT_OK, Intent().apply { putExtra(RESULT_PAGE_INDEX, pageIndex) })
        finish()
    }

    private fun getPageSizePt(file: File, pageIndex: Int): Pair<Float, Float> {
        return try {
            PdfTextPicker.ensureInit(this)
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                val box = doc.getPage(pageIndex).mediaBox
                box.width to box.height
            }
        } catch (e: Exception) { 595f to 842f }
    }
}
