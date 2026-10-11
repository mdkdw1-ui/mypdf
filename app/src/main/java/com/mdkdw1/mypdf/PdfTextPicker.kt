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
                                        currentY = tp.yDirAdj - tp.heightDir
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

                    if (currentText.isNotEmpty()) {
                        words.add(Word(
                            currentText.toString(),
                            currentX, currentY, currentWidth,
                            currentHeight, currentFontSize, currentFont
                        ))
                    }

                    CrashLogger.log(context, "PdfTextPicker", "페이지 $pageIndex 단어 ${words.size}개 추출")
                    Result.success(words)
                }
            } catch (e: Throwable) {
                Result.failure(e as? Exception ?: Exception(e))
            }
        }

    fun findWordAt(
        context: Context,
        words: List<Word>,
        tapX: Float, tapY: Float,
        viewWidth: Int, viewHeight: Int,
        pageWidthPt: Float = 595f, pageHeightPt: Float = 842f,
        zoom: Float = 1f,
        scrollX: Float = 0f,
        scrollY: Float = 0f
    ): Word? {
        val scale = zoom
        val renderedW = pageWidthPt * scale
        val renderedH = pageHeightPt * scale
        val pageLeft = (viewWidth - renderedW) / 2f - scrollX
        val pageTop = -scrollY

        val pdfX = (tapX - pageLeft) / scale
        val pdfY = (tapY - pageTop) / scale

        CrashLogger.log(context, "PdfTextPicker",
            "tap=(${"%.1f".format(tapX)}, ${"%.1f".format(tapY)}) " +
            "view=${viewWidth}x$viewHeight " +
            "page=${"%.0f".format(pageWidthPt)}x${"%.0f".format(pageHeightPt)} " +
            "scale=${"%.2f".format(scale)} " +
            "scroll=(${"%.1f".format(scrollX)}, ${"%.1f".format(scrollY)}) " +
            "pageLeft=${"%.1f".format(pageLeft)} pageTop=${"%.1f".format(pageTop)} " +
            "→ pdf=(${"%.1f".format(pdfX)}, ${"%.1f".format(pdfY)})"
        )

        val threshold = 40f + (scale * 10f)

        val nearest = words.minByOrNull { w ->
            val cx = w.x + w.width / 2
            val cy = w.y + w.height / 2
            val dx = cx - pdfX
            val dy = cy - pdfY
            kotlin.math.sqrt(dx * dx + dy * dy)
        }

        return nearest?.takeIf { w ->
            val cx = w.x + w.width / 2
            val cy = w.y + w.height / 2
            val dx = cx - pdfX
            val dy = cy - pdfY
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)

            CrashLogger.log(context, "PdfTextPicker",
                "nearest='${w.text}' center=(${"%.1f".format(cx)}, ${"%.1f".format(cy)}) " +
                "dist=${"%.1f".format(dist)} threshold=${"%.1f".format(threshold)}"
            )
            dist < threshold
        }
    }
}
