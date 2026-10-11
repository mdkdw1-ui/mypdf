package com.mdkdw1.mypdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfPagePreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "pdf_path"
        const val EXTRA_PAGE = "page_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        val pageIndex = intent.getIntExtra(EXTRA_PAGE, 0)
        val file = File(path)
        if (!file.exists()) { finish(); return }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#0D1117"))

        // ⭐ PhotoView (핀치/더블탭 줌)
        val photoView = PhotoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            minimumScale = 1f
            mediumScale = 2f
            maximumScale = 8f
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        root.addView(photoView)

        // 상단 (페이지 번호 + 닫기)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(16, 24, 16, 12)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        }

        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(16, 24 + statusBar.top, 16, 12)
            insets
        }

        val btnClose = Button(this).apply {
            text = "✕"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0)
            setOnClickListener { finish() }
        }

        val tvTitle = TextView(this).apply {
            text = "페이지 ${pageIndex + 1}"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        topBar.addView(btnClose)
        topBar.addView(tvTitle)
        root.addView(topBar)

        setContentView(root)

        // ⭐ 비동기 렌더링
        CoroutineScope(Dispatchers.Main).launch {
            val bmp = withContext(Dispatchers.IO) {
                renderPage(file, pageIndex, 2400)
            }
            if (bmp != null) {
                photoView.setImageBitmap(bmp)
            }
        }
    }

    private fun renderPage(file: File, pageIndex: Int, targetWidth: Int): Bitmap? {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val scale = targetWidth.toFloat() / page.width
                    val h = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
