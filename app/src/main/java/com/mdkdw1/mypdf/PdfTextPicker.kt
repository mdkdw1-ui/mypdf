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
        val x: Float,          // PDF 좌표 (좌상단 기준, pt)
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
                // ⭐ 페이지 pt 크기 얻기
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
     * ⭐ 정확한 좌표 변환: 뷰 내 페이지 오프셋/크기 기반
     *
     * @param tapX, tapY 뷰 좌표계 탭 위치
     * @param pageOffsetX, pageOffsetY 뷰 내 페이지 top-left 위치 (pdfView.getPageOffset)
     * @param pageViewWidth, pageViewHeight 뷰 내 페이지 렌더 크기 (pdfView.getPageSize)
     * @param pageWidthPt, pageHeightPt PDF 페이지 pt 크기 (PdfBox)
     */
    fun findWordAt(
        context: Context,
        words: List<Word>,
        tapX: Float, tapY: Float,
        pageOffsetX: Float, pageOffsetY: Float,
        pageViewWidth: Float, pageViewHeight: Float,
        pageWidthPt: Float, pageHeightPt: Float
    ): Word? {
        // 뷰 내 페이지 로컬 좌표 (픽셀)
        val localX = tapX - pageOffsetX
        val localY = tapY - pageOffsetY

        // pt 좌표로 변환
        val pdfX = (localX / pageViewWidth) * pageWidthPt
        val pdfY = (localY / pageViewHeight) * pageHeightPt

        CrashLogger.log(context, "PdfTextPicker",
            "tap=(${"%.1f".format(tapX)}, ${"%.1f".format(tapY)}) " +
            "pageOffset=(${"%.1f".format(pageOffsetX)}, ${"%.1f".format(pageOffsetY)}) " +
            "pageView=${"%.0f".format(pageViewWidth)}x${"%.0f".format(pageViewHeight)} " +
            "pagePt=${"%.0f".format(pageWidthPt)}x${"%.0f".format(pageHeightPt)} " +
            "→ local=(${"%.1f".format(localX)}, ${"%.1f".format(localY)}) " +
            "pdf=(${"%.1f".format(pdfX)}, ${"%.1f".format(pdfY)})"
        )

        val threshold = 30f  // pt 단위

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
                "dist=${"%.1f".format(dist)} threshold=$threshold"
            )
            dist < threshold
        }
    }
}
