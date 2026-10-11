package com.mdkdw1.mypdf

import android.app.Application
import android.content.Intent
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ⭐ PdfBox 초기화 — 반드시 최우선
        try {
            PDFBoxResourceLoader.init(applicationContext)
            CrashLogger.log(this, "APP", "PdfBox OK (glyphlist 로드)")
        } catch (e: Throwable) {
            CrashLogger.e(this, "APP", "PdfBox 실패", e as? Exception ?: Exception(e))
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashLogger.logCrash(this, throwable)
                val crashIntent = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("stack", CrashLogger.readCrash(this@MyApplication))
                }
                startActivity(crashIntent)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        CrashLogger.log(this, "APP", "앱 시작됨")
    }
}
