package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfAnnotation {

    suspend fun addHighlight(
        context: Context, file: File, pageIndex: Int,
        x: Float, y: Float, w: Float, h: Float,
        color: Int = Color.YELLOW
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                val annot = PDAnnotationHighlight().apply {
                    rectangle = PDRectangle(x, y, w, h)
                    contents = "Highlight"
                    this.color = floatArrayOf(
                        Color.red(color) / 255f,
                        Color.green(color) / 255f,
                        Color.blue(color) / 255f
                    )
                }
                page.annotations.add(annot)
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addStickyNote(
        context: Context, file: File, pageIndex: Int,
        x: Float, y: Float, text: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                val annot = PDAnnotationText().apply {
                    rectangle = PDRectangle(x, y, 24f, 24f)
                    contents = text
                    open = true
                }
                page.annotations.add(annot)
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addRectangle(
        context: Context, file: File, pageIndex: Int,
        x: Float, y: Float, w: Float, h: Float,
        color: Int = Color.RED, strokeWidth: Float = 2f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                val annot = PDAnnotationSquareCircle(
                    PDAnnotationSquareCircle.SUB_TYPE_SQUARE
                ).apply {
                    rectangle = PDRectangle(x, y, w, h)
                    contents = "Rectangle"
                    this.color = floatArrayOf(
                        Color.red(color) / 255f,
                        Color.green(color) / 255f,
                        Color.blue(color) / 255f
                    )
                    borderStyle = PDBorderStyleDictionary().apply { width = strokeWidth }
                }
                page.annotations.add(annot)
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addCircle(
        context: Context, file: File, pageIndex: Int,
        x: Float, y: Float, w: Float, h: Float,
        color: Int = Color.RED, strokeWidth: Float = 2f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                val annot = PDAnnotationSquareCircle(
                    PDAnnotationSquareCircle.SUB_TYPE_CIRCLE
                ).apply {
                    rectangle = PDRectangle(x, y, w, h)
                    contents = "Circle"
                    this.color = floatArrayOf(
                        Color.red(color) / 255f,
                        Color.green(color) / 255f,
                        Color.blue(color) / 255f
                    )
                }
                page.annotations.add(annot)
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun drawFreehand(
        context: Context, file: File, pageIndex: Int,
        points: List<Pair<Float, Float>>,
        color: Int = Color.RED, strokeWidth: Float = 2f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            PDDocument.load(file).use { doc ->
                val page = doc.getPage(pageIndex)
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    cs.setStrokingColor(Color.red(color), Color.green(color), Color.blue(color))
                    cs.setLineWidth(strokeWidth)
                    if (points.size >= 2) {
                        cs.moveTo(points[0].first, points[0].second)
                        for (i in 1 until points.size) {
                            cs.lineTo(points[i].first, points[i].second)
                        }
                        cs.stroke()
                    }
                }
                doc.save(file)
            }
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }
}
