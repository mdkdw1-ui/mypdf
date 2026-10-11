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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        file = File(path)
        if (!file.exists()) { finish(); return }

        prefs = ReadingPrefs(this)
        session = PdfEditSession(this, file)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#1A1A1A"))

        recyclerView = RecyclerView(this)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.setPadding(8, 8, 8, 8)
        recyclerView.clipToPadding = false
        recyclerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(recyclerView)

        // 상단 툴바
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xDD000000.toInt())
            setPadding(8, 12, 8, 12)
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

        val btnSave = Button(this).apply {
            text = "💾"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setOnClickListener { saveDocument() }
        }

        val btnMore = Button(this).apply {
            text = "⋮"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setOnClickListener { showMoreMenu() }
        }

        toolbar.addView(btnClose)
        toolbar.addView(tvFileName)
        toolbar.addView(btnSave)
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
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = "로딩 중..."
        }

        val btnAddBlank = Button(this).apply {
            text = "➕"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(40, 20, 40, 20)
            setOnClickListener { addBlankPage() }
        }

        val btnAnnotate = Button(this).apply {
            text = "🖍"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(40, 20, 40, 20)
            setOnClickListener { showAnnotationMenu() }
        }

        val btnWatermark = Button(this).apply {
            text = "💧"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setPadding(40, 20, 40, 20)
            setOnClickListener { showWatermarkDialog() }
        }

        bottomBar.addView(tvPageCount)
        bottomBar.addView(btnAddBlank)
        bottomBar.addView(btnAnnotate)
        bottomBar.addView(btnWatermark)
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

    private fun refreshAdapter() {
        val pageInfos = session.getPageInfos()
        adapter = PdfEditPageAdapter(
            pages = pageInfos,
            thumbnails = thumbnails,
            onPageClick = { originalIndex ->
                currentPageIndex = pageInfos.indexOfFirst { it.originalIndex == originalIndex }
                showPageMenu(originalIndex)
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

        val options = arrayOf(
            "🔄 오른쪽 90° 회전",
            "🔄 왼쪽 90° 회전",
            "🖍 하이라이트 (좌상단)",
            "📌 스티키 노트",
            "⬜ 사각형",
            "⭕ 원",
            "🗑 이 페이지 삭제",
            "📄 이 페이지만 추출"
        )

        AlertDialog.Builder(this)
            .setTitle("페이지 ${currentPos + 1}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> rotatePage(originalIndex, 90)
                    1 -> rotatePage(originalIndex, -90)
                    2 -> addHighlight(originalIndex)
                    3 -> addStickyNote(originalIndex)
                    4 -> addRectangle(originalIndex)
                    5 -> addCircle(originalIndex)
                    6 -> confirmDeletePage(originalIndex)
                    7 -> extractPage(originalIndex)
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
        val input = EditText(this).apply { hint = "메모 내용" }
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
        lifecycleScope.launch {
            PdfAnnotation.addCircle(
                this@PdfEditorActivity, file, originalIndex,
                100f, 400f, 150f, 150f, Color.RED, 2f
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
        val input = EditText(this).apply {
            setText("CONFIDENTIAL")
            hint = "워터마크 텍스트"
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
        val input = EditText(this).apply { hint = "스탬프 텍스트" }
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
                val selected = checked.mapIndexedNotNull { i, c -> if (c) i else null }
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
                val input = EditText(this).apply {
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

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}
