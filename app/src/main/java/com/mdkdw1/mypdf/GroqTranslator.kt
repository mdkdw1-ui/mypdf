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
    private const val MODEL = "llama-3.3-70b-versatile"

    /**
     * 번역 - 모드: "word" (단어) / "sentence" (문장) / "doc" (전체)
     */
    suspend fun translate(
        apiKey: String,
        text: String,
        targetLang: String = "ko",
        mode: String = "auto"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val langName = langName(targetLang)
            val systemPrompt = when (mode) {
                "word" -> """
                    You are a dictionary and translator. For the given word or short phrase:
                    1. Give the most common translation to $langName
                    2. If it's a noun, add part of speech
                    3. Add 1-2 example sentences if useful
                    Keep it concise. Output in $langName.
                """.trimIndent()
                "sentence" -> """
                    Translate the sentence to $langName naturally, preserving tone.
                    Output ONLY the translation, no quotes, no explanation.
                """.trimIndent()
                "doc" -> """
                    Translate the following document to $langName.
                    Preserve paragraph structure. Output ONLY the translation.
                """.trimIndent()
                else -> """
                    Translate to $langName naturally. Output ONLY the translation.
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
                return@withContext Result.failure(
                    Exception("Groq API 오류 $code: ${response.take(300)}")
                )
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

    /** 언어 코드 → 이름 */
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

    /** 텍스트 언어 자동 감지 (한글/영어/일본어/중국어) */
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
        val max = maxOf(korean, japanese, chinese, latin)
        return when (max) {
            korean -> "ko"
            japanese -> "ja"
            chinese -> "zh"
            else -> "en"
        }
    }
}
