package com.mdkdw1.mypdf

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class FilePickerActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var tvPath: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnUp: Button
    private lateinit var btnClose: Button

    private var currentDir: File = File("/storage/emulated/0")

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
        if (blockedNames.contains(name)) return true
        return false
    }

    private fun hasPdfDirect(dir: File): Boolean {
        val files = dir.listFiles() ?: return false
        return files.any { it.isFile && it.extension.lowercase() == "pdf" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_picker)

        listView = findViewById(R.id.listFiles)
        tvPath = findViewById(R.id.tvPath)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnUp = findViewById(R.id.btnUp)
        btnClose = findViewById(R.id.btnClose)

        btnClose.setOnClickListener { finish() }

        btnUp.setOnClickListener {
            val parent = currentDir.parentFile
            val roots = listOf("/storage/emulated/0", "/storage")
            if (parent != null && parent.canRead() &&
                !roots.contains(currentDir.absolutePath)) {
                currentDir = parent
                loadDir()
            } else {
                Toast.makeText(this, "최상위 폴더입니다", Toast.LENGTH_SHORT).show()
            }
        }

        // ── 권한 처리 ──
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
            // Android 11+: 모든 파일 액세스 권한 요청
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, 2001)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, 2001)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                2002
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 2001) {
            // Settings에서 돌아옴 → 재확인
            if (checkStoragePermission()) {
                loadDir()
            } else {
                Toast.makeText(
                    this,
                    "저장소 권한이 필요합니다.\n설정 → 앱 → 권한 → 모든 파일 액세스",
                    Toast.LENGTH_LONG
                ).show()
                // 권한 없어도 앱 내부 저장소는 보여줌
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
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadDir()
        } else {
            Toast.makeText(this, "권한 거부됨", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadDir() {
        tvPath.text = currentDir.absolutePath

        val allFiles = currentDir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()

        // 권한 없어 listFiles() null이면 안내
        if (allFiles.isEmpty() && !currentDir.canRead()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "폴더를 읽을 수 없습니다.\n모든 파일 액세스 권한을 허용해주세요."
            return
        }

        val pdfFiles = allFiles.filter { f ->
            f.isFile && f.extension.lowercase() == "pdf"
        }
        val subFolders = allFiles.filter { f ->
            f.isDirectory && !isBlocked(f.name)
        }

        val display = mutableListOf<Pair<File, String>>()
        subFolders.forEach { f ->
            val prefix = if (hasPdfDirect(f)) "📁🔴" else "📁"
            display.add(f to "$prefix ${f.name}")
        }
        pdfFiles.forEach { f ->
            display.add(f to "📄 ${f.name}")
        }

        if (display.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "이 폴더에 PDF나 하위 폴더가 없습니다"
            return
        }

        listView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        listView.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            display.map { it.second }
        )

        listView.setOnItemClickListener { _, _, pos, _ ->
            val (file, _) = display[pos]
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
        val roots = listOf("/storage/emulated/0", "/storage")
        if (parent != null && parent.canRead() &&
            !roots.contains(currentDir.absolutePath)) {
            currentDir = parent
            loadDir()
        } else {
            super.onBackPressed()
        }
    }
}
