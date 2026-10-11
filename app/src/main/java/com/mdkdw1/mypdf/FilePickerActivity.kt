package com.mdkdw1.mypdf

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
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

    // ── 🎨 컬러 팔레트 (다크) ──
    private val bgColor = Color.parseColor("#0D1117")          // 배경
    private val topBarColor = Color.parseColor("#161B22")      // 상단바
    private val searchBg = Color.parseColor("#21262D")         // 검색창
    private val primary = Color.parseColor("#FF6B9D")          // 핑크
    private val textPrimary = Color.parseColor("#F0F6FC")      // 밝은 흰색
    private val textSecondary = Color.parseColor("#8B949E")    // 회색
    private val divider = Color.parseColor("#30363D")          // 구분선
    private val badgePdf = Color.parseColor("#F85149")         // PDF 뱃지 빨강
    private val folderBlue = Color.parseColor("#58A6FF")       // 폴더 파랑
    private val pdfPink = Color.parseColor("#FF7B9C")          // PDF 핑크

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = topBarColor
            window.navigationBarColor = bgColor
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            fitsSystemWindows = true
        }

        // ── 상단 바 ──
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 16, 12, 16)
            setBackgroundColor(topBarColor)
            gravity = Gravity.CENTER_VERTICAL
        }

        btnClose = Button(this).apply {
            text = "✕"
            setTextColor(textPrimary)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 20f
            minWidth = 0
            minimumWidth = 0
            setPadding(40, 20, 40, 20)
            setOnClickListener { finish() }
        }
        btnUp = Button(this).apply {
            text = "↑"
            setTextColor(primary)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 20f
            minWidth = 0
            minimumWidth = 0
            setPadding(40, 20, 40, 20)
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
            textSize = 13f
            setTextColor(primary)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            setPadding(16, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        topBar.addView(btnClose)
        topBar.addView(btnUp)
        topBar.addView(tvPath)
        root.addView(topBar)

        // ── 검색창 ──
        val searchWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 12)
        }

        etSearch = EditText(this).apply {
            hint = "🔍  검색..."
            setHintTextColor(textSecondary)
            setTextColor(textPrimary)
            textSize = 14f
            setPadding(40, 32, 40, 32)
            setSingleLine(true)
            background = GradientDrawable().apply {
                setColor(searchBg)
                cornerRadius = 60f
                setStroke(2, divider)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    searchQuery = s?.toString() ?: ""
                    applyFilter()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        searchWrapper.addView(etSearch)
        root.addView(searchWrapper)

        // ── 요약 ──
        tvSummary = TextView(this).apply {
            textSize = 11f
            setTextColor(textSecondary)
            setPadding(24, 0, 24, 12)
        }
        root.addView(tvSummary)

        // ── 빈 상태 ──
        tvEmpty = TextView(this).apply {
            textSize = 14f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setPadding(24, 80, 24, 80)
            visibility = View.GONE
        }
        root.addView(tvEmpty)

        // ── 리스트 ──
        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setDivider(android.graphics.drawable.ColorDrawable(divider))
            setDividerHeight(1)
            setBackgroundColor(bgColor)
            setPadding(0, 8, 0, 8)
            clipToPadding = false
        }
        root.addView(listView)

        setContentView(root)

        if (!checkStoragePermission()) requestStoragePermission() else loadDir()
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

        subFolders.forEach { f ->
            val hasPdf = hasPdfDirect(f)
            val icon = if (hasPdf) "📁🔴" else "📁"
            val subCount = try { f.listFiles()?.size ?: 0 } catch (_: Exception) { 0 }
            allItems.add(Triple(f, "$icon ${f.name}", "$subCount 항목"))
        }
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
        tvSummary.text = "📁 $folders  ·  📄 $pdfs"
    }

    private fun refreshList() {
        if (filteredItems.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (searchQuery.isNotBlank()) "🔍 검색 결과 없음"
                           else "📭 이 폴더에 PDF나 하위 폴더가 없습니다"
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
                    setPadding(40, 36, 40, 36)
                    gravity = Gravity.CENTER_VERTICAL
                }
                (v as LinearLayout).removeAllViews()

                val (file, title, sub) = filteredItems[position]
                val isPdf = file.isFile
                val hasPdf = !isPdf && title.startsWith("📁🔴")

                // 🎨 아이콘 — 원형 그라데이션
                val iconWrapper = FrameLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(130, 130)
                }

                val baseColor = if (isPdf) pdfPink else folderBlue

                val iconBg = View(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(120, 120).apply {
                        gravity = Gravity.CENTER
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        colors = intArrayOf(
                            baseColor,
                            Color.parseColor("#0D1117")  // 어두운 배경과 어울리게
                        )
                        gradientType = GradientDrawable.LINEAR_GRADIENT
                        orientation = GradientDrawable.Orientation.TL_BR
                    }
                }

                val iconText = TextView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(120, 120)
                    text = if (isPdf) "📄" else "📁"
                    textSize = 30f
                    gravity = Gravity.CENTER
                }

                iconWrapper.addView(iconBg)
                iconWrapper.addView(iconText)

                // 🔴 PDF 있음 뱃지
                if (hasPdf) {
                    val badge = TextView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(40, 40).apply {
                            gravity = Gravity.TOP or Gravity.END
                        }
                        text = "●"
                        setTextColor(badgePdf)
                        textSize = 18f
                        gravity = Gravity.CENTER
                    }
                    iconWrapper.addView(badge)
                }

                // 정보
                val infoLayout = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(28, 0, 0, 0)
                }

                val cleanTitle = title.removePrefix("📁🔴 ").removePrefix("📁 ").removePrefix("📄 ")

                val titleView = TextView(ctx).apply {
                    text = cleanTitle
                    textSize = 15f
                    setTextColor(textPrimary)   // ⭐ 밝은 흰색 — 이제 잘 보임
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                }

                val subView = TextView(ctx).apply {
                    text = sub
                    textSize = 11f
                    setTextColor(if (hasPdf) primary else textSecondary)  // PDF 있음 = 핑크
                    setPadding(0, 6, 0, 0)
                }

                infoLayout.addView(titleView)
                infoLayout.addView(subView)

                // 우측 화살표 (폴더만)
                if (file.isDirectory) {
                    val arrow = TextView(ctx).apply {
                        text = "›"
                        textSize = 24f
                        setTextColor(textSecondary)
                        setPadding(16, 0, 0, 0)
                    }
                    v.addView(iconWrapper)
                    v.addView(infoLayout)
                    v.addView(arrow)
                } else {
                    v.addView(iconWrapper)
                    v.addView(infoLayout)
                }

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
