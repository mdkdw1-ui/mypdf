package com.mdkdw1.mypdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfTextPicker {

    @Volatile
    private var initialized = false

    /** 반드시 사용 전 호출 (Application에서도 호출) */
    @Synchronized
    fun ensureInit(context: Context) {
        if (initialized) return
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
            CrashLogger.log(context, "PdfBox", "리소스 초기화 완료")
        } catch (e: Throwable) {
            CrashLogger.e(context, "PdfBox", "초기화 실패", e as? Exception ?: Exception(e))
            throw e
        }
    }

    data class Word(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val fontSize: Float,
        val fontName: String
    )

    suspend fun extractWords(context: Context, file: File, pageIndex: Int): Result<List<Word>> =
        withContext(Dispatchers.IO) {
            try {
                // ⭐ 사용 직전 초기화 보장
                ensureInit(context)

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
                        override fun writeString(
                            text: String,
                            textPositions: MutableList<TextPosition>
                        ) {
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
                        }
                    }
                    stripper.sortByPosition = true
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(doc)

                    // 마지막 단어
                    if (currentText.isNotEmpty()) {
                        words.add(Word(
                            currentText.toString(),
                            currentX, currentY, currentWidth,
                            currentHeight, currentFontSize, currentFont
                        ))
                    }
                    Result.success(words)
                }
            } catch (e: Throwable) {
                Result.failure(e as? Exception ?: Exception(e))
            }
        }

    fun findWordAt(
        words: List<Word>,
        tapX: Float, tapY: Float,
        viewWidth: Int, viewHeight: Int,
        pageWidth: Float, pageHeight: Float
    ): Word? {
        val scale = minOf(viewWidth / pageWidth, viewHeight / pageHeight)
        val offsetX = (viewWidth - pageWidth * scale) / 2
        val offsetY = (viewHeight - pageHeight * scale) / 2
        val pdfX = (tapX - offsetX) / scale
        val pdfY = pageHeight - (tapY - offsetY) / scale

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
