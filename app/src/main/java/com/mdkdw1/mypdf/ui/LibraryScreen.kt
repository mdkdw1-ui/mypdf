package com.mdkdw1.mypdf.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdkdw1.mypdf.FilePickerActivity
import com.mdkdw1.mypdf.ReadingPrefs
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenReader: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ReadingPrefs(context) }
    var sortMode by remember { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val path = result.data?.getStringExtra("pdf_path") ?: return@rememberLauncherForActivityResult
            val src = File(path)
            if (src.exists()) {
                val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
                val dst = File(dir, src.name)
                try {
                    src.inputStream().use { input ->
                        FileOutputStream(dst).use { out -> input.copyTo(out) }
                    }
                    refresh++
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context, "저장 실패: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val files = remember(sortMode, refresh) {
        val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val list = dir.listFiles { f -> f.extension.lowercase() == "pdf" }?.toList() ?: emptyList()
        when (sortMode) {
            0 -> list.sortedByDescending { prefs.getLastOpened(it.name) }
            1 -> list.sortedBy { it.name.lowercase() }
            2 -> list.sortedByDescending {
                (if (prefs.isFavorite(it.name)) 1_000_000_000L else 0L) +
                        prefs.getLastOpened(it.name)
            }
            else -> list
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My PDF for HJ") },
                actions = {
                    IconButton(onClick = { sortMode = (sortMode + 1) % 3 }) {
                        Icon(Icons.Filled.Sort, contentDescription = "정렬")
                    }
                    IconButton(onClick = {
                        launcher.launch(Intent(context, FilePickerActivity::class.java))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "PDF 추가")
                    }
                }
            )
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "저장된 PDF가 없습니다.\n[+ PDF 추가] 버튼을 눌러주세요.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(files, key = { it.absolutePath }) { file ->
                    val fav = prefs.isFavorite(file.name)
                    ListItem(
                        headlineContent = { Text(file.name) },
                        supportingContent = {
                            Text("%.1f MB".format(file.length() / 1024.0 / 1024.0))
                        },
                        leadingContent = {
                            if (fav) Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { onOpenReader(file.absolutePath) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
