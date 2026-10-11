package com.mdkdw1.mypdf

import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 원본 텍스트 위치를 흰색으로 덮고, 새 텍스트를 그려넣기
 */
object PdfTextOverlay {

    data class ReplaceOp(
        val pageIndex: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val newText: String,
        val fontSize: Float,
        val fontName: String,   // "sans-serif" | "serif" | "monospace"
        val color: Int,
        val originalText: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    )

    /**
     * 여러 위치의 텍스트를 한 번에 교체 (원본 덮고 새 텍스트 그림)
     */
    suspend fun replaceTexts(
        file: File,
        ops: List<ReplaceOp>,
        koreanFontFile: File? = null   // 한글 폰트 (없으면 기본)
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file).use { doc ->
                // 한글 폰트 준비
                val koreanFont = koreanFontFile?.let {
                    try {
                        PDType0Font.load(doc, FileInputStream(it), true)
                    } catch (_: Exception) { null }
                }

                // 페이지별 그룹화
                ops.groupBy { it.pageIndex }.forEach { (pageIdx, pageOps) ->
                    val page: PDPage = doc.getPage(pageIdx)
                    // 배경 덮기 + 새 텍스트 그리기 (같은 스트림에서 순서대로)
                    PDPageContentStream(
                        doc, page,
                        PDPageContentStream.AppendMode.APPEND,
                        true, true
                    ).use { cs ->
                        pageOps.forEach { op ->
                            drawReplace(doc, page, cs, op, koreanFont)
                        }
                    }
                }

                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun drawReplace(
        doc: PDDocument,
        page: PDPage,
        cs: PDPageContentStream,
        op: ReplaceOp,
        koreanFont: PDType0Font?
    ) {
        try {
            // ── 1) 흰색 사각형으로 기존 텍스트 가리기 ──
            cs.setNonStrokingColor(Color.WHITE)
            cs.addRect(
                op.x - 1f,
                op.y - op.height + 2f,
                op.width + 4f,
                op.height + 2f
            )
            cs.fill()

            // ── 2) 새 텍스트 그리기 ──
            cs.setNonStrokingColor(op.color)
            cs.beginText()

            // 폰트 선택
            val (font, size) = chooseFont(doc, op, koreanFont)
            cs.setFont(font, size)

            // 원본 위치에 그리기 (PDF는 좌하단 기준)
            cs.newLineAtOffset(op.x, op.y)
            cs.showText(op.newText)
            cs.endText()
        } catch (e: Exception) {
            android.util.Log.e("PdfTextOverlay", "drawReplace 실패", e)
        }
    }

    private fun chooseFont(
        doc: PDDocument,
        op: ReplaceOp,
        koreanFont: PDType0Font?
    ): Pair<com.tom_roush.pdfbox.pdmodel.font.PDFont, Float> {
        // 한글 포함 여부 확인
        val hasKorean = op.newText.any { it.code in 0xAC00..0xD7A3 || it.code in 0x3131..0x318E }

        val font = if (hasKorean && koreanFont != null) {
            koreanFont
        } else {
            when (op.fontName.lowercase()) {
                "serif" -> when {
                    op.isBold && op.isItalic -> PDType1Font.TIMES_BOLD_ITALIC
                    op.isBold -> PDType1Font.TIMES_BOLD
                    op.isItalic -> PDType1Font.TIMES_ITALIC
                    else -> PDType1Font.TIMES_ROMAN
                }
                "monospace" -> when {
                    op.isBold && op.isItalic -> PDType1Font.COURIER_BOLD_OBLIQUE
                    op.isBold -> PDType1Font.COURIER_BOLD
                    op.isItalic -> PDType1Font.COURIER_OBLIQUE
                    else -> PDType1Font.COURIER
                }
                else -> when {
                    op.isBold && op.isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                    op.isBold -> PDType1Font.HELVETICA_BOLD
                    op.isItalic -> PDType1Font.HELVETICA_OBLIQUE
                    else -> PDType1Font.HELVETICA
                }
            }
        }

        // 크기 조정 (원본 크기 유지, 너무 작으면 확대)
        val size = if (op.fontSize > 0) op.fontSize else 12f
        return font to size
    }

    /**
     * 편의 함수: 원본 텍스트 위치에 새 텍스트로 한 번에 교체
     */
    suspend fun replaceByOriginal(
        file: File,
        originalText: String,
        newText: String,
        pageIndex: Int,
        koreanFontFile: File? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val items = PdfFontExtractor.extractWords(file, pageIndex).getOrNull()
            ?: return@withContext Result.failure(Exception("텍스트 추출 실패"))

        // 원본 텍스트와 일치하는 항목 찾기
        val match = items.firstOrNull { it.text.trim() == originalText.trim() }
            ?: return@withContext Result.failure(Exception("'$originalText' 찾을 수 없음"))

        val op = ReplaceOp(
            pageIndex = pageIndex,
            x = match.x,
            y = match.y,
            width = match.width,
            height = match.height,
            newText = newText,
            fontSize = match.fontSize,
            fontName = match.matchedFont,
            color = match.color,
            originalText = originalText,
            isBold = (match.fontFlags and 16) != 0,
            isItalic = (match.fontFlags and 64) != 0
        )

        replaceTexts(file, listOf(op), koreanFontFile)
    }
}
