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
                    val intent = Intent(ctx, PdfViewerActivity::class.java).apply {
                        putExtra(PdfViewerActivity.EXTRA_PATH, path)
                    }
                    ctx.startActivity(intent)
                }
            )
        }
    }
}
