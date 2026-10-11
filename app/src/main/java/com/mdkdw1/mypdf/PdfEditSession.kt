package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfEditSession(
    private val context: Context,
    val sourceFile: File
) {
    var document: PDDocument? = null
        private set

    val pageCount: Int
        get() = document?.numberOfPages ?: 0

    // ═══════════════════════════════════════════════
    // 세션 관리
    // ═══════════════════════════════════════════════

    suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            PdfTextPicker.ensureInit(context)
            document = PDDocument.load(sourceFile)
            CrashLogger.log(context, "PdfEditSession",
                "열기: ${document?.numberOfPages} 페이지")
            Result.success(Unit)
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfEditSession", "열기 실패", e)
            Result.failure(e)
        }
    }

    /**
     * ⭐ 외부 편집 후 재로드 (PdfTextEditor/PdfAnnotation 사용 후 필수)
     */
    suspend fun reload(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            document?.close()
            document = PDDocument.load(sourceFile)
            CrashLogger.log(context, "PdfEditSession",
                "재로드: ${document?.numberOfPages} 페이지 (${sourceFile.length()} bytes)")
            Result.success(Unit)
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfEditSession", "재로드 실패", e)
            Result.failure(e)
        }
    }

    data class PageInfo(
        val originalIndex: Int,
        val rotation: Int,
        val widthPt: Float,
        val heightPt: Float
    )

    fun getPageInfos(): List<PageInfo> {
        val doc = document ?: return emptyList()
        return (0 until doc.numberOfPages).map { i ->
            val page = doc.getPage(i)
            val box = page.mediaBox
            PageInfo(
                originalIndex = i,
                rotation = page.rotation,
                widthPt = box.width,
                heightPt = box.height
            )
        }
    }

    // ═══════════════════════════════════════════════
    // Phase 1: 기본 편집
    // ═══════════════════════════════════════════════

    suspend fun rotatePage(index: Int, degrees: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
                val page = doc.getPage(index)
                page.rotation = (page.rotation + degrees + 360) % 360
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun deletePage(index: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            if (doc.numberOfPages <= 1) {
                return@withContext Result.failure(Exception("최소 1페이지 필요"))
            }
            doc.removePage(index)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun movePage(from: Int, to: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            if (from == to) return@withContext Result.success(Unit)
            if (from < 0 || from >= doc.numberOfPages) return@withContext Result.failure(Exception("범위 초과"))
            if (to < 0 || to >= doc.numberOfPages) return@withContext Result.failure(Exception("범위 초과"))

            // 안전한 재배치: 새 문서에 순서대로 importPage
            val order = (0 until doc.numberOfPages).toMutableList()
            val moved = order.removeAt(from)
            order.add(to, moved)

            // 원본 페이지 복사
            val pageCopies = order.map { doc.getPage(it) }
            // 페이지를 새 문서로 옮기지 않고 순서만 참조 (PdfBox는 clone 사용)
            val newDoc = com.tom_roush.pdfbox.pdmodel.PDDocument()
            order.forEach { idx ->
                newDoc.importPage(doc.getPage(idx))
            }

            // 원본의 어노테이션/주석 유지는 어려움 — 단순 페이지 순서만
            // 원본 닫고 새 문서로 교체
            newDoc.save(sourceFile)
            newDoc.close()

            // 재오픈
            document?.close()
            document = PDDocument.load(sourceFile)

            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addBlankPage(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            val size = if (doc.numberOfPages > 0) doc.getPage(0).mediaBox else PDRectangle.A4
            doc.addPage(PDPage(size))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun extractPage(index: Int): Result<File> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            val outDir = File(context.filesDir, "pdfs").apply { mkdirs() }
            val outFile = File(outDir,
                "${sourceFile.nameWithoutExtension}_p${index + 1}_${System.currentTimeMillis()}.pdf")
            PDDocument().use { newDoc ->
                newDoc.importPage(doc.getPage(index))
                newDoc.save(outFile)
            }
            Result.success(outFile)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ═══════════════════════════════════════════════
    // Phase 2: 주석 (하이라이트/스티키/도형)
    // ═══════════════════════════════════════════════

    /** 사각형 그리기 */
    suspend fun addRectangle(
        pageIndex: Int, x: Float, y: Float, w: Float, h: Float,
        color: Int = Color.RED, strokeWidth: Float = 2f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            val page = doc.getPage(pageIndex)
            PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { cs ->
                cs.setStrokingColor(Color.red(color), Color.green(color), Color.blue(color))
                cs.setLineWidth(strokeWidth)
                cs.addRect(x, y, w, h)
                cs.stroke()
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 원 그리기 */
    suspend fun addCircle(
        pageIndex: Int, cx: Float, cy: Float, r: Float,
        color: Int = Color.BLUE, strokeWidth: Float = 2f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            val page = doc.getPage(pageIndex)
            PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { cs ->
                cs.setStrokingColor(Color.red(color), Color.green(color), Color.blue(color))
                cs.setLineWidth(strokeWidth)
                // 원을 4개 베지어로 근사
                val k = 0.5523f * r
                cs.moveTo(cx + r, cy)
                cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
                cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
                cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
                cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
                cs.closePath()
                cs.stroke()
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 선 그리기 */
    suspend fun addLine(
        pageIndex: Int, x1: Float, y1: Float, x2: Float, y2: Float,
        color: Int = Color.BLACK, strokeWidth: Float = 2f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            val page = doc.getPage(pageIndex)
            PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { cs ->
                cs.setStrokingColor(Color.red(color), Color.green(color), Color.blue(color))
                cs.setLineWidth(strokeWidth)
                cs.moveTo(x1, y1)
                cs.lineTo(x2, y2)
                cs.stroke()
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 자유 그리기 (Ink) — 점 리스트 */
    suspend fun addInkPath(
        pageIndex: Int,
        points: List<Pair<Float, Float>>,
        color: Int = Color.BLACK, strokeWidth: Float = 2f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (points.size < 2) return@withContext Result.success(Unit)
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            val page = doc.getPage(pageIndex)
            PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { cs ->
                cs.setStrokingColor(Color.red(color), Color.green(color), Color.blue(color))
                cs.setLineWidth(strokeWidth)
                cs.setLineCapStyle(1) // round
                cs.moveTo(points[0].first, points[0].second)
                for (i in 1 until points.size) {
                    cs.lineTo(points[i].first, points[i].second)
                }
                cs.stroke()
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ═══════════════════════════════════════════════
    // Phase 3: 워터마크 & 스탬프
    // ═══════════════════════════════════════════════

    /** 텍스트 워터마크 (전체 페이지) */
    suspend fun addTextWatermark(
        text: String, opacity: Float = 0.15f,
        fontSize: Float = 60f, rotation: Float = 45f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            doc.pages.forEach { page ->
                val size = page.mediaBox
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity
                    }
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(128, 128, 128)
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
                    val rad = Math.toRadians(rotation.toDouble())
                    val cos = Math.cos(rad).toFloat()
                    val sin = Math.sin(rad).toFloat()
                    cs.setTextMatrix(
                        com.tom_roush.pdfbox.util.Matrix(
                            cos, sin, -sin, cos,
                            size.width / 4, size.height / 3
                        )
                    )
                    cs.showText(text)
                    cs.endText()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 이미지 워터마크 (전체 페이지, 중앙) */
    suspend fun addImageWatermark(
        imageBitmap: Bitmap, opacity: Float = 0.3f
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            // Bitmap → 임시 파일 → PDImageXObject
            val tmpFile = File(context.cacheDir, "wm_${System.currentTimeMillis()}.png")
            FileOutputStream(tmpFile).use { out ->
                imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val pdImage = PDImageXObject.createFromFileByContent(tmpFile, doc)

            doc.pages.forEach { page ->
                val size = page.mediaBox
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity
                    }
                    cs.setGraphicsStateParameters(gs)
                    val imgW = size.width / 2
                    val imgH = imgW * imageBitmap.height / imageBitmap.width
                    cs.drawImage(
                        pdImage,
                        (size.width - imgW) / 2,
                        (size.height - imgH) / 2,
                        imgW, imgH
                    )
                }
            }
            tmpFile.delete()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 텍스트 스탬프 (특정 페이지 특정 위치) */
    suspend fun addTextStamp(
        pageIndex: Int, x: Float, y: Float,
        text: String, fontSize: Float = 14f,
        color: Int = Color.RED
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            val page = doc.getPage(pageIndex)
            PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { cs ->
                cs.setNonStrokingColor(Color.red(color), Color.green(color), Color.blue(color))
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
                cs.newLineAtOffset(x, y)
                cs.showText(text)
                cs.endText()
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ═══════════════════════════════════════════════
    // 저장 / 종료
    // ═══════════════════════════════════════════════

    suspend fun save(): Result<File> = withContext(Dispatchers.IO) {
        try {
            // ⭐ 편집은 PdfTextEditor/PdfAnnotation이 이미 파일에 반영함
            // 여기서 저장하면 옛 document로 덮어씀 → 재로드만 수행
            document?.close()
            document = PDDocument.load(sourceFile)

            CrashLogger.log(context, "PdfEditSession",
                "💾 save() 재로드: ${sourceFile.name} (${sourceFile.length()} bytes)")
            Result.success(sourceFile)
        } catch (e: Exception) {
            CrashLogger.e(context, "PdfEditSession", "save 실패", e)
            Result.failure(e)
        }
    }

    suspend fun saveAs(newName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val doc = document ?: return@withContext Result.failure(Exception("문서 없음"))
            val outDir = File(context.filesDir, "pdfs").apply { mkdirs() }
            val outFile = File(outDir, newName)
            doc.save(outFile)
            Result.success(outFile)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun close() {
        try { document?.close() } catch (_: Exception) {}
        document = null
    }

    // ═══════════════════════════════════════════════
    // 썸네일
    // ═══════════════════════════════════════════════

    suspend fun renderThumbnail(
        pageIndex: Int,
        width: Int = 300,
        applyRotation: Boolean = true
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                renderer.openPage(pageIndex).use { page ->
                    val scale = width.toFloat() / page.width
                    val h = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    if (applyRotation) {
                        val pageInfo = getPageInfos().getOrNull(pageIndex)
                        val rot = pageInfo?.rotation ?: 0
                        if (rot != 0) {
                            val m = android.graphics.Matrix()
                            m.postRotate(rot.toFloat())
                            return@withContext Bitmap.createBitmap(
                                bmp, 0, 0, bmp.width, bmp.height, m, true
                            )
                        }
                    }
                    return@withContext bmp
                }
            }
            null
        } catch (e: Exception) { null }
    }
}
