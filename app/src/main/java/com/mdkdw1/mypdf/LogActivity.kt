package com.mdkdw1.mypdf

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var tvLog: TextView
    private var currentMode = "app"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val btnApp = Button(this).apply { text = "앱" }
        val btnCrash = Button(this).apply { text = "크래시" }
        val btnShare = Button(this).apply { text = "공유" }
        val btnClear = Button(this).apply { text = "지우기" }
        val btnClose = Button(this).apply { text = "닫기" }

        topBar.addView(btnApp)
        topBar.addView(btnCrash)
        topBar.addView(btnShare)
        topBar.addView(btnClear)
        topBar.addView(btnClose)

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        tvLog = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        scrollView.addView(tvLog)

        root.addView(topBar)
        root.addView(scrollView)
        setContentView(root)

        loadLog()

        btnApp.setOnClickListener {
            currentMode = "app"
            loadLog()
        }
        btnCrash.setOnClickListener {
            currentMode = "crash"
            loadLog()
        }
        btnShare.setOnClickListener {
            val log = if (currentMode == "app")
                CrashLogger.readLog(this@LogActivity)
            else
                CrashLogger.readCrash(this@LogActivity)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MyPDF 로그")
                putExtra(Intent.EXTRA_TEXT, log)
            }
            startActivity(Intent.createChooser(shareIntent, "로그 공유"))
        }
        btnClear.setOnClickListener {
            if (currentMode == "app") CrashLogger.clearLog(this@LogActivity)
            else CrashLogger.clearCrash(this@LogActivity)
            loadLog()
            Toast.makeText(this@LogActivity, "지워짐", Toast.LENGTH_SHORT).show()
        }
        btnClose.setOnClickListener { finish() }
    }

    private fun loadLog() {
        val log = if (currentMode == "app")
            CrashLogger.readLog(this)
        else
            CrashLogger.readCrash(this)
        tvLog.text = log
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
