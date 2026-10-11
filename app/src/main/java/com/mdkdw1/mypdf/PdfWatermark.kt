package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfWatermark {

    suspend fun addTextWatermark(
        context: Context, file: File, text: String,
        fontSize: Float = 60f, opacity: Float = 0.15f,
        color: Int = Color.GRAY, angle: Float = 45f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                doc.pages.forEach { page ->
                    val size = page.mediaBox
                    PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { cs ->
                        val gs = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = opacity
                        }
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(
                            Color.red(color), Color.green(color), Color.blue(color)
                        )
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize)

                        val rad = Math.toRadians(angle.toDouble())
                        val cos = Math.cos(rad).toFloat()
                        val sin = Math.sin(rad).toFloat()
                        cs.setTextMatrix(
                            Matrix(cos, sin, -sin, cos, size.width / 3, size.height / 3)
                        )
                        cs.showText(text)
                        cs.endText()
                    }
                }
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addTextStamp(
        context: Context, file: File, pageIndex: Int,
        x: Float, y: Float, text: String,
        fontSize: Float = 12f, color: Int = Color.BLACK
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    cs.setNonStrokingColor(
                        Color.red(color), Color.green(color), Color.blue(color)
                    )
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, fontSize)
                    cs.newLineAtOffset(x, y)
                    cs.showText(text)
                    cs.endText()
                }
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addImageWatermark(
        context: Context, file: File, bitmap: Bitmap,
        opacity: Float = 0.3f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val pdImage = LosslessFactory.createFromImage(doc, bitmap)
                doc.pages.forEach { page ->
                    val size = page.mediaBox
                    PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { cs ->
                        val gs = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = opacity
                        }
                        cs.setGraphicsStateParameters(gs)
                        val imgW = bitmap.width.toFloat()
                        val imgH = bitmap.height.toFloat()
                        val scale = minOf(size.width / imgW, size.height / imgH) * 0.7f
                        val drawW = imgW * scale
                        val drawH = imgH * scale
                        cs.drawImage(
                            pdImage,
                            (size.width - drawW) / 2,
                            (size.height - drawH) / 2,
                            drawW, drawH
                        )
                    }
                }
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }
}
