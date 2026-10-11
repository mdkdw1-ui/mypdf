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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ⭐ 완벽한 핀치 줌 + 팬 + 탭 (손가락 위치 중심 확대)
 * - 두 손가락 중심으로 확대/축소 (focal point)
 * - 한 손가락 드래그로 상하좌우 팬
 * - 탭 감지 (좌표 콜백)
 * - 더블탭 줌
 */
class ZoomablePageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    // 현재 상태
    private val matrix = Matrix()
    private val savedMatrix = Matrix()

    // 핀치 줌
    private var mode = NONE
    private var startPoint = PointF()
    private var midPoint = PointF()
    private var oldDist = 1f
    private var minScale = 1f
    private var maxScale = 8f

    // 탭 콜백
    var onTapListener: ((Float, Float) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // ⭐ 핵심: focusX, focusY (두 손가락 중심) 기준 스케일
                val scaleFactor = detector.scaleFactor

                // 현재 스케일 계산
                val values = FloatArray(9)
                matrix.getValues(values)
                val currentScale = values[Matrix.MSCALE_X]
                val newScale = currentScale * scaleFactor

                // 최소/최대 제한
                if (newScale in minScale..maxScale) {
                    matrix.postScale(
                        scaleFactor, scaleFactor,
                        detector.focusX, detector.focusY  // ⭐ 손가락 중심
                    )
                    imageMatrix = matrix
                }
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // 탭 → 콜백 (뷰 좌표)
                onTapListener?.invoke(e.x, e.y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 더블탭 → 확대/원복 토글
                val values = FloatArray(9)
                matrix.getValues(values)
                val currentScale = values[Matrix.MSCALE_X]

                if (currentScale > minScale + 0.1f) {
                    // 원복
                    matrix.reset()
                    matrix.setScale(minScale, minScale)
                    centerImage()
                } else {
                    // 2배 확대 (탭 위치 중심)
                    val scale = 2f
                    matrix.postScale(scale, scale, e.x, e.y)
                }
                imageMatrix = matrix
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post {
            matrix.reset()
            if (drawable != null) {
                fitImageToView()
            }
            imageMatrix = matrix
        }
    }

    /**
     * ⭐ 이미지 fit-to-view (center inside) + 초기 matrix 설정
     */
    private fun fitImageToView() {
        val drawable = drawable ?: return
        val dWidth = drawable.intrinsicWidth.toFloat()
        val dHeight = drawable.intrinsicHeight.toFloat()
        val vWidth = width.toFloat()
        val vHeight = height.toFloat()

        if (dWidth <= 0 || dHeight <= 0 || vWidth <= 0 || vHeight <= 0) return

        // Scale to fit
        val scale = min(vWidth / dWidth, vHeight / dHeight)
        minScale = scale
        maxScale = scale * 8f

        // Center
        val dx = (vWidth - dWidth * scale) / 2f
        val dy = (vHeight - dHeight * scale) / 2f

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
    }

    /**
     * 이미지가 뷰 안에 있으면 중앙 정렬
     */
    private fun centerImage() {
        fitImageToView()
        imageMatrix = matrix
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
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint(midPoint, event)
                    mode = ZOOM
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && event.pointerCount == 1) {
                    // ⭐ 한 손가락 팬 (스크롤)
                    matrix.set(savedMatrix)
                    matrix.postTranslate(
                        event.x - startPoint.x,
                        event.y - startPoint.y
                    )
                    imageMatrix = matrix
                }
                // ZOOM은 scaleDetector가 처리
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
        }
        return true
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        if (event.pointerCount < 2) return
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }

    /**
     * ⭐ 뷰 좌표 → 이미지 내부 좌표 변환 (탭 위치 계산용)
     */
    fun viewToImage(viewX: Float, viewY: Float): PointF {
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return PointF(viewX, viewY)
        val pts = floatArrayOf(viewX, viewY)
        inverse.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    /**
     * 현재 스케일
     */
    fun getCurrentScale(): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
