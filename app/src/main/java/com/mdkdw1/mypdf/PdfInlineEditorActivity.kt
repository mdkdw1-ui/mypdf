package com.mdkdw1.mypdf

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ⭐ 인라인 PDF 텍스트 편집기 (WYSIWYG)
 * - 페이지 위 글자를 탭 → 그 자리에 EditText 오버레이
 * - 입력 완료 시 원본 위치/크기 그대로 PDF 반영
 */
class PdfInlineEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "pdf_path"
        const val EXTRA_PAGE = "page_index"
        const val RESULT_PAGE_INDEX = "result_page_index"
        private const val CANVAS_WIDTH = 1600
    }

    private lateinit var file: File
    private var pageIndex = 0

    private lateinit var imageView: ImageView
    private lateinit var overlay: FrameLayout
    private lateinit var tvHint: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnUndo: Button

    private var pageBitmap: Bitmap? = null
    private var words: List<PdfTextPicker.Word> = emptyList()
    private var pageWidthPt = 595f
    private var pageHeightPt = 842f

    private var bitmapLeft = 0f
    private var bitmapTop = 0f
    private var bitmapScale = 1f

    private var currentEditView: EditText? = null
    private var currentEditWord: PdfTextPicker.Word? = null

    // 편집 이력 (순서 유지, word 단위 유일)
    private val appliedEdits = linkedMapOf<String, Pair<PdfTextPicker.Word, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        pageIndex = intent.getIntExtra(EXTRA_PAGE, 0)
        file = File(path)
        if (!file.exists()) { finish(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D1117.toInt())
        }

        // ── 상단 바 ──
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1A1F26.toInt())
            setPadding(8, 24, 8, 8)
            gravity = Gravity.CENTER_VERTICAL
        }
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(8, 24 + sb.top, 8, 8)
            insets
        }

        val btnClose = Button(this).apply {
            text = "\u2715"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(32, 20, 32, 20)
            setOnClickListener { confirmExit() }
        }

        tvStatus = TextView(this).apply {
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12, 0, 8, 0)
            text = "\ub85c\ub529 \uc911..."
        }

        btnUndo = Button(this).apply {
            text = "\u21b6"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(28, 20, 28, 20)
            setOnClickListener { undo() }
        }

        val btnSave = Button(this).apply {
            text = "\ud83d\udcbe \uc800\uc7a5"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFFF6B9D.toInt())
            setPadding(44, 30, 44, 30)
            setOnClickListener { saveAll() }
        }

        topBar.addView(btnClose)
        topBar.addView(tvStatus)
        topBar.addView(btnUndo)
        topBar.addView(btnSave)
        root.addView(topBar)

        // ── 캔버스 ──
        overlay = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF1A1A1A.toInt())
        }
        overlay.addView(imageView)

        tvHint = TextView(this).apply {
            text = "\ud83d\udc46 \uae00\uc790\ub97c \ud0ed\ud558\uba74 \uadf8 \uc790\ub9ac\uc5d0\uc11c \ud3b8\uc9d1"
            setTextColor(0xFFFF6B9D.toInt())
            textSize = 12f
            setBackgroundColor(0xCC000000.toInt())
            setPadding(20, 10, 20, 10)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 12
            }
        }
        overlay.addView(tvHint)
        root.addView(overlay)
        setContentView(root)

        imageView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBitmapLayout() }

        imageView.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) handleTap(e.x, e.y)
            true
        }

        lifecycleScope.launch { loadPage() }
    }

    private suspend fun loadPage() {
        tvStatus.text = "\ub85c\ub529 \uc911..."
        val (w, h) = withContext(Dispatchers.IO) { getPageSizePt(file, pageIndex) }
        pageWidthPt = w
        pageHeightPt = h

        val bmp = withContext(Dispatchers.IO) { renderPage(file, pageIndex, CANVAS_WIDTH) }
        if (bmp == null) {
            Toast.makeText(this, "\ud398\uc774\uc9c0 \ub85c\ub4dc \uc2e4\ud328", Toast.LENGTH_LONG).show()
            finish(); return
        }
        pageBitmap = bmp
        imageView.setImageBitmap(bmp)

        tvStatus.text = "\ud14d\uc2a4\ud2b8 \ubd84\uc11d \uc911..."
        val result = withContext(Dispatchers.IO) {
            PdfTextPicker.extractWordsWithSize(this@PdfInlineEditorActivity, file, pageIndex)
        }
        result.fold(
            onSuccess = { r ->
                words = r.words
                tvStatus.text = "\ud398\uc774\uc9c0 ${pageIndex + 1} \u00b7 ${words.size}\uac1c \ub2e8\uc5b4"
            },
            onFailure = { tvStatus.text = "\ud14d\uc2a4\ud2b8 \ucd94\ucd9c \uc2e4\ud328" }
        )
        imageView.post { updateBitmapLayout() }
    }

    private fun updateBitmapLayout() {
        val bmp = pageBitmap ?: return
        val vw = imageView.width.toFloat()
        val vh = imageView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val s = minOf(vw / bmp.width, vh / bmp.height)
        bitmapScale = s
        bitmapLeft = (vw - bmp.width * s) / 2f
        bitmapTop = (vh - bmp.height * s) / 2f
    }

    /** view px → PDF pt (top-origin, PdfTextPicker 좌표계) */
    private fun viewToPdf(vx: Float, vy: Float): Pair<Float, Float>? {
        val bmp = pageBitmap ?: return null
        val cx = (vx - bitmapLeft) / bitmapScale
        val cy = (vy - bitmapTop) / bitmapScale
        if (cx < 0 || cy < 0 || cx > bmp.width || cy > bmp.height) return null
        val pdfX = cx / bmp.width * pageWidthPt
        val pdfY = cy / bmp.height * pageHeightPt
        return pdfX to pdfY
    }

    private fun handleTap(viewX: Float, viewY: Float) {
        commitCurrentEdit()
        val (pdfX, pdfY) = viewToPdf(viewX, viewY) ?: return

        val nearest = words.minByOrNull { w ->
            val cx = w.x + w.width / 2
            val cy = w.y + w.height / 2
            val dx = cx - pdfX
            val dy = cy - pdfY
            kotlin.math.sqrt(dx * dx + dy * dy)
        } ?: return

        val cx = nearest.x + nearest.width / 2
        val cy = nearest.y + nearest.height / 2
        val dx = cx - pdfX
        val dy = cy - pdfY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist > 40f) {
            Toast.makeText(this, "\uae00\uc790 \uadfc\ucc98\ub97c \ud0ed\ud558\uc138\uc694", Toast.LENGTH_SHORT).show()
            return
        }
        showInlineEdit(nearest)
    }

    private fun showInlineEdit(word: PdfTextPicker.Word) {
        val bmp = pageBitmap ?: return
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()

        val vx = word.x / pageWidthPt * bw * bitmapScale + bitmapLeft
        val vy = word.y / pageHeightPt * bh * bitmapScale + bitmapTop
        val vw = word.width / pageWidthPt * bw * bitmapScale
        val vh = word.height / pageHeightPt * bh * bitmapScale

        val screenW = resources.displayMetrics.widthPixels
        val editW = (vw + 80f).toInt().coerceIn(160, screenW - 32)

        val et = EditText(this).apply {
            setText(word.text)
            setSelection(word.text.length)
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, (vh * 0.85f).coerceIn(24f, 80f))
            setPadding(16, 8, 16, 8)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = FrameLayout.LayoutParams(
                editW,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (vx - 16f).toInt().coerceIn(8, screenW - editW - 8)
                topMargin = (vy - 8f).toInt().coerceAtLeast(8)
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitCurrentEdit(); true
                } else false
            }
        }

        currentEditView = et
        currentEditWord = word
        overlay.addView(et)
        et.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
        tvHint.visibility = View.GONE
    }

    private fun commitCurrentEdit() {
        val et = currentEditView ?: return
        val word = currentEditWord
        val newText = et.text.toString()

        currentEditView = null
        currentEditWord = null
        overlay.removeView(et)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(et.windowToken, 0)
        tvHint.visibility = View.VISIBLE

        if (word == null) return
        if (newText == word.text) {
            // 원본과 동일 → 이력 제거
            appliedEdits.remove(keyOf(word))
        } else {
            appliedEdits[keyOf(word)] = word to newText
        }
        updateStatus()
    }

    private fun keyOf(w: PdfTextPicker.Word): String =
        "${w.x}_${w.y}_${w.text}"

    private fun updateStatus() {
        tvStatus.text = if (appliedEdits.isEmpty())
            "\ud398\uc774\uc9c0 ${pageIndex + 1} \u00b7 ${words.size}\uac1c \ub2e8\uc5b4"
        else
            "\ud398\uc774\uc9c0 ${pageIndex + 1} \u00b7 \ud3b8\uc9d1 ${appliedEdits.size}\uac1c (\ubbf8\uc800\uc7a5)"
    }

    private fun undo() {
        if (appliedEdits.isEmpty()) {
            Toast.makeText(this, "\ucde8\uc18c\ud560 \ud3b8\uc9d1 \uc5c6\uc74c", Toast.LENGTH_SHORT).show()
            return
        }
        val lastKey = appliedEdits.keys.last()
        appliedEdits.remove(lastKey)
        updateStatus()
    }

    private fun saveAll() {
        commitCurrentEdit()
        if (appliedEdits.isEmpty()) {
            Toast.makeText(this, "\ubcc0\uacbd\uc0ac\ud56d \uc5c6\uc74c", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("\uc800\uc7a5")
            .setMessage("${appliedEdits.size}\uac1c \ubcc0\uacbd\uc0ac\ud56d\uc744 \uc800\uc7a5\ud560\uae4c\uc694?")
            .setPositiveButton("\uc800\uc7a5") { _, _ -> performSave() }
            .setNegativeButton("\ucde8\uc18c", null)
            .show()
    }

    private fun performSave() {
        lifecycleScope.launch {
            Toast.makeText(this@PdfInlineEditorActivity, "\uc800\uc7a5 \uc911...", Toast.LENGTH_SHORT).show()
            val result = withContext(Dispatchers.IO) {
                val edits = appliedEdits.values.mapIndexed { idx, (w, txt) ->
                    idx to (w to txt)
                }.toMap()
                PdfTextEditor.replaceMultiple(
                    context = this@PdfInlineEditorActivity,
                    file = file,
                    pageIndex = pageIndex,
                    edits = edits
                )
            }
            result.fold(
                onSuccess = {
                    Toast.makeText(this@PdfInlineEditorActivity,
                        "\u2705 ${appliedEdits.size}\uac1c \uc800\uc7a5\ub428", Toast.LENGTH_LONG).show()
                    appliedEdits.clear()
                    loadPage()
                    setResult(Activity.RESULT_OK, Intent().apply {
                        putExtra(RESULT_PAGE_INDEX, pageIndex)
                    })
                },
                onFailure = {
                    Toast.makeText(this@PdfInlineEditorActivity,
                        "\u274c ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun confirmExit() {
        commitCurrentEdit()
        if (appliedEdits.isEmpty()) { finish(); return }
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("\uc885\ub8cc")
            .setMessage("\uc800\uc7a5\ud558\uc9c0 \uc54a\uc740 \ud3b8\uc9d1 ${appliedEdits.size}\uac1c\uac00 \uc788\uc2b5\ub2c8\ub2e4.\n\uc885\ub8cc\ud560\uae4c\uc694?")
            .setPositiveButton("\uc885\ub8cc") { _, _ -> finish() }
            .setNegativeButton("\ucde8\uc18c", null)
            .show()
    }

    private fun renderPage(f: File, idx: Int, targetW: Int): Bitmap? = try {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(pfd).use { r ->
            if (idx < 0 || idx >= r.pageCount) null
            else r.openPage(idx).use { page ->
                val s = targetW.toFloat() / page.width
                val h = (page.height * s).toInt()
                val bmp = Bitmap.createBitmap(targetW, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    } catch (e: Exception) { null }

    private fun getPageSizePt(f: File, idx: Int): Pair<Float, Float> = try {
        PdfTextPicker.ensureInit(this)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(f).use { doc ->
            val box = doc.getPage(idx).mediaBox
            box.width to box.height
        }
    } catch (e: Exception) { 595f to 842f }
}
