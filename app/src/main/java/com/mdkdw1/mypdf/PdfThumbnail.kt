package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfThumbnail {

    suspend fun get(context: Context, file: File, width: Int = 200): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.filesDir, "thumbs").apply { mkdirs() }
                val cacheFile = File(cacheDir, "${file.nameWithoutExtension}.png")

                if (cacheFile.exists() && cacheFile.lastModified() >= file.lastModified()) {
                    return@withContext BitmapFactory.decodeFile(cacheFile.absolutePath)
                }

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@withContext null
                    renderer.openPage(0).use { page ->
                        val scale = width.toFloat() / page.width
                        val h = (page.height * scale).toInt()
                        val bitmap = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        try {
                            cacheFile.outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
                            }
                        } catch (_: Exception) {}

                        return@withContext bitmap
                    }
                }
                null
            } catch (e: Exception) {
                CrashLogger.e(context, "Thumb", "생성 실패: ${file.name}", e)
                null
            }
        }

    fun clearCache(context: Context) {
        try {
            File(context.filesDir, "thumbs").deleteRecursively()
        } catch (_: Exception) {}
    }
}
