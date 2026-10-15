package com.mdkdw1.mypdf

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * ⭐ 개선판
 * - 핀치 줌: 손가락 중심(focal point) 기준
 * - 확대 시: 한 손가락으로 자유롭게 팬(상하좌우)
 * - 축소 상태: 자식(RecyclerView)이 정상 스크롤
 * - 경계 제한: 콘텐츠 밖으로 나가지 않음
 * - 더블탭: 확대 ↔ 원복
 */
class ZoomableFrameLayout(context: Context) : FrameLayout(context) {

    var target: View? = null
    var currentScale = 1f
        private set
    var minScale = 1f
    var maxScale = 5f
    var doubleTapScale = 2.5f

    private var translateX = 0f
    private var translateY = 0f

    /** ⭐ 외부 좌표 계산용 공개 값 */
    val currentTranslateX: Float get() = translateX
    val currentTranslateY: Float get() = translateY

    private var lastX = 0f
    private var lastY = 0f

    // ⭐ 핀치 줌 — focal point 유지
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = currentScale
                val newScale = (currentScale * detector.scaleFactor)
                    .coerceIn(minScale, maxScale)
                if (newScale == oldScale) return true

                // focal point 기준 좌표 변환
                val factor = newScale / oldScale
                translateX = detector.focusX - (detector.focusX - translateX) * factor
                translateY = detector.focusY - (detector.focusY - translateY) * factor

                currentScale = newScale
                clampTranslation()
                applyTransform()
                return true
            }
        }
    )

    // ⭐ 더블탭
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val oldScale = currentScale
                val newScale = if (currentScale > minScale + 0.1f) {
                    minScale
                } else {
                    doubleTapScale.coerceAtMost(maxScale)
                }
                if (newScale == oldScale) return true

                val factor = newScale / oldScale
                translateX = e.x - (e.x - translateX) * factor
                translateY = e.y - (e.y - translateY) * factor
                currentScale = newScale

                if (newScale == minScale) {
                    translateX = 0f
                    translateY = 0f
                }
                clampTranslation()
                applyTransform()
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true
        }
    )

    private fun applyTransform() {
        target?.let {
            it.scaleX = currentScale
            it.scaleY = currentScale
            it.pivotX = 0f
            it.pivotY = 0f
            it.translationX = translateX
            it.translationY = translateY
        }
    }

    /** 확대된 콘텐츠가 뷰 밖으로 나가지 않게 제한 */
    private fun clampTranslation() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (currentScale <= minScale + 0.001f) {
            translateX = 0f
            translateY = 0f
            return
        }

        val scaledW = w * currentScale
        val scaledH = h * currentScale

        // 가로: 확대된 폭이 뷰보다 크면 [w - scaledW, 0] 범위
        translateX = if (scaledW > w) {
            translateX.coerceIn(w - scaledW, 0f)
        } else 0f

        // 세로
        translateY = if (scaledH > h) {
            translateY.coerceIn(h - scaledH, 0f)
        } else 0f
    }

    // ⭐ 확대 상태일 때만 부모(RecyclerView) 대신 팬 처리
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        // 축소 상태 → 자식이 스크롤하게 둘
        if (currentScale <= minScale + 0.001f) return false

        return when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.x
                lastY = ev.y
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - lastX)
                val dy = abs(ev.y - lastY)
                dx > 8f || dy > 8f  // 임계값 넘으면 팬으로 인터셉트
            }
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    // ⭐ 한 손가락 = 팬 (상하좌우 자유)
                    translateX += event.x - lastX
                    translateY += event.y - lastY
                    clampTranslation()
                    applyTransform()
                }
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 남은 손가락 좌표로 재설정 (튀는 현상 방지)
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** 외부에서 강제 초기화 (예: 페이지 전환) */
    fun resetZoom() {
        currentScale = minScale
        translateX = 0f
        translateY = 0f
        applyTransform()
    }
}
