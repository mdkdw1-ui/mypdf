package com.mdkdw1.mypdf

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout

/**
 * 자식 View 전체를 핀치 줌 (RecyclerView 전체 확대)
 */
class ZoomableFrameLayout(context: Context) : FrameLayout(context) {

    var target: View? = null
    var currentScale = 1f
        private set
    var minScale = 1f
    var maxScale = 4f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                currentScale = newScale
                target?.let {
                    it.scaleX = newScale
                    it.scaleY = newScale
                }
                return true
            }
        }
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}
