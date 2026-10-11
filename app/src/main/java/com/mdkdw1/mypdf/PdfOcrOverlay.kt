package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 페이지에서 OCR로 텍스트 블록 + 위치 추출
 */
object PdfOcrOverlay {

    data class TextBlock(
        val text: String,              // 원본 텍스트
        val boundingBox: Rect,         // 이미지 좌표계
        val translated: String? = null,// 번역 결과 (null이면 미번역)
        val loading: Boolean = false,
        val error: String? = null
    )

    /**
     * PDF 페이지 이미지에서 OCR → 텍스트 블록 리스트
     */
    suspend fun extractBlocks(
        context: Context,
        pageBitmap: Bitmap
    ): Result<List<TextBlock>> = withContext(Dispatchers.IO) {
        try {
            val recognizer = TextRecognition.getClient(
                KoreanTextRecognizerOptions.Builder().build()
            )
            val image = InputImage.fromBitmap(pageBitmap, 0)
            val visionText = recognizer.process(image).await()

            val blocks = mutableListOf<TextBlock>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val box = line.boundingBox ?: continue
                    blocks.add(TextBlock(line.text, box))
                }
            }
            recognizer.close()
            Result.success(blocks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 블록 하나를 번역 → 새 리스트 반환
     */
    suspend fun translateBlock(
        apiKey: String,
        block: TextBlock,
        targetLang: String
    ): TextBlock {
        return try {
            val r = GroqTranslator.translate(
                apiKey = apiKey,
                text = block.text,
                targetLang = targetLang,
                mode = "sentence"
            )
            r.fold(
                onSuccess = { block.copy(translated = it, loading = false) },
                onFailure = { block.copy(error = it.message, loading = false) }
            )
        } catch (e: Exception) {
            block.copy(error = e.message, loading = false)
        }
    }

    /**
     * 모든 블록 병렬 번역 (동시 5개)
     */
    suspend fun translateAll(
        apiKey: String,
        blocks: List<TextBlock>,
        targetLang: String,
        onUpdate: (Int, TextBlock) -> Unit
    ): List<TextBlock> = withContext(Dispatchers.IO) {
        val result = blocks.toMutableList()
        // 순차 처리 (rate limit 회피) — 필요하면 병렬로 변경
        for (i in blocks.indices) {
            val translated = translateBlock(apiKey, blocks[i], targetLang)
            result[i] = translated
            withContext(Dispatchers.Main) {
                onUpdate(i, translated)
            }
        }
        result.toList()
    }

    /**
     * PDF 파일 전체 또는 특정 페이지 Bitmap 렌더링은 PdfPageCapture에서 처리
     */
    suspend fun renderAndExtract(
        context: Context,
        file: File,
        pageIndex: Int,
        maxWidth: Int = 1600
    ): Result<Pair<Bitmap, List<TextBlock>>> {
        val bmp = PdfPageCapture.renderPage(context, file, pageIndex, maxWidth)
            ?: return Result.failure(Exception("페이지 렌더 실패"))

        // ⭐ 캐시 확인
        val cached = OcrCache.load(context, file, pageIndex)
        if (cached != null && cached.isNotEmpty()) {
            val blocks = cached.map {
                TextBlock(
                    text = it.text,
                    boundingBox = it.toRect(),
                    translated = it.translated
                )
            }
            return Result.success(bmp to blocks)
        }

        // OCR
        val result = extractBlocks(context, bmp)
        result.onSuccess { blocks ->
            // 캐시 저장
            OcrCache.save(context, file, pageIndex, blocks.map {
                OcrCache.CachedBlock(
                    it.text,
                    it.boundingBox.left, it.boundingBox.top,
                    it.boundingBox.right, it.boundingBox.bottom,
                    it.translated
                )
            })
        }
        return result.map { bmp to it }
    }

    /** 번역 후 캐시 갱신 */
    fun saveCacheAfterTranslate(
        context: Context, file: File, pageIndex: Int,
        blocks: List<TextBlock>
    ) {
        OcrCache.save(context, file, pageIndex, blocks.map {
            OcrCache.CachedBlock(
                it.text,
                it.boundingBox.left, it.boundingBox.top,
                it.boundingBox.right, it.boundingBox.bottom,
                it.translated
            )
        })
    }
}
