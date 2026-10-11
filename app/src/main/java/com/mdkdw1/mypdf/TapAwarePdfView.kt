package com.mdkdw1.mypdf

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import com.github.barteksc.pdfviewer.PDFView

/**
 * ⭐ PDFView + 탭 감지 (내부 드래그/핀치 방해 X)
 * - dispatchTouchEvent에서 GestureDetector만 돌리고 super 호출
 * - PDFView의 DragPinchManager 정상 작동
 */
class TapAwarePdfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PDFView(context, attrs) {

    var onTapListener: ((Float, Float) -> Unit)? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onTapListener?.invoke(e.x, e.y)
                return true
            }
            override fun onDown(e: MotionEvent): Boolean = true
        }
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 탭 감지만 (super는 그대로 → DragPinchManager 정상)
        try {
            gestureDetector.onTouchEvent(ev)
        } catch (_: Exception) {}
        return super.dispatchTouchEvent(ev)
    }
}
