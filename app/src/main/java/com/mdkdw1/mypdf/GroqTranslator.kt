package com.mdkdw1.mypdf

import android.content.Context
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
     * 텍스트를 targetLang으로 번역
     * @param apiKey Groq API 키
     * @param text 번역할 텍스트
     * @param targetLang 목표 언어 (ko, en, ja, zh 등)
     */
    suspend fun translate(
        apiKey: String,
        text: String,
        targetLang: String = "ko"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val langName = when (targetLang) {
                "ko" -> "Korean"
                "en" -> "English"
                "ja" -> "Japanese"
                "zh" -> "Chinese"
                "es" -> "Spanish"
                "fr" -> "French"
                "de" -> "German"
                else -> targetLang
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("temperature", 0.2)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are a professional translator. Translate the user's text to $langName. Output ONLY the translation, no explanations, no quotes.")
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
                    Exception("Groq API 오류 $code: ${response.take(200)}")
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
}
