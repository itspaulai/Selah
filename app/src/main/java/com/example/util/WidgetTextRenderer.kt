package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.WidgetFontStyle
import com.example.model.WidgetShape
import com.example.model.WidgetSize
import com.example.model.WidgetTextScale
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Single-source-of-truth renderer shared by both the in-app Widget Studio preview and the
 * home screen RemoteViews widgets across all 29 Material Design 3 Expressive shapes.
 *
 * Drawing both the borderless M3 Expressive shape and the bundled Google Sans Flex / Newsreader
 * typography on Canvas guarantees 100% pixel-identical fonts, shapes, colors, and alignment
 * between Widget Studio and every Android launcher.
 */
object WidgetTextRenderer {

    const val UNIFIED_LINE_HEIGHT_RATIO = 1.20f

    fun loadVerseTypeface(context: Context, fontStyle: WidgetFontStyle): Typeface {
        val fontRes = when (fontStyle) {
            WidgetFontStyle.EDITORIAL_SERIF -> R.font.newsreader
            WidgetFontStyle.PIXEL_SANS -> R.font.google_sans_flex
        }
        return runCatching { ResourcesCompat.getFont(context, fontRes) }.getOrNull()
            ?: if (fontStyle == WidgetFontStyle.EDITORIAL_SERIF) Typeface.SERIF else Typeface.SANS_SERIF
    }

    fun loadReferenceTypeface(context: Context): Typeface {
        return runCatching { ResourcesCompat.getFont(context, R.font.google_sans_flex) }.getOrNull()
            ?: Typeface.SANS_SERIF
    }

    fun createVersePaint(
        textSizePx: Float,
        textColor: Int,
        typeface: Typeface
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = textColor
        textSize = textSizePx
        this.typeface = typeface
        textAlign = Paint.Align.LEFT
    }

    fun fitForWidget(
        verseText: String,
        referenceText: String,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        shape: WidgetShape,
        size: WidgetSize,
        textScale: WidgetTextScale,
        verseTypeface: Typeface
    ): FittedWidgetText {
        val paint = createVersePaint(
            textSizePx = 16f * density,
            textColor = 0,
            typeface = verseTypeface
        )
        return WidgetTextFitter.fitText(
            verseText = verseText,
            referenceText = referenceText,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            shape = shape,
            size = size,
            textScale = textScale,
            widthSafetyFactor = 0.94f,
            lineHeightRatio = UNIFIED_LINE_HEIGHT_RATIO
        ) { sample, fontSizePx ->
            paint.textSize = fontSizePx
            paint.measureText(sample)
        }
    }

    /**
     * Draws both the fitted verse lines (at each line's exact shape-aware horizontal span) and
     * the reference (curved bottom arc for circular/scalloped shapes, or crisp horizontal label
     * anchored inside the shape's safe bottom band) onto [canvas].
     */
    fun drawWidgetText(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        fitted: FittedWidgetText,
        referenceText: String,
        shape: WidgetShape,
        size: WidgetSize,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        verseTypeface: Typeface,
        referenceTypeface: Typeface
    ) {
        if (widthPx <= 0f || heightPx <= 0f) return

        val verseFontSizePx = fitted.verseFontSizeDp * density
        val lineHeightPx = verseFontSizePx * fitted.lineHeightMultiplier
        val refFontSizePx = fitted.referenceFontSizeDp * density

        val versePaint = createVersePaint(verseFontSizePx, primaryTextColor, verseTypeface).apply {
            textAlign = if (fitted.leftAligned) Paint.Align.LEFT else Paint.Align.CENTER
        }
        val fm = versePaint.fontMetrics
        val glyphHeight = fm.descent - fm.ascent
        val baselineOffsetInBand = ((lineHeightPx - glyphHeight) / 2f) - fm.ascent

        for (line in fitted.fittedLines) {
            val x = if (fitted.leftAligned) line.leftX else line.centerX
            val baselineY = line.topY + baselineOffsetInBand
            canvas.drawText(line.text, x, baselineY, versePaint)
        }

        if (fitted.isCurvedReference) {
            val diameter = min(widthPx, heightPx)
            val outerInkRadius = diameter * fitted.referenceArcOuterRadiusFraction
            CurvedReferenceRenderer.drawCurvedReference(
                canvas = canvas,
                centerX = widthPx / 2f,
                centerY = heightPx / 2f,
                outerInkRadiusPx = outerInkRadius,
                referenceText = referenceText,
                textSizePx = refFontSizePx,
                textColor = secondaryTextColor,
                typeface = referenceTypeface
            )
        } else {
            val refPaint = CurvedReferenceRenderer.createReferencePaint(
                textSizePx = refFontSizePx,
                textColor = secondaryTextColor,
                typeface = referenceTypeface
            ).apply {
                textAlign = if (fitted.leftAligned) Paint.Align.LEFT else Paint.Align.CENTER
            }
            val refFm = refPaint.fontMetrics
            val refBandH = (fitted.referenceBottomY - fitted.referenceTopY).coerceAtLeast(refFm.descent - refFm.ascent)
            val refBaselineY = fitted.referenceTopY + ((refBandH - (refFm.descent - refFm.ascent)) / 2f) - refFm.ascent
            val refX = if (fitted.leftAligned) fitted.referenceLeftX else fitted.referenceCenterX
            canvas.drawText(referenceText, refX, refBaselineY, refPaint)
        }
    }

