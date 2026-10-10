package com.mdkdw1.mypdf

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private val PICK_PDF_FILE = 2
    private lateinit var pdfView: PDFView
    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var btnAdd: Button
    private lateinit var btnBack: Button
    private lateinit var rootLayout: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pdfView = findViewById(R.id.pdfView)
        listView = findViewById(R.id.listView)
        emptyText = findViewById(R.id.emptyText)
        btnAdd = findViewById(R.id.btnAdd)
        btnBack = findViewById(R.id.btnBack)
        rootLayout = findViewById(R.id.rootLayout)

        btnAdd.setOnClickListener { pickPdf() }
        btnBack.setOnClickListener {
            pdfView.visibility = View.GONE
            showList()
        }

        // 인텐트로 PDF 열기 요청이 오면 바로 처리
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

    // ── 앱 내부 저장소의 PDF 목록 표시 ──
    private fun showList() {
        listView.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        btnBack.visibility = View.GONE

        val files = getPdfFiles()
        if (files.isEmpty()) {
            listView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = "저장된 PDF가 없습니다.\n[PDF 추가] 버튼을 눌러주세요."
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

    // ── PDF 추가 (SAF 파일 선택기) ──
    private fun pickPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, PICK_PDF_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PDF_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                copyToInternal(uri)
                showList()
            }
        }
    }

    private fun copyToInternal(uri: Uri) {
        try {
            val dir = File(filesDir, "pdfs").apply { mkdirs() }
            val name = queryFileName(uri) ?: "document_${System.currentTimeMillis()}.pdf"
            val target = File(dir, name)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            }
            Toast.makeText(this, "저장됨: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
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

    // ── 인텐트로 온 URI 처리 ──
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

    // ── PDF 열기 ──
    private fun openPdf(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "파일 없음: ${file.name}", Toast.LENGTH_LONG).show()
            return
        }

        listView.visibility = View.GONE
        emptyText.visibility = View.GONE
        btnBack.visibility = View.VISIBLE
        pdfView.visibility = View.VISIBLE

        // ⭐️ 흰 화면 방지: 레이아웃 완료 후 로드
        pdfView.post {
            try {
                pdfView.fromFile(file)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .enableAntialiasing(true)
                    .spacing(10)
                    .defaultPage(0)
                    .onLoad {
                        Toast.makeText(
                            this,
                            "${file.name}\n${pageCount} 페이지",
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
                    .onPageError { page, t ->
                        Toast.makeText(
                            this,
                            "페이지 $page 오류: ${t.message}",
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
