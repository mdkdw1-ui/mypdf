package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfTextEditor {

    private fun hasNonAscii(text: String) = text.any { it.code > 127 }

    /**
     * ⭐ 원본 텍스트와 일치하는 위치/폰트/크기를 찾아서 그대로 교체
     * - 원본 폰트 객체 사용 (Helvetica 아님)
     * - 원본 크기 유지
     * - 원본 baseline 위치 유지
     * - 흰 사각형으로 원본 완전 덮기
     */
    suspend fun replaceByOriginal(
        context: Context,
        file: File,
        pageIndex: Int,
        originalText: String,
        newText: String,
        bgColor: Int = Color.WHITE
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                val pageHeightPt = page.mediaBox.height

                // ⭐ 원본 폰트/위치/크기 찾기 (같은 doc 로드 상태에서)
                var foundFont: PDFont? = null
                var foundFontSize = 12f
                var foundX = 0f
                var foundY = 0f          // baseline (좌하단 기준)
                var foundWidth = 0f
                var foundHeight = 0f
                var foundColor = Color.BLACK
                var found = false

                val stripper = object : PDFTextStripper() {
                    override fun writeString(text: String, positions: MutableList<TextPosition>) {
                        if (found) return

                        for (tp in positions) {
                            val ch = tp.unicode ?: ""
                            if (ch.isBlank()) continue

                            if (ch == originalText || originalText.contains(ch) ||
                                text.contains(originalText)) {
                                // 매칭!
                                foundFont = tp.font
                                foundFontSize = tp.fontSizeInPt
                                foundX = tp.xDirAdj
                                // PDFTextStripper의 yDirAdj는 좌상단 기준 (텍스트 상단)
                                // baseline은 yDirAdj + heightDir
                                foundY = pageHeightPt - (tp.yDirAdj + tp.heightDir)
                                foundWidth = tp.widthDirAdj
                                foundHeight = tp.heightDir
                                // 색상은 기본 검정 (PdfBox-Android API 제한)
                                foundColor = Color.BLACK
                                found = true
                                return
                            }
                        }
                    }
                }
                stripper.sortByPosition = true
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.getText(doc)

                if (!found || foundFont == null) {
                    CrashLogger.log(context, "PdfTextEditor",
                        "원본 텍스트 못 찾음: '$originalText'")
                    return@withContext Result.failure(
                        Exception("'$originalText' 위치를 찾을 수 없음")
                    )
                }

                CrashLogger.log(context, "PdfTextEditor",
                    "원본 발견: font=${foundFont!!.name} size=${"%.1f".format(foundFontSize)}pt " +
                    "pos=(${"%.1f".format(foundX)}, ${"%.1f".format(foundY)}) " +
                    "size=(${"%.1f".format(foundWidth)}x${"%.1f".format(foundHeight)})")

                // ⭐ 덮기 + 새 텍스트
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    // 1) 원본 완전 덮기 (여유 패딩)
                    val padX = foundWidth * 0.05f + 2f
                    val padY = foundHeight * 0.5f + 2f

                    cs.setNonStrokingColor(
                        Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)
                    )
                    cs.addRect(
                        foundX - padX,
                        foundY - padY,
                        foundWidth + padX * 2,
                        foundHeight + padY * 2
                    )
                    cs.fill()

                    // 2) 새 텍스트
                    if (hasNonAscii(newText)) {
                        // 한글 등: Bitmap으로 렌더링 (원본 폰트 크기 유지)
                        drawTextAsBitmap(
                            doc, cs, newText, foundFontSize, foundColor,
                            foundX, foundY, foundHeight
                        )
                    } else {
                        // ASCII: 원본 폰트 그대로
                        cs.setNonStrokingColor(
                            Color.red(foundColor), Color.green(foundColor), Color.blue(foundColor)
                        )
                        cs.beginText()
                        cs.setFont(foundFont, foundFontSize)   // ⭐ 원본 폰트
                        cs.newLineAtOffset(foundX, foundY)     // ⭐ 원본 baseline
                        cs.showText(newText)
                        cs.endText()
                    }
                }

                safeSave(doc, file, context)
            }
            Result.success(file)
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfTextEditor", "replaceByOriginal 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 기존 방식 (좌표 직접) — 호환용
     */
    suspend fun replaceText(
        context: Context, file: File, pageIndex: Int,
        left: Float, bottom: Float, width: Float, height: Float,
        newText: String, fontSize: Float = 12f,
        color: Int = Color.BLACK, bgColor: Int = Color.WHITE
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    val padX = 2f
                    val padY = height * 0.3f
                    cs.setNonStrokingColor(
                        Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)
                    )
                    cs.addRect(left - padX, bottom - padY,
                               width + padX * 2, height + padY * 2)
                    cs.fill()

                    if (hasNonAscii(newText)) {
                        drawTextAsBitmap(doc, cs, newText, fontSize, color, left, bottom, height)
                    } else {
                        cs.setNonStrokingColor(
                            Color.red(color), Color.green(color), Color.blue(color)
                        )
                        cs.beginText()
                        cs.setFont(
                            com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, fontSize
                        )
                        cs.newLineAtOffset(left, bottom)
                        cs.showText(newText)
                        cs.endText()
                    }
                }
                safeSave(doc, file, context)
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ⭐ 여러 텍스트를 한 번에 교체 (원본 위치/크기 유지)
     * @param edits Map<wordIndex, Pair<Word, newText>>
     */
    suspend fun replaceMultiple(
        context: Context,
        file: File,
        pageIndex: Int,
        edits: Map<Int, Pair<PdfTextPicker.Word, String>>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                val pageHeight = page.mediaBox.height

                // 한글 폰트 (있으면)
                val koreanFont: PDFont? = try {
                    PDType0Font.load(
                        doc,
                        context.assets.open("fonts/NotoSansKR-Regular.otf"),
                        true
                    )
                } catch (e: Exception) {
                    null
                }

                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    for ((_, pair) in edits) {
                        val (word, newText) = pair

                        // ⭐ Y 좌표: word.y는 baseline (좌하단 기준)
                        val baselineY = word.y
                        val coverLeft = word.x - 2f
                        val coverBottom = baselineY - word.height * 0.4f
                        val coverW = word.width + 4f
                        val coverH = word.height * 1.8f

                        // 1) 덮기
                        cs.setNonStrokingColor(255, 255, 255)
                        cs.addRect(coverLeft, coverBottom, coverW, coverH)
                        cs.fill()

                        // 2) 새 텍스트 (원본 크기)
                        val hasKorean = newText.any { it.code > 127 }
                        val font = if (hasKorean && koreanFont != null) koreanFont
                                   else PDType1Font.HELVETICA

                        cs.setNonStrokingColor(0, 0, 0)
                        cs.beginText()
                        cs.setFont(font, word.fontSize.coerceIn(6f, 72f))
                        cs.newLineAtOffset(word.x, baselineY)
                        cs.showText(newText.take(200))
                        cs.endText()

                        CrashLogger.log(context, "PdfTextEditor",
                            "교체: '${word.text}' → '$newText' " +
                            "pos=(${"%.1f".format(word.x)}, ${"%.1f".format(baselineY)}) " +
                            "font=${"%.1f".format(word.fontSize)}pt")
                    }
                }

                safeSave(doc, file, context)
            }
            Result.success(file)
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfTextEditor", "replaceMultiple 실패", e)
            Result.failure(e)
        }
    }

    /**
     * ⭐ 새 텍스트 추가
     */
    suspend fun addText(
        context: Context, file: File, pageIndex: Int,
        x: Float, y: Float, text: String,
        fontSize: Float = 14f, color: Int = Color.BLACK
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    if (hasNonAscii(text)) {
                        drawTextAsBitmap(doc, cs, text, fontSize, color, x, y, fontSize)
                    } else {
                        cs.setNonStrokingColor(
                            Color.red(color), Color.green(color), Color.blue(color)
                        )
                        cs.beginText()
                        cs.setFont(
                            com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, fontSize
                        )
                        cs.newLineAtOffset(x, y)
                        cs.showText(text)
                        cs.endText()
                    }
                }
                safeSave(doc, file, context)
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 한글 → Bitmap → 이미지 삽입
     * baselineY는 좌하단 기준 baseline
     */
    private fun drawTextAsBitmap(
        doc: PDDocument, cs: PDPageContentStream,
        text: String, fontSize: Float, color: Int,
        x: Float, baselineY: Float, lineHeightPt: Float
    ) {
        val scale = 4f
        val pxSize = fontSize * scale

        val paint = Paint().apply {
            textSize = pxSize
            this.color = color
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT
        }

        val textWidthPx = paint.measureText(text).coerceAtLeast(1f)
        val fm = paint.fontMetrics
        val textHeightPx = (fm.descent - fm.ascent).coerceAtLeast(1f)

        val pad = 4
        val bmpW = (textWidthPx.toInt() + pad * 2).coerceAtLeast(1)
        val bmpH = (textHeightPx.toInt() + pad * 2).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawText(text, pad.toFloat(), pad - fm.ascent, paint)

        val pdImage = LosslessFactory.createFromImage(doc, bmp)

        // 이미지 크기 (pt)
        val drawW = bmpW / scale
        val drawH = bmpH / scale

        // baseline 정렬: ascent 만큼 위로
        // 이미지의 top은 baseline + ascent(음수) 위치
        val ascentPt = (-fm.ascent) / scale
        val drawY = baselineY + ascentPt - (pad / scale)

        cs.drawImage(pdImage, x - pad / scale, drawY, drawW, drawH)
        bmp.recycle()
    }

    private fun safeSave(doc: PDDocument, target: File, context: Context): Boolean {
        return try {
            val beforeSize = if (target.exists()) target.length() else 0
            val tmp = File(context.cacheDir, "tmp_${System.currentTimeMillis()}.pdf")
            doc.save(tmp)
            if (target.exists()) target.delete()
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            CrashLogger.log(context, "PdfTextEditor",
                "safeSave: $beforeSize → ${target.length()} bytes (+${target.length() - beforeSize})")
            true
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfTextEditor", "safeSave 실패", e)
            false
        }
    }
}
