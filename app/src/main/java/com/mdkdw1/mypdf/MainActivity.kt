package com.mdkdw1.mypdf

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var currentPage = 0
    private var pageCount = 0

    private lateinit var imageView: ImageView
    private lateinit var pageLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val prevBtn = Button(this).apply { text = "◀ 이전" }
        val nextBtn = Button(this).apply { text = "다음 ▶" }
        pageLabel = TextView(this).apply {
            text = "0 / 0"
            setPadding(24, 24, 24, 24)
        }

        topBar.addView(prevBtn)
        topBar.addView(pageLabel)
        topBar.addView(nextBtn)

        imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            adjustViewBounds = true
        }

        root.addView(topBar)
        root.addView(imageView)
        setContentView(root)

        prevBtn.setOnClickListener { goToPage(currentPage - 1) }
        nextBtn.setOnClickListener { goToPage(currentPage + 1) }

        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { openUri(it) } ?: loadDefault()
            else -> loadDefault()
        }
    }

    private fun loadDefault() {
        val file = File(filesDir, "sample.pdf")
        if (file.exists()) {
            openFile(file)
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
            openFile(tmp)
        } catch (e: Exception) {
            Toast.makeText(this, "열기 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openFile(file: File) {
        closeRenderer()
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd!!)
            pageCount = renderer!!.pageCount
            currentPage = 0
            renderPage()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF 열기 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun goToPage(index: Int) {
        if (renderer == null) return
        if (index < 0 || index >= pageCount) return
        currentPage = index
        renderPage()
    }

    private fun renderPage() {
        val r = renderer ?: return
        val page = r.openPage(currentPage)
        val scale = 2
        val bmp = Bitmap.createBitmap(
            page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888
        )
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        imageView.setImageBitmap(bmp)
        pageLabel.text = "${currentPage + 1} / $pageCount"
    }

    private fun closeRenderer() {
        try {
            renderer?.close()
            pfd?.close()
        } catch (_: Exception) {}
        renderer = null
        pfd = null
    }

    override fun onDestroy() {
        closeRenderer()
        super.onDestroy()
    }
}
