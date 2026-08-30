package eu.hxreborn.biometricapplock.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.min

/**
 * iOS-style Face ID scanning overlay. Shows a compact, non-intrusive indicator at the
 * top of the screen with an animated face-scan bracket frame, a scanning line, and
 * lock state transitions — inspired by the Apple Face ID padlock glyph behaviour.
 *
 * States:
 *  1. SCANNING  — brackets pulse, scan-line sweeps, text says "Face ID"
 *  2. SUCCESS   — brackets turn green, lock opens, text says "✓", auto-dismiss
 *  3. FAILED    — brackets turn red, shake, text says "✕", auto-dismiss
 */
class FaceScanOverlay(
    private val context: Context,
) {
    companion object {
        private const val TAG = "FaceScanOverlay"
    }

    enum class State { SCANNING, SUCCESS, FAILED }

    private var windowManager: WindowManager? = null
    private var overlayView: FaceScanView? = null
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())

    fun show() {
        if (isShowing) return
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayView = FaceScanView(context)

            val params =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = dpToPx(80)

            windowManager?.addView(overlayView, params)
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
            when (state) {
                State.SUCCESS -> {
                    handler.postDelayed({ dismiss() }, 1200)
                }

                State.FAILED -> {
                    handler.postDelayed({ dismiss() }, 1800)
                }

                State.SCANNING -> { /* keep showing */ }
            }
        }
    }

    fun dismiss() {
        if (!isShowing) return
        overlayView
            ?.animate()
            ?.alpha(0f)
            ?.scaleX(0.7f)
            ?.scaleY(0.7f)
            ?.setDuration(250)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removeView()
                    }
                },
            )?.start()
    }

    private fun removeView() {
        try {
            if (isShowing) {
                windowManager?.removeView(overlayView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay", e)
        }
        isShowing = false
        overlayView = null
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    /**
     * The custom View that draws the iOS Face ID scanning indicator.
     * Entirely Canvas-based — no bitmaps, no Lottie, zero external dependencies.
     */
    private class FaceScanView(
        context: Context,
    ) : View(context) {
        // ── Dimensions ──────────────────────────────────────────────────
        private val viewWidthDp = 200
        private val viewHeightDp = 100
        private val cornerRadius: Float
        private val bracketSize: Float
        private val bracketStroke: Float
        private val faceFrameSize: Float
        private val density = context.resources.displayMetrics.density

        // ── Paints ──────────────────────────────────────────────────────
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bracketPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
        private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
        private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val lockBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val lockKeyholePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ── Animation state ─────────────────────────────────────────────
        private var currentState = State.SCANNING
        private var scanLineProgress = 0f
        private var bracketPulse = 0f
        private var lockShackleOffset = 0f
        private var shakeOffset = 0f
        private var stateColor = Color.WHITE

        private var scanAnimator: ValueAnimator? = null
        private var pulseAnimator: ValueAnimator? = null

        init {
            cornerRadius = 24f * density
            bracketSize = 18f * density
            bracketStroke = 2.5f * density
            faceFrameSize = 36f * density

            bgPaint.color = Color.argb(200, 20, 20, 22)

            bracketPaint.strokeWidth = bracketStroke
            bracketPaint.color = Color.WHITE

            textPaint.textSize = 13f * density
            textPaint.color = Color.WHITE

            lockPaint.color = Color.WHITE
            lockPaint.style = Paint.Style.STROKE
            lockPaint.strokeWidth = 2f * density
            lockPaint.strokeCap = Paint.Cap.ROUND

            lockBodyPaint.color = Color.WHITE
            lockBodyPaint.style = Paint.Style.FILL

            lockKeyholePaint.color = Color.argb(200, 20, 20, 22)
            lockKeyholePaint.style = Paint.Style.FILL

            startScanAnimation()
            startPulseAnimation()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val w = (viewWidthDp * density).toInt()
            val h = (viewHeightDp * density).toInt()
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            // Save and apply shake offset if in FAILED state
            canvas.save()
            canvas.translate(shakeOffset, 0f)

            // ── Background pill ─────────────────────────────────────────
            val bgRect = RectF(0f, 0f, w, h)
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            // ── Layout: [Lock icon] [Face scan frame] [Status text] ─────
            val centerY = h / 2f

            // Lock icon area (left side)
            val lockCenterX = w * 0.17f
            drawLockIcon(canvas, lockCenterX, centerY)

            // Face scan frame (center)
            val frameCenterX = w * 0.5f
            drawFaceScanFrame(canvas, frameCenterX, centerY)

            // Status text (right side)
            val textCenterX = w * 0.82f
            drawStatusText(canvas, textCenterX, centerY)

            canvas.restore()
        }

        private fun drawLockIcon(
            canvas: Canvas,
            cx: Float,
            cy: Float,
        ) {
            val lockSize = 14f * density
            val bodyW = lockSize * 0.85f
            val bodyH = lockSize * 0.65f
            val shackleW = lockSize * 0.5f
            val shackleH = lockSize * 0.45f

            lockPaint.color = stateColor
            lockBodyPaint.color = stateColor

            // Shackle
            val shackleLeft = cx - shackleW / 2f
            val bodyTop = cy - bodyH / 2f + 2f * density
            val shackleTop = bodyTop - shackleH + lockShackleOffset
            val shackleRect = RectF(shackleLeft, shackleTop, cx + shackleW / 2f, bodyTop)

            // For open lock, draw only the arc (not closing line)
            if (currentState == State.SUCCESS) {
                val arcRect =
                    RectF(
                        shackleLeft,
                        shackleTop - shackleH / 2f,
                        cx + shackleW / 2f,
                        bodyTop,
                    )
                canvas.drawArc(arcRect, 180f, 180f, false, lockPaint)
            } else {
                val arcRect =
                    RectF(
                        shackleLeft,
                        shackleTop - shackleH / 2f,
                        cx + shackleW / 2f,
                        bodyTop,
                    )
                canvas.drawArc(arcRect, 180f, 180f, false, lockPaint)
                // Close the shackle
                canvas.drawLine(
                    cx + shackleW / 2f,
                    bodyTop - shackleH / 2f + shackleH / 2f,
                    cx + shackleW / 2f,
                    bodyTop,
                    lockPaint,
                )
                canvas.drawLine(
                    shackleLeft,
                    bodyTop - shackleH / 2f + shackleH / 2f,
                    shackleLeft,
                    bodyTop,
                    lockPaint,
                )
            }

            // Body
            val bodyRect =
                RectF(
                    cx - bodyW / 2f,
                    bodyTop,
                    cx + bodyW / 2f,
                    bodyTop + bodyH,
                )
            val bodyRadius = 2.5f * density
            canvas.drawRoundRect(bodyRect, bodyRadius, bodyRadius, lockBodyPaint)

            // Keyhole
            val khRadius = 2f * density
            canvas.drawCircle(cx, bodyTop + bodyH * 0.38f, khRadius, lockKeyholePaint)
            val khLineW = 1.2f * density
            canvas.drawRect(
                cx - khLineW / 2f,
                bodyTop + bodyH * 0.45f,
                cx + khLineW / 2f,
                bodyTop + bodyH * 0.7f,
                lockKeyholePaint,
            )
        }

        private fun drawFaceScanFrame(
            canvas: Canvas,
            cx: Float,
            cy: Float,
        ) {
            val halfSize = faceFrameSize / 2f
            val bracketLen = bracketSize * 0.55f
            val pulsedHalf = halfSize + bracketPulse * 2f * density

            bracketPaint.color = stateColor

            // Four corner brackets
            val corners =
                arrayOf(
                    // top-left
                    floatArrayOf(
                        cx - pulsedHalf,
                        cy - pulsedHalf + bracketLen,
                        cx - pulsedHalf,
                        cy - pulsedHalf,
                        cx - pulsedHalf + bracketLen,
                        cy - pulsedHalf,
                    ),
                    // top-right
                    floatArrayOf(
                        cx + pulsedHalf - bracketLen,
                        cy - pulsedHalf,
                        cx + pulsedHalf,
                        cy - pulsedHalf,
                        cx + pulsedHalf,
                        cy - pulsedHalf + bracketLen,
                    ),
                    // bottom-left
                    floatArrayOf(
                        cx - pulsedHalf,
                        cy + pulsedHalf - bracketLen,
                        cx - pulsedHalf,
                        cy + pulsedHalf,
                        cx - pulsedHalf + bracketLen,
                        cy + pulsedHalf,
                    ),
                    // bottom-right
                    floatArrayOf(
                        cx + pulsedHalf - bracketLen,
                        cy + pulsedHalf,
                        cx + pulsedHalf,
                        cy + pulsedHalf,
                        cx + pulsedHalf,
                        cy + pulsedHalf - bracketLen,
                    ),
                )

            for (c in corners) {
                val path = Path()
                path.moveTo(c[0], c[1])
                path.lineTo(c[2], c[3])
                path.lineTo(c[4], c[5])
                canvas.drawPath(path, bracketPaint)
            }

            // Scan line (only during SCANNING)
            if (currentState == State.SCANNING) {
                val scanY = cy - pulsedHalf + (2f * pulsedHalf * scanLineProgress)
                val scanWidth = pulsedHalf * 1.4f
                val gradient =
                    LinearGradient(
                        cx - scanWidth,
                        scanY,
                        cx + scanWidth,
                        scanY,
                        intArrayOf(
                            Color.argb(0, 100, 200, 255),
                            Color.argb(140, 100, 200, 255),
                            Color.argb(0, 100, 200, 255),
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                scanLinePaint.shader = gradient
                scanLinePaint.strokeWidth = 1.5f * density
                canvas.drawLine(cx - scanWidth, scanY, cx + scanWidth, scanY, scanLinePaint)
            }

            // Checkmark (SUCCESS) or X (FAILED) inside the frame
            when (currentState) {
                State.SUCCESS -> {
                    val checkPaint =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = stateColor
                            style = Paint.Style.STROKE
                            strokeWidth = 2.5f * density
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                    val s = halfSize * 0.5f
                    val path = Path()
                    path.moveTo(cx - s * 0.5f, cy)
                    path.lineTo(cx - s * 0.1f, cy + s * 0.4f)
                    path.lineTo(cx + s * 0.6f, cy - s * 0.4f)
                    canvas.drawPath(path, checkPaint)
                }

                State.FAILED -> {
                    val xPaint =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = stateColor
                            style = Paint.Style.STROKE
                            strokeWidth = 2.5f * density
                            strokeCap = Paint.Cap.ROUND
                        }
                    val s = halfSize * 0.35f
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, xPaint)
                    canvas.drawLine(cx + s, cy - s, cx - s, cy + s, xPaint)
                }

                else -> { /* no icon in scanning state, the scan line is the feedback */ }
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
                    State.SCANNING -> "Face ID"
                    State.SUCCESS -> "Done"
                    State.FAILED -> "Try Again"
                }
            // Vertically center the text
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, cx, textY, textPaint)
        }

        fun setState(state: State) {
            currentState = state
            when (state) {
                State.SUCCESS -> {
                    stopAnimations()
                    stateColor = Color.rgb(52, 199, 89) // iOS green
                    animateLockOpen()
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
                    duration = 1800
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
                    duration = 1200
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

        private fun animateLockOpen() {
            ObjectAnimator.ofFloat(this, "lockShackle", 0f, -6f * density).apply {
                duration = 400
                interpolator = OvershootInterpolator(2f)
                addUpdateListener { invalidate() }
                start()
            }
        }

        @Suppress("unused") // used by ObjectAnimator
        fun setLockShackle(value: Float) {
            lockShackleOffset = value
            invalidate()
        }

        @Suppress("unused") // used by ObjectAnimator
        fun getLockShackle(): Float = lockShackleOffset

        private fun animateShake() {
            val shakeAnim =
                ValueAnimator.ofFloat(0f, 12f, -10f, 8f, -6f, 4f, -2f, 0f).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        shakeOffset = (it.animatedValue as Float) * density
                        invalidate()
                    }
                }
            shakeAnim.start()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopAnimations()
        }
    }
}
