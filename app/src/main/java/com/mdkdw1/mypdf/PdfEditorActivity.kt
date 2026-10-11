package com.mdkdw1.mypdf

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "pdf_path"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PdfEditPageAdapter
    private lateinit var tvFileName: TextView
    private lateinit var tvPageCount: TextView
    private lateinit var session: PdfEditSession
    private lateinit var file: File
    private lateinit var prefs: ReadingPrefs

    private val thumbnails = mutableMapOf<Int, Bitmap?>()
    private var currentPageIndex = 0

    // ⭐ 미리보기 결과 런처
    private val previewLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val idx = result.data?.getIntExtra(
                PdfPagePreviewActivity.RESULT_PAGE_INDEX, -1) ?: -1
            if (idx >= 0) {
                refreshThumb(idx)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        file = File(path)
        if (!file.exists()) { finish(); return }

        prefs = ReadingPrefs(this)
        session = PdfEditSession(this, file)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#1A1A1A"))

        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@PdfEditorActivity, 3)
            setPadding(8, 8, 8, 8)
            clipToPadding = false
            // ⭐ 스크롤 성능 최적화
            setHasFixedSize(true)
            itemAnimator = null
            setItemViewCacheSize(20)
            isNestedScrollingEnabled = false
        }
        recyclerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(recyclerView)

        // 상단 툴바
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xEE000000.toInt())
            setPadding(8, 24, 8, 16)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        }

        // ⭐ 상단 WindowInsets (상태바 겹침 방지)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(8, 24 + statusBar.top, 8, 16)
            insets
        }

        val btnClose = Button(this).apply {
            text = "✕"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setOnClickListener { confirmExit() }
        }

        tvFileName = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 0, 8, 0)
            text = "📝 ${file.name}"
        }

        val btnMore = Button(this).apply {
            text = "⋮"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(32, 16, 32, 16)
            setOnClickListener { showMoreMenu() }
        }

        toolbar.addView(btnClose)
        toolbar.addView(tvFileName)
        toolbar.addView(btnMore)
        root.addView(toolbar)

        // 하단 바
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

        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(8, 16, 8, 16 + nav.bottom)
            insets
        }

        tvPageCount = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f)
            text = "로딩..."
        }

        val btnAddBlank = Button(this).apply {
            text = "➕"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(40, 30, 40, 30)
            setOnClickListener { addBlankPage() }
        }

        val btnAnnotate = Button(this).apply {
            text = "🖍"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(40, 30, 40, 30)
            setOnClickListener { showAnnotationMenu() }
        }

        val btnWatermark = Button(this).apply {
            text = "💧"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(40, 30, 40, 30)
            setOnClickListener { showWatermarkDialog() }
        }

        // ⭐ 저장 버튼 (핑크 강조, 크게)
        val btnSaveBottom = Button(this).apply {
            text = "💾 저장"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFFF6B9D.toInt())
            setPadding(48, 40, 48, 40)
            setOnClickListener { saveDocument() }
        }

        bottomBar.addView(tvPageCount)
        bottomBar.addView(btnAddBlank)
        bottomBar.addView(btnAnnotate)
        bottomBar.addView(btnWatermark)
        bottomBar.addView(btnSaveBottom)
        root.addView(bottomBar)

        setContentView(root)

        lifecycleScope.launch {
            val result = session.open()
            if (result.isFailure) {
                Toast.makeText(this@PdfEditorActivity,
                    "❌ PDF 열기 실패: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            loadThumbnails()
        }

        setupDragAndDrop()
    }

    private suspend fun loadThumbnails() {
        val pageInfos = session.getPageInfos()
        thumbnails.clear()

        for ((idx, page) in pageInfos.withIndex()) {
            val thumb = session.renderThumbnail(page.originalIndex, 300, true)
            thumbnails[page.originalIndex] = thumb

            withContext(Dispatchers.Main) {
                tvPageCount.text = "${idx + 1} / ${pageInfos.size}"
            }
        }

        withContext(Dispatchers.Main) {
            refreshAdapter()
        }
    }

    /**
     * ⭐ 다크 테마용 EditText 스타일
     */
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

    /**
     * ⭐ 다크 테마용 TextView 스타일
     */
    private fun makeStyledTextView(text: String = "", size: Float = 12f): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0xFFCCCCCC.toInt())
            textSize = size
            setPadding(0, 8, 0, 8)
        }
    }

    private fun refreshAdapter() {
        val pageInfos = session.getPageInfos()
        adapter = PdfEditPageAdapter(
            pages = pageInfos,
            thumbnails = thumbnails,
            onPageClick = { originalIndex ->
                currentPageIndex = pageInfos.indexOfFirst { it.originalIndex == originalIndex }
                showPageMenu(originalIndex)
            },
            onPageLongClick = { originalIndex ->
                // ⭐ 롱프레스 → 순수 미리보기
                val intent = Intent(this@PdfEditorActivity, PdfPagePreviewActivity::class.java).apply {
                    putExtra(PdfPagePreviewActivity.EXTRA_PATH, file.absolutePath)
                    putExtra(PdfPagePreviewActivity.EXTRA_PAGE, originalIndex)
                    putExtra(PdfPagePreviewActivity.EXTRA_MODE, PdfPagePreviewActivity.MODE_PREVIEW)
                }
                previewLauncher.launch(intent)
            }
        )
        recyclerView.adapter = adapter
        tvPageCount.text = "${pageInfos.size} 페이지"
    }

    /** ⭐ 드래그 앤 드롭으로 페이지 재배치 */
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = t.bindingAdapterPosition
                if (from < 0 || to < 0) return false

                lifecycleScope.launch {
                    val result = session.movePage(from, to)
                    if (result.isSuccess) {
                        refreshAdapter()
                    }
                }
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = true
        }

        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun showPageMenu(originalIndex: Int) {
        val currentPos = session.getPageInfos().indexOfFirst { it.originalIndex == originalIndex }
        if (currentPos < 0) return

        // ⭐ 아이콘 그리드 메뉴
        val items = listOf(
            MenuItem("✏️", "텍스트 편집", 0),
            MenuItem("➕", "텍스트 추가", 1),
            MenuItem("🖍", "하이라이트", 2),
            MenuItem("📌", "스티키 노트", 3),
            MenuItem("⬜", "사각형", 4),
            MenuItem("⭕", "원", 5),
            MenuItem("🔄", "오른쪽 회전", 6),
            MenuItem("🔄", "왼쪽 회전", 7),
            MenuItem("⬅", "왼쪽 이동", 8),
            MenuItem("➡", "오른쪽 이동", 9),
            MenuItem("📄", "추출", 10),
            MenuItem("🗑", "삭제", 11, isDanger = true)
        )

        val gridView = GridView(this).apply {
            numColumns = 4
            adapter = MenuGridAdapter(this@PdfEditorActivity, items)
            setPadding(16, 16, 16, 16)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("📄 페이지 ${currentPos + 1}")
            .setView(gridView)
            .setNegativeButton("닫기", null)
            .create()

        gridView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            val id = items[position].id
            when (id) {
                0 -> showTextEditDialog(originalIndex)
                1 -> showAddTextDialog(originalIndex)
                2 -> addHighlight(originalIndex)
                3 -> addStickyNote(originalIndex)
                4 -> addRectangle(originalIndex)
                5 -> addCircle(originalIndex)
                6 -> rotatePage(originalIndex, 90)
                7 -> rotatePage(originalIndex, -90)
                8 -> {
                    if (currentPos > 0) {
                        lifecycleScope.launch {
                            session.movePage(currentPos, currentPos - 1).fold(
                                onSuccess = { refreshAdapter() },
                                onFailure = { Toast.makeText(this@PdfEditorActivity,
                                    "❌ ${it.message}", Toast.LENGTH_LONG).show() }
                            )
                        }
                    } else Toast.makeText(this, "첫 페이지", Toast.LENGTH_SHORT).show()
                }
                9 -> {
                    if (currentPos < session.pageCount - 1) {
                        lifecycleScope.launch {
                            session.movePage(currentPos, currentPos + 1).fold(
                                onSuccess = { refreshAdapter() },
                                onFailure = { Toast.makeText(this@PdfEditorActivity,
                                    "❌ ${it.message}", Toast.LENGTH_LONG).show() }
                            )
                        }
                    } else Toast.makeText(this, "마지막 페이지", Toast.LENGTH_SHORT).show()
                }
                10 -> extractPage(originalIndex)
                11 -> confirmDeletePage(originalIndex)
            }
        }
        dialog.show()
    }

    private fun showPageMenuOld(originalIndex: Int) {
        val currentPos = session.getPageInfos().indexOfFirst { it.originalIndex == originalIndex }
        if (currentPos < 0) return

        val options = arrayOf(
            "✏️ 텍스트 편집 (기존 수정)",
            "➕ 텍스트 추가 (새로 쓰기)",
            "🖍 하이라이트 추가",
            "📌 스티키 노트 추가",
            "⬜ 사각형 그리기",
            "⭕ 원 그리기",
            "🔄 오른쪽으로 회전",
            "🔄 왼쪽으로 회전",
            "⬅ 왼쪽으로 이동",
            "➡ 오른쪽으로 이동",
            "🗑 이 페이지 삭제",
            "📄 이 페이지만 추출"
        )

        AlertDialog.Builder(this)
            .setTitle("페이지 ${currentPos + 1} 편집")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTextEditDialog(originalIndex)
                    1 -> showAddTextDialog(originalIndex)
                    2 -> addHighlight(originalIndex)
                    3 -> addStickyNote(originalIndex)
                    4 -> addRectangle(originalIndex)
                    5 -> addCircle(originalIndex)
                    6 -> rotatePage(originalIndex, 90)
                    7 -> rotatePage(originalIndex, -90)
                    8 -> {
                        if (currentPos > 0) {
                            lifecycleScope.launch {
                                session.movePage(currentPos, currentPos - 1).fold(
                                    onSuccess = { refreshAdapter() },
                                    onFailure = { Toast.makeText(this@PdfEditorActivity,
                                        "❌ ${it.message}", Toast.LENGTH_LONG).show() }
                                )
                            }
                        } else Toast.makeText(this, "첫 페이지", Toast.LENGTH_SHORT).show()
                    }
                    9 -> {
                        if (currentPos < session.pageCount - 1) {
                            lifecycleScope.launch {
                                session.movePage(currentPos, currentPos + 1).fold(
                                    onSuccess = { refreshAdapter() },
                                    onFailure = { Toast.makeText(this@PdfEditorActivity,
                                        "❌ ${it.message}", Toast.LENGTH_LONG).show() }
                                )
                            }
                        } else Toast.makeText(this, "마지막 페이지", Toast.LENGTH_SHORT).show()
                    }
                    10 -> confirmDeletePage(originalIndex)
                    11 -> extractPage(originalIndex)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun rotatePage(originalIndex: Int, degrees: Int) {
        lifecycleScope.launch {
            session.rotatePage(originalIndex, degrees).fold(
                onSuccess = {
                    val newThumb = session.renderThumbnail(originalIndex, 300, true)
                    thumbnails[originalIndex] = newThumb
                    refreshAdapter()
                },
                onFailure = {
                    Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun addHighlight(originalIndex: Int) {
        // ⭐ 미리보기로 위임
        val intent = Intent(this, PdfPagePreviewActivity::class.java).apply {
            putExtra(PdfPagePreviewActivity.EXTRA_PATH, file.absolutePath)
            putExtra(PdfPagePreviewActivity.EXTRA_PAGE, originalIndex)
            putExtra(PdfPagePreviewActivity.EXTRA_MODE, PdfPagePreviewActivity.MODE_HIGHLIGHT)
        }
        previewLauncher.launch(intent)
    }

    private fun addHighlightOld(originalIndex: Int) {
        lifecycleScope.launch {
            PdfAnnotation.addHighlight(
                this@PdfEditorActivity, file, originalIndex,
                100f, 500f, 200f, 20f, Color.YELLOW
            ).fold(
                onSuccess = {
                    refreshThumb(originalIndex)
                    Toast.makeText(this@PdfEditorActivity, "✅ 하이라이트 추가", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun addStickyNote(originalIndex: Int) {
        // ⭐ 미리보기로 위임
        val intent = Intent(this, PdfPagePreviewActivity::class.java).apply {
            putExtra(PdfPagePreviewActivity.EXTRA_PATH, file.absolutePath)
            putExtra(PdfPagePreviewActivity.EXTRA_PAGE, originalIndex)
            putExtra(PdfPagePreviewActivity.EXTRA_MODE, PdfPagePreviewActivity.MODE_STICKY)
        }
        previewLauncher.launch(intent)
    }

    private fun addStickyNoteOld(originalIndex: Int) {
        val input = makeStyledEditText("메모 내용")
        AlertDialog.Builder(this)
            .setTitle("스티키 노트")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    PdfAnnotation.addStickyNote(
                        this@PdfEditorActivity, file, originalIndex,
                        100f, 500f, text
                    ).fold(
                        onSuccess = {
                            refreshThumb(originalIndex)
                            Toast.makeText(this@PdfEditorActivity, "✅ 노트 추가", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = {
                            Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addRectangle(originalIndex: Int) {
        val intent = Intent(this, PdfPagePreviewActivity::class.java).apply {
            putExtra(PdfPagePreviewActivity.EXTRA_PATH, file.absolutePath)
            putExtra(PdfPagePreviewActivity.EXTRA_PAGE, originalIndex)
            putExtra(PdfPagePreviewActivity.EXTRA_MODE, PdfPagePreviewActivity.MODE_RECTANGLE)
        }
        previewLauncher.launch(intent)
    }

    private fun addRectangleOld(originalIndex: Int) {
        lifecycleScope.launch {
            PdfAnnotation.addRectangle(
                this@PdfEditorActivity, file, originalIndex,
                100f, 400f, 200f, 100f, Color.RED, 2f
            ).fold(
                onSuccess = {
                    refreshThumb(originalIndex)
                    Toast.makeText(this@PdfEditorActivity, "✅ 사각형 추가", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun addCircle(originalIndex: Int) {
        val intent = Intent(this, PdfPagePreviewActivity::class.java).apply {
            putExtra(PdfPagePreviewActivity.EXTRA_PATH, file.absolutePath)
            putExtra(PdfPagePreviewActivity.EXTRA_PAGE, originalIndex)
            putExtra(PdfPagePreviewActivity.EXTRA_MODE, PdfPagePreviewActivity.MODE_CIRCLE)
        }
        previewLauncher.launch(intent)
    }

    private fun addCircleOld(originalIndex: Int) {
        lifecycleScope.launch {
            PdfAnnotation.addCircle(
                this@PdfEditorActivity, file, originalIndex,
                175f, 475f, 75f, Color.RED, 2f
            ).fold(
                onSuccess = {
                    refreshThumb(originalIndex)
                    Toast.makeText(this@PdfEditorActivity, "✅ 원 추가", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun confirmDeletePage(originalIndex: Int) {
        if (session.pageCount <= 1) {
            Toast.makeText(this, "최소 1페이지 필요", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("페이지 삭제")
            .setMessage("이 페이지를 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    session.deletePage(originalIndex).fold(
                        onSuccess = {
                            thumbnails.remove(originalIndex)
                            refreshAdapter()
                        },
                        onFailure = {
                            Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun extractPage(originalIndex: Int) {
        lifecycleScope.launch {
            session.extractPage(originalIndex).fold(
                onSuccess = {
                    Toast.makeText(this@PdfEditorActivity,
                        "✅ ${it.name} 저장됨", Toast.LENGTH_LONG).show()
                },
                onFailure = {
                    Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun addBlankPage() {
        lifecycleScope.launch {
            session.addBlankPage().fold(
                onSuccess = {
                    val newIndex = session.pageCount - 1
                    val newThumb = session.renderThumbnail(newIndex, 300, true)
                    thumbnails[newIndex] = newThumb
                    refreshAdapter()
                },
                onFailure = {
                    Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun refreshThumb(originalIndex: Int) {
        lifecycleScope.launch {
            // ⭐ 외부 편집 후 세션 재로드 (필수!)
            session.reload()
            val newThumb = session.renderThumbnail(originalIndex, 300, true)
            thumbnails[originalIndex] = newThumb
            refreshAdapter()
        }
    }

    private fun showAnnotationMenu() {
        val pageInfos = session.getPageInfos()
        if (pageInfos.isEmpty()) return

        val labels = pageInfos.mapIndexed { i, _ -> "페이지 ${i + 1}" }.toTypedArray()
        var selectedPage = currentPageIndex

        AlertDialog.Builder(this)
            .setTitle("주석 추가할 페이지 선택")
            .setSingleChoiceItems(labels, currentPageIndex) { _, which ->
                selectedPage = which
            }
            .setPositiveButton("다음") { _, _ ->
                val originalIndex = pageInfos[selectedPage].originalIndex
                showPageMenu(originalIndex)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showWatermarkDialog() {
        val options = arrayOf(
            "💧 텍스트 워터마크 (전체 페이지)",
            "🖼 이미지 워터마크",
            "🏷 텍스트 스탬프 (현재 페이지)"
        )

        AlertDialog.Builder(this)
            .setTitle("워터마크")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTextWatermarkInput()
                    1 -> Toast.makeText(this, "이미지 워터마크는 다음 업데이트", Toast.LENGTH_SHORT).show()
                    2 -> showStampInput()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showTextWatermarkInput() {
        val input = makeStyledEditText("워터마크 텍스트").apply {
            setText("CONFIDENTIAL")
        }
        AlertDialog.Builder(this)
            .setTitle("텍스트 워터마크")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    Toast.makeText(this@PdfEditorActivity, "워터마크 추가 중...", Toast.LENGTH_SHORT).show()
                    PdfWatermark.addTextWatermark(
                        this@PdfEditorActivity, file, text, 60f, 0.15f, Color.GRAY, 45f
                    ).fold(
                        onSuccess = {
                            Toast.makeText(this@PdfEditorActivity, "✅ 완료", Toast.LENGTH_SHORT).show()
                            loadThumbnails()
                        },
                        onFailure = {
                            Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showStampInput() {
        val input = makeStyledEditText("스탬프 텍스트")
        AlertDialog.Builder(this)
            .setTitle("텍스트 스탬프 (페이지 ${currentPageIndex + 1})")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                val pageInfos = session.getPageInfos()
                if (currentPageIndex >= pageInfos.size) return@setPositiveButton
                val idx = pageInfos[currentPageIndex].originalIndex

                lifecycleScope.launch {
                    PdfWatermark.addTextStamp(
                        this@PdfEditorActivity, file, idx,
                        100f, 100f, text, 14f, Color.RED
                    ).fold(
                        onSuccess = {
                            refreshThumb(idx)
                            Toast.makeText(this@PdfEditorActivity, "✅ 완료", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = {
                            Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showMoreMenu() {
        val options = arrayOf(
            "📄 선택 페이지 추출",
            "🎨 이미지 워터마크",
            "🧹 모든 캐시 삭제",
            "ℹ️ 파일 정보"
        )
        AlertDialog.Builder(this)
            .setTitle("더보기")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showExtractDialog()
                    1 -> Toast.makeText(this, "이미지 워터마크는 다음 업데이트", Toast.LENGTH_SHORT).show()
                    2 -> {
                        PdfThumbnail.clearCache(this)
                        OcrCache.clear(this)
                        Toast.makeText(this, "✅ 캐시 삭제", Toast.LENGTH_SHORT).show()
                    }
                    3 -> showFileInfo()
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showExtractDialog() {
        val pageInfos = session.getPageInfos()
        val labels = pageInfos.mapIndexed { i, _ -> "페이지 ${i + 1}" }.toTypedArray()
        val checked = BooleanArray(pageInfos.size)

        AlertDialog.Builder(this)
            .setTitle("추출할 페이지 선택")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("추출") { _, _ ->
                val selected = mutableListOf<Int>()
                for (i in checked.indices) {
                    if (checked[i]) selected.add(i)
                }
                if (selected.isEmpty()) {
                    Toast.makeText(this, "페이지를 선택하세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                extractPages(selected)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun extractPages(indices: List<Int>) {
        lifecycleScope.launch {
            Toast.makeText(this@PdfEditorActivity, "${indices.size}페이지 추출 중...", Toast.LENGTH_SHORT).show()
            try {
                val outDir = File(filesDir, "pdfs").apply { mkdirs() }
                val outFile = File(outDir,
                    "${file.nameWithoutExtension}_extract_${System.currentTimeMillis()}.pdf")

                withContext(Dispatchers.IO) {
                    val doc = session.document ?: return@withContext
                    com.tom_roush.pdfbox.pdmodel.PDDocument().use { newDoc ->
                        indices.forEach { idx ->
                            newDoc.importPage(doc.getPage(idx))
                        }
                        newDoc.save(outFile)
                    }
                }
                Toast.makeText(this@PdfEditorActivity,
                    "✅ ${outFile.name} 저장됨", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@PdfEditorActivity,
                    "❌ ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showFileInfo() {
        AlertDialog.Builder(this)
            .setTitle("파일 정보")
            .setMessage(
                "이름: ${file.name}\n" +
                "크기: ${"%.2f".format(file.length() / 1024.0 / 1024.0)} MB\n" +
                "페이지: ${session.pageCount}\n" +
                "경로: ${file.absolutePath}"
            )
            .setPositiveButton("확인", null)
            .show()
    }

    private fun saveDocument() {
        AlertDialog.Builder(this)
            .setTitle("저장")
            .setMessage("저장 방식을 선택하세요")
            .setPositiveButton("원본 덮어쓰기") { _, _ ->
                lifecycleScope.launch {
                    session.save().fold(
                        onSuccess = {
                            Toast.makeText(this@PdfEditorActivity, "✅ 저장 완료", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = {
                            Toast.makeText(this@PdfEditorActivity, "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNeutralButton("다른 이름으로") { _, _ ->
                val input = makeStyledEditText("새 파일명").apply {
                    setText("${file.nameWithoutExtension}_edited.pdf")
                }
                AlertDialog.Builder(this)
                    .setTitle("새 파일명")
                    .setView(input)
                    .setPositiveButton("저장") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isBlank()) return@setPositiveButton
                        lifecycleScope.launch {
                            session.saveAs(name).fold(
                                onSuccess = {
                                    Toast.makeText(this@PdfEditorActivity,
                                        "✅ ${it.name} 저장됨", Toast.LENGTH_LONG).show()
                                },
                                onFailure = {
                                    Toast.makeText(this@PdfEditorActivity,
                                        "❌ ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("종료")
            .setMessage("저장하지 않은 변경사항은 사라집니다.\n종료할까요?")
            .setPositiveButton("종료") { _, _ -> finish() }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * ⭐ 기존 텍스트 편집 (페이지 텍스트 목록 → 선택 → 수정)
     */
    private fun showTextEditDialog(originalIndex: Int) {
        lifecycleScope.launch {
            Toast.makeText(this@PdfEditorActivity, "텍스트 추출 중...", Toast.LENGTH_SHORT).show()

            val result = withContext(Dispatchers.IO) {
                PdfTextPicker.extractWordsWithSize(this@PdfEditorActivity, file, originalIndex)
            }.getOrNull()

            if (result == null || result.words.isEmpty()) {
                Toast.makeText(this@PdfEditorActivity,
                    "❌ 텍스트 없음 (스캔본?)", Toast.LENGTH_LONG).show()
                return@launch
            }

            // 텍스트 리스트 다이얼로그
            val labels = result.words.map { it.text }.toTypedArray()
            AlertDialog.Builder(this@PdfEditorActivity)
                .setTitle("수정할 텍스트 선택 (${result.words.size}개)")
                .setItems(labels) { _, which ->
                    val word = result.words[which]
                    showEditTextInput(originalIndex, word)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    /**
     * ⭐ 텍스트 수정 입력 다이얼로그
     */
    private fun showEditTextInput(originalIndex: Int, word: PdfTextPicker.Word) {
        val input = makeStyledEditText("새 텍스트").apply {
            setText(word.text)
            setSelection(word.text.length)
        }

        val label = TextView(this).apply {
            text = "원본: ${word.text}\n" +
                   "위치: (${"%.1f".format(word.x)}, ${"%.1f".format(word.y)})\n" +
                   "폰트: ${"%.1f".format(word.fontSize)}pt"
            setTextColor(0xFF666666.toInt())
            textSize = 11f
            setPadding(0, 0, 0, 16)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            addView(label)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("텍스트 편집")
            .setView(container)
            .setPositiveButton("저장") { _, _ ->
                val newText = input.text.toString()
                if (newText.isBlank()) {
                    Toast.makeText(this, "텍스트를 입력하세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                performTextReplace(originalIndex, word, newText)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * ⭐ 텍스트 교체 실행
     */
    private fun performTextReplace(
        originalIndex: Int,
        word: PdfTextPicker.Word,
        newText: String
    ) {
        lifecycleScope.launch {
            Toast.makeText(this@PdfEditorActivity, "저장 중...", Toast.LENGTH_SHORT).show()

            PdfTextEditor.replaceText(
                context = this@PdfEditorActivity,
                file = file,
                pageIndex = originalIndex,
                originalLeft = word.x,
                originalBottom = word.y,
                originalWidth = word.width,
                originalHeight = word.height,
                newText = newText,
                fontSize = word.fontSize,
                color = Color.BLACK,
                bgColor = Color.WHITE
            ).fold(
                onSuccess = {
                    refreshThumb(originalIndex)
                    Toast.makeText(this@PdfEditorActivity,
                        "✅ 텍스트 저장됨", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(this@PdfEditorActivity,
                        "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    /**
     * ⭐ 새 텍스트 추가 (좌표 지정)
     */
    private fun showAddTextDialog(originalIndex: Int) {
        // ⭐ 미리보기 (탭으로 위치 지정)
        val intent = Intent(this, PdfPagePreviewActivity::class.java).apply {
            putExtra(PdfPagePreviewActivity.EXTRA_PATH, file.absolutePath)
            putExtra(PdfPagePreviewActivity.EXTRA_PAGE, originalIndex)
            putExtra(PdfPagePreviewActivity.EXTRA_MODE, PdfPagePreviewActivity.MODE_ADD_TEXT)
        }
        previewLauncher.launch(intent)
    }

    private fun showAddTextDialogOld(originalIndex: Int) {
        val input = EditText(this).apply {
            hint = "새 텍스트 입력"
            minLines = 2
        }

        val xInput = EditText(this).apply {
            setText("100")
            hint = "X (pt, 0~595)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val yInput = EditText(this).apply {
            setText("400")
            hint = "Y (pt, 0~842)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val sizeInput = EditText(this).apply {
            setText("14")
            hint = "폰트 크기 (pt)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val info = TextView(this).apply {
            text = "페이지 ${originalIndex + 1}\n" +
                   "PDF 좌표: 좌하단(0,0) → 우상단(595, 842)"
            setTextColor(0xFF666666.toInt())
            textSize = 11f
            setPadding(0, 0, 0, 16)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            addView(info)
            addView(input)
            addView(TextView(this@PdfEditorActivity).apply {
                text = "위치 X:"
                setTextColor(0xFF888888.toInt())
                textSize = 11f
                setPadding(0, 12, 0, 4)
            })
            addView(xInput)
            addView(TextView(this@PdfEditorActivity).apply {
                text = "위치 Y (좌하단 기준):"
                setTextColor(0xFF888888.toInt())
                textSize = 11f
                setPadding(0, 12, 0, 4)
            })
            addView(yInput)
            addView(TextView(this@PdfEditorActivity).apply {
                text = "폰트 크기:"
                setTextColor(0xFF888888.toInt())
                textSize = 11f
                setPadding(0, 12, 0, 4)
            })
            addView(sizeInput)
        }

        AlertDialog.Builder(this)
            .setTitle("➕ 새 텍스트 추가")
            .setView(container)
            .setPositiveButton("추가") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) {
                    Toast.makeText(this, "텍스트를 입력하세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val x = xInput.text.toString().toFloatOrNull() ?: 100f
                val y = yInput.text.toString().toFloatOrNull() ?: 400f
                val fontSize = sizeInput.text.toString().toFloatOrNull() ?: 14f

                lifecycleScope.launch {
                    PdfTextEditor.addText(
                        context = this@PdfEditorActivity,
                        file = file,
                        pageIndex = originalIndex,
                        x = x, y = y,
                        text = text,
                        fontSize = fontSize,
                        color = Color.BLACK
                    ).fold(
                        onSuccess = {
                            refreshThumb(originalIndex)
                            Toast.makeText(this@PdfEditorActivity,
                                "✅ 텍스트 추가됨", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = {
                            Toast.makeText(this@PdfEditorActivity,
                                "❌ ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}
