package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 텍스트 편집 (기존 수정 + 새 텍스트 추가)
 * - ASCII: PdfBox 폰트 직접 사용
 * - 한글/유니코드: Bitmap → 이미지로 삽입
 */
object PdfTextEditor {

    /**
     * ⭐ 안전 저장: 임시 파일 → 원본 교체 (PdfBox 2.x 이슈 회피)
     */
    private fun safeSave(doc: PDDocument, target: File, context: Context): Boolean {
        return try {
            val beforeSize = if (target.exists()) target.length() else 0

            val tmp = File(context.cacheDir, "tmp_${System.currentTimeMillis()}.pdf")
            doc.save(tmp)

            if (target.exists()) target.delete()
            tmp.copyTo(target, overwrite = true)
            tmp.delete()

            val afterSize = target.length()
            CrashLogger.log(context, "PdfTextEditor",
                "safeSave: $beforeSize → $afterSize bytes (${if (afterSize > beforeSize) "+" else ""}${afterSize - beforeSize})")
            true
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfTextEditor", "safeSave 실패", e)
            false
        }
    }

    /** 텍스트에 한글이나 비-ASCII 문자 포함 여부 */
    private fun hasNonAscii(text: String): Boolean =
        text.any { it.code > 127 }

    /**
     * 기존 텍스트를 덮고 새 텍스트 쓰기 (오버레이 방식)
     * @param originalRect 원본 텍스트 영역 (PDF 좌표, 좌하단)
     * @param newText 새 텍스트
     * @param fontSize 폰트 크기 (pt)
     * @param color 텍스트 색
     * @param bgColor 배경색 (원본 덮을 색, 기본 흰색)
     */
    suspend fun replaceText(
        context: Context,
        file: File,
        pageIndex: Int,
        originalLeft: Float, originalBottom: Float,
        originalWidth: Float, originalHeight: Float,
        newText: String,
        fontSize: Float = 12f,
        color: Int = Color.BLACK,
        bgColor: Int = Color.WHITE
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            CrashLogger.log(context, "PdfTextEditor",
                "replaceText: pos=(${"%.1f".format(originalLeft)}, ${"%.1f".format(originalBottom)}) " +
                "size=(${"%.1f".format(originalWidth)}x${"%.1f".format(originalHeight)}) " +
                "text='${newText.take(20)}'")
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    // 1) 원본 텍스트 덮기
                    cs.setNonStrokingColor(
                        Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)
                    )
                    // 여유 패딩
                    cs.addRect(
                        originalLeft - 1f,
                        originalBottom - 1f,
                        originalWidth + 2f,
                        originalHeight + 2f
                    )
                    cs.fill()

                    // 2) 새 텍스트
                    if (newText.isNotBlank()) {
                        if (hasNonAscii(newText)) {
                            // 한글: Bitmap → 이미지
                            drawTextAsImage(
                                doc, cs, newText, fontSize, color,
                                originalLeft, originalBottom, originalWidth, originalHeight
                            )
                        } else {
                            // ASCII: PdfBox 폰트
                            cs.setNonStrokingColor(
                                Color.red(color), Color.green(color), Color.blue(color)
                            )
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA, fontSize)
                            cs.newLineAtOffset(originalLeft, originalBottom + originalHeight * 0.25f)
                            cs.showText(newText)
                            cs.endText()
                        }
                    }
                }
                safeSave(doc, file, context)
                CrashLogger.log(context, "PdfTextEditor",
                    "✅ 텍스트 저장: '${newText.take(20)}' → ${file.name}")
            }
            Result.success(file)
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfTextEditor", "텍스트 교체 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 새 텍스트 추가 (기존 텍스트 없음)
     */
    suspend fun addText(
        context: Context,
        file: File,
        pageIndex: Int,
        x: Float, y: Float,
        text: String,
        fontSize: Float = 14f,
        color: Int = Color.BLACK
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    if (hasNonAscii(text)) {
                        drawTextAsImage(
                            doc, cs, text, fontSize, color,
                            x, y, 0f, 0f
                        )
                    } else {
                        cs.setNonStrokingColor(
                            Color.red(color), Color.green(color), Color.blue(color)
                        )
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, fontSize)
                        cs.newLineAtOffset(x, y)
                        cs.showText(text)
                        cs.endText()
                    }
                }
                safeSave(doc, file, context)
                CrashLogger.log(context, "PdfTextEditor",
                    "✅ 텍스트 추가: '${text.take(20)}' at ($x, $y)")
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 텍스트를 Bitmap으로 렌더링 → PDF에 이미지로 삽입
     * (한글 등 유니코드 지원)
     */
    private fun drawTextAsImage(
        doc: PDDocument,
        cs: PDPageContentStream,
        text: String,
        fontSize: Float,
        color: Int,
        x: Float, y: Float,
        targetWidth: Float,
        targetHeight: Float
    ) {
        // 고해상도 렌더링
        val scale = 4f
        val pxSize = (fontSize * scale).coerceAtLeast(8f)

        val paint = Paint().apply {
            textSize = pxSize
            this.color = color
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT
        }

        // 텍스트 크기 측정
        val textWidthPx = paint.measureText(text)
        val fm = paint.fontMetrics
        val textHeightPx = fm.descent - fm.ascent

        // Bitmap 생성 (패딩 포함)
        val pad = 4
        val bmpW = (textWidthPx.toInt() + pad * 2).coerceAtLeast(1)
        val bmpH = (textHeightPx.toInt() + pad * 2).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawText(text, pad.toFloat(), pad - fm.ascent, paint)

        // PDF에 이미지 삽입
        val pdImage = LosslessFactory.createFromImage(doc, bmp)

        // 이미지 크기 (pt)
        val drawW = (bmpW / scale).coerceAtLeast(4f)
        val drawH = (bmpH / scale).coerceAtLeast(4f)

        // ⭐ y는 좌하단 기준 baseline → 이미지의 bottom이 y가 되게
        val drawY = y
        cs.drawImage(pdImage, x, drawY, drawW, drawH)

        // Bitmap 정리
        bmp.recycle()
    }

    /**
     * 특정 위치의 텍스트를 찾아서 그 정보 반환 (수정용)
     */
    suspend fun getTextAt(
        context: Context,
        file: File,
        pageIndex: Int,
        pdfX: Float, pdfY: Float,
        threshold: Float = 50f
    ): Result<PdfTextPicker.Word?> = withContext(Dispatchers.IO) {
        try {
            val result = PdfTextPicker.extractWordsWithSize(context, file, pageIndex)
            result.map { r ->
                r.words.minByOrNull { w ->
                    val cx = w.x + w.width / 2
                    val cy = w.y + w.height / 2
                    val dx = cx - pdfX
                    val dy = cy - pdfY
                    kotlin.math.sqrt(dx * dx + dy * dy)
                }?.takeIf { w ->
                    val cx = w.x + w.width / 2
                    val cy = w.y + w.height / 2
                    val dx = cx - pdfX
                    val dy = cy - pdfY
                    kotlin.math.sqrt(dx * dx + dy * dy) < threshold
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
