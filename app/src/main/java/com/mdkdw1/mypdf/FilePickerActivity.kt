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

    private var currentDir: File = Environment.getExternalStorageDirectory()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_picker)

        listView = findViewById(R.id.listFiles)
        tvPath = findViewById(R.id.tvPath)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnUp = findViewById(R.id.btnUp)

        btnUp.setOnClickListener {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead()) {
                currentDir = parent
                loadDir()
            }
        }

        // 외부 저장소 접근 가능한 경로부터 시작
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

        val items = currentDir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        ) ?: emptyList()

        // 폴더는 모두, 파일은 pdf만
        val visible = items.filter { f ->
            f.isDirectory || f.extension.lowercase() == "pdf"
        }

        if (visible.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "이 폴더에 PDF가 없습니다"
            return
        }

        listView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val display = visible.map { f ->
            if (f.isDirectory) "📁 ${f.name}"
            else "📄 ${f.name}"
        }

        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, display)
        listView.setOnItemClickListener { _, _, pos, _ ->
            val f = visible[pos]
            if (f.isDirectory) {
                currentDir = f
                loadDir()
            } else {
                // PDF 선택 → MainActivity로 결과 반환
                val data = Intent().apply { putExtra("pdf_path", f.absolutePath) }
                setResult(RESULT_OK, data)
                finish()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        val parent = currentDir.parentFile
        if (parent != null && parent.canRead() &&
            currentDir.absolutePath != "/" &&
            currentDir.absolutePath != "/storage") {
            currentDir = parent
            loadDir()
        } else {
            super.onBackPressed()
        }
    }
}
