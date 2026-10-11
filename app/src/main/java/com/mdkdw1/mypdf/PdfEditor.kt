package com.mdkdw1.mypdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfEditor {

    fun init(context: Context) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("PdfEditor", "init 실패", e)
        }
    }

    suspend fun extractText(file: File, pageIndex: Int = -1): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(file).use { doc ->
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                        if (pageIndex >= 0) {
                            startPage = pageIndex + 1
                            endPage = pageIndex + 1
                        }
                    }
                    Result.success(stripper.getText(doc))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getPageCount(file: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file).use { Result.success(it.numberOfPages) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rotatePage(file: File, pageIndex: Int, degrees: Int): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(file).use { doc ->
                    val page = doc.getPage(pageIndex)
                    page.rotation = (page.rotation + degrees) % 360
                    doc.save(file)
                }
                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deletePage(file: File, pageIndex: Int): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(file).use { doc ->
                    doc.removePage(pageIndex)
                    doc.save(file)
                }
                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun addWatermark(file: File, text: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(file).use { doc ->
                    doc.pages.forEach { page ->
                        val size = page.mediaBox
                        PDPageContentStream(
                            doc, page,
                            PDPageContentStream.AppendMode.APPEND, true, true
                        ).use { cs ->
                            cs.setGraphicsStateParameters(
                                com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState().apply {
                                    nonStrokingAlphaConstant = 0.15f
                                }
                            )
                            cs.setNonStrokingColor(128, 128, 128)
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA_BOLD, 60f)
                            cs.newLineAtOffset(size.width / 4, size.height / 2)
                            cs.showText(text)
                            cs.endText()
                        }
                    }
                    doc.save(file)
                }
                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun addBlankPage(file: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file).use { doc ->
                doc.addPage(PDPage(PDRectangle.A4))
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
