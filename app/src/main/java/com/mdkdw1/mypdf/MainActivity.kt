package com.mdkdw1.mypdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private val REQ_PICK_PDF = 1001
    private lateinit var pdfView: PDFView
    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var btnAdd: Button
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pdfView = findViewById(R.id.pdfView)
        listView = findViewById(R.id.listView)
        emptyText = findViewById(R.id.emptyText)
        btnAdd = findViewById(R.id.btnAdd)
        btnBack = findViewById(R.id.btnBack)

        btnAdd.setOnClickListener {
            // 앱 내부 탐색기 실행
            startActivityForResult(Intent(this, FilePickerActivity::class.java), REQ_PICK_PDF)
        }
        btnBack.setOnClickListener {
            pdfView.visibility = View.GONE
            showList()
        }

        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { openUri(it) }
        } else {
            showList()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { openUri(it) }
        }
    }

    private fun showList() {
        listView.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        btnBack.visibility = View.GONE

        val files = getPdfFiles()
        if (files.isEmpty()) {
            listView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = "저장된 PDF가 없습니다.\n[+ PDF 추가] 버튼을 눌러주세요."
            return
        }

        val names = files.map { it.name }.toTypedArray()
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.setOnItemClickListener { _, _, pos, _ -> openPdf(files[pos]) }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showDeleteDialog(files[pos])
            true
        }
    }

    private fun getPdfFiles(): List<File> {
        val dir = File(filesDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        return dir.listFiles { f -> f.extension.lowercase() == "pdf" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun showDeleteDialog(file: File) {
        android.app.AlertDialog.Builder(this)
            .setTitle("삭제")
            .setMessage("${file.name} 을(를) 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                file.delete()
                showList()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_PDF && resultCode == RESULT_OK) {
            val path = data?.getStringExtra("pdf_path")
            if (path != null) {
                val src = File(path)
                if (src.exists()) {
                    // 앱 내부 저장소로 복사 후 열기
                    val dir = File(filesDir, "pdfs").apply { mkdirs() }
                    val dst = File(dir, src.name)
                    try {
                        src.inputStream().use { input ->
                            FileOutputStream(dst).use { out -> input.copyTo(out) }
                        }
                        Toast.makeText(this, "저장됨: ${src.name}", Toast.LENGTH_SHORT).show()
                        openPdf(dst)
                    } catch (e: Exception) {
                        Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
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
            openPdf(target)
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

    private fun openPdf(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "파일 없음: ${file.name}", Toast.LENGTH_LONG).show()
            return
        }

        listView.visibility = View.GONE
        emptyText.visibility = View.GONE
        btnBack.visibility = View.VISIBLE
        pdfView.visibility = View.VISIBLE

        pdfView.post {
            try {
                pdfView.fromFile(file)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .enableAntialiasing(true)
                    .spacing(10)
                    .defaultPage(0)
                    .onLoad { pages ->
                        Toast.makeText(
                            this,
                            "${file.name}\n${pages} 페이지",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onError { t ->
                        Toast.makeText(
                            this,
                            "PDF 오류: ${t.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .load()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "로드 실패: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
