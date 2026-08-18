package com.teegle.opened

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.time.format.TextStyle
import java.util.Locale

class PercentageBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var openFraction = 0f

    fun setUsage(openMs: Long, foldedMs: Long) {
        val total = openMs + foldedMs
        openFraction = if (total > 0) openMs.toFloat() / total else 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(16))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = height / 2f
        paint.color = context.getColor(R.color.opened_folded_bar)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        if (openFraction > 0f) {
            paint.color = context.getColor(R.color.opened_accent)
            canvas.save()
            canvas.clipRect(0f, 0f, width * openFraction, height.toFloat())
            canvas.drawRoundRect(bounds, radius, radius, paint)
            canvas.restore()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

class WeeklyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var days: List<DailyUsage> = emptyList()

    fun setDays(value: List<DailyUsage>) {
        days = value
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(190))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return
        val labelHeight = dp(28).toFloat()
        val chartHeight = height - labelHeight
        val maxTotal = days.maxOfOrNull { it.openMs + it.foldedMs }?.coerceAtLeast(1L) ?: 1L
        val slot = width / days.size.toFloat()
        val barWidth = slot * 0.48f

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(12f)
        days.forEachIndexed { index, day ->
            val center = slot * index + slot / 2f
            val total = day.openMs + day.foldedMs
            val totalHeight = chartHeight * total / maxTotal
            val openHeight = if (total > 0) totalHeight * day.openMs / total else 0f
            val foldedHeight = totalHeight - openHeight
            val bottom = chartHeight

            paint.color = context.getColor(R.color.opened_folded_bar)
            canvas.drawRoundRect(
                center - barWidth / 2f,
                bottom - totalHeight,
                center + barWidth / 2f,
                bottom,
                dp(5).toFloat(),
                dp(5).toFloat(),
                paint
            )
            if (openHeight > 0) {
                paint.color = context.getColor(R.color.opened_accent)
                canvas.drawRoundRect(
                    center - barWidth / 2f,
                    bottom - foldedHeight - openHeight,
                    center + barWidth / 2f,
                    bottom - foldedHeight + dp(4),
                    dp(5).toFloat(),
                    dp(5).toFloat(),
                    paint
                )
            }

            paint.color = context.getColor(R.color.opened_muted)
            val label = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            canvas.drawText(label, center, height - dp(7).toFloat(), paint)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun sp(value: Float) =
        value * resources.displayMetrics.density * resources.configuration.fontScale
}
