package com.mdkdw1.mypdf

import android.app.Application
import android.content.Intent

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 크래시 로그 저장
                CrashLogger.logCrash(this, throwable)

                // 크래시 화면 표시
                val crashIntent = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("stack", CrashLogger.readCrash(this@MyApplication))
                }
                startActivity(crashIntent)
            } catch (e: Exception) {
                // 저장 실패 시 무시
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        CrashLogger.log(this, "APP", "앱 시작됨")
    }
}