    /**
     * Renders the complete home-screen widget (borderless M3 Expressive shape fill + exact
     * bundled typography) into a single crisp, compact transparent PNG suitable for
     * [android.graphics.drawable.Icon.createWithData] in RemoteViews.
     */
    fun renderWidgetCompositePng(
        verseText: String,
        referenceText: String,
        widthDp: Float,
        heightDp: Float,
        deviceDensity: Float,
        shape: WidgetShape,
        size: WidgetSize,
        textScale: WidgetTextScale,
        containerColor: Int,
        primaryTextColor: Int,
        secondaryTextColor: Int,
        verseTypeface: Typeface,
        referenceTypeface: Typeface
    ): ByteArray {
        // Always match the launcher's exact reported widget box (widthDp x heightDp) so
        // box-filling expressive shapes fill edge-to-edge with zero vertical or horizontal
        // letterboxing, while strictly circular shapes stay centered at min(w, h).
        val logicalWidthDp = widthDp.coerceAtLeast(80f)
        val logicalHeightDp = heightDp.coerceAtLeast(80f)

        val maxSidePx = when (size) {
            WidgetSize.SMALL -> 360
            WidgetSize.MEDIUM -> 620
            WidgetSize.LARGE -> 560
        }
        val desiredDensity = deviceDensity.coerceIn(2.0f, 2.5f)
        val maxLogicalSide = max(logicalWidthDp, logicalHeightDp).coerceAtLeast(1f)
        val effectiveDensity = min(desiredDensity, maxSidePx / maxLogicalSide)

        val targetWidthPx = ceil(logicalWidthDp * effectiveDensity).toInt().coerceAtLeast(160)
        val targetHeightPx = ceil(logicalHeightDp * effectiveDensity).toInt().coerceAtLeast(120)

        val fitted = fitForWidget(
            verseText = verseText,
            referenceText = referenceText,
            widthPx = targetWidthPx.toFloat(),
            heightPx = targetHeightPx.toFloat(),
            density = effectiveDensity,
            shape = shape,
            size = size,
            textScale = textScale,
            verseTypeface = verseTypeface
        )

        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Borderless Material 3 Expressive shape background
        val shapePath = M3ShapeGeometry.buildAndroidPath(
            shape = shape,
            width = targetWidthPx.toFloat(),
            height = targetHeightPx.toFloat(),
            size = size
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = containerColor
        }
        canvas.drawPath(shapePath, bgPaint)

        // 2. Exact bundled typography (verse + curved or horizontal expressive reference)
        drawWidgetText(
            canvas = canvas,
            widthPx = targetWidthPx.toFloat(),
            heightPx = targetHeightPx.toFloat(),
            density = effectiveDensity,
            fitted = fitted,
            referenceText = referenceText,
            shape = shape,
            size = size,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor,
            verseTypeface = verseTypeface,
            referenceTypeface = referenceTypeface
        )

        val out = ByteArrayOutputStream(24 * 1024)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
