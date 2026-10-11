package com.mdkdw1.mypdf

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class ZoomablePageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val matrix = Matrix()
    private var minScale = 1f
    private var maxScale = 8f

    private var mode = NONE
    private val startPoint = PointF()
    private val savedMatrix = Matrix()

    var onTapListener: ((Float, Float) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val values = FloatArray(9)
                matrix.getValues(values)
                val currentScale = values[Matrix.MSCALE_X]
                val newScale = currentScale * factor
                if (newScale < minScale * 0.5f || newScale > maxScale) return true

                // ⭐ 핵심: focusX, focusY (두 손가락 중심)
                matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                imageMatrix = matrix
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
                    // 원복
                    fitImageToView()
                } else {
                    // 2배 확대 (탭 위치 중심)
                    matrix.postScale(2f, 2f, e.x, e.y)
                    imageMatrix = matrix
                }
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
    }

    // ⭐ layout 완료 시 matrix 초기화 (가장 확실)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            fitImageToView()
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        // layout 이미 됐으면 바로 fit
        if (width > 0 && height > 0) {
            fitImageToView()
        }
        // layout 대기는 onSizeChanged가 처리
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
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = ZOOM
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && event.pointerCount == 1) {
                    matrix.set(savedMatrix)
                    matrix.postTranslate(
                        event.x - startPoint.x,
                        event.y - startPoint.y
                    )
                    imageMatrix = matrix
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
        }
        return true
    }

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
