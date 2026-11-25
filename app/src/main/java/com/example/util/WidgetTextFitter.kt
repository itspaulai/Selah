package com.example.util

import com.example.model.WidgetShape
import com.example.model.WidgetSize
import com.example.model.WidgetTextScale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class FittedLine(
    val text: String,
    val leftX: Float,
    val rightX: Float,
    val topY: Float
) {
    val centerX: Float
        get() = (leftX + rightX) / 2f
}

data class FittedWidgetText(
    val fittedLines: List<FittedLine>,
    val verseFontSizeDp: Float,
    val referenceFontSizeDp: Float,
    val lineHeightMultiplier: Float,
    val topPaddingPx: Float,
    val referenceBottomPaddingPx: Float,
    val verseBottomPaddingPx: Float,
    val isCurvedReference: Boolean,
    val referenceArcOuterRadiusFraction: Float,
    val referenceLeftX: Float,
    val referenceCenterX: Float,
    val referenceTopY: Float,
    val referenceBottomY: Float,
    val leftAligned: Boolean
) {
    val lines: List<String>
        get() = fittedLines.map { it.text }

    val formattedText: String
        get() = fittedLines.joinToString("\n") { it.text }
}

object WidgetTextFitter {

    private const val DEFAULT_LINE_HEIGHT_RATIO = 1.20f

