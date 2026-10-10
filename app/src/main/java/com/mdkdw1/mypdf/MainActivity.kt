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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mdkdw1.mypdf.ui.EbookReaderScreen
import com.mdkdw1.mypdf.ui.LibraryScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colors = if (darkTheme) darkColorScheme(
                primary = Color(0xFFFFB6D9),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
            ) else lightColorScheme(
                primary = Color(0xFFD6336C),
                background = Color(0xFFFFF5F8),
                surface = Color.White,
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
            LibraryScreen(
                onOpenReader = { path -> nav.navigate("reader?path=$path") }
            )
        }
        composable("reader?path={path}") { back ->
            val path = back.arguments?.getString("path") ?: return@composable
            EbookReaderScreen(
                path = path,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
