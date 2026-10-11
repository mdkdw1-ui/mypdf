package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ⭐ PDF 페이지를 이미지 + 숨겨진 텍스트 레이어로 재생성
 * - 이미지: 편집된 Bitmap (원본과 시각적 100% 동일)
 * - 텍스트: 보이지 않는 Tr=3 모드 (검색 가능)
 */
object PdfRasterizer {

    data class TextOverlay(
        val text: String,
        val x: Float,           // PDF pt (좌하단)
        val y: Float,
        val fontSize: Float
    )

    /**
     * 편집된 Bitmap을 새 PDF 페이지로 만들고 텍스트 레이어 삽입
     *
     * @param sourcePdf 원본 PDF (텍스트 추출용)
     * @param pageIndex 교체할 페이지
     * @param editedBitmap 편집된 페이지 이미지
     * @param extraTexts 사용자가 추가한 텍스트 (검색용)
     * @param replaceInPlace true=원본 덮어쓰기, false=새 파일
     */
    suspend fun replacePageWithImage(
        context: Context,
        sourcePdf: File,
        pageIndex: Int,
        editedBitmap: Bitmap,
        extraTexts: List<TextOverlay> = emptyList(),
        outputPdf: File = sourcePdf
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)

            // ⭐ 1. 원본 텍스트 추출 (invisible layer용)
            val words = PdfTextPicker.extractWordsWithSize(context, sourcePdf, pageIndex)
                .getOrNull()

            // ⭐ 2. 원본 로드 → 페이지 크기 저장
            PDDocument.load(sourcePdf).use { doc ->
                val oldPage = doc.getPage(pageIndex)
                val pageWidthPt = oldPage.mediaBox.width
                val pageHeightPt = oldPage.mediaBox.height

                // ⭐ 3. 새 문서 생성 (모든 페이지 복사, 해당 페이지만 교체)
                PDDocument().use { newDoc ->

                    // 한글 폰트 로드 (있으면)
                    val koreanFont = try {
                        PDType0Font.load(
                            newDoc,
                            context.assets.open("fonts/NotoSansKR-Regular.otf"),
                            true
                        )
                    } catch (e: Exception) {
                        CrashLogger.log(context, "PdfRasterizer",
                            "한글 폰트 없음 → 검색 한글 미지원 (선택사항)")
                        null
                    }

                    // 페이지 순서대로 복사
                    for (i in 0 until doc.numberOfPages) {
                        if (i != pageIndex) {
                            // 원본 그대로 복사
                            newDoc.importPage(doc.getPage(i))
                        } else {
                            // ⭐ 편집된 페이지: 이미지 + 텍스트 레이어
                            val newPage = PDPage(PDRectangle(pageWidthPt, pageHeightPt))
                            newDoc.addPage(newPage)

                            PDPageContentStream(
                                newDoc, newPage,
                                PDPageContentStream.AppendMode.APPEND, true, true
                            ).use { cs ->

                                // ⭐ 3-1. 이미지 그리기 (전체 페이지)
                                val pdImage = LosslessFactory.createFromImage(
                                    newDoc, editedBitmap
                                )
                                cs.drawImage(
                                    pdImage,
                                    0f, 0f,
                                    pageWidthPt, pageHeightPt
                                )

                                // ⭐ 3-2. 숨겨진 텍스트 레이어 (원본 텍스트)
                                if (words != null) {
                                    cs.setRenderingMode(
                                        PDPageContentStream.RenderingMode.NEED_APP_RENDERING
                                    )
                                    for (w in words.words) {
                                        try {
                                            val hasKorean = w.text.any { it.code > 127 }
                                            val font = if (hasKorean && koreanFont != null) {
                                                koreanFont
                                            } else {
                                                PDType1Font.HELVETICA
                                            }
                                            cs.beginText()
                                            cs.setFont(font, w.fontSize.coerceAtLeast(6f))
                                            cs.newLineAtOffset(w.x, w.y)
                                            cs.showText(w.text.take(200))
                                            cs.endText()
                                        } catch (e: Exception) {
                                            // 개별 단어 실패는 무시
                                        }
                                    }
                                }

                                // ⭐ 3-3. 사용자 추가 텍스트도 검색 가능하도록
                                for (extra in extraTexts) {
                                    try {
                                        val hasKorean = extra.text.any { it.code > 127 }
                                        val font = if (hasKorean && koreanFont != null) {
                                            koreanFont
                                        } else {
                                            PDType1Font.HELVETICA
                                        }
                                        cs.beginText()
                                        cs.setFont(font, extra.fontSize.coerceAtLeast(6f))
                                        cs.newLineAtOffset(extra.x, extra.y)
                                        cs.showText(extra.text.take(200))
                                        cs.endText()
                                    } catch (e: Exception) {
                                        // 무시
                                    }
                                }
                            }
                        }
                    }

                    // ⭐ 4. 저장 (임시 → 원본)
                    val tmp = File(context.cacheDir, "rasterize_${System.currentTimeMillis()}.pdf")
                    newDoc.save(tmp)

                    if (outputPdf.exists()) outputPdf.delete()
                    tmp.copyTo(outputPdf, overwrite = true)
                    tmp.delete()

                    CrashLogger.log(context, "PdfRasterizer",
                        "페이지 $pageIndex 이미지 교체 완료 " +
                        "(words=${words?.words?.size ?: 0}, " +
                        "extra=${extraTexts.size}, " +
                        "size=${outputPdf.length()})")
                }
            }

            Result.success(outputPdf)
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfRasterizer", "이미지 교체 실패", e)
            Result.failure(e)
        }
    }
}
