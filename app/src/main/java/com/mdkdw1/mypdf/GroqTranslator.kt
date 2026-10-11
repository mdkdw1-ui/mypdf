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

object GroqTranslator {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    // ⭐ 검증된 최신 Groq 모델 (2026-01)
    private const val MODEL = "openai/gpt-oss-120b"

    suspend fun translate(
        apiKey: String,
        text: String,
        targetLang: String = "ko",
        mode: String = "auto"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val langName = langName(targetLang)

            // 번역 프롬프트 (reasoning 방지: "no explanation" 강조)
            val systemPrompt = when (mode) {
                "word" -> """
                    You are a dictionary translator. For the given word:
                    1. Translate to $langName
                    2. Add part of speech
                    3. Optionally 1 example
                    Keep concise. NO explanations, NO reasoning.
                """.trimIndent()
                "sentence" -> """
                    Translate to $langName. Output ONLY the translation.
                    NO explanations, NO reasoning, NO quotes.
                """.trimIndent()
                "doc" -> """
                    Translate the document to $langName.
                    Preserve paragraphs. Output ONLY the translation.
                    NO explanations, NO reasoning.
                """.trimIndent()
                else -> """
                    Translate to $langName. Output ONLY the translation.
                    NO explanations, NO reasoning.
                """.trimIndent()
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("temperature", 0.2)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    })
                })
            }

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 60000
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
                } catch (_: Exception) {
                    response.take(300)
                }
                return@withContext Result.failure(Exception("Groq 오류 $code: $msg"))
            }

            val json = JSONObject(response)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun langName(code: String): String = when (code) {
        "ko" -> "Korean"
        "en" -> "English"
        "ja" -> "Japanese"
        "zh" -> "Chinese"
        "es" -> "Spanish"
        "fr" -> "French"
        "de" -> "German"
        "ru" -> "Russian"
        else -> code
    }

    fun detectLang(text: String): String {
        if (text.isEmpty()) return "en"
        var korean = 0; var japanese = 0; var chinese = 0; var latin = 0
        for (c in text) {
            when {
                c.code in 0xAC00..0xD7A3 || c.code in 0x3131..0x318E -> korean++
                c.code in 0x3040..0x309F || c.code in 0x30A0..0x30FF -> japanese++
                c.code in 0x4E00..0x9FFF -> chinese++
                c.code in 0x0041..0x007A -> latin++
            }
        }
        return when (maxOf(korean, japanese, chinese, latin)) {
            korean -> "ko"
            japanese -> "ja"
            chinese -> "zh"
            else -> "en"
        }
    }
}
