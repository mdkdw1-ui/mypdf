package com.mdkdw1.mypdf

import android.graphics.ColorMatrix
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import java.io.File

class ReaderActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener {

    private lateinit var pdfView: PDFView
    private lateinit var toolbar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var tvPageInfo: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnDark: Button
    private lateinit var btnToc: Button
    private lateinit var btnSearch: Button
    private lateinit var btnFav: Button
    private lateinit var btnEbook: Button
    private lateinit var btnClose: Button
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    private lateinit var prefs: ReadingPrefs
    private lateinit var pdfFile: File
    private var pageCount = 0
    private var currentPage = 0
    private var toolbarVisible = true
    private var isHorizontal = false

    companion object {
        const val EXTRA_PATH = "pdf_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null) {
            Toast.makeText(this, "파일 경로 없음", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        pdfFile = File(path)
        if (!pdfFile.exists()) {
            Toast.makeText(this, "파일 없음: $path", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        prefs = ReadingPrefs(this)

        pdfView = findViewById(R.id.pdfView)
        toolbar = findViewById(R.id.toolbar)
        bottomBar = findViewById(R.id.bottomBar)
        tvPageInfo = findViewById(R.id.tvPageInfo)
        seekBar = findViewById(R.id.seekBar)
        btnDark = findViewById(R.id.btnDark)
        btnToc = findViewById(R.id.btnToc)
        btnSearch = findViewById(R.id.btnSearch)
        btnFav = findViewById(R.id.btnFav)
        btnEbook = findViewById(R.id.btnEbook)
        btnClose = findViewById(R.id.btnClose)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)

        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        updateFavButton()

        btnPrev.setOnClickListener { jump(currentPage - 1) }
        btnNext.setOnClickListener { jump(currentPage + 1) }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) jump(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnDark.setOnClickListener {
            prefs.darkMode = !prefs.darkMode
            applyDarkMode()
        }

        btnEbook.setOnClickListener {
            isHorizontal = !isHorizontal
            Toast.makeText(
                this,
                if (isHorizontal) "가로 넘김 (이북)" else "세로 스크롤",
                Toast.LENGTH_SHORT
            ).show()
            loadPdf()
        }

        // 목차: mhiew 라이브러리 API 다름 → 안내만
        btnToc.setOnClickListener {
            Toast.makeText(
                this,
                "이 라이브러리 버전은 목차를 지원하지 않습니다.\n슬라이더를 사용하세요.",
                Toast.LENGTH_LONG
            ).show()
        }

        btnSearch.setOnClickListener { showSearchDialog() }

        btnFav.setOnClickListener {
            prefs.toggleFavorite(pdfFile.name)
            updateFavButton()
            Toast.makeText(
                this,
                if (prefs.isFavorite(pdfFile.name)) "즐겨찾기 추가" else "즐겨찾기 해제",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnClose.setOnClickListener { finish() }
        pdfView.setOnClickListener { toggleToolbar() }

        loadPdf()
    }

    private fun updateFavButton() {
        btnFav.text = if (prefs.isFavorite(pdfFile.name)) "★" else "☆"
    }

    private fun toggleToolbar() {
        toolbarVisible = !toolbarVisible
        val v = if (toolbarVisible) View.VISIBLE else View.GONE
        toolbar.visibility = v
        bottomBar.visibility = v
    }

    private fun loadPdf() {
        val savedPage = prefs.getPage(pdfFile.name)
        prefs.saveLastOpened(pdfFile.name)

        pdfView.fromFile(pdfFile)
            .enableSwipe(true)
            .swipeHorizontal(isHorizontal)
            .enableDoubletap(true)
            .enableAntialiasing(true)
            .spacing(if (isHorizontal) 0 else 10)
            .defaultPage(savedPage)
            .scrollHandle(DefaultScrollHandle(this))
            .onLoad(this)
            .onPageChange(this)
            .load()

        applyDarkMode()
    }

    private fun applyDarkMode() {
        // 배경색만 변경 (setColorMatrix 미지원)
        if (prefs.darkMode) {
            window.decorView.setBackgroundColor(0xFF000000.toInt())
            pdfView.setBackgroundColor(0xFF000000.toInt())
            btnDark.text = "☀"
        } else {
            window.decorView.setBackgroundColor(0xFFEEEEEE.toInt())
            pdfView.setBackgroundColor(0xFFEEEEEE.toInt())
            btnDark.text = "🌙"
        }
    }

    private fun jump(page: Int) {
        if (page in 0 until pageCount) {
            pdfView.jumpTo(page)
        }
    }

    override fun loadComplete(nbPages: Int) {
        pageCount = nbPages
        runOnUiThread {
            seekBar.max = (nbPages - 1).coerceAtLeast(0)
        }
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        currentPage = page
        prefs.savePage(pdfFile.name, page)
        runOnUiThread {
            tvPageInfo.text = "${page + 1} / $pageCount"
            seekBar.progress = page
        }
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "페이지 번호"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("페이지 이동")
            .setMessage("1 ~ $pageCount 사이 숫자 입력")
            .setView(input)
            .setPositiveButton("이동") { _, _ ->
                val n = input.text.toString().toIntOrNull()
                if (n != null && n in 1..pageCount) {
                    jump(n - 1)
                } else {
                    Toast.makeText(this, "1~$pageCount 사이 숫자", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (prefs.volumeKeyPage) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { jump(currentPage + 1); return true }
                KeyEvent.KEYCODE_VOLUME_UP -> { jump(currentPage - 1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
