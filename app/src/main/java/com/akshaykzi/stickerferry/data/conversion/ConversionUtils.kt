package com.akshaykzi.stickerferry.data.conversion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF

object ConversionUtils {
    const val TARGET_SIZE = 512

    fun resizeAndPad(bitmap: Bitmap, targetSize: Int = TARGET_SIZE, scaleFactor: Float = 1.0f): Bitmap {
        val result = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        // WhatsApp requires a margin for the sticker
        val baseMargin = targetSize * 0.03125f
        val extraMargin = targetSize * ((1f - scaleFactor) / 2f)
        val margin = (baseMargin + extraMargin).toInt().coerceAtLeast(4)
        val safeSize = targetSize - (margin * 2)

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        
        val scale = minOf(safeSize.toFloat() / width, safeSize.toFloat() / height)
        val scaledWidth = width * scale
        val scaledHeight = height * scale

        val left = (targetSize - scaledWidth) / 2f
        val top = (targetSize - scaledHeight) / 2f

        val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
        canvas.drawBitmap(bitmap, null, destRect, paint)

        return result
    }
}
