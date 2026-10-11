package com.mdkdw1.mypdf

import android.content.Context
import android.content.Intent

object DictionaryHelper {

    /**
     * 외부 사전 앱 호출 (ColorDict, GoldenDict, 네이버 사전 등)
     * 설치된 앱들이 자동으로 선택지에 뜸
     */
    fun lookup(context: Context, word: String) {
        // 방법 1: ACTION_PROCESS_TEXT (Android 6+) — 사전 앱들이 인식
        val processText = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, word)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }

        // 방법 2: ColorDict/GoldenDict 전용 액션
        val colorDict = Intent("com.android.intent.action.SHOW_DEFINITION").apply {
            putExtra("query", word)
        }

        val chooser = Intent.createChooser(processText, "\"$word\" 검색")
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            try {
                context.startActivity(colorDict)
            } catch (e2: Exception) {
                // 폴백: 구글 검색
                val search = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(
                        "https://www.google.com/search?q=define+${android.net.Uri.encode(word)}"
                    )
                }
                context.startActivity(search)
            }
        }
    }
}
