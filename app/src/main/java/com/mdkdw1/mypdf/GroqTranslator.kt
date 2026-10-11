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
    private const val MODEL = "openai/gpt-oss-120b"

    suspend fun translate(
        apiKey: String,
        text: String,
        targetLang: String = "ko",
        mode: String = "auto"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val langName = langName(targetLang)
            val langCode = targetLang

            // ⭐ 강력한 언어 강제 시스템 프롬프트
            val systemPrompt = """
                You are a translation engine. Your ONLY job is translating text to $langName ($langCode).
                
                STRICT RULES:
                1. Output language MUST be $langName. NEVER output in Chinese, English, or any other language unless asked.
                2. Output ONLY the translation. No explanations, no quotes, no markdown, no reasoning.
                3. Keep numbers, URLs, and proper nouns as-is.
                4. If input is a single word, output the $langName translation with brief meaning.
                
                EXAMPLES:
                - "cephalic" → "두부의, 머리와 관련된"
                - "hello world" → "안녕 세상"
                - "invoice" → "청구서, 송장"
                
                NOW translate to $langName. Remember: OUTPUT LANGUAGE = $langName.
            """.trimIndent()

            val userPrompt = when (mode) {
                "word" -> "Word: \"$text\"\n→ $langName 번역 + 품사 (간결히)"
                "sentence" -> "Sentence: \"$text\"\n→ $langName 자연스러운 번역"
                "doc" -> "Document:\n$text\n→ $langName 전체 번역 (문단 유지)"
                else -> text
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("temperature", 0.1)   // 0.2 → 0.1 (더 결정적)
                put("max_tokens", 2000)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
            }

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 90000
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
                } catch (_: Exception) { response.take(300) }
                return@withContext Result.failure(Exception("Groq 오류 $code: $msg"))
            }

            val json = JSONObject(response)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                // 혹시 reasoning 태그가 섞이면 제거
                .replace(Regex("(?s)Thinking\\.\\.\\..*?done thinking\\.", RegexOption.IGNORE_CASE), "")
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
