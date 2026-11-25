package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.min

/**
 * Renders the Bible book, chapter, and verse reference along a gentle circular arc hugging the
 * lower rim of the Scallop circle widget. Shared by both the Compose live preview and the
 * home screen RemoteViews widget so they match pixel-for-pixel.
 */
object CurvedReferenceRenderer {

    /**
     * Height of the bottom-strip bitmap as a fraction of the circle diameter.
     * Covers from +0.14*D below center down to +0.50*D (the bottom edge of the circle),
     * leaving ample vertical room for even 23-character references while cutting bitmap IPC
     * memory by ~64%.
     */
    const val BOTTOM_STRIP_HEIGHT_FRACTION = 0.36f

    private const val ARC_SWEEP_DEGREES = 160f

    fun createReferencePaint(
        textSizePx: Float,
        textColor: Int,
        typeface: Typeface?
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = textColor
        textSize = textSizePx
        letterSpacing = 0.045f
        this.typeface = if (typeface != null) {
            Typeface.create(typeface, Typeface.BOLD)
        } else {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        isFakeBoldText = typeface != null && !this.typeface.isBold
        textAlign = Paint.Align.LEFT
    }

    /**
     * Draws [referenceText] upright and left-to-right along a counter-clockwise arc centered at
     * the bottom (6 o'clock / 90°) of a circle centered at ([centerX], [centerY]).
     */
    fun drawCurvedReference(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        outerInkRadiusPx: Float,
        referenceText: String,
        textSizePx: Float,
        textColor: Int,
        typeface: Typeface?
    ) {
        if (referenceText.isBlank() || outerInkRadiusPx <= 0f || textSizePx <= 0f) return

        val paint = createReferencePaint(textSizePx, textColor, typeface)
        val fm = paint.fontMetrics
        // Glyphs stand inward toward circle center; descenders extend outward by +fm.descent.
        val baselineRadius = (outerInkRadiusPx - fm.descent).coerceAtLeast(textSizePx)

        val arcBounds = RectF(
            centerX - baselineRadius,
            centerY - baselineRadius,
            centerX + baselineRadius,
            centerY + baselineRadius
        )

        // Counter-clockwise arc from lower-left (170°) through bottom (90°) to lower-right (10°)
        // renders text left-to-right and upright following the bottom curve of the circle.
        val path = Path().apply {
            addArc(arcBounds, 90f + ARC_SWEEP_DEGREES / 2f, -ARC_SWEEP_DEGREES)
        }

        val arcLength = (baselineRadius * Math.toRadians(ARC_SWEEP_DEGREES.toDouble())).toFloat()
        val textWidth = paint.measureText(referenceText)
        val hOffset = ((arcLength - textWidth) / 2f).coerceAtLeast(0f)

        canvas.drawTextOnPath(referenceText, path, hOffset, 0f, paint)
    }

    /**
     * Renders a compact, crisp transparent bottom-strip [Bitmap] sized for RemoteViews IPC.
     * The bitmap's width represents the full scallop circle diameter and its bottom edge aligns
     * with the bottom edge of the scallop circle.
     */
    fun renderBottomArcBitmap(
        diameterDp: Float,
        maxBitmapWidthPx: Int,
        density: Float,
        referenceText: String,
        referenceFontSizeDp: Float,
        outerInkRadiusFraction: Float,
        textColor: Int,
        typeface: Typeface?
    ): Bitmap {
        val targetWidthPx = min(
            ceil(diameterDp * density).toInt().coerceAtLeast(160),
            maxBitmapWidthPx
        )
        val targetHeightPx = ceil(targetWidthPx * BOTTOM_STRIP_HEIGHT_FRACTION).toInt().coerceAtLeast(64)

        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scale = targetWidthPx.toFloat() / diameterDp.coerceAtLeast(1f)
        val centerX = targetWidthPx / 2f
        val centerY = targetHeightPx.toFloat() - targetWidthPx / 2f
        val outerInkRadiusPx = targetWidthPx * outerInkRadiusFraction
        val scaledTextSizePx = referenceFontSizeDp * scale

        drawCurvedReference(
            canvas = canvas,
            centerX = centerX,
            centerY = centerY,
            outerInkRadiusPx = outerInkRadiusPx,
            referenceText = referenceText,
            textSizePx = scaledTextSizePx,
            textColor = textColor,
            typeface = typeface
        )

        return bitmap
    }
}
