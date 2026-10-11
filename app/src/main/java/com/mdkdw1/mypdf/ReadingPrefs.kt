package com.mdkdw1.mypdf

import android.content.Context

class ReadingPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("reading_prefs", Context.MODE_PRIVATE)

    fun savePage(fileName: String, page: Int) {
        prefs.edit().putInt("page_$fileName", page).apply()
    }
    fun getPage(fileName: String): Int = prefs.getInt("page_$fileName", 0)

    fun saveLastOpened(fileName: String) {
        prefs.edit().putLong("last_$fileName", System.currentTimeMillis()).apply()
    }
    fun getLastOpened(fileName: String): Long = prefs.getLong("last_$fileName", 0L)

    fun toggleFavorite(fileName: String): Boolean {
        val c = isFavorite(fileName)
        prefs.edit().putBoolean("fav_$fileName", !c).apply()
        return !c
    }
    fun isFavorite(fileName: String): Boolean = prefs.getBoolean("fav_$fileName", false)

    // ── 테마 모드: 0=일반, 1=세피아, 2=다크, 3=야간(블루라이트↓) ──
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(v) = prefs.edit().putInt("theme_mode", v).apply()

    // ── 눈 건강 ──
    var eyeCareEnabled: Boolean
        get() = prefs.getBoolean("eye_care", false)
        set(v) = prefs.edit().putBoolean("eye_care", v).apply()

    var eyeCareIntervalMin: Int
        get() = prefs.getInt("eye_care_interval", 20)
        set(v) = prefs.edit().putInt("eye_care_interval", v).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(v) = prefs.edit().putBoolean("keep_screen_on", v).apply()

    var fontSize: Int
        get() = prefs.getInt("font_size", 18)
        set(v) = prefs.edit().putInt("font_size", v).apply()

    // ── Groq 번역 ──
    var groqApiKey: String?
        get() = prefs.getString("groq_api_key", null)
        set(v) = prefs.edit().putString("groq_api_key", v).apply()

    var targetLang: String
        get() = prefs.getString("target_lang", "ko") ?: "ko"
        set(v) = prefs.edit().putString("target_lang", v).apply()
}
