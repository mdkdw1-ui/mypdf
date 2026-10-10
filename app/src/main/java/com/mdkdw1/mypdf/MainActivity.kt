package com.mdkdw1.mypdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private val REQ_PICK_PDF = 1001
    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var btnAdd: Button
    private lateinit var btnSort: Button
    private lateinit var prefs: ReadingPrefs

    // 정렬 모드: 0=최근본, 1=이름, 2=즐겨찾기
    private var sortMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = ReadingPrefs(this)

        listView = findViewById(R.id.listView)
        emptyText = findViewById(R.id.emptyText)
        btnAdd = findViewById(R.id.btnAdd)
        btnSort = findViewById(R.id.btnSort)

        btnAdd.setOnClickListener {
            startActivityForResult(Intent(this, FilePickerActivity::class.java), REQ_PICK_PDF)
        }

        btnSort.setOnClickListener {
            sortMode = (sortMode + 1) % 3
            val label = when (sortMode) {
                0 -> "정렬: 최근"
                1 -> "정렬: 이름"
                2 -> "정렬: 즐겨찾기"
                else -> "정렬"
            }
            btnSort.text = label
            showList()
        }

        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { openUri(it) }
        } else {
            showList()
        }
    }

    override fun onResume() {
        super.onResume()
        showList()
    }

    private fun showList() {
        val files = getPdfFiles()
        if (files.isEmpty()) {
            listView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = "저장된 PDF가 없습니다.\n[+ PDF 추가] 버튼을 눌러주세요."
            return
        }
        listView.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        val display = files.map { f ->
            val fav = if (prefs.isFavorite(f.name)) "⭐" else "  "
            val size = "%.1fMB".format(f.length() / 1024.0 / 1024.0)
            "$fav ${f.name}\n      $size"
        }

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            display
        )
        listView.setOnItemClickListener { _, _, pos, _ -> openReader(files[pos]) }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showFileMenu(files[pos])
            true
        }
    }

    private fun getPdfFiles(): List<File> {
        val dir = File(filesDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles { f -> f.extension.lowercase() == "pdf" }?.toList() ?: emptyList()
        return when (sortMode) {
            0 -> files.sortedByDescending { prefs.getLastOpened(it.name) }
            1 -> files.sortedBy { it.name.lowercase() }
            2 -> files.sortedByDescending {
                (if (prefs.isFavorite(it.name)) 1 else 0) * 1_000_000_000L +
                        prefs.getLastOpened(it.name)
            }
            else -> files
        }
    }

    private fun showFileMenu(file: File) {
        val options = arrayOf(
            if (prefs.isFavorite(file.name)) "★ 즐겨찾기 해제" else "☆ 즐겨찾기 추가",
            "📖 이북 모드로 열기",
            "🗑 삭제",
            "ℹ ${file.name}"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { prefs.toggleFavorite(file.name); showList() }
                    1 -> openReader(file)
                    2 -> confirmDelete(file)
                    3 -> showFileInfo(file)
                }
            }
            .show()
    }

    private fun confirmDelete(file: File) {
        android.app.AlertDialog.Builder(this)
            .setTitle("삭제")
            .setMessage("${file.name} 을(를) 삭제할까요?")
            .setPositiveButton("삭제") { _, _ -> file.delete(); showList() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showFileInfo(file: File) {
        val last = prefs.getLastOpened(file.name)
        val dateStr = if (last > 0) java.util.Date(last).toString() else "기록 없음"
        val page = prefs.getPage(file.name)
        android.app.AlertDialog.Builder(this)
            .setTitle("파일 정보")
            .setMessage(
                "이름: ${file.name}\n" +
                "크기: ${"%.2f".format(file.length() / 1024.0 / 1024.0)} MB\n" +
                "경로: ${file.absolutePath}\n" +
                "마지막 페이지: ${page + 1}\n" +
                "마지막 열람: $dateStr"
            )
            .setPositiveButton("확인", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_PDF && resultCode == RESULT_OK) {
            val path = data?.getStringExtra("pdf_path") ?: return
            val src = File(path)
            if (!src.exists()) return
            val dir = File(filesDir, "pdfs").apply { mkdirs() }
            val dst = File(dir, src.name)
            try {
                src.inputStream().use { input ->
                    FileOutputStream(dst).use { out -> input.copyTo(out) }
                }
                Toast.makeText(this, "저장됨: ${src.name}", Toast.LENGTH_SHORT).show()
                showList()
            } catch (e: Exception) {
                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openUri(uri: Uri) {
        try {
            val dir = File(filesDir, "pdfs").apply { mkdirs() }
            val name = queryFileName(uri) ?: "intent_${System.currentTimeMillis()}.pdf"
            val target = File(dir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            }
            openReader(target)
        } catch (e: Exception) {
            Toast.makeText(this, "열기 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) { null }
    }

    private fun openReader(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "파일 없음: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_PATH, file.absolutePath)
        }
        startActivity(intent)
    }
}
