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

    data class ExtractResult(
        val words: List<Word>,
        val pageWidthPt: Float,
        val pageHeightPt: Float
    )

    suspend fun extractWordsWithSize(
        context: Context,
        file: File,
        pageIndex: Int
    ): Result<ExtractResult> = withContext(Dispatchers.IO) {
        try {
            ensureInit(context)

            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                val pageBox = page.mediaBox
                val pageWidthPt = pageBox.width
                val pageHeightPt = pageBox.height

                CrashLogger.log(context, "PdfTextPicker",
                    "페이지 $pageIndex pt 크기: ${"%.1f".format(pageWidthPt)} x ${"%.1f".format(pageHeightPt)}")

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

                CrashLogger.log(context, "PdfTextPicker", "단어 ${words.size}개 추출")

                Result.success(ExtractResult(words, pageWidthPt, pageHeightPt))
            }
        } catch (e: Throwable) {
            Result.failure(e as? Exception ?: Exception(e))
        }
    }

    /**
     * 화면 탭 → PDF 단어 매칭
     * PDFView가 뷰 중앙에 페이지를 배치한다고 가정
     */
    fun findWordAt(
        context: Context,
        words: List<Word>,
        tapX: Float, tapY: Float,
        pageSizePxW: Float, pageSizePxH: Float,
        pageWidthPt: Float, pageHeightPt: Float,
        viewWidth: Float, viewHeight: Float
    ): Word? {
        val pageLeft = (viewWidth - pageSizePxW) / 2f
        val pageTop = (viewHeight - pageSizePxH) / 2f

        val localX = tapX - pageLeft
        val localY = tapY - pageTop

        val scaleX = pageSizePxW / pageWidthPt
        val scaleY = pageSizePxH / pageHeightPt

        val pdfX = localX / scaleX
        val pdfY = localY / scaleY

        CrashLogger.log(context, "PdfTextPicker",
            "tap=(${"%.1f".format(tapX)}, ${"%.1f".format(tapY)}) " +
            "pageSizePx=${"%.0f".format(pageSizePxW)}x${"%.0f".format(pageSizePxH)} " +
            "pagePt=${"%.0f".format(pageWidthPt)}x${"%.0f".format(pageHeightPt)} " +
            "pageLeft=${"%.1f".format(pageLeft)} pageTop=${"%.1f".format(pageTop)} " +
            "local=(${"%.1f".format(localX)}, ${"%.1f".format(localY)}) " +
            "scale=(${"%.2f".format(scaleX)}, ${"%.2f".format(scaleY)}) " +
            "→ pdf=(${"%.1f".format(pdfX)}, ${"%.1f".format(pdfY)})"
        )

        val threshold = 30f

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
                "dist=${"%.1f".format(dist)}")
            dist < threshold
        }
    }

    /**
     * ⭐ 직접 PDF pt 좌표로 매칭 (PdfRenderer 기반 뷰어용)
     */
    fun findWordAtDirect(
        context: Context,
        words: List<Word>,
        pdfX: Float,
        pdfY: Float,
        threshold: Float = 30f
    ): Word? {
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
                "pdf=(${"%.1f".format(pdfX)}, ${"%.1f".format(pdfY)}) " +
                "nearest='${w.text}' center=(${"%.1f".format(cx)}, ${"%.1f".format(cy)}) " +
                "dist=${"%.1f".format(dist)}")
            dist < threshold
        }
    }

}
