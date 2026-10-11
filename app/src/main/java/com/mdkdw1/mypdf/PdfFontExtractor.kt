package com.mdkdw1.mypdf

import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF에서 텍스트 위치 + 폰트 + 크기 + 색상 정보를 추출
 */
object PdfFontExtractor {

    data class TextItem(
        val text: String,
        val pageIndex: Int,
        val x: Float,          // 좌하단 기준 PDF 좌표
        val y: Float,
        val width: Float,
        val height: Float,
        val fontSize: Float,
        val fontName: String,  // 원본 폰트 이름
        val matchedFont: String, // Android 매칭 폰트
        val color: Int,        // ARGB
        val fontFlags: Int     // bold/italic 여부 (1=Fixed, 2=Serif, 16=Bold, 64=Italic)
    )

    /**
     * 페이지의 모든 텍스트 아이템 추출
     */
    suspend fun extractPage(file: File, pageIndex: Int): Result<List<TextItem>> =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(file).use { doc ->
                    val stripper = object : PDFTextStripper() {
                        val items = mutableListOf<TextItem>()
                        override fun processTextPosition(tp: TextPosition) {
                            super.processTextPosition(tp)
                            try {
                                val font = tp.font
                                val fontName = font?.name ?: "Unknown"
                                val matched = matchAndroidFont(fontName, font?.fontDescriptor?.flags ?: 0)
                                val colorInt = try {
                                    val c = tp.graphicsState?.nonStrokingColor
                                    c?.toRGB() ?: android.graphics.Color.BLACK
                                } catch (_: Exception) { android.graphics.Color.BLACK }

                                items.add(
                                    TextItem(
                                        text = tp.unicode ?: "",
                                        pageIndex = pageIndex,
                                        x = tp.xDirAdj,
                                        y = tp.yDirAdj,
                                        width = tp.widthDirAdj,
                                        height = tp.heightDir,
                                        fontSize = tp.fontSizeInPt,
                                        fontName = fontName,
                                        matchedFont = matched,
                                        color = colorInt,
                                        fontFlags = font?.fontDescriptor?.flags ?: 0
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                    }
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(doc)  // processTextPosition 트리거
                    Result.success(stripper.items)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 페이지의 모든 텍스트를 단어 단위로 그룹화
     */
    suspend fun extractWords(file: File, pageIndex: Int): Result<List<TextItem>> =
        extractPage(file, pageIndex).map { items ->
            // 공백 기준 그룹화
            val words = mutableListOf<TextItem>()
            var current = StringBuilder()
            var firstItem: TextItem? = null
            var lastItem: TextItem? = null

            for (item in items) {
                if (item.text.isBlank() || item.text == " ") {
                    if (current.isNotEmpty() && firstItem != null && lastItem != null) {
                        words.add(mergeItems(firstItem, lastItem, current.toString()))
                        current = StringBuilder()
                        firstItem = null
                        lastItem = null
                    }
                } else {
                    if (firstItem == null) firstItem = item
                    lastItem = item
                    current.append(item.text)
                }
            }
            if (current.isNotEmpty() && firstItem != null && lastItem != null) {
                words.add(mergeItems(firstItem, lastItem, current.toString()))
            }
            words
        }

    private fun mergeItems(first: TextItem, last: TextItem, text: String): TextItem {
        val x = first.x
        val y = first.y
        val w = (last.x + last.width) - first.x
        val h = maxOf(first.height, last.height)
        return first.copy(text = text, x = x, y = y, width = w, height = h)
    }

    /**
     * 원본 폰트 이름 → Android 내장 폰트 매칭
     */
    fun matchAndroidFont(fontName: String, flags: Int): String {
        val name = fontName.lowercase()

        // Serif 여부 (Times, Georgia 등)
        val isSerif = name.contains("times") ||
                name.contains("serif") ||
                name.contains("georgia") ||
                name.contains("garamond") ||
                name.contains("book") ||
                (flags and 2) != 0

        // Mono 여부 (Courier, Consolas 등)
        val isMono = name.contains("courier") ||
                name.contains("mono") ||
                name.contains("consol") ||
                (flags and 1) != 0

        // Bold / Italic
        val isBold = name.contains("bold") || (flags and 16) != 0
        val isItalic = name.contains("italic") || name.contains("oblique") || (flags and 64) != 0

        // 한글 여부 (HY, KoPub, Noto Sans KR 등)
        val isKorean = name.contains("hy") ||
                name.contains("kopub") ||
                name.contains("nanum") ||
                name.contains("malgun") ||
                name.contains("gulim") ||
                name.contains("batang") ||
                name.contains("dotum")

        return when {
            isMono -> when {
                isBold && isItalic -> "monospace"  // Android 기본 mono
                else -> "monospace"
            }
            isSerif -> "serif"
            isKorean -> "sans-serif"  // 한글은 Noto Sans가 기본
            else -> "sans-serif"
        }
    }

    /**
     * PDF 좌표 → 화면 좌표 변환 (뷰 크기 기반)
     */
    fun toScreenRect(
        item: TextItem,
        pageHeightPt: Float,
        viewWidth: Int,
        viewHeight: Int,
        zoom: Float = 1f
    ): RectF {
        val scaleX = viewWidth / (pageHeightPt * 0.772f)  // A4 기준 595x842pt
        val scaleY = viewHeight / pageHeightPt
        val scale = minOf(scaleX, scaleY) * zoom

        // PDF y는 아래에서 위, 화면 y는 위에서 아래 → 반전
        val left = item.x * scale
        val top = (pageHeightPt - item.y - item.height) * scale
        val right = left + item.width * scale
        val bottom = top + item.height * scale
        return RectF(left, top, right, bottom)
    }
}
