package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object PdfPageCapture {

    /** PDF 페이지를 Bitmap으로 렌더링 */
    suspend fun renderPage(
        context: Context,
        file: File,
        pageIndex: Int,
        maxWidth: Int = 1200
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                renderer.openPage(pageIndex).use { page ->
                    val scale = maxWidth.toFloat() / page.width
                    val w = maxWidth
                    val h = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        } catch (e: Exception) {
            CrashLogger.e(context, "Capture", "렌더 실패", e)
            null
        }
    }

    /** Bitmap → Base64 (JPEG 압축) */
    fun toBase64Jpeg(bitmap: Bitmap, quality: Int = 85): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
    }

    /** Bitmap → Base64 (PNG) — 텍스트 선명도 우선 */
    fun toBase64Png(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
    }
}
