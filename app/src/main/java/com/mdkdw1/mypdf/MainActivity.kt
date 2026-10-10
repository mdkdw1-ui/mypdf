package com.mdkdw1.mypdf

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private val PICK_PDF_FILE = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pdfView = findViewById(R.id.pdfView)

        // 앱 실행 시 파일 선택기 즉시 띄우기
        openFilePicker()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, PICK_PDF_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PDF_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri -> openUri(uri) }
        }
    }

    private fun openUri(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri) ?: return
            val tmp = File(cacheDir, "temp.pdf")
            FileOutputStream(tmp).use { out -> input.copyTo(out) }
            input.close()
            showPdf(tmp)
        } catch (e: Exception) {
            Toast.makeText(this, "열기 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPdf(file: File) {
        pdfView.fromFile(file)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .enableAntialiasing(true) // 부드러운 렌더링
            .spacing(10)              // 페이지 간격 (부드러운 스크롤 효과)
            .defaultPage(0)
            .load()
    }
}
