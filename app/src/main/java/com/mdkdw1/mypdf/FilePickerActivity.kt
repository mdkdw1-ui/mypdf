package com.mdkdw1.mypdf

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class FilePickerActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var tvPath: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSummary: TextView
    private lateinit var btnUp: Button
    private lateinit var btnClose: Button
    private lateinit var etSearch: EditText

    private var currentDir: File = File("/storage/emulated/0")
    private var allItems: MutableList<Triple<File, String, String>> = mutableListOf()
    private var filteredItems: MutableList<Triple<File, String, String>> = mutableListOf()
    private var searchQuery: String = ""

    private val blockedNames = setOf(
        "Android", "android", "data", "obb",
        "MIUI", "miui", "tencent", "Tencent",
        "System", "system", "cache", "Cache",
        "tmp", "temp", "Temp", "lost+found", "LOST.DIR",
        ".thumbnails", "log", "logs", "Log", "backups"
    )

    private fun isBlocked(name: String): Boolean {
        if (name.startsWith(".")) return true
        if (name.startsWith("com.")) return true
        if (name.startsWith("org.")) return true
        if (name.startsWith("me.")) return true
        return blockedNames.contains(name)
    }

    private fun hasPdfDirect(dir: File): Boolean {
        val files = dir.listFiles() ?: return false
        return files.any { it.isFile && it.extension.lowercase() == "pdf" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── 프로그래매틱 UI ──
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 상단 바
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFFF5F5F5.toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        btnClose = Button(this).apply {
            text = "✕"
            minWidth = 100
            setOnClickListener { finish() }
        }
        btnUp = Button(this).apply {
            text = "↑"
            minWidth = 100
            setOnClickListener {
                val parent = currentDir.parentFile
                if (parent != null && parent.canRead() &&
                    currentDir.absolutePath != "/storage/emulated/0") {
                    currentDir = parent
                    loadDir()
                } else {
                    Toast.makeText(this@FilePickerActivity, "최상위 폴더", Toast.LENGTH_SHORT).show()
                }
            }
        }
        tvPath = TextView(this).apply {
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            setPadding(12, 0, 12, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        topBar.addView(btnClose)
        topBar.addView(btnUp)
        topBar.addView(tvPath)
        root.addView(topBar)

        // 검색창
        etSearch = EditText(this).apply {
            hint = "현재 폴더에서 검색..."
            singleLine = true
            setPadding(24, 20, 24, 20)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    searchQuery = s?.toString() ?: ""
                    applyFilter()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        root.addView(etSearch)

        // 요약 정보
        tvSummary = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFF888888.toInt())
            setPadding(24, 8, 24, 8)
        }
        root.addView(tvSummary)

        // 빈 상태
        tvEmpty = TextView(this).apply {
            text = "이 폴더에 PDF가 없습니다"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(24, 48, 24, 48)
            visibility = View.GONE
        }
        root.addView(tvEmpty)

        // 리스트
        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            divider = android.graphics.drawable.ColorDrawable(0xFFEEEEEE.toInt())
            dividerHeight = 2
        }
        root.addView(listView)

        setContentView(root)

        // 권한 체크
        if (!checkStoragePermission()) {
            requestStoragePermission()
        } else {
            loadDir()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, 2001)
            } catch (e: Exception) {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), 2001
                )
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 2002
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001) {
            if (checkStoragePermission()) loadDir()
            else {
                Toast.makeText(this, "권한 필요", Toast.LENGTH_LONG).show()
                currentDir = filesDir
                loadDir()
            }
        } else if (requestCode == 2002) {
            if (checkStoragePermission()) loadDir()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2002 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) loadDir()
    }

    private fun loadDir() {
        tvPath.text = currentDir.absolutePath
        etSearch.setText("")

        val files = currentDir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()

        val pdfFiles = files.filter { it.isFile && it.extension.lowercase() == "pdf" }
        val subFolders = files.filter { it.isDirectory && !isBlocked(it.name) }

        allItems = mutableListOf()

        // 폴더 먼저 (PDF 있으면 🔴)
        subFolders.forEach { f ->
            val hasPdf = hasPdfDirect(f)
            val icon = if (hasPdf) "📁🔴" else "📁"
            val subCount = try { f.listFiles()?.size ?: 0 } catch (_: Exception) { 0 }
            allItems.add(Triple(f, "$icon ${f.name}", "$subCount 항목"))
        }
        // PDF 파일 (크기 표시)
        pdfFiles.forEach { f ->
            val mb = "%.1f MB".format(f.length() / 1024.0 / 1024.0)
            allItems.add(Triple(f, "📄 ${f.name}", mb))
        }

        filteredItems = allItems.toMutableList()
        updateSummary()
        refreshList()
    }

    private fun applyFilter() {
        filteredItems = if (searchQuery.isBlank()) {
            allItems.toMutableList()
        } else {
            allItems.filter {
                it.first.name.contains(searchQuery, ignoreCase = true)
            }.toMutableList()
        }
        updateSummary()
        refreshList()
    }

    private fun updateSummary() {
        val folders = filteredItems.count { it.first.isDirectory }
        val pdfs = filteredItems.count { it.first.isFile }
        tvSummary.text = "폴더 $folders · PDF $pdfs"
    }

    private fun refreshList() {
        if (filteredItems.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (searchQuery.isNotBlank()) "검색 결과 없음"
                           else "이 폴더에 PDF나 하위 폴더가 없습니다"
            return
        }
        listView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        listView.adapter = object : BaseAdapter() {
            override fun getCount() = filteredItems.size
            override fun getItem(position: Int) = filteredItems[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val ctx = this@FilePickerActivity
                val v = convertView ?: LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(32, 28, 32, 28)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                (v as LinearLayout).removeAllViews()

                val (file, title, sub) = filteredItems[position]

                // 아이콘 (별도 TextView, 큰 글씨)
                val iconView = TextView(ctx).apply {
                    val iconText = if (file.isDirectory) {
                        if (title.startsWith("📁🔴")) "📁" else "📁"
                    } else "📄"
                    text = iconText
                    textSize = 28f
                    setPadding(0, 0, 24, 0)
                }

                // 정보 (제목 + 부제)
                val infoLayout = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val cleanTitle = title.removePrefix("📁🔴 ").removePrefix("📁 ").removePrefix("📄 ")

                val titleView = TextView(ctx).apply {
                    text = cleanTitle
                    textSize = 15f
                    setTextColor(if (file.isDirectory) 0xFF1A1A1A.toInt() else 0xFF333333.toInt())
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                }

                val subView = TextView(ctx).apply {
                    text = if (file.isDirectory && title.startsWith("📁🔴")) "🔴 $sub" else sub
                    textSize = 11f
                    setTextColor(if (title.startsWith("📁🔴")) 0xFFE91E63.toInt() else 0xFF888888.toInt())
                }

                infoLayout.addView(titleView)
                infoLayout.addView(subView)

                v.addView(iconView)
                v.addView(infoLayout)

                return v
            }
        }

        listView.setOnItemClickListener { _, _, pos, _ ->
            val (file, _, _) = filteredItems[pos]
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
        if (parent != null && parent.canRead() &&
            currentDir.absolutePath != "/storage/emulated/0") {
            currentDir = parent
            loadDir()
        } else super.onBackPressed()
    }
}
