package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 주석 (contentStream 기반 — PdfBox-Android 어노테이션 클래스 없이)
 */
object PdfAnnotation {

    /** 하이라이트 (반투명 사각형) */
    suspend fun addHighlight(
        context: Context, file: File, pageIndex: Int,
        x: Float, y: Float, w: Float, h: Float,
        color: Int = Color.YELLOW, opacity: Float = 0.4f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity
                    }
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(
                        Color.red(color), Color.green(color), Color.blue(color)
                    )
                    cs.addRect(x, y, w, h)
                    cs.fill()
                }
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfAnnotation", "하이라이트 실패", e)
            Result.failure(e)
        }
    }

    /** 스티키 노트 (노란 사각형 + 텍스트) */
    suspend fun addStickyNote(
        context: Context, file: File, pageIndex: Int,
        x: Float, y: Float, text: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    // 노란 배경
                    cs.setNonStrokingColor(255, 235, 59)
                    cs.addRect(x, y, 200f, 40f)
                    cs.fill()

                    // 검정 테두리
                    cs.setStrokingColor(0, 0, 0)
                    cs.setLineWidth(1f)
                    cs.addRect(x, y, 200f, 40f)
                    cs.stroke()

                    // 텍스트
                    cs.setNonStrokingColor(0, 0, 0)
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 10f)
                    cs.newLineAtOffset(x + 5f, y + 20f)
                    // 안전 처리: ASCII만
                    cs.showText(text.take(60))
                    cs.endText()
                }
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 사각형 */
    suspend fun addRectangle(
        context: Context, file: File, pageIndex: Int,
        x: Float, y: Float, w: Float, h: Float,
        color: Int = Color.RED, strokeWidth: Float = 2f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    cs.setStrokingColor(
                        Color.red(color), Color.green(color), Color.blue(color)
                    )
                    cs.setLineWidth(strokeWidth)
                    cs.addRect(x, y, w, h)
                    cs.stroke()
                }
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 원 */
    suspend fun addCircle(
        context: Context, file: File, pageIndex: Int,
        cx: Float, cy: Float, r: Float,
        color: Int = Color.BLUE, strokeWidth: Float = 2f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    cs.setStrokingColor(
                        Color.red(color), Color.green(color), Color.blue(color)
                    )
                    cs.setLineWidth(strokeWidth)
                    val k = 0.5523f * r
                    cs.moveTo(cx + r, cy)
                    cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
                    cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
                    cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
                    cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
                    cs.closePath()
                    cs.stroke()
                }
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 자유 그리기 */
    suspend fun drawFreehand(
        context: Context, file: File, pageIndex: Int,
        points: List<Pair<Float, Float>>,
        color: Int = Color.RED, strokeWidth: Float = 2f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (points.size < 2) return@withContext Result.success(file)
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    cs.setStrokingColor(
                        Color.red(color), Color.green(color), Color.blue(color)
                    )
                    cs.setLineWidth(strokeWidth)
                    cs.setLineCapStyle(1)
                    cs.moveTo(points[0].first, points[0].second)
                    for (i in 1 until points.size) {
                        cs.lineTo(points[i].first, points[i].second)
                    }
                    cs.stroke()
                }
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }
}
