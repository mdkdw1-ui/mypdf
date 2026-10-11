package com.mdkdw1.mypdf

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ⭐ 이미지 기반 페이지 편집기 (그림판 스타일)
 * - 원본 페이지를 Canvas에 그려놓고 그림
 * - 저장 시 이미지 + 숨은 텍스트 레이어 PDF로 재생성
 */
class PdfPageEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "pdf_path"
        const val EXTRA_PAGE = "page_index"
        const val RESULT_PAGE_INDEX = "result_page_index"
        private const val CANVAS_WIDTH = 2400  // 렌더 해상도
    }

    // 도구
    private enum class Tool { PEN, HIGHLIGHTER, TEXT, RECT, CIRCLE, ERASER }

    private var currentTool = Tool.PEN
    private var currentColor = Color.RED
    private var strokeWidth = 4f

    // 그리기 데이터
    private val drawActions = mutableListOf<DrawAction>()
    private val undoneActions = mutableListOf<DrawAction>()

    // 상태
    private var baseBitmap: Bitmap? = null      // 원본 페이지 이미지
    private var workBitmap: Bitmap? = null      // 현재 캔버스 (base + 그림)
    private var canvasWidth = 0
    private var canvasHeight = 0
    private var pageWidthPt = 595f
    private var pageHeightPt = 842f

    // 진행 중인 액션
    private var currentPath: Path? = null
    private var currentStart: PointF? = null
    private var currentEnd: PointF? = null
    private var isDrawing = false

    // 뷰
    private lateinit var imageView: ImageView
    private lateinit var tvHint: TextView
    private lateinit var file: File
    private var pageIndex = 0

    // 추가된 텍스트 (검색용)
    private val addedTexts = mutableListOf<AddedText>()

    data class AddedText(val text: String, val x: Float, val y: Float, val fontSize: Float)

    sealed class DrawAction {
        data class Stroke(val path: Path, val color: Int, val width: Float, val alpha: Int) : DrawAction()
        data class Rect(val rect: RectF, val color: Int, val width: Float) : DrawAction()
        data class Circle(val rect: RectF, val color: Int, val width: Float) : DrawAction()
        data class Text(val text: String, val x: Float, val y: Float, val color: Int, val size: Float) : DrawAction()
    }

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

        // ⭐ 상단: 저장 + Undo/Redo
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1A1F26.toInt())
            setPadding(8, 24, 8, 8)
            gravity = Gravity.CENTER_VERTICAL
        }
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(8, 24 + statusBar.top, 8, 8)
            insets
        }

        val btnClose = Button(this).apply {
            text = "✕"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(32, 20, 32, 20)
            setOnClickListener {
                AlertDialog.Builder(this@PdfPageEditorActivity, R.style.DarkDialogTheme)
                    .setTitle("종료")
                    .setMessage("저장하지 않은 변경사항은 사라집니다")
                    .setPositiveButton("종료") { _, _ -> finish() }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }

        val btnUndo = Button(this).apply {
            text = "↶"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(32, 20, 32, 20)
            setOnClickListener { undo() }
        }

        val btnRedo = Button(this).apply {
            text = "↷"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(32, 20, 32, 20)
            setOnClickListener { redo() }
        }

        val btnSave = Button(this).apply {
            text = "💾 저장"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFFF6B9D.toInt())
            setPadding(48, 30, 48, 30)
            setOnClickListener { saveEdits() }
        }

        topBar.addView(btnClose)
        topBar.addView(btnUndo)
        topBar.addView(btnRedo)
        topBar.addView(Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        })
        topBar.addView(btnSave)
        root.addView(topBar)

        // ⭐ 캔버스 영역
        val canvasContainer = FrameLayout(this).apply {
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
        canvasContainer.addView(imageView)

        tvHint = TextView(this).apply {
            text = "✏️ 펜 모드 — 그려보세요"
            setTextColor(0xFFFF6B9D.toInt())
            textSize = 11f
            setBackgroundColor(0xCC000000.toInt())
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.START; setMargins(8, 8, 8, 8) }
        }
        canvasContainer.addView(tvHint)

        root.addView(canvasContainer)

        // ⭐ 하단 도구 바
        val toolsBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1A1F26.toInt())
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER
        }
        ViewCompat.setOnApplyWindowInsetsListener(toolsBar) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(8, 8, 8, 8 + nav.bottom)
            insets
        }

        fun toolButton(icon: String, tool: Tool, label: String): Button {
            return Button(this).apply {
                text = icon
                textSize = 20f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0)
                setPadding(32, 20, 32, 20)
                setOnClickListener {
                    currentTool = tool
                    tvHint.text = label
                    updateToolButtons(toolsBar, tool)
                }
            }
        }

        val btnPen = toolButton("✏️", Tool.PEN, "✏️ 펜")
        val btnHighlighter = toolButton("🖍", Tool.HIGHLIGHTER, "🖍 형광펜")
        val btnText = toolButton("📝", Tool.TEXT, "📝 탭하여 텍스트")
        val btnRect = toolButton("⬜", Tool.RECT, "⬜ 사각형")
        val btnCircle = toolButton("⭕", Tool.CIRCLE, "⭕ 원")
        val btnColor = Button(this).apply {
            text = "🎨"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(32, 20, 32, 20)
            setOnClickListener { showColorPicker() }
        }

        toolsBar.addView(btnPen)
        toolsBar.addView(btnHighlighter)
        toolsBar.addView(btnText)
        toolsBar.addView(btnRect)
        toolsBar.addView(btnCircle)
        toolsBar.addView(btnColor)
        root.addView(toolsBar)

        setContentView(root)

        // ⭐ 초기 로드
        lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) { getPageSizePt(file, pageIndex) }
            pageWidthPt = size.first
            pageHeightPt = size.second

            val bmp = withContext(Dispatchers.IO) { renderPage(file, pageIndex, CANVAS_WIDTH) }
            if (bmp != null) {
                baseBitmap = bmp
                canvasWidth = bmp.width
                canvasHeight = bmp.height
                workBitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
                imageView.setImageBitmap(workBitmap)
            } else {
                Toast.makeText(this@PdfPageEditorActivity,
                    "렌더 실패", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        // ⭐ 터치 처리
        imageView.setOnTouchListener { view, event -> handleTouch(view, event) }
    }

    private fun updateToolButtons(container: LinearLayout, active: Tool) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is Button) {
                child.setBackgroundColor(0)
            }
        }
        // 활성 버튼만 핑크
        val idx = when (active) {
            Tool.PEN -> 0; Tool.HIGHLIGHTER -> 1; Tool.TEXT -> 2
            Tool.RECT -> 3; Tool.CIRCLE -> 4; else -> -1
        }
        if (idx >= 0 && idx < container.childCount) {
            (container.getChildAt(idx) as? Button)?.setBackgroundColor(0xFFFF6B9D.toInt())
        }
    }

    // ⭐ 좌표 변환: 뷰 → 캔버스 픽셀
    private fun viewToCanvas(viewX: Float, viewY: Float): PointF? {
        val bmp = workBitmap ?: return null
        val vw = imageView.width.toFloat()
        val vh = imageView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return null

        // FIT_CENTER 계산
        val scale = minOf(vw / bmp.width, vh / bmp.height)
        val dispW = bmp.width * scale
        val dispH = bmp.height * scale
        val left = (vw - dispW) / 2f
        val top = (vh - dispH) / 2f

        val cx = (viewX - left) / scale
        val cy = (viewY - top) / scale

        if (cx < 0 || cy < 0 || cx > bmp.width || cy > bmp.height) return null
        return PointF(cx, cy)
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val pt = viewToCanvas(event.x, event.y) ?: return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                when (currentTool) {
                    Tool.PEN, Tool.HIGHLIGHTER -> {
                        currentPath = Path().apply { moveTo(pt.x, pt.y) }
                        isDrawing = true
                    }
                    Tool.RECT, Tool.CIRCLE -> {
                        currentStart = pt
                        currentEnd = pt
                        isDrawing = true
                    }
                    Tool.TEXT -> {
                        // 텍스트 위치 → 다이얼로그
                        showTextInputDialog(pt.x, pt.y)
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDrawing) return true
                when (currentTool) {
                    Tool.PEN, Tool.HIGHLIGHTER -> {
                        currentPath?.lineTo(pt.x, pt.y)
                        redrawCanvas()
                    }
                    Tool.RECT, Tool.CIRCLE -> {
                        currentEnd = pt
                        redrawCanvas()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDrawing) return true
                when (currentTool) {
                    Tool.PEN -> {
                        currentPath?.let {
                            drawActions.add(DrawAction.Stroke(
                                Path(it), currentColor, strokeWidth, 255
                            ))
                            undoneActions.clear()
                        }
                        currentPath = null
                    }
                    Tool.HIGHLIGHTER -> {
                        currentPath?.let {
                            drawActions.add(DrawAction.Stroke(
                                Path(it),
                                0xFFFFEB3B.toInt(),
                                strokeWidth * 4f,
                                100
                            ))
                            undoneActions.clear()
                        }
                        currentPath = null
                    }
                    Tool.RECT -> {
                        val s = currentStart
                        val e = currentEnd
                        if (s != null && e != null && (kotlin.math.abs(s.x - e.x) > 5 || kotlin.math.abs(s.y - e.y) > 5)) {
                            drawActions.add(DrawAction.Rect(
                                RectF(minOf(s.x, e.x), minOf(s.y, e.y), maxOf(s.x, e.x), maxOf(s.y, e.y)),
                                currentColor, strokeWidth
                            ))
                            undoneActions.clear()
                        }
                        currentStart = null; currentEnd = null
                    }
                    Tool.CIRCLE -> {
                        val s = currentStart
                        val e = currentEnd
                        if (s != null && e != null && (kotlin.math.abs(s.x - e.x) > 5 || kotlin.math.abs(s.y - e.y) > 5)) {
                            drawActions.add(DrawAction.Circle(
                                RectF(minOf(s.x, e.x), minOf(s.y, e.y), maxOf(s.x, e.x), maxOf(s.y, e.y)),
                                currentColor, strokeWidth
                            ))
                            undoneActions.clear()
                        }
                        currentStart = null; currentEnd = null
                    }
                }
                isDrawing = false
                redrawCanvas()
                return true
            }
        }
        return true
    }

    private fun redrawCanvas() {
        val base = baseBitmap ?: return
        val work = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(work)

        // 저장된 액션 재생
        for (action in drawActions) {
            drawAction(canvas, action)
        }

        // 진행 중인 액션
        currentPath?.let { p ->
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                if (currentTool == Tool.HIGHLIGHTER) {
                    color = 0xFFFFEB3B.toInt()
                    alpha = 100
                    strokeWidth = this@PdfPageEditorActivity.strokeWidth * 4f
                } else {
                    color = currentColor
                    this.strokeWidth = this@PdfPageEditorActivity.strokeWidth
                }
            }
            canvas.drawPath(p, paint)
        }

        currentStart?.let { s ->
            currentEnd?.let { e ->
                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    color = currentColor
                    strokeWidth = this@PdfPageEditorActivity.strokeWidth
                }
                val rect = RectF(minOf(s.x, e.x), minOf(s.y, e.y), maxOf(s.x, e.x), maxOf(s.y, e.y))
                if (currentTool == Tool.RECT) canvas.drawRect(rect, paint)
                if (currentTool == Tool.CIRCLE) canvas.drawOval(rect, paint)
            }
        }

        workBitmap = work
        imageView.setImageBitmap(work)
    }

    private fun drawAction(canvas: Canvas, action: DrawAction) {
        when (action) {
            is DrawAction.Stroke -> {
                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = action.color
                    strokeWidth = action.width
                    alpha = action.alpha
                }
                canvas.drawPath(action.path, paint)
            }
            is DrawAction.Rect -> {
                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    color = action.color
                    strokeWidth = action.width
                }
                canvas.drawRect(action.rect, paint)
            }
            is DrawAction.Circle -> {
                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    color = action.color
                    strokeWidth = action.width
                }
                canvas.drawOval(action.rect, paint)
            }
            is DrawAction.Text -> {
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = action.color
                    textSize = action.size
                    typeface = Typeface.DEFAULT
                }
                canvas.drawText(action.text, action.x, action.y, paint)
            }
        }
    }

    private fun showTextInputDialog(x: Float, y: Float) {
        val input = EditText(this).apply {
            hint = "텍스트"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF2A2A2A.toInt())
        }
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("📝 텍스트 입력")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                val fontSize = strokeWidth * 6f + 30f
                drawActions.add(DrawAction.Text(text, x, y, currentColor, fontSize))
                undoneActions.clear()

                // 검색용 (나중에 invisible layer)
                val pdfX = x / canvasWidth * pageWidthPt
                val pdfY = (1f - y / canvasHeight) * pageHeightPt
                addedTexts.add(PdfRasterizer.TextOverlay(
                    text = text,
                    x = pdfX,
                    y = pdfY,
                    fontSize = fontSize / canvasWidth * pageWidthPt
                ))

                redrawCanvas()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showColorPicker() {
        val colors = arrayOf(
            "🔴 빨강" to Color.RED,
            "⚫ 검정" to Color.BLACK,
            "🔵 파랑" to Color.BLUE,
            "🟢 초록" to Color.GREEN,
            "🟡 노랑" to Color.YELLOW,
            "🟣 보라" to Color.MAGENTA
        )
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("🎨 색상 선택")
            .setItems(colors.map { it.first }.toTypedArray()) { _, which ->
                currentColor = colors[which].second
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun undo() {
        if (drawActions.isEmpty()) {
            Toast.makeText(this, "취소할 작업 없음", Toast.LENGTH_SHORT).show()
            return
        }
        val last = drawActions.removeAt(drawActions.size - 1)
        undoneActions.add(last)
        // 텍스트 액션이면 addedTexts도 제거
        if (last is DrawAction.Text) {
            addedTexts.removeAll { it.text == last.text }
        }
        redrawCanvas()
    }

    private fun redo() {
        if (undoneActions.isEmpty()) {
            Toast.makeText(this, "재실행할 작업 없음", Toast.LENGTH_SHORT).show()
            return
        }
        val action = undoneActions.removeAt(undoneActions.size - 1)
        drawActions.add(action)
        redrawCanvas()
    }

    private fun saveEdits() {
        val work = workBitmap ?: run {
            Toast.makeText(this, "이미지 없음", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("💾 저장")
            .setMessage("편집한 페이지를 저장할까요?\n\n" +
                       "• 원본 이미지 위에 그림\n" +
                       "• 텍스트 검색 가능 (숨겨진 레이어)")
            .setPositiveButton("저장") { _, _ ->
                performSave(work)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performSave(work: Bitmap) {
        lifecycleScope.launch {
            Toast.makeText(this@PdfPageEditorActivity, "저장 중...", Toast.LENGTH_SHORT).show()

            val result = withContext(Dispatchers.IO) {
                PdfRasterizer.replacePageWithImage(
                    context = this@PdfPageEditorActivity,
                    sourcePdf = file,
                    pageIndex = pageIndex,
                    editedBitmap = work,
                    extraTexts = addedTexts,
                    outputPdf = file
                )
            }

            result.fold(
                onSuccess = {
                    Toast.makeText(this@PdfPageEditorActivity,
                        "✅ 저장됨", Toast.LENGTH_SHORT).show()
                    val data = Intent().apply { putExtra(RESULT_PAGE_INDEX, pageIndex) }
                    setResult(Activity.RESULT_OK, data)
                    finish()
                },
                onFailure = {
                    Toast.makeText(this@PdfPageEditorActivity,
                        "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // ═══════════════ 렌더/사이즈 ═══════════════

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
        } catch (e: Exception) { 595f to 842f }
    }
}
