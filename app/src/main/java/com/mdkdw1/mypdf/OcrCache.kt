package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * OCR + 번역 결과 캐시 (파일별, 페이지별)
 */
object OcrCache {

    data class CachedBlock(
        val text: String,
        val left: Int, val top: Int, val right: Int, val bottom: Int,
        val translated: String?
    ) {
        fun toRect() = Rect(left, top, right, bottom)
    }

    private fun cacheFile(context: Context, pdfFile: File): File {
        val dir = File(context.filesDir, "ocr_cache").apply { mkdirs() }
        return File(dir, "${pdfFile.nameWithoutExtension}_${pdfFile.length()}.json")
    }

    /** 특정 페이지의 캐시 블록 로드 */
    fun load(context: Context, pdfFile: File, pageIndex: Int): List<CachedBlock>? {
        try {
            val f = cacheFile(context, pdfFile)
            if (!f.exists()) return null
            val json = JSONObject(f.readText())
            val pages = json.optJSONObject("pages") ?: return null
            val pageArr = pages.optJSONArray(pageIndex.toString()) ?: return null
            val result = mutableListOf<CachedBlock>()
            for (i in 0 until pageArr.length()) {
                val o = pageArr.getJSONObject(i)
                result.add(
                    CachedBlock(
                        text = o.getString("text"),
                        left = o.getInt("left"),
                        top = o.getInt("top"),
                        right = o.getInt("right"),
                        bottom = o.getInt("bottom"),
                        translated = if (o.has("translated") && !o.isNull("translated"))
                            o.getString("translated") else null
                    )
                )
            }
            return result
        } catch (e: Exception) {
            return null
        }
    }

    /** 페이지 캐시 저장 */
    fun save(context: Context, pdfFile: File, pageIndex: Int, blocks: List<CachedBlock>) {
        try {
            val f = cacheFile(context, pdfFile)
            val json = if (f.exists()) JSONObject(f.readText()) else JSONObject()
            val pages = json.optJSONObject("pages") ?: JSONObject().also {
                json.put("pages", it)
            }

            val arr = JSONArray()
            for (b in blocks) {
                arr.put(JSONObject().apply {
                    put("text", b.text)
                    put("left", b.left)
                    put("top", b.top)
                    put("right", b.right)
                    put("bottom", b.bottom)
                    put("translated", b.translated ?: JSONObject.NULL)
                })
            }
            pages.put(pageIndex.toString(), arr)
            f.writeText(json.toString())
        } catch (e: Exception) {
            CrashLogger.e(context, "OcrCache", "저장 실패", e)
        }
    }

    /** 전체 캐시 삭제 */
    fun clear(context: Context) {
        try {
            File(context.filesDir, "ocr_cache").deleteRecursively()
        } catch (_: Exception) {}
    }
}
