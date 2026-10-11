package com.mdkdw1.mypdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GroqVisionTranslator {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "qwen/qwen3.8-27b"

    /**
     * 이미지 통번역
     * @param imageBase64 data:image/jpeg;base64,... 형식 아닌 순수 base64
     * @param targetLang "ko", "en" 등
     * @param mode "text_only" (텍스트만) / "layout" (레이아웃 유지) / "summary" (요약)
     */
    suspend fun translateImage(
        apiKey: String,
        imageBase64: String,
        targetLang: String = "ko",
        mode: String = "layout"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val langName = GroqTranslator.langName(targetLang)

            val systemPrompt = when (mode) {
                "text_only" -> """
                    Extract all text from the image and translate to $langName.
                    Output ONLY the translation as plain text.
                    NO explanations, NO reasoning.
                """.trimIndent()
                "layout" -> """
                    You are a document translator. Translate ALL content in the image to $langName.
                    Preserve layout: tables as markdown tables, headers, paragraphs.
                    Include text from images, diagrams, and handwriting.
                    Output ONLY the translated content in $langName.
                    NO explanations, NO reasoning.
                """.trimIndent()
                "summary" -> """
                    Analyze the image and provide a summary in $langName.
                    Include: main topic, key numbers, key points (bullet list).
                    Output ONLY in $langName.
                """.trimIndent()
                else -> "Translate to $langName."
            }

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "Translate this document image to $langName.")
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$imageBase64")
                            })
                        })
                    })
                })
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("temperature", 0.1)
                put("max_tokens", 4000)
                put("messages", messages)
            }

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = 120000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (code !in 200..299) {
                val msg = try {
                    JSONObject(response).getJSONObject("error").getString("message")
                } catch (_: Exception) { response.take(400) }
                return@withContext Result.failure(Exception("Groq Vision 오류 $code: $msg"))
            }

            val json = JSONObject(response)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .replace(Regex("(?s)Thinking\\.\\.\\..*?done thinking\\.", RegexOption.IGNORE_CASE), "")
                .trim()

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
