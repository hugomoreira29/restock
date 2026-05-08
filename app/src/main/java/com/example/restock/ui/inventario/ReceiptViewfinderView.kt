package com.example.restock.ui.inventario

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Transparent overlay for the receipt camera viewfinder.
 * Draws a dim background with a clear receipt-shaped window and highlighted corner brackets.
 */
class ReceiptViewfinderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    private var frameRect = RectF()
    private val dimPath   = Path()

    // Corner bracket length in pixels (set in onSizeChanged)
    private var bracketLen = 0f
    private val frameRadius = resources.displayMetrics.density * 10f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Frame: 84% wide, 72% tall, shifted slightly above center
        val frameW = w * 0.84f
        val frameH = h * 0.72f
        val left   = (w - frameW) / 2f
        val top    = (h - frameH) / 2f - h * 0.03f
        frameRect  = RectF(left, top, left + frameW, top + frameH)

        bracketLen = resources.displayMetrics.density * 28f

        // Build the dim path: full screen minus the transparent receipt window
        dimPath.reset()
        dimPath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        dimPath.addRoundRect(frameRect, frameRadius, frameRadius, Path.Direction.CCW)
        dimPath.fillType = Path.FillType.EVEN_ODD
    }

    override fun onDraw(canvas: Canvas) {
        // Dark overlay with hole
        canvas.drawPath(dimPath, dimPaint)
        // Subtle border around the clear area
        canvas.drawRoundRect(frameRect, frameRadius, frameRadius, borderPaint)
        // Corner brackets
        drawCorners(canvas)
    }

    private fun drawCorners(canvas: Canvas) {
        val l  = frameRect.left
        val t  = frameRect.top
        val r  = frameRect.right
        val b  = frameRect.bottom
        val bl = bracketLen

        // Top-left
        canvas.drawLine(l, t + bl, l, t + frameRadius, cornerPaint)
        canvas.drawLine(l, t, l + bl, t, cornerPaint)
        // Top-right
        canvas.drawLine(r - bl, t, r, t, cornerPaint)
        canvas.drawLine(r, t, r, t + bl, cornerPaint)
        // Bottom-left
        canvas.drawLine(l, b - bl, l, b, cornerPaint)
        canvas.drawLine(l, b, l + bl, b, cornerPaint)
        // Bottom-right
        canvas.drawLine(r - bl, b, r, b, cornerPaint)
        canvas.drawLine(r, b, r, b - bl, cornerPaint)
    }
}
