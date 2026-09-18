package eu.hxreborn.biometricapplock.util

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import eu.hxreborn.biometricapplock.R

/**
 * iOS-style Face Unlock scanning overlay. Shows a compact, non-intrusive indicator at the
 * top of the screen with an animated face-scan bracket frame, a scanning line, and
 * lock state transitions.
 */
class FaceScanOverlay(
    private val activity: Activity,
) {
    companion object {
        private const val TAG = "FaceScanOverlay"
    }

    enum class State { SCANNING, SUCCESS, FAILED }

    private var overlayView: FaceScanView? = null
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())

    fun show() {
        if (isShowing) return
        try {
            overlayView = FaceScanView(activity)

            val params =
                FrameLayout
                    .LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        topMargin = dpToPx(80)
                    }

            // Add the view directly to the Activity's decor view
            val decorView = activity.window.decorView as? ViewGroup
            decorView?.addView(overlayView, params)
            isShowing = true

            // Entrance animation
            overlayView?.alpha = 0f
            overlayView?.scaleX = 0.7f
            overlayView?.scaleY = 0.7f
            overlayView
                ?.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(350)
                ?.setInterpolator(OvershootInterpolator(1.2f))
                ?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    fun setState(state: State) {
        handler.post {
            overlayView?.setState(state)
        }
    }

    fun dismiss() {
        if (!isShowing) return
        isShowing = false
        handler.post {
            try {
                overlayView
                    ?.animate()
                    ?.alpha(0f)
                    ?.scaleX(0.8f)
                    ?.scaleY(0.8f)
                    ?.setDuration(250)
                    ?.withEndAction {
                        val decorView = activity.window.decorView as? ViewGroup
                        decorView?.removeView(overlayView)
                        overlayView = null
                    }?.start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dismiss overlay", e)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = activity.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private inner class FaceScanView(
        context: Context,
    ) : View(context) {
        private val density = context.resources.displayMetrics.density
        private val size = 64f * density
        private val halfSize = size / 2f
        private val bracketLen = 14f * density
        private val bracketStroke = 3f * density

        private var currentState = State.SCANNING
        private var stateColor = Color.WHITE

        private var bracketPulse = 0f
        private var scanLineProgress = 0f
        private var lockShackleOffset = 0f
        private var shakeOffset = 0f

        private var pulseAnimator: ValueAnimator? = null
        private var scanAnimator: ValueAnimator? = null

        private val bracketPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = bracketStroke
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        private val scanLinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 14f * density
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }

        private val cornerPaths = Array(4) { Path() }
        private val checkPath = Path()
        private val checkPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        private val xPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
                strokeCap = Paint.Cap.ROUND
            }
        private val scanGradientColors =
            intArrayOf(
                Color.argb(0, 100, 200, 255),
                Color.argb(140, 100, 200, 255),
                Color.argb(0, 100, 200, 255),
            )
        private val scanGradientPositions = floatArrayOf(0f, 0.5f, 1f)
        private val scanMatrix = Matrix()
        private var scanShader: LinearGradient? = null

        init {
            // Needed to ensure onDraw is called
            setWillNotDraw(false)
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val w = (size * 1.5f).toInt()
            val h = (size * 1.8f).toInt()
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f + shakeOffset
            val cy = size / 2f

            drawFaceScan(canvas, cx, cy)
            drawStatusText(canvas, cx, cy + size * 0.7f)
        }

        private fun drawFaceScan(
            canvas: Canvas,
            cx: Float,
            cy: Float,
        ) {
            // Pulse effect
            val pulseScale = 1f + (0.05f * bracketPulse)
            val pulsedHalf = halfSize * pulseScale

            bracketPaint.color = stateColor

            // Top-left corner
            cornerPaths[0].reset()
            cornerPaths[0].moveTo(cx - pulsedHalf, cy - pulsedHalf + bracketLen)
            cornerPaths[0].lineTo(cx - pulsedHalf, cy - pulsedHalf)
            cornerPaths[0].lineTo(cx - pulsedHalf + bracketLen, cy - pulsedHalf)
            canvas.drawPath(cornerPaths[0], bracketPaint)

            // Top-right corner
            cornerPaths[1].reset()
            cornerPaths[1].moveTo(cx + pulsedHalf - bracketLen, cy - pulsedHalf)
            cornerPaths[1].lineTo(cx + pulsedHalf, cy - pulsedHalf)
            cornerPaths[1].lineTo(cx + pulsedHalf, cy - pulsedHalf + bracketLen)
            canvas.drawPath(cornerPaths[1], bracketPaint)

            // Bottom-left corner
            cornerPaths[2].reset()
            cornerPaths[2].moveTo(cx - pulsedHalf, cy + pulsedHalf - bracketLen)
            cornerPaths[2].lineTo(cx - pulsedHalf, cy + pulsedHalf)
            cornerPaths[2].lineTo(cx - pulsedHalf + bracketLen, cy + pulsedHalf)
            canvas.drawPath(cornerPaths[2], bracketPaint)

            // Bottom-right corner
            cornerPaths[3].reset()
            cornerPaths[3].moveTo(cx + pulsedHalf - bracketLen, cy + pulsedHalf)
            cornerPaths[3].lineTo(cx + pulsedHalf, cy + pulsedHalf)
            cornerPaths[3].lineTo(cx + pulsedHalf, cy + pulsedHalf - bracketLen)
            canvas.drawPath(cornerPaths[3], bracketPaint)

            if (currentState == State.SCANNING) {
                val scanY = cy - pulsedHalf + (2f * pulsedHalf * scanLineProgress)
                val baseScanWidth = halfSize * 1.4f
                val scanWidth = pulsedHalf * 1.4f

                if (scanShader == null) {
                    scanShader =
                        LinearGradient(
                            -baseScanWidth,
                            0f,
                            baseScanWidth,
                            0f,
                            scanGradientColors,
                            scanGradientPositions,
                            Shader.TileMode.CLAMP,
                        )
                }
                val pulseScale = 1f + 0.05f * bracketPulse
                scanMatrix.setScale(pulseScale, 1f)
                scanMatrix.postTranslate(cx, scanY)
                scanShader?.setLocalMatrix(scanMatrix)
                scanLinePaint.shader = scanShader
                scanLinePaint.strokeWidth = 1.5f * density
                canvas.drawLine(cx - scanWidth, scanY, cx + scanWidth, scanY, scanLinePaint)
            }

            when (currentState) {
                State.SUCCESS -> {
                    checkPaint.color = stateColor
                    val s = halfSize * 0.5f
                    checkPath.reset()
                    checkPath.moveTo(cx - s * 0.5f, cy + lockShackleOffset)
                    checkPath.lineTo(cx - s * 0.1f, cy + s * 0.4f + lockShackleOffset)
                    checkPath.lineTo(cx + s * 0.6f, cy - s * 0.4f + lockShackleOffset)
                    canvas.drawPath(checkPath, checkPaint)
                }

                State.FAILED -> {
                    xPaint.color = stateColor
                    val s = halfSize * 0.35f
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, xPaint)
                    canvas.drawLine(cx + s, cy - s, cx - s, cy + s, xPaint)
                }

                else -> {}
            }
        }

        private fun drawStatusText(
            canvas: Canvas,
            cx: Float,
            cy: Float,
        ) {
            textPaint.color = stateColor
            val text =
                when (currentState) {
                    State.SCANNING -> context.getString(R.string.miui_face_scanning)
                    State.SUCCESS -> context.getString(R.string.miui_face_success)
                    State.FAILED -> context.getString(R.string.miui_face_failed)
                }
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, cx, textY, textPaint)
        }

        fun setState(state: State) {
            currentState = state
            when (state) {
                State.SUCCESS -> {
                    stopAnimations()
                    stateColor = Color.rgb(52, 199, 89) // iOS green
                    animateSuccess()
                }

                State.FAILED -> {
                    stopAnimations()
                    stateColor = Color.rgb(255, 69, 58) // iOS red
                    animateShake()
                }

                State.SCANNING -> {
                    stateColor = Color.WHITE
                    startScanAnimation()
                    startPulseAnimation()
                }
            }
            invalidate()
        }

        private fun startScanAnimation() {
            scanAnimator?.cancel()
            scanAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1500
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        scanLineProgress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        }

        private fun startPulseAnimation() {
            pulseAnimator?.cancel()
            pulseAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1000
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        bracketPulse = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        }

        private fun stopAnimations() {
            scanAnimator?.cancel()
            pulseAnimator?.cancel()
        }

        private fun animateSuccess() {
            ValueAnimator.ofFloat(0f, -6f * density).apply {
                duration = 400
                interpolator = OvershootInterpolator(2f)
                addUpdateListener {
                    lockShackleOffset = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun animateShake() {
            ValueAnimator.ofFloat(0f, 12f, -10f, 8f, -6f, 4f, -2f, 0f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    shakeOffset = (it.animatedValue as Float) * density
                    invalidate()
                }
                start()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (currentState == State.SCANNING) {
                startScanAnimation()
                startPulseAnimation()
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopAnimations()
        }
    }
}
