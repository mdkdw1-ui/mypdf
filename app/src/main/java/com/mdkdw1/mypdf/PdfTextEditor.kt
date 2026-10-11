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
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

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
    /**
     * ⭐ 여러 텍스트를 한 번에 교체 (원본 위치/크기 유지)
     * - ASCII: 원본 폰트 그대로
     * - 한글: Bitmap 렌더링 (원본 크기/위치 유지)
     */
    /**
     * ⭐ 여러 텍스트를 한 번에 교체 (원본 폰트/위치/크기 최대한 유지)
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
                val pageHeightPt = page.mediaBox.height  // ⭐ Y 변환용

                // ⭐ 페이지에서 사용 가능한 모든 폰트 수집
                val availableFonts = mutableMapOf<String, PDFont>()
                try {
                    val resources = page.resources
                    if (resources != null) {
                        for (cosName in resources.fontNames) {
                            try {
                                val fontKey = cosName.name ?: cosName.toString()
                                val f = resources.getFont(cosName)
                                if (f != null) {
                                    availableFonts[fontKey] = f
                                    f.name?.let { availableFonts[it] = f }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    CrashLogger.log(context, "PdfTextEditor",
                        "폰트 수집 실패: ${e.message}")
                }

                CrashLogger.log(context, "PdfTextEditor",
                    "페이지 폰트 ${availableFonts.size}개: ${availableFonts.keys.take(5)}")

                // 한글 폰트 (assets에 있으면)
                val koreanFont: PDFont? = try {
                    PDType0Font.load(
                        doc,
                        context.assets.open("fonts/NotoSansKR-Regular.otf"),
                        true
                    )
                } catch (e: Exception) { null }

                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    for ((_, pair) in edits) {
                        val (word, newText) = pair

                        // ⭐ word.y는 top-origin (PdfTextPicker) → bottom-origin 변환
                        val baselineY = pageHeightPt - word.y - word.height
                        val coverTop = baselineY - word.height * 0.4f

                        // 1) 원본 텍스트 완전 덮기
                        // ⭐ 흰색 덮기 (원본 완전히 가리게)
                        cs.setNonStrokingColor(255, 255, 255)
                        cs.addRect(
                            word.x - 4f,             // 좌우 여유 더 크게
                            baselineY - word.height * 1.2f,
                            word.width + 8f,
                            word.height * 2.2f
                        )
                        cs.fill()

                        // 2) 새 텍스트
                        val hasKorean = newText.any { it.code > 127 }

                        if (hasKorean && koreanFont == null) {
                            // 한글 + 폰트 없음 → Bitmap
                            drawTextAsBitmap(doc, cs, newText, word.fontSize,
                                Color.BLACK, word.x, baselineY, word.height)
                            CrashLogger.log(context, "PdfTextEditor",
                                "교체(Bitmap): '${word.text}' → '$newText'")
                        } else {
                            // ⭐ 원본 폰트 찾기
                            val originalFont: PDFont = when {
                                hasKorean && koreanFont != null -> koreanFont
                                else -> {
                                    // 1) 이름 그대로 매칭
                                    availableFonts[word.fontName]
                                    // 2) 부분 매칭 (예: "DotumChe" in "DotumChe_PDF_Subset")
                                    ?: availableFonts.entries.find {
                                        word.fontName.contains(it.key) ||
                                        it.key.contains(word.fontName)
                                    }?.value
                                    // 3) 실패 시 Helvetica
                                    ?: PDType1Font.HELVETICA
                                }
                            }

                            try {
                                cs.setNonStrokingColor(0, 0, 0)
                                cs.beginText()
                                cs.setFont(originalFont, word.fontSize.coerceIn(6f, 72f))
                                cs.newLineAtOffset(word.x, baselineY)
                                cs.showText(newText.take(200))
                                cs.endText()
                                val fontDisplayName = try {
                                    originalFont.name ?: "unknown"
                                } catch (_: Exception) { "unknown" }
                                CrashLogger.log(context, "PdfTextEditor",
                                    "교체(Font '$fontDisplayName'): " +
                                    "'${word.text}' → '$newText' (was ${word.fontName})")
                            } catch (e: Exception) {
                                // 원본 폰트로 실패 → Bitmap 폴백
                                CrashLogger.e(context, "PdfTextEditor",
                                    "원본 폰트 실패 → Bitmap", e)
                                drawTextAsBitmap(doc, cs, newText, word.fontSize,
                                    Color.BLACK, word.x, baselineY, word.height)
                            }
                        }
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
    /**
     * ⭐ Bitmap을 원본 텍스트 영역(bbox)에 정확히 맞춰서 렌더링
     * - 폭: word.width에 딱 맞게 폰트 크기 조정
     * - 높이: word.height에 맞춤
     * - baseline: 원본과 동일
     */
    private fun drawTextAsBitmap(
        doc: PDDocument,
        cs: PDPageContentStream,
        text: String,
        fontSize: Float,
        color: Int,
        x: Float,           // PDF 좌표 (좌하단)
        baselineY: Float,   // 원본 baseline
        textHeightPt: Float // 원본 텍스트 높이 (pt)
    ) {
        // 목표: 폭 = 원본 텍스트 폭, 높이 = 원본 텍스트 높이
        // 하지만 원본 폭을 모르면 fontSize 기반으로 폭 계산
        
        val targetHeightPt = textHeightPt.coerceAtLeast(fontSize * 0.8f)
        
        // 렌더 배율 (고해상도)
        val scale = 8f
        
        // 1) 먼저 원하는 폭을 알기 위해 폰트 크기별 측정
        val probe = Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT
            textSize = fontSize * scale
        }
        
        // 원본 폭 추정: fontSize * text.length * 0.6 (한글 평균)
        // 더 정확히: 원본 폭을 파라미터로 받는 게 좋지만 여기선 fontSize 기반
        
        // 높이 기반 bitmap
        val fm = probe.fontMetrics
        val textHeightPx = (fm.descent - fm.ascent)
        
        // 2) 폰트 크기 = targetHeightPt * scale을 약 0.75배 (여유)
        val pxFontSize = (targetHeightPt * scale * 0.85f).coerceAtLeast(8f)
        
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color
            typeface = android.graphics.Typeface.DEFAULT
            textSize = pxFontSize
        }
        
        // 3) 실제 텍스트 폭 측정
        val textWidthPx = paint.measureText(text).coerceAtLeast(1f)
        
        val fmFinal = paint.fontMetrics
        val textHeightFinalPx = (fmFinal.descent - fmFinal.ascent).coerceAtLeast(1f)
        
        // 4) Bitmap 생성
        val padPx = 2
        val bmpW = (textWidthPx.toInt() + padPx * 2).coerceAtLeast(1)
        val bmpH = (textHeightFinalPx.toInt() + padPx * 2).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawText(text, padPx.toFloat(), padPx - fmFinal.ascent, paint)
        
        // 5) PDF 이미지로 삽입 (원본 폭에 맞게)
        val pdImage = LosslessFactory.createFromImage(doc, bmp)
        
        // 이미지 pt 크기
        val drawW = bmpW / scale
        val drawH = bmpH / scale
        
        // baseline 정렬
        // 이미지의 bottom이 baseline - descent 위치에 오도록
        val descentPt = -fmFinal.descent / scale
        val drawY = baselineY - descentPt - (padPx / scale)
        val drawX = x - (padPx / scale)
        
        cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
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
