package com.mdkdw1.mypdf

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

object CrashLogger {

    private const val TAG = "MyPDF"
    private const val LOG_FILE = "app.log"
    private const val CRASH_FILE = "crash.log"

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // ── 일반 로그 ──
    fun log(context: Context, tag: String, msg: String) {
        Log.d(TAG, "[$tag] $msg")
        appendToFile(context, LOG_FILE, "${now()} [$tag] $msg")
    }

    fun e(context: Context, tag: String, msg: String, t: Throwable? = null) {
        Log.e(TAG, "[$tag] $msg", t)
        val stack = t?.let { "\n${stackTrace(it)}" } ?: ""
        appendToFile(context, LOG_FILE, "${now()} [E/$tag] $msg$stack")
    }

    // ── 크래시 저장 ──
    fun logCrash(context: Context, t: Throwable) {
        val header = "\n========== CRASH ${now()} ==========\n"
        val body = stackTrace(t)
        appendToFile(context, CRASH_FILE, header + body)
        appendToFile(context, LOG_FILE, header + body)
    }

    // ── 파일 읽기 ──
    fun readLog(context: Context): String = readFile(context, LOG_FILE)
    fun readCrash(context: Context): String = readFile(context, CRASH_FILE)

    // ── 파일 지우기 ──
    fun clearLog(context: Context) {
        File(context.filesDir, LOG_FILE).writeText("")
    }
    fun clearCrash(context: Context) {
        File(context.filesDir, CRASH_FILE).writeText("")
    }

    private fun readFile(context: Context, name: String): String {
        val f = File(context.filesDir, name)
        return if (f.exists()) f.readText() else "(로그 없음)"
    }

    private fun appendToFile(context: Context, name: String, text: String) {
        try {
            val f = File(context.filesDir, name)
            // 로그가 너무 커지면 초기화 (500KB)
            if (f.exists() && f.length() > 500_000) {
                f.writeText("(로그 초기화)\n")
            }
            f.appendText(text + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "로그 저장 실패", e)
        }
    }

    private fun stackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun now(): String = dateFmt.format(Date())
}
