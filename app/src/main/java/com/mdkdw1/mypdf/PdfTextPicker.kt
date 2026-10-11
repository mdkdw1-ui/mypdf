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
        val y: Float,          // PDF 좌표 (좌상단 기준, pt)
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
                    Result.success(words)
                }
            } catch (e: Throwable) {
                Result.failure(e as? Exception ?: Exception(e))
            }
        }

    /**
     * 탭 위치 → 가장 가까운 단어 찾기
     *
     * @param words 추출된 단어 목록 (PDF 좌표, 좌상단 기준)
     * @param tapX, tapY 화면 픽셀 좌표 (PDFView 기준)
     * @param viewWidth, viewHeight PDFView 크기
     * @param pageWidthPt, pageHeightPt PDF 페이지 크기 (pt)
     * @param zoom PDFView 현재 줌 (getZoom)
     * @param scrollX, scrollY PDFView 스크롤 오프셋 (getCurrentXOffset / YOffset)
     */
    fun findWordAt(
        words: List<Word>,
        tapX: Float, tapY: Float,
        viewWidth: Int, viewHeight: Int,
        pageWidthPt: Float, pageHeightPt: Float,
        zoom: Float, scrollX: Float, scrollY: Float
    ): Word? {
        // ── 1. PDFView 렌더링 스케일 계산 ──
        // PDFView는 페이지를 뷰 너비에 맞춰 렌더링(fit-to-width),
        // 그 위에 zoom을 곱함.
        // 하지만 mhiew 라이브러리에서 zoom은 "1pt = N px"의 절대값.
        // 사용자가 핀치하면 zoom이 변함.
        //
        // 안전한 계산: 뷰 너비 / 페이지 너비(pt) = fit-scale
        // 이게 현재 화면에 페이지가 꽉 찬 상태의 스케일.
        // 만약 사용자가 zoom을 바꿨다면 PDFView의 zoom이 1이 아님.

        val fitScale = viewWidth.toFloat() / pageWidthPt
        // 실효 스케일: fitScale 자체가 아니라, PDFView가 실제 사용하는 scale.
        // mhiew는 zoom=1일 때 1pt=1px로 렌더링 → 그래서 fitScale이 아니라 zoom 그대로.
        // 근데 그러면 페이지가 화면보다 작아짐.
        //
        // 실제 라이브러리 동작 확인 결과: 초기 zoom = fit-to-width,
        // 즉 화면에 꽉 차게 보임. 그래서 zoom은 "fitScale에 대한 배수"가 아니라
        // "실제 scale"로 취급함.

        // ⭐ 가장 확실한 방법: zoom을 그대로 스케일로 사용
        // (초기값은 fit-to-width 스케일)
        val scale = zoom

        // ── 2. 뷰 내 페이지 위치 계산 ──
        // 페이지 렌더 크기 (px)
        val renderedW = pageWidthPt * scale
        val renderedH = pageHeightPt * scale

        // 뷰에서 페이지 top-left 위치:
        // - 수평: 중앙 정렬
        // - 수직: top-aligned (첫 페이지 기준)
        // - 스크롤 반영
        val pageLeft = (viewWidth - renderedW) / 2f - scrollX
        val pageTop = -scrollY

        // ── 3. 탭 → PDF 좌표(좌상단 기준, pt) ──
        val pdfX = (tapX - pageLeft) / scale
        val pdfY = (tapY - pageTop) / scale

        android.util.Log.d("PdfTextPicker",
            "tap=($tapX, $tapY) view=${viewWidth}x$viewHeight " +
            "page=${pageWidthPt}x$pageHeightPt scale=$scale " +
            "scroll=($scrollX, $scrollY) pageLeft=$pageLeft pageTop=$pageTop " +
            "→ pdf=($pdfX, $pdfY)")

        // ── 4. 가장 가까운 단어 찾기 ──
        val threshold = 40f + (scale * 10f)  // 스케일이 크면 여유도 크게

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
            android.util.Log.d("PdfTextPicker",
                "nearest='${w.text}' center=($cx, $cy) dist=$dist threshold=$threshold")
            dist < threshold
        }
    }
}
