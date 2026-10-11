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

    // ⭐ 무료 티어 한도 1000 OTPM → max_tokens를 안전하게 800으로
    private const val MAX_TOKENS = 800

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
                    Translate all visible text to $langName.
                    Output ONLY the translation, no explanations.
                    Be concise.
                """.trimIndent()
                "layout" -> """
                    You are a document translator. Translate ALL visible content in the image to $langName.
                    
                    CRITICAL RULES:
                    1. Preserve the EXACT layout structure:
                       - Use markdown tables for tables: | col1 | col2 |
                       - Use # for headings, ## for subheadings
                       - Keep line breaks where they exist
                       - Preserve bullet points and numbering
                    2. Translate ALL text including:
                       - Headers, footers, footnotes
                       - Small print, legal text
                       - Numbers stay as numbers
                       - Currency symbols preserved
                    3. Layout mapping:
                       - If text is on the left → put it on the left
                       - If it's a form → use a table
                       - If it's a receipt/invoice → use structured layout
                    
                    Output ONLY the translated document in $langName.
                    NO explanations, NO reasoning, NO preamble.
                """.trimIndent()
                "summary" -> """
                    Summarize this document in $langName.
                    Bullet points, key numbers. Concise.
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
                            put("text", "Translate this page to $langName.")
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
                put("max_tokens", MAX_TOKENS)
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
                return@withContext Result.failure(Exception("Groq Vision $code: $msg"))
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
