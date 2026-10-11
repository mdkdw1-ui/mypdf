package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.min

/**
 * ⭐ 완벽한 핀치/팬/탭 (표준 Android 패턴)
 */
class ZoomablePageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private val imageRect = RectF()     // 현재 이미지 위치

    private var minScale = 1f
    private var maxScale = 8f

    private var mode = NONE
    private val startPoint = PointF()
    private val lastTouch = PointF()

    var onTapListener: ((Float, Float) -> Unit)? = null

    // ⭐ 핀치 줌 (focal point 정확)
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                mode = ZOOM
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val values = FloatArray(9)
                matrix.getValues(values)
                val currentScale = values[Matrix.MSCALE_X]
                val newScale = currentScale * factor

                if (newScale in minScale..maxScale) {
                    // ⭐ 손가락 중심(focus)으로 확대
                    matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                    imageMatrix = matrix
                }
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onTapListener?.invoke(e.x, e.y)
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val values = FloatArray(9)
                matrix.getValues(values)
                val cur = values[Matrix.MSCALE_X]
                if (cur > minScale * 1.2f) {
                    fitImageToView()
                } else {
                    matrix.postScale(2f, 2f, e.x, e.y)
                    imageMatrix = matrix
                }
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) fitImageToView()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (width > 0 && height > 0) fitImageToView()
    }

    private fun fitImageToView() {
        val d = drawable ?: return
        val dW = d.intrinsicWidth.toFloat()
        val dH = d.intrinsicHeight.toFloat()
        val vW = width.toFloat()
        val vH = height.toFloat()
        if (dW <= 0f || dH <= 0f || vW <= 0f || vH <= 0f) return

        val scale = min(vW / dW, vH / dH)
        minScale = scale
        maxScale = scale * 8f

        val dx = (vW - dW * scale) / 2f
        val dy = (vH - dH * scale) / 2f

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        imageMatrix = matrix
        updateImageRect()

        android.util.Log.d("ZoomablePageView",
            "fit: d=${dW.toInt()}x${dH.toInt()} v=${vW.toInt()}x${vH.toInt()} scale=$scale")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                startPoint.set(event.x, event.y)
                lastTouch.set(event.x, event.y)
                mode = DRAG
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 두 손가락 시작 → ZOOM
                mode = ZOOM
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && event.pointerCount == 1) {
                    // ⭐ 한 손가락 팬
                    matrix.set(savedMatrix)
                    matrix.postTranslate(
                        event.x - startPoint.x,
                        event.y - startPoint.y
                    )
                    imageMatrix = matrix
                    updateImageRect()
                }
                lastTouch.set(event.x, event.y)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // ⭐ 한 손가락이 떼짐 → 남은 손가락으로 DRAG 재시작
                if (event.pointerCount >= 2) {
                    val remainIdx = if (event.actionIndex == 0) 1 else 0
                    startPoint.set(event.getX(remainIdx), event.getY(remainIdx))
                    savedMatrix.set(matrix)
                    mode = DRAG
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = NONE
                updateImageRect()
            }
        }
        return true
    }

    private fun updateImageRect() {
        val d = drawable ?: return
        val values = FloatArray(9)
        matrix.getValues(values)
        val left = values[Matrix.MTRANS_X]
        val top = values[Matrix.MTRANS_Y]
        val right = left + d.intrinsicWidth * values[Matrix.MSCALE_X]
        val bottom = top + d.intrinsicHeight * values[Matrix.MSCALE_Y]
        imageRect.set(left, top, right, bottom)
    }

    /**
     * 뷰 좌표 → 이미지 내부 좌표 (탭 위치 계산)
     */
    fun viewToImage(viewX: Float, viewY: Float): PointF {
        val inv = Matrix()
        if (!matrix.invert(inv)) return PointF(viewX, viewY)
        val pts = floatArrayOf(viewX, viewY)
        inv.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
