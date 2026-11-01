package com.mdkdw1.mypdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mdkdw1.mypdf.PdfViewerActivity
import android.content.Intent
import com.mdkdw1.mypdf.ui.LibraryScreen
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colors = if (darkTheme) darkColorScheme(
                primary = Color(0xFFFF6B9D),      // 핑크 포인트
                onPrimary = Color.White,
                secondary = Color(0xFFB794F6),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                onSurface = Color(0xFFE0E0E0),
                surfaceVariant = Color(0xFF2A2A2A),
                onSurfaceVariant = Color(0xFFAAAAAA),
            ) else lightColorScheme(
                primary = Color(0xFFE91E63),      // 진한 핑크
                onPrimary = Color.White,
                secondary = Color(0xFF9C27B0),
                background = Color(0xFFFFF5F8),   // 연한 핑크 배경
                surface = Color.White,
                onSurface = Color(0xFF1A1A1A),
                surfaceVariant = Color(0xFFF5F0F5),
                onSurfaceVariant = Color(0xFF666666),
            )

            MaterialTheme(colorScheme = colors) {
                AppNavHost()
            }
        }
    
        handleIncomingIntent(intent)
}
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent) {
        if (intent.action != android.content.Intent.ACTION_VIEW) return
        val uri = intent.data ?: return

        try {
            val fileName = getFileNameFromUri(uri) ?: "shared"
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext != "pdf" && ext != "hwp" && ext != "hwpx") {
                android.widget.Toast.makeText(this,
                    "지원하지 않는 파일: .$ext",
                    android.widget.Toast.LENGTH_LONG).show()
                return
            }

            // 파일 복사 (content:// → filesDir/pdfs/)
            val dir = java.io.File(filesDir, "pdfs").apply { mkdirs() }
            val dst = java.io.File(dir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(dst).use { out -> input.copyTo(out) }
            } ?: run {
                android.widget.Toast.makeText(this, "파일 읽기 실패", android.widget.Toast.LENGTH_LONG).show()
                return
            }

            val target = if (ext == "hwp" || ext == "hwpx") {
                android.content.Intent(this, HwpViewerActivity::class.java).apply {
                    putExtra(HwpViewerActivity.EXTRA_PATH, dst.absolutePath)
                }
            } else {
                android.content.Intent(this, PdfViewerActivity::class.java).apply {
                    putExtra(PdfViewerActivity.EXTRA_PATH, dst.absolutePath)
                }
            }
            startActivity(target)
        } catch (e: Exception) {
            com.mdkdw1.mypdf.CrashLogger.e(this, "MainActivity", "외부 인텐트 처리 실패", e)
            android.widget.Toast.makeText(this, "파일 열기 실패: ${e.message}",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileNameFromUri(uri: android.net.Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
        return name ?: uri.lastPathSegment
    }
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            val ctx = LocalContext.current
            LibraryScreen(
                onOpenReader = { path ->
                    val ext = java.io.File(path).extension.lowercase()
                    val intent = if (ext == "hwp" || ext == "hwpx") {
                        Intent(ctx, HwpViewerActivity::class.java).apply {
                            putExtra(HwpViewerActivity.EXTRA_PATH, path)
                        }
                    } else {
                        Intent(ctx, PdfViewerActivity::class.java).apply {
                            putExtra(PdfViewerActivity.EXTRA_PATH, path)
                        }
                    }
                    ctx.startActivity(intent)
                }
            )
        }
    }
}
