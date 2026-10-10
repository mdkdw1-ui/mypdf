package com.mdkdw1.mypdf

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pdfView = PDFView(this)
        setContentView(pdfView)

        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { openUri(it) } ?: loadDefault()
            else -> loadDefault()
        }
    }

    private fun loadDefault() {
        val file = File(filesDir, "sample.pdf")
        if (file.exists()) {
            pdfView.fromFile(file).enableSwipe(true).load()
        } else {
            Toast.makeText(this, "PDF 파일을 열어주세요", Toast.LENGTH_LONG).show()
        }
    }

    private fun openUri(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri) ?: return
            val tmp = File(cacheDir, "temp.pdf")
            FileOutputStream(tmp).use { out -> input.copyTo(out) }
            input.close()
            pdfView.fromFile(tmp).enableSwipe(true).load()
        } catch (e: Exception) {
            Toast.makeText(this, "열기 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
