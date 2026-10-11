package com.mdkdw1.mypdf

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.chrisbanes.photoview.PhotoView
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

    private lateinit var photoView: PhotoView
    private lateinit var tvHint: TextView
    private var pageBitmap: Bitmap? = null
    private var pageWidthPt = 595f
    private var pageHeightPt = 842f
    private var pageIndex = 0
    private var mode = MODE_PREVIEW
    private lateinit var file: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        pageIndex = intent.getIntExtra(EXTRA_PAGE, 0)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PREVIEW
        file = File(path)
        if (!file.exists()) { finish(); return }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#0D1117"))

        photoView = PhotoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // ⭐ FIT_CENTER: 이미지 중앙 배치 (핀치 초점 정상화)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            minimumScale = 1f
            mediumScale = 2f
            maximumScale = 8f
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        root.addView(photoView)

        // 상단 (닫기 + 제목 + 힌트)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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

        topRow.addView(btnClose)
        topRow.addView(tvTitle)
        topBar.addView(topRow)

        // ⭐ 모드별 힌트
        tvHint = TextView(this).apply {
            setTextColor(0xFFFF6B9D.toInt())
            textSize = 12f
            setPadding(0, 8, 0, 0)
            text = when (mode) {
                MODE_ADD_TEXT -> "👆 텍스트를 넣을 위치를 탭하세요"
                MODE_HIGHLIGHT -> "👆 하이라이트할 위치를 탭하세요"
                MODE_STICKY -> "👆 메모를 붙일 위치를 탭하세요"
                MODE_RECTANGLE -> "👆 사각형 시작 위치를 탭하세요"
                MODE_CIRCLE -> "👆 원의 중심 위치를 탭하세요"
                else -> ""
            }
            visibility = if (mode == MODE_PREVIEW) android.view.View.GONE
                         else android.view.View.VISIBLE
        }
        topBar.addView(tvHint)

        root.addView(topBar)
        setContentView(root)

        // ⭐ 페이지 크기 계산 (PdfRenderer)
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                renderPage(file, pageIndex, 2400)
            }
            if (bmp != null) {
                pageBitmap = bmp
                photoView.setZoomable(true)
                photoView.setImageBitmap(bmp)

                // PDF pt 크기 계산 (비율 기반)
                val (w, h) = withContext(Dispatchers.IO) {
                    getPageSizePt(file, pageIndex)
                }
                pageWidthPt = w
                pageHeightPt = h
            }
        }

        // ⭐ 탭 위치 감지 (PhotoView OnPhotoTapListener)
        photoView.setOnPhotoTapListener { _, xRatio, yRatio ->
            if (mode == MODE_PREVIEW) return@setOnPhotoTapListener
            handleTap(xRatio, yRatio)
        }
    }

    private fun handleTap(xRatio: Float, yRatio: Float) {
        // 이미지 픽셀 좌표
        val bmp = pageBitmap ?: return
        val imgX = xRatio * bmp.width
        val imgY = yRatio * bmp.height

        // PDF pt 좌표 (좌하단 기준)
        val pdfX = xRatio * pageWidthPt
        val pdfY = (1f - yRatio) * pageHeightPt

        when (mode) {
            MODE_ADD_TEXT -> showAddTextDialog(pdfX, pdfY, imgX, imgY)
            MODE_HIGHLIGHT -> performHighlight(pdfX, pdfY)
            MODE_STICKY -> showStickyDialog(pdfX, pdfY)
            MODE_RECTANGLE -> showRectangleDialog(pdfX, pdfY)
            MODE_CIRCLE -> showCircleDialog(pdfX, pdfY)
        }
    }

    private fun showAddTextDialog(pdfX: Float, pdfY: Float, imgX: Float, imgY: Float) {
        val input = makeStyledEditText("텍스트 입력").apply {
            minLines = 2
        }
        val sizeInput = makeStyledEditText("폰트 크기 (pt)").apply {
            setText("14")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            addView(TextView(this@PdfPagePreviewActivity).apply {
                text = "위치: (${"%.0f".format(pdfX)}, ${"%.0f".format(pdfY)}) pt"
                setTextColor(0xFF666666.toInt())
                textSize = 11f
                setPadding(0, 0, 0, 12)
            })
            addView(input)
            addView(TextView(this@PdfPagePreviewActivity).apply {
                text = "폰트 크기:"
                setTextColor(0xFF888888.toInt())
                textSize = 11f
                setPadding(0, 12, 0, 4)
            })
            addView(sizeInput)
        }

        AlertDialog.Builder(this)
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
            Toast.makeText(this@PdfPagePreviewActivity, "저장 중...", Toast.LENGTH_SHORT).show()
            val result = withContext(Dispatchers.IO) {
                PdfTextEditor.addText(
                    this@PdfPagePreviewActivity, file, pageIndex,
                    x, y, text, fontSize, Color.BLACK
                )
            }
            result.fold(
                onSuccess = {
                    Toast.makeText(this@PdfPagePreviewActivity, "✅ 추가됨", Toast.LENGTH_SHORT).show()
                    returnResult()
                },
                onFailure = {
                    Toast.makeText(this@PdfPagePreviewActivity,
                        "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun performHighlight(x: Float, y: Float) {
        AlertDialog.Builder(this)
            .setTitle("🖍 하이라이트")
            .setItems(arrayOf("노랑", "초록", "핑크", "파랑")) { _, which ->
                val color = when (which) {
                    0 -> Color.YELLOW
                    1 -> Color.GREEN
                    2 -> 0xFFFF6B9D.toInt()
                    else -> Color.CYAN
                }
                lifecycleScope.launch {
                    Toast.makeText(this@PdfPagePreviewActivity, "저장 중...", Toast.LENGTH_SHORT).show()
                    val result = withContext(Dispatchers.IO) {
                        PdfAnnotation.addHighlight(
                            this@PdfPagePreviewActivity, file, pageIndex,
                            x - 50f, y - 10f, 100f, 20f, color, 0.4f
                        )
                    }
                    result.fold(
                        onSuccess = {
                            Toast.makeText(this@PdfPagePreviewActivity, "✅ 추가됨", Toast.LENGTH_SHORT).show()
                            returnResult()
                        },
                        onFailure = {
                            Toast.makeText(this@PdfPagePreviewActivity,
                                "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showStickyDialog(x: Float, y: Float) {
        val input = makeStyledEditText("메모 내용").apply {
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle("📌 스티키 노트")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    Toast.makeText(this@PdfPagePreviewActivity, "저장 중...", Toast.LENGTH_SHORT).show()
                    val result = withContext(Dispatchers.IO) {
                        PdfAnnotation.addStickyNote(
                            this@PdfPagePreviewActivity, file, pageIndex, x, y, text
                        )
                    }
                    result.fold(
                        onSuccess = {
                            Toast.makeText(this@PdfPagePreviewActivity, "✅ 추가됨", Toast.LENGTH_SHORT).show()
                            returnResult()
                        },
                        onFailure = {
                            Toast.makeText(this@PdfPagePreviewActivity,
                                "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showRectangleDialog(x: Float, y: Float) {
        AlertDialog.Builder(this)
            .setTitle("⬜ 사각형 크기")
            .setItems(arrayOf("작게 (100x50)", "중간 (200x100)", "크게 (300x150)")) { _, which ->
                val (w, h) = when (which) {
                    0 -> 100f to 50f
                    1 -> 200f to 100f
                    else -> 300f to 150f
                }
                lifecycleScope.launch {
                    Toast.makeText(this@PdfPagePreviewActivity, "저장 중...", Toast.LENGTH_SHORT).show()
                    val result = withContext(Dispatchers.IO) {
                        PdfAnnotation.addRectangle(
                            this@PdfPagePreviewActivity, file, pageIndex,
                            x - w/2, y - h/2, w, h, Color.RED, 2f
                        )
                    }
                    result.fold(
                        onSuccess = { returnResult() },
                        onFailure = {
                            Toast.makeText(this@PdfPagePreviewActivity,
                                "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showCircleDialog(x: Float, y: Float) {
        AlertDialog.Builder(this)
            .setTitle("⭕ 원 크기")
            .setItems(arrayOf("작게 (r=30)", "중간 (r=60)", "크게 (r=100)")) { _, which ->
                val r = when (which) {
                    0 -> 30f
                    1 -> 60f
                    else -> 100f
                }
                lifecycleScope.launch {
                    Toast.makeText(this@PdfPagePreviewActivity, "저장 중...", Toast.LENGTH_SHORT).show()
                    val result = withContext(Dispatchers.IO) {
                        PdfAnnotation.addCircle(
                            this@PdfPagePreviewActivity, file, pageIndex,
                            x, y, r, Color.RED, 2f
                        )
                    }
                    result.fold(
                        onSuccess = { returnResult() },
                        onFailure = {
                            Toast.makeText(this@PdfPagePreviewActivity,
                                "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun makeStyledEditText(hint: String = ""): EditText {
        return EditText(this).apply {
            this.hint = hint
            setHintTextColor(0xFF888888.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF2A2A2A.toInt())
        }
    }

    private fun returnResult() {
        val data = Intent().apply {
            putExtra(RESULT_PAGE_INDEX, pageIndex)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    // ═══════════════ 렌더링 ═══════════════

    private fun renderPage(file: File, pageIndex: Int, targetWidth: Int): Bitmap? {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val scale = targetWidth.toFloat() / page.width
                    val h = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        } catch (e: Exception) { null }
    }

    private fun getPageSizePt(file: File, pageIndex: Int): Pair<Float, Float> {
        return try {
            PdfTextPicker.ensureInit(this)
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                val box = page.mediaBox
                box.width to box.height
            }
        } catch (e: Exception) {
            595f to 842f
        }
    }
}