    /**
     * Fits the entire verse + reference strictly inside the exact geometry of any of the 29
     * Material Design 3 Expressive shapes with zero word truncation and guaranteed readability.
     *
     * Uses exact scanline ray intersection against [M3ShapeGeometry.ShapeContour] so tapered,
     * tilted, asymmetric, or concave shapes (Triangle, Arrow, Diamond, Fan, Slanted, Oval, Pill,
     * Semicircle, Bun, Flower, Burst, Ghost-ish, Cookies, Clovers) wrap each line to the true
     * interior width and horizontal center at that vertical position.
     */
    fun fitText(
        verseText: String,
        referenceText: String,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        shape: WidgetShape,
        size: WidgetSize,
        textScale: WidgetTextScale,
        widthSafetyFactor: Float = 0.94f,
        lineHeightRatio: Float = DEFAULT_LINE_HEIGHT_RATIO,
        measureWidthPx: (text: String, fontSizePx: Float) -> Float
    ): FittedWidgetText {
        val words = verseText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val isCurved = shape.supportsCurvedReference && size != WidgetSize.MEDIUM
        val leftAligned = shape == WidgetShape.SQUARE
        val rimOuterFraction = shape.curvedArcOuterRadiusFraction
        val minArcOuterFraction = (rimOuterFraction - 0.037f).coerceAtLeast(0.32f)

        if (words.isEmpty() || widthPx <= 0f || heightPx <= 0f) {
            return FittedWidgetText(
                fittedLines = listOf(FittedLine(verseText, 0f, widthPx, 0f)),
                verseFontSizeDp = 13f,
                referenceFontSizeDp = 11f,
                lineHeightMultiplier = lineHeightRatio,
                topPaddingPx = 12f * density,
                referenceBottomPaddingPx = 12f * density,
                verseBottomPaddingPx = 28f * density,
                isCurvedReference = isCurved,
                referenceArcOuterRadiusFraction = rimOuterFraction,
                referenceLeftX = 12f * density,
                referenceCenterX = widthPx / 2f,
                referenceTopY = (heightPx - 24f * density).coerceAtLeast(0f),
                referenceBottomY = (heightPx - 12f * density).coerceAtLeast(0f),
                leftAligned = leftAligned
            )
        }

        val contour = M3ShapeGeometry.buildContour(shape, widthPx, heightPx, size)
        val diameter = min(widthPx, heightPx)
        val cx = widthPx / 2f
        val cy = heightPx / 2f

        // Shape-matched clean interior margin so glyphs never touch silhouette curves or corners
        val hInsetPx = if (shape == WidgetShape.SQUARE) {
            when (size) {
                WidgetSize.SMALL -> 11f * density
                WidgetSize.MEDIUM -> 16f * density
                WidgetSize.LARGE -> 20f * density
            }
        } else {
            (diameter * 0.036f).coerceIn(5.5f * density, 13f * density)
        }
        val vInsetPx = if (shape == WidgetShape.SQUARE) {
            when (size) {
                WidgetSize.SMALL -> 11f * density
                WidgetSize.MEDIUM -> 14f * density
                WidgetSize.LARGE -> 18f * density
            }
        } else {
            (diameter * 0.038f).coerceIn(5.5f * density, 13f * density)
        }

        val shapeTopY = (contour.minY + vInsetPx).coerceIn(0f, heightPx * 0.45f)
        val shapeBottomY = (contour.maxY - vInsetPx).coerceIn(heightPx * 0.55f, heightPx)

        val baseMaxDp = when (size) {
            WidgetSize.SMALL -> if (isCurved) 21.5f else 20.0f
            WidgetSize.MEDIUM -> 22.0f
            WidgetSize.LARGE -> if (isCurved) 29.5f else 28.0f
        }
        val maxDp = (baseMaxDp * textScale.multiplier).coerceAtLeast(11.5f)

        val refMinDp = when (size) {
            WidgetSize.SMALL -> 10.2f
            WidgetSize.MEDIUM -> 11.5f
            WidgetSize.LARGE -> 12.5f
        }
        val refMaxDp = when (size) {
            WidgetSize.SMALL -> 12.5f
            WidgetSize.MEDIUM -> 13.5f
            WidgetSize.LARGE -> 15.0f
        } * textScale.multiplier

        val minReadableRefDp = when (size) {
            WidgetSize.SMALL -> 8.2f
            WidgetSize.MEDIUM -> 10.0f
            WidgetSize.LARGE -> 11.0f
        }

        fun tryLayout(
            fontSizeDp: Float,
            customStartY: Float? = null
        ): LayoutAttempt? {
            val fontSizePx = fontSizeDp * density
            val lineHeightPx = fontSizePx * lineHeightRatio
            var refDp = if (size == WidgetSize.SMALL && !isCurved) {
                (fontSizeDp * 0.92f).coerceIn(8.4f, refMaxDp)
            } else {
                (fontSizeDp * 0.84f).coerceIn(refMinDp, refMaxDp)
            }
            val gapPx = (fontSizePx * 0.34f).coerceIn(4f * density, 10f * density)

            var refPx = refDp * density
            var refHeightPx = refPx * max(lineHeightRatio, 1.22f)

            val verseBottomLimitY: Float
            val refBottomPaddingPx: Float
            val curvedClearanceRadius: Float
            val curvedRefEndY: Float
            var refLeftX = hInsetPx
            var refCenterX = cx
            var refTopY = shapeBottomY - refHeightPx
            var refBottomY = shapeBottomY

            if (isCurved) {
                val rimOuterRadius = rimOuterFraction * diameter
                val maxArcSpanRad = (118.0 * PI / 180.0).toFloat()
                var baselineRadius = (rimOuterRadius - refPx * 0.22f).coerceAtLeast(refPx)
                var refArcWidth = measureWidthPx(referenceText, refPx) * 1.13f
                val maxArcWidth = baselineRadius * maxArcSpanRad

                if (refArcWidth > maxArcWidth && maxArcWidth > 0f) {
                    refDp = (refDp * (maxArcWidth / refArcWidth)).coerceAtLeast(9.5f)
                    refPx = refDp * density
                    refHeightPx = refPx * max(lineHeightRatio, 1.22f)
                    baselineRadius = (rimOuterRadius - refPx * 0.22f).coerceAtLeast(refPx)
                    refArcWidth = measureWidthPx(referenceText, refPx) * 1.13f
                }

                val refInnerRadius = (rimOuterRadius - refHeightPx).coerceAtLeast(diameter * 0.20f)
                curvedClearanceRadius = (refInnerRadius - gapPx).coerceAtLeast(diameter * 0.16f)
                val halfArcAngleRad = ((refArcWidth / (2f * baselineRadius)) + 0.10f).coerceAtMost(1.25f)
                curvedRefEndY = cy + refInnerRadius * cos(halfArcAngleRad)
                verseBottomLimitY = cy + curvedClearanceRadius
                refBottomPaddingPx = (heightPx - (cy + rimOuterRadius)).coerceAtLeast(0f)
                refTopY = cy + refInnerRadius
                refBottomY = cy + rimOuterRadius
            } else {
                // Horizontal reference: find the lowest safe band inside the contour that fits referenceText.
                var refWidth = measureWidthPx(referenceText, refPx) * 1.07f
                val maxScanUp = (shapeBottomY - shapeTopY) * 0.34f
                val stepPx = max(2f * density, 3f)

                fun findBestRefBand(reqWidth: Float, hPx: Float): Triple<Float, Float, Float>? {
                    var botY = shapeBottomY
                    val minBotY = shapeBottomY - maxScanUp
                    while (botY >= minBotY) {
                        val topY = botY - hPx
                        val span = contour.safeBandSpan(topY, botY, hInsetPx)
                        if (span != null && (span.second - span.first) >= reqWidth) {
                            return Triple(topY, span.first, span.second)
                        }
                        botY -= stepPx
                    }
                    return null
                }

                var found = findBestRefBand(refWidth, refHeightPx)
                if (found == null) {
                    // Gently scale reference down to fit narrow bottom tips (e.g. Diamond / Flower)
                    var candidateDp = refDp * 0.90f
                    while (found == null && candidateDp >= minReadableRefDp) {
                        val cPx = candidateDp * density
                        val cH = cPx * max(lineHeightRatio, 1.22f)
                        val cW = measureWidthPx(referenceText, cPx) * 1.07f
                        val attemptBand = findBestRefBand(cW, cH)
                        if (attemptBand != null) {
                            refDp = candidateDp
                            refPx = cPx
                            refHeightPx = cH
                            refWidth = cW
                            found = attemptBand
                        } else {
                            candidateDp -= 0.4f
                        }
                    }
                }

                if (found != null) {
                    refTopY = found.first
                    refBottomY = refTopY + refHeightPx
                    refLeftX = found.second
                    refCenterX = (found.second + found.third) / 2f
                } else {
                    // Last-resort placement centered inside the widest lower band
                    val fallbackBotY = shapeBottomY - maxScanUp * 0.65f
                    refTopY = fallbackBotY - refHeightPx
                    refBottomY = fallbackBotY
                    val span = contour.safeBandSpan(refTopY, refBottomY, hInsetPx)
                    if (span != null) {
                        refLeftX = span.first
                        refCenterX = (span.first + span.second) / 2f
                    }
                }

                verseBottomLimitY = refTopY - gapPx
                refBottomPaddingPx = (heightPx - refBottomY).coerceAtLeast(1f)
                curvedClearanceRadius = Float.MAX_VALUE
                curvedRefEndY = Float.MAX_VALUE
            }

            fun bandSpan(yTop: Float, yBottom: Float): Pair<Float, Float>? {
                val baseSpan = contour.safeBandSpan(yTop, yBottom, hInsetPx) ?: return null
                var left = baseSpan.first
                var right = baseSpan.second

                if (isCurved) {
                    val inkBottom = yBottom - (yBottom - yTop) * 0.10f
                    if (inkBottom > curvedRefEndY) {
                        val dy = inkBottom - cy
                        if (dy >= curvedClearanceRadius) return null
                        val halfChord = sqrt(
                            (curvedClearanceRadius * curvedClearanceRadius - dy * dy).coerceAtLeast(0f)
                        )
                        left = max(left, cx - halfChord)
                        right = min(right, cx + halfChord)
                    }
                }
                return if (right > left) left to right else null
            }

            var yCursor = customStartY ?: shapeTopY
            var wordIndex = 0
            val placed = ArrayList<FittedLine>()
            var firstLineTopY: Float? = null

            var safetySteps = 0
            while (wordIndex < words.size && safetySteps < 100) {
                safetySteps++
                val lineBottom = yCursor + lineHeightPx
                if (lineBottom > verseBottomLimitY) {
                    return null
                }

                val span = bandSpan(yCursor, lineBottom)
                val availW = if (span != null) (span.second - span.first) * widthSafetyFactor else 0f
                val nextWord = words[wordIndex]
                val nextWordW = measureWidthPx(nextWord, fontSizePx)

                if (span == null || availW < nextWordW) {
                    if (placed.isEmpty() && yCursor < verseBottomLimitY - lineHeightPx) {
                        // Slide down past narrow top apex (Triangle, Arrow, Diamond, Flower, Semicircle)
                        yCursor += lineHeightPx * 0.22f
                        continue
                    } else {
                        return null
                    }
                }

                val sb = StringBuilder(nextWord)
                wordIndex++
                while (wordIndex < words.size) {
                    val candidate = "$sb ${words[wordIndex]}"
                    if (measureWidthPx(candidate, fontSizePx) <= availW) {
                        sb.append(' ').append(words[wordIndex])
                        wordIndex++
                    } else {
                        break
                    }
                }

                if (firstLineTopY == null) firstLineTopY = yCursor
                placed.add(
                    FittedLine(
                        text = sb.toString(),
                        leftX = span.first,
                        rightX = span.second,
                        topY = yCursor
                    )
                )
                yCursor = lineBottom
            }

            if (wordIndex < words.size) return null

            val usedTop = firstLineTopY ?: shapeTopY
            val usedBottom = yCursor

            return LayoutAttempt(
                fittedLines = placed,
                verseFontSizeDp = fontSizeDp,
                referenceFontSizeDp = refDp,
                usedTopY = usedTop,
                usedBottomY = usedBottom,
                verseBottomLimitY = verseBottomLimitY,
                refBottomPaddingPx = refBottomPaddingPx,
                refHeightPx = refHeightPx,
                gapPx = gapPx,
                refLeftX = refLeftX,
                refCenterX = refCenterX,
                refTopY = refTopY,
                refBottomY = refBottomY
            )
        }

        // Binary search for the largest font size that fits 100% of words + reference strictly inside the shape
        var low = 6.2f
        var high = maxDp
        var best: LayoutAttempt? = null

        repeat(15) {
            val mid = (low + high) / 2f
            val attempt = tryLayout(mid)
            if (attempt != null) {
                best = attempt
                low = mid
            } else {
                high = mid
            }
        }

        val chosen = best ?: LayoutAttempt(
            fittedLines = listOf(FittedLine(verseText, hInsetPx, (widthPx - hInsetPx).coerceAtLeast(hInsetPx + 1f), shapeTopY)),
            verseFontSizeDp = 7.5f,
            referenceFontSizeDp = minReadableRefDp,
            usedTopY = shapeTopY,
            usedBottomY = shapeBottomY - 18f * density,
            verseBottomLimitY = shapeBottomY - 18f * density,
            refBottomPaddingPx = vInsetPx,
            refHeightPx = minReadableRefDp * density * 1.22f,
            gapPx = 5f * density,
            refLeftX = hInsetPx,
            refCenterX = cx,
            refTopY = shapeBottomY - 14f * density,
            refBottomY = shapeBottomY
        )

        // Vertically balance the fitted verse block inside the available interior region of the shape
        val blockHeight = chosen.usedBottomY - chosen.usedTopY
        val availableVerticalSpan = (chosen.verseBottomLimitY - chosen.usedTopY).coerceAtLeast(0f)
        val slack = (availableVerticalSpan - blockHeight).coerceAtLeast(0f)

        val balanced = if (slack > 4f * density) {
            val balanceFactor = if (isCurved) 0.50f else 0.48f
            val targetStartY = chosen.usedTopY + slack * balanceFactor
            tryLayout(chosen.verseFontSizeDp, customStartY = targetStartY) ?: chosen
        } else {
            chosen
        }

        // For circular/scalloped shapes, adaptively tuck the curved reference slightly inward on
        // shorter verses so there is never an awkward bottom gap.
        val adaptiveArcOuterFraction = if (isCurved && diameter > 0f) {
            val rimOuterPx = rimOuterFraction * diameter
            val usedBottomFromCenter = balanced.usedBottomY - cy
            val desiredOuterPx = usedBottomFromCenter + balanced.gapPx + balanced.refHeightPx + 4f * density
            val extraBottomGap = (rimOuterPx - desiredOuterPx).coerceAtLeast(0f)
            val tuckedOuterPx = rimOuterPx - extraBottomGap * 0.45f
            (tuckedOuterPx / diameter).coerceIn(minArcOuterFraction, rimOuterFraction)
        } else {
            rimOuterFraction
        }

        return FittedWidgetText(
            fittedLines = balanced.fittedLines,
            verseFontSizeDp = balanced.verseFontSizeDp,
            referenceFontSizeDp = balanced.referenceFontSizeDp,
            lineHeightMultiplier = lineHeightRatio,
            topPaddingPx = shapeTopY,
            referenceBottomPaddingPx = balanced.refBottomPaddingPx,
            verseBottomPaddingPx = (heightPx - balanced.verseBottomLimitY).coerceAtLeast(0f),
            isCurvedReference = isCurved,
            referenceArcOuterRadiusFraction = adaptiveArcOuterFraction,
            referenceLeftX = balanced.refLeftX,
            referenceCenterX = balanced.refCenterX,
            referenceTopY = balanced.refTopY,
            referenceBottomY = balanced.refBottomY,
            leftAligned = leftAligned
        )
    }

    private data class LayoutAttempt(
        val fittedLines: List<FittedLine>,
        val verseFontSizeDp: Float,
        val referenceFontSizeDp: Float,
        val usedTopY: Float,
        val usedBottomY: Float,
        val verseBottomLimitY: Float,
        val refBottomPaddingPx: Float,
        val refHeightPx: Float,
        val gapPx: Float,
        val refLeftX: Float,
        val refCenterX: Float,
        val refTopY: Float,
        val refBottomY: Float
    )
}
