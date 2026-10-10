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

    // 🚫 시스템/숨김 폴더 이름 (정확히 이 이름이면 제외)
    private val blockedNames = setOf(
        "Android", "android", "data", "obb",
        "MIUI", "miui", "tencent", "Tencent",
        "System", "system", "cache", "Cache",
        "tmp", "temp", "Temp", "lost+found", "LOST.DIR",
        "Document", "Documents", "Music", "Podcasts",
        "Ringtones", "Alarms", "Notifications",
        "Movies", "Pictures", "DCIM",
        ".thumbnails", "log", "logs", "Log", "backups"
    )

    private fun isBlocked(name: String): Boolean {
        if (name.startsWith(".")) return true          // 숨김
        if (name.startsWith("com.")) return true        // 앱 데이터
        if (name.startsWith("org.")) return true
        if (name.startsWith("me.")) return true
        if (blockedNames.contains(name)) return true
        return false
    }

    // 현재 폴더 바로 아래 PDF 있는지
    private fun hasPdfDirect(dir: File): Boolean {
        val files = dir.listFiles() ?: return false
        return files.any { it.isFile && it.extension.lowercase() == "pdf" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_picker)

        listView = findViewById(R.id.listFiles)
        tvPath = findViewById(R.id.tvPath)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnUp = findViewById(R.id.btnUp)
        btnClose = findViewById(R.id.btnClose)

        btnClose.setOnClickListener { finish() }

        btnUp.setOnClickListener {
            val parent = currentDir.parentFile
            val root = "/storage/emulated/0"
            if (parent != null && parent.canRead() && currentDir.absolutePath != root) {
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

        // 1) 현재 폴더의 PDF
        val pdfFiles = allFiles.filter { f ->
            f.isFile && f.extension.lowercase() == "pdf"
        }

        // 2) 하위 폴더 (시스템/숨김 제외)
        val subFolders = allFiles.filter { f ->
            f.isDirectory && !isBlocked(f.name)
        }

        val display = mutableListOf<Pair<File, String>>()

        // 폴더 먼저 (PDF 있으면 🔴)
        subFolders.forEach { f ->
            val prefix = if (hasPdfDirect(f)) "📁🔴" else "📁"
            display.add(f to "$prefix ${f.name}")
        }
        // PDF 파일
        pdfFiles.forEach { f ->
            display.add(f to "📄 ${f.name}")
        }

        if (display.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "이 폴더에 PDF나 하위 폴더가 없습니다"
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
