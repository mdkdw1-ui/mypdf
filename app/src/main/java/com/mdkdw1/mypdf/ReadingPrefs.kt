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

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(v) = prefs.edit().putBoolean("dark_mode", v).apply()

    var ebookMode: Boolean
        get() = prefs.getBoolean("ebook_mode", true)
        set(v) = prefs.edit().putBoolean("ebook_mode", v).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(v) = prefs.edit().putBoolean("keep_screen_on", v).apply()

    var volumeKeyPage: Boolean
        get() = prefs.getBoolean("volume_key_page", true)
        set(v) = prefs.edit().putBoolean("volume_key_page", v).apply()
}
