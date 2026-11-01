package com.mdkdw1.mypdf

import java.io.File

/**
 * HWP 5.0 텍스트 추출 (2단계에서 OLE 파서 구현 예정)
 * 현재는 스텁 — 명확한 오류 메시지 반환
 */
object Hwp5Parser {

    fun parseTextOnly(file: File): Result<String> {
        return try {
            val header = file.inputStream().use { input ->
                val buf = ByteArray(8)
                val n = input.read(buf)
                if (n < 8) ByteArray(0) else buf
            }
            val sig = String(header, Charsets.US_ASCII)
            if (sig != "HWP Docu") {
                return Result.failure(Exception("HWP 5.0 파일이 아님 (시그니처: $sig)"))
            }
            // TODO 2단계: OLE 파싱 + BodyText/Section* 압축 해제 + 레코드 파싱
            Result.failure(Exception("HWP 5.0 파서 구현 중입니다 (2단계 예정).\nHWPX 파일로 변환 후 다시 시도해주세요."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
