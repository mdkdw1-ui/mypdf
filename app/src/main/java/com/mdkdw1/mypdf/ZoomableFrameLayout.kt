package com.mdkdw1.mypdf

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout

/**
 * 자식 View 전체를 핀치 줌 + 더블탭 줌 (RecyclerView 전체 확대)
 */
class ZoomableFrameLayout(context: Context) : FrameLayout(context) {

    var target: View? = null
    var currentScale = 1f
        private set
    var minScale = 1f
    var maxScale = 4f
    var doubleTapScale = 2.0f   // ⭐ 더블탭 시 확대 배율

    // ⭐ 핀치 줌
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                applyScale(newScale)
                return true
            }
        }
    )

    // ⭐ 더블탭 줌
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val newScale = if (currentScale > minScale + 0.1f) {
                    minScale  // 원복
                } else {
                    doubleTapScale.coerceAtMost(maxScale)  // 확대
                }
                applyScale(newScale)
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true
        }
    )

    private fun applyScale(scale: Float) {
        currentScale = scale
        target?.let {
            it.scaleX = scale
            it.scaleY = scale
            it.pivotX = 0f
            it.pivotY = 0f
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // ⭐ 더블탭/핀치 모두 감지
        gestureDetector.onTouchEvent(ev)
        scaleDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}
