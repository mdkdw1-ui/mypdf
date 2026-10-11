package com.mdkdw1.mypdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfTextPicker {

    data class Word(
        val text: String,
        val x: Float,          // PDF 좌표
        val y: Float,
        val width: Float,
        val height: Float,
        val fontSize: Float,
        val fontName: String
    )

    /** 페이지의 모든 단어 + 좌표 */
    suspend fun extractWords(file: File, pageIndex: Int): Result<List<Word>> =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(file).use { doc ->
                    val words = mutableListOf<Word>()
                    var currentText = StringBuilder()
                    var currentX = 0f
                    var currentY = 0f
                    var currentWidth = 0f
                    var currentHeight = 0f
                    var currentFont = ""
                    var currentFontSize = 0f

                    val stripper = object : PDFTextStripper() {
                        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                            for (tp in textPositions) {
                                val ch = tp.unicode ?: ""
                                if (ch.isBlank()) {
                                    if (currentText.isNotEmpty()) {
                                        words.add(Word(
                                            currentText.toString(),
                                            currentX, currentY, currentWidth,
                                            currentHeight, currentFontSize, currentFont
                                        ))
                                        currentText = StringBuilder()
                                        currentWidth = 0f
                                    }
                                } else {
                                    if (currentText.isEmpty()) {
                                        currentX = tp.xDirAdj
                                        currentY = tp.yDirAdj
                                        currentHeight = tp.heightDir
                                        currentFont = tp.font?.name ?: ""
                                        currentFontSize = tp.fontSizeInPt
                                    }
                                    currentText.append(ch)
                                    currentWidth += tp.widthDirAdj
                                }
                            }
                            // 문장 끝
                            if (currentText.isNotEmpty()) {
                                words.add(Word(
                                    currentText.toString(),
                                    currentX, currentY, currentWidth,
                                    currentHeight, currentFontSize, currentFont
                                ))
                                currentText = StringBuilder()
                                currentWidth = 0f
                            }
                        }
                    }
                    stripper.sortByPosition = true
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(doc)
                    Result.success(words)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 화면 좌표(탭 위치) → 가장 가까운 단어 찾기
     * @param tapX, tapY 화면 픽셀 좌표
     * @param viewWidth, viewHeight PDF 뷰 크기
     * @param pageWidth, pageHeight PDF 페이지 크기 (pt)
     */
    fun findWordAt(
        words: List<Word>,
        tapX: Float, tapY: Float,
        viewWidth: Int, viewHeight: Int,
        pageWidth: Float, pageHeight: Float
    ): Word? {
        // 화면 → PDF 좌표 변환
        val scale = minOf(viewWidth / pageWidth, viewHeight / pageHeight)
        val offsetX = (viewWidth - pageWidth * scale) / 2
        val offsetY = (viewHeight - pageHeight * scale) / 2
        val pdfX = (tapX - offsetX) / scale
        val pdfY = pageHeight - (tapY - offsetY) / scale  // Y축 반전

        // 탭 위치에 가장 가까운 단어 (거리 < 30pt)
        return words.minByOrNull { w ->
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
            kotlin.math.sqrt(dx * dx + dy * dy) < 40f
        }
    }
}
