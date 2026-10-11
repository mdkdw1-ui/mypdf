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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ⭐ PDF 텍스트 편집 전용 (그림/도형 없음)
 * - 페이지 텍스트 목록
 * - 선택 → 편집 → 원본 위치/크기 그대로 치환
 */
class PdfSimpleEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "pdf_path"
        const val EXTRA_PAGE = "page_index"
        const val RESULT_PAGE_INDEX = "result_page_index"
    }

    private lateinit var file: File
    private var pageIndex = 0
    private lateinit var listView: ListView
    private lateinit var tvStatus: TextView
    private lateinit var btnSave: Button

    private var words: List<PdfTextPicker.Word> = emptyList()
    private val editedWords = mutableMapOf<Int, String>()  // index → newText

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

        // ⭐ 상단 바
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
                if (editedWords.isEmpty()) {
                    finish()
                } else {
                    AlertDialog.Builder(this@PdfSimpleEditorActivity, R.style.DarkDialogTheme)
                        .setTitle("종료")
                        .setMessage("저장하지 않은 편집 ${editedWords.size}개가 있습니다.\n종료할까요?")
                        .setPositiveButton("종료") { _, _ -> finish() }
                        .setNegativeButton("취소", null)
                        .show()
                }
            }
        }

        tvStatus = TextView(this).apply {
            text = "로딩 중..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 0, 8, 0)
        }

        btnSave = Button(this).apply {
            text = "💾 저장"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFFF6B9D.toInt())
            setPadding(48, 30, 48, 30)
            setOnClickListener { saveAllEdits() }
        }

        topBar.addView(btnClose)
        topBar.addView(tvStatus)
        topBar.addView(btnSave)
        root.addView(topBar)

        // ⭐ 검색 바
        val searchInput = EditText(this).apply {
            hint = "🔍 텍스트 검색..."
            setHintTextColor(0xFF888888.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2A2A2A.toInt())
            setPadding(24, 24, 24, 24)
            textSize = 14f
        }
        root.addView(searchInput)

        // ⭐ 텍스트 리스트
        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(0xFF0D1117.toInt())
            divider = android.graphics.drawable.ColorDrawable(0xFF2A323C.toInt())
            dividerHeight = 1
        }
        root.addView(listView)

        setContentView(root)

        // 페이지 정보
        tvStatus.text = "페이지 ${pageIndex + 1}  ·  로딩 중..."

        // 검색 필터
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                refreshList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // 텍스트 추출
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                PdfTextPicker.extractWordsWithSize(this@PdfSimpleEditorActivity, file, pageIndex)
            }
            result.fold(
                onSuccess = { r ->
                    words = r.words
                    tvStatus.text = "페이지 ${pageIndex + 1}  ·  ${words.size}개 단어"
                    refreshList("")
                },
                onFailure = {
                    Toast.makeText(this@PdfSimpleEditorActivity,
                        "❌ 텍스트 추출 실패: ${it.message}", Toast.LENGTH_LONG).show()
                    tvStatus.text = "❌ 추출 실패"
                }
            )
        }
    }

    private fun refreshList(query: String) {
        val filtered = if (query.isBlank()) {
            words.withIndex().toList()
        } else {
            words.withIndex().filter {
                it.value.text.contains(query, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            listView.adapter = null
            tvStatus.text = "검색 결과 없음"
            return
        }

        val display = filtered.map { (idx, word) ->
            val edited = editedWords[idx]
            if (edited != null) {
                "✏️ ${word.text}  →  $edited"
            } else {
                "${word.text}   (${"%.1f".format(word.fontSize)}pt)"
            }
        }

        listView.adapter = object : BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(pos: Int) = filtered[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, convertView: android.view.View?, parent: ViewGroup?): android.view.View {
                val tv = convertView as? TextView ?: TextView(this@PdfSimpleEditorActivity).apply {
                    setPadding(32, 28, 32, 28)
                    textSize = 15f
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFF0D1117.toInt())
                }
                val (idx, _) = filtered[pos]
                tv.text = display[pos]
                tv.setTextColor(
                    if (editedWords.containsKey(idx)) 0xFFFF6B9D.toInt()
                    else 0xFFFFFFFF.toInt()
                )
                return tv
            }
        }

        listView.setOnItemClickListener { _, _, pos, _ ->
            val (idx, word) = filtered[pos]
            showEditDialog(idx, word)
        }
    }

    private fun showEditDialog(index: Int, word: PdfTextPicker.Word) {
        val currentText = editedWords[index] ?: word.text

        val input = EditText(this).apply {
            setText(currentText)
            setSelection(currentText.length)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundColor(0xFF2A2A2A.toInt())
            setPadding(24, 20, 24, 20)
            textSize = 16f
            hint = "새 텍스트"
        }

        val info = TextView(this).apply {
            text = "원본: ${word.text}\n" +
                   "위치: (${"%.1f".format(word.x)}, ${"%.1f".format(word.y)}) pt\n" +
                   "크기: ${"%.1f".format(word.fontSize)}pt\n" +
                   "폭: ${"%.1f".format(word.width)}pt, 높이: ${"%.1f".format(word.height)}pt"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 11f
            setPadding(0, 0, 0, 16)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            addView(info)
            addView(input)
        }

        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("✏️ 텍스트 편집")
            .setView(container)
            .setPositiveButton("확인") { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotBlank()) {
                    editedWords[index] = newText
                    refreshList("")
                }
            }
            .setNeutralButton("원본 복원") { _, _ ->
                editedWords.remove(index)
                refreshList("")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveAllEdits() {
        if (editedWords.isEmpty()) {
            Toast.makeText(this, "편집한 내용이 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("💾 저장")
            .setMessage("${editedWords.size}개 텍스트를 저장할까요?\n\n" +
                       "• 원본 위치/크기 그대로\n" +
                       "• 원본 위에 덮어쓰기")
            .setPositiveButton("저장") { _, _ -> performSave() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performSave() {
        lifecycleScope.launch {
            Toast.makeText(this@PdfSimpleEditorActivity, "저장 중...", Toast.LENGTH_SHORT).show()

            val result = withContext(Dispatchers.IO) {
                PdfTextEditor.replaceMultiple(
                    context = this@PdfSimpleEditorActivity,
                    file = file,
                    pageIndex = pageIndex,
                    edits = editedWords.map { (idx, newText) ->
                        idx to (words[idx] to newText)
                    }.toMap()
                )
            }

            result.fold(
                onSuccess = {
                    Toast.makeText(this@PdfSimpleEditorActivity,
                        "✅ ${editedWords.size}개 저장됨", Toast.LENGTH_LONG).show()
                    val data = Intent().apply { putExtra(RESULT_PAGE_INDEX, pageIndex) }
                    setResult(Activity.RESULT_OK, data)
                    finish()
                },
                onFailure = {
                    Toast.makeText(this@PdfSimpleEditorActivity,
                        "❌ ${it.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}
