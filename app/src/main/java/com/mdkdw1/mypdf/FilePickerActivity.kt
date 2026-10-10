package com.mdkdw1.mypdf

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class FilePickerActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var tvPath: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnUp: Button
    private lateinit var btnClose: Button

    private var currentDir: File = Environment.getExternalStorageDirectory()

    // 폴더에 PDF가 있는지 재귀적으로 검사 (깊이 제한)
    private fun hasPdfDeep(dir: File, depth: Int = 0): Boolean {
        if (depth > 4) return false
        val files = dir.listFiles() ?: return false
        for (f in files) {
            if (f.isFile && f.extension.lowercase() == "pdf") return true
        }
        for (f in files) {
            if (f.isDirectory && !f.name.startsWith(".")) {
                if (hasPdfDeep(f, depth + 1)) return true
            }
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_picker)

        listView = findViewById(R.id.listFiles)
        tvPath = findViewById(R.id.tvPath)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnUp = findViewById(R.id.btnUp)
        btnClose = findViewById(R.id.btnClose)

        // ← 뒤로 (앱 닫기)
        btnClose.setOnClickListener { finish() }

        // ↑ 상위 폴더
        btnUp.setOnClickListener {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead()) {
                currentDir = parent
                loadDir()
            } else {
                Toast.makeText(this, "최상위 폴더입니다", Toast.LENGTH_SHORT).show()
            }
        }

        val start = when {
            File("/storage/emulated/0").canRead() -> File("/storage/emulated/0")
            Environment.getExternalStorageDirectory().canRead() ->
                Environment.getExternalStorageDirectory()
            else -> filesDir
        }
        currentDir = start
        loadDir()
    }

    private fun loadDir() {
        tvPath.text = currentDir.absolutePath

        val allFiles = currentDir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()

        // 표시할 항목:
        // 1) 이 폴더의 PDF 파일들
        // 2) 하위 폴더 중 PDF가 있는 것만
        val pdfFiles = allFiles.filter { f ->
            f.isFile && f.extension.lowercase() == "pdf"
        }
        val pdfFolders = allFiles.filter { f ->
            f.isDirectory &&
            !f.name.startsWith(".") &&
            f.canRead() &&
            hasPdfDeep(f)
        }

        val display = mutableListOf<Pair<File, String>>()
        pdfFolders.forEach { display.add(it to "📁 ${it.name}") }
        pdfFiles.forEach { display.add(it to "📄 ${it.name}") }

        if (display.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "이 폴더에 PDF가 없습니다\n다른 폴더로 이동해보세요"
            return
        }

        listView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            display.map { it.second }
        )

        listView.setOnItemClickListener { _, _, pos, _ ->
            val (file, _) = display[pos]
            if (file.isDirectory) {
                currentDir = file
                loadDir()
            } else {
                val data = Intent().apply { putExtra("pdf_path", file.absolutePath) }
                setResult(RESULT_OK, data)
                finish()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        val parent = currentDir.parentFile
        val root = "/storage/emulated/0"
        if (parent != null && parent.canRead() && currentDir.absolutePath != root) {
            currentDir = parent
            loadDir()
        } else {
            super.onBackPressed()
        }
    }
}
