package com.example.util

import android.graphics.Path
import com.example.model.WidgetShape
import com.example.model.WidgetSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure-Kotlin parametric geometry engine for all 29 Material Design 3 Expressive shapes.
 *
 * Every shape is defined as a closed list of cubic Bézier segments so that:
 * 1. Compose previews, shape picker thumbnails, and Home Screen widgets render the EXACT same curve.
 * 2. [WidgetTextFitter] can evaluate exact horizontal interior spans [leftX, rightX] at any Y via
 *    pure-Kotlin scanline ray intersection (100% deterministic in Android & JVM Robolectric).
 */
object M3ShapeGeometry {

    private const val KAPPA = 0.55228475f

    data class CubicSeg(
        val x0: Float,
        val y0: Float,
        val cx1: Float,
        val cy1: Float,
        val cx2: Float,
        val cy2: Float,
        val x1: Float,
        val y1: Float
    )

    data class VertexSpec(
        val x: Float,
        val y: Float,
        val rounding: Float
    )

    data class ShapeContour(
        val cubics: List<CubicSeg>,
        val xs: FloatArray,
        val ys: FloatArray,
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float
    ) {
        fun toAndroidPath(): Path {
            val path = Path()
            if (cubics.isEmpty()) return path
            path.moveTo(cubics[0].x0, cubics[0].y0)
            for (c in cubics) {
                path.cubicTo(c.cx1, c.cy1, c.cx2, c.cy2, c.x1, c.y1)
            }
            path.close()
            return path
        }

        /**
         * Returns the widest interior horizontal interval (leftX, rightX) inside the polygon at [y],
         * or null if [y] lies outside the shape.
         */
        fun horizontalSpanAt(y: Float): Pair<Float, Float>? {
            val n = xs.size
            if (n < 3) return null
            val intersections = FloatArray(24)
            var count = 0
            var j = n - 1
            for (i in 0 until n) {
                val yi = ys[i]
                val yj = ys[j]
                if ((yi <= y && yj > y) || (yj <= y && yi > y)) {
                    val t = (y - yi) / (yj - yi)
                    val ix = xs[i] + t * (xs[j] - xs[i])
                    if (count < intersections.size) {
                        intersections[count++] = ix
                    }
                }
                j = i
            }
            if (count < 2) return null
            // Insertion sort tiny array
            for (i in 1 until count) {
                val key = intersections[i]
                var k = i - 1
                while (k >= 0 && intersections[k] > key) {
                    intersections[k + 1] = intersections[k]
                    k--
                }
                intersections[k + 1] = key
            }
            var bestLeft = 0f
            var bestRight = 0f
            var bestWidth = -1f
            var idx = 0
            while (idx + 1 < count) {
                val l = intersections[idx]
                val r = intersections[idx + 1]
                val w = r - l
                if (w > bestWidth) {
                    bestWidth = w
                    bestLeft = l
                    bestRight = r
                }
                idx += 2
            }
            return if (bestWidth > 0f) bestLeft to bestRight else null
        }

        /**
         * Computes the guaranteed safe interior horizontal interval [leftX, rightX] across a
         * vertical band [yTop, yBottom], inset inward on both sides by [horizontalInsetPx].
         */
        fun safeBandSpan(
            yTop: Float,
            yBottom: Float,
            horizontalInsetPx: Float
        ): Pair<Float, Float>? {
            val bandH = (yBottom - yTop).coerceAtLeast(0f)
            val s0 = horizontalSpanAt(yTop + bandH * 0.12f) ?: return null
            val s1 = horizontalSpanAt(yTop + bandH * 0.50f) ?: return null
            val s2 = horizontalSpanAt(yBottom - bandH * 0.12f) ?: return null

            val left = max(s0.first, max(s1.first, s2.first)) + horizontalInsetPx
            val right = min(s0.second, min(s1.second, s2.second)) - horizontalInsetPx
            return if (right > left) left to right else null
        }
    }

    fun buildContour(
        shape: WidgetShape,
        width: Float,
        height: Float,
        size: WidgetSize = WidgetSize.SMALL
    ): ShapeContour {
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        val isWide = w > h * 1.25f && size == WidgetSize.MEDIUM

        val rawCubics = buildCubics(shape, w, h, isWide)
        val normalized = fitCubicsToBox(
            cubics = rawCubics,
            w = w,
            h = h,
            fillBothAxes = shouldFillBothAxes(shape, isWide),
            maxAspectDistortion = maxAllowedAspectAdaptation(shape, isWide)
        )
        return contourFromCubics(normalized)
    }

    fun buildAndroidPath(
        shape: WidgetShape,
        width: Float,
        height: Float,
        size: WidgetSize = WidgetSize.SMALL
    ): Path = buildContour(shape, width, height, size).toAndroidPath()

    private fun shouldFillBothAxes(shape: WidgetShape, isWide: Boolean): Boolean = when (shape) {
        WidgetShape.SQUARE,
        WidgetShape.SLANTED,
        WidgetShape.ARCH,
        WidgetShape.FAN,
        WidgetShape.GHOSTISH -> true
        WidgetShape.CIRCLE -> false
        else -> isWide
    }

    private fun maxAllowedAspectAdaptation(shape: WidgetShape, isWide: Boolean): Float = when {
        isWide && shape != WidgetShape.CIRCLE -> 1.0f // fillBothAxes handles wide shapes cleanly
        shape.supportsCurvedReference -> 1.0f // keep circular/curved-arc shapes strictly 1:1
        shape == WidgetShape.SEMICIRCLE || shape == WidgetShape.CLAMSHELL || shape == WidgetShape.PUFFY -> 1.10f
        else -> 1.18f // gently adapt to slightly non-square 2x2 / 4x4 launcher cells without distortion
    }

    private fun buildCubics(
        shape: WidgetShape,
        w: Float,
        h: Float,
        isWide: Boolean
    ): List<CubicSeg> {
        val cx = w / 2f
        val cy = h / 2f
        val d = min(w, h)
        val r = d / 2f

        return when (shape) {
            WidgetShape.CIRCLE -> ellipseCubics(cx, cy, r, r)

            WidgetShape.SQUARE -> {
                val cr = min(w, h) * if (isWide) 0.22f else 0.24f
                roundedRectCubics(0f, 0f, w, h, cr)
            }

            WidgetShape.SLANTED -> {
                val shear = h * 0.11f
                val round = if (isWide) 0.19f else 0.23f
                roundedPolygonCubics(
                    listOf(
                        VertexSpec(shear, 0f, round),
                        VertexSpec(w, 0f, round),
                        VertexSpec(w - shear, h, round),
                        VertexSpec(0f, h, round)
                    )
                )
            }

            WidgetShape.ARCH -> {
                val topR = min(w / 2f, h * 0.54f)
                val botR = min(w, h) * 0.14f
                archCubics(0f, 0f, w, h, topR, botR)
            }

            WidgetShape.SEMICIRCLE -> {
                val boxW = if (isWide) w else d
                val boxH = if (isWide) h else d * 0.66f
                val left = cx - boxW / 2f
                val right = cx + boxW / 2f
                val top = cy - boxH / 2f
                val bottom = cy + boxH / 2f
                val botR = boxH * 0.14f
                semicircleCubics(left, top, right, bottom, botR)
            }

            WidgetShape.OVAL -> {
                if (isWide) {
                    ellipseCubics(cx, cy, w / 2f, h / 2f)
                } else {
                    rotateCubics(
                        ellipseCubics(cx, cy, r, r * 0.68f),
                        cx, cy, -45.0
                    )
                }
            }

            WidgetShape.PILL -> {
                if (isWide) {
                    roundedRectCubics(0f, 0f, w, h, h / 2f)
                } else {
                    val pillW = d
                    val pillH = d * 0.72f
                    rotateCubics(
                        roundedRectCubics(
                            cx - pillW / 2f,
                            cy - pillH / 2f,
                            cx + pillW / 2f,
                            cy + pillH / 2f,
                            pillH / 2f
                        ),
                        cx, cy, -45.0
                    )
                }
            }

            WidgetShape.TRIANGLE -> {
                val boxW = if (isWide) w else d
                val boxH = if (isWide) h else d * 0.94f
                val top = cy - boxH / 2f
                val bottom = cy + boxH / 2f
                roundedPolygonCubics(
                    listOf(
                        VertexSpec(cx, top, 0.20f),
                        VertexSpec(cx + boxW / 2f, bottom, 0.20f),
                        VertexSpec(cx - boxW / 2f, bottom, 0.20f)
                    )
                )
            }

            WidgetShape.ARROW -> {
                val boxW = if (isWide) w else d
                val boxH = if (isWide) h else d * 0.94f
                val top = cy - boxH / 2f
                val bottom = cy + boxH / 2f
                val notchY = bottom - boxH * 0.14f
                roundedPolygonCubics(
                    listOf(
                        VertexSpec(cx, top, 0.24f),
                        VertexSpec(cx + boxW / 2f, bottom, 0.28f),
                        VertexSpec(cx, notchY, 0.48f),
                        VertexSpec(cx - boxW / 2f, bottom, 0.28f)
                    )
                )
            }

            WidgetShape.FAN -> fanCubics(0f, 0f, w, h)

            WidgetShape.DIAMOND -> {
                val rx = if (isWide) w / 2f else r * 0.88f
                val ry = if (isWide) h / 2f else r
                roundedPolygonCubics(
                    listOf(
                        VertexSpec(cx, cy - ry, 0.22f),
                        VertexSpec(cx + rx, cy, 0.22f),
                        VertexSpec(cx, cy + ry, 0.22f),
                        VertexSpec(cx - rx, cy, 0.22f)
                    )
                )
            }

            WidgetShape.CLAMSHELL -> {
                val boxW = if (isWide) w else d
                val boxH = if (isWide) h else d * 0.76f
                val inset = min(boxW * 0.22f, boxH * 0.42f)
                val left = cx - boxW / 2f
                val right = cx + boxW / 2f
                val top = cy - boxH / 2f
                val bottom = cy + boxH / 2f
                roundedPolygonCubics(
                    listOf(
                        VertexSpec(left + inset, top, 0.24f),
                        VertexSpec(right - inset, top, 0.24f),
                        VertexSpec(right, cy, 0.24f),
                        VertexSpec(right - inset, bottom, 0.24f),
                        VertexSpec(left + inset, bottom, 0.24f),
                        VertexSpec(left, cy, 0.24f)
                    )
                )
            }

            WidgetShape.PENTAGON -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                regularPolygonCubics(cx, cy, rx, ry, sides = 5, startDeg = -90.0, rounding = 0.22f)
            }

            WidgetShape.GEM -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                regularPolygonCubics(cx, cy, rx, ry, sides = 6, startDeg = -90.0, rounding = 0.28f)
            }

            WidgetShape.VERY_SUNNY -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                starCubics(cx, cy, rx, ry, innerRatio = 0.72f, points = 8, startDeg = -90.0, outerRounding = 0.34f, innerRounding = 0.32f)
            }

            WidgetShape.SUNNY -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                starCubics(cx, cy, rx, ry, innerRatio = 0.83f, points = 8, startDeg = -90.0, outerRounding = 0.34f, innerRounding = 0.34f)
            }

            WidgetShape.COOKIE_4 -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                scallopRadialCubics(cx, cy, rx, ry, lobes = 4, amplitudeFraction = 0.115f, startDeg = -45.0)
            }

            WidgetShape.COOKIE_6 -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                scallopRadialCubics(cx, cy, rx, ry, lobes = 6, amplitudeFraction = 0.068f, startDeg = -90.0)
            }

            WidgetShape.COOKIE_7 -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                scallopRadialCubics(cx, cy, rx, ry, lobes = 7, amplitudeFraction = 0.060f, startDeg = -90.0)
            }

            WidgetShape.COOKIE_9 -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                scallopRadialCubics(cx, cy, rx, ry, lobes = 9, amplitudeFraction = 0.052f, startDeg = -90.0)
            }

            WidgetShape.COOKIE_12 -> {
                if (isWide) {
                    scallopStadiumCubics(w, h, straightLobes = 4, amplitudeFraction = 0.048f)
                } else {
                    scallopRadialCubics(cx, cy, r, r, lobes = 12, amplitudeFraction = 0.048f, startDeg = -90.0)
                }
            }

            WidgetShape.CLOVER_4 -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                starCubics(cx, cy, rx, ry, innerRatio = 0.67f, points = 4, startDeg = -45.0, outerRounding = 0.48f, innerRounding = 0.14f)
            }

            WidgetShape.CLOVER_8 -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                starCubics(cx, cy, rx, ry, innerRatio = 0.81f, points = 8, startDeg = -67.5, outerRounding = 0.46f, innerRounding = 0.10f)
            }

            WidgetShape.BURST -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                starCubics(cx, cy, rx, ry, innerRatio = 0.68f, points = 12, startDeg = -90.0, outerRounding = 0.02f, innerRounding = 0.02f)
            }

            WidgetShape.SOFT_BURST -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                starCubics(cx, cy, rx, ry, innerRatio = 0.74f, points = 10, startDeg = -90.0, outerRounding = 0.28f, innerRounding = 0.24f)
            }

            WidgetShape.FLOWER -> {
                val rx = if (isWide) w / 2f else r
                val ry = if (isWide) h / 2f else r
                starCubics(cx, cy, rx, ry, innerRatio = 0.63f, points = 8, startDeg = -90.0, outerRounding = 0.44f, innerRounding = 0.08f)
            }

            WidgetShape.PUFFY -> {
                val rx = if (isWide) w / 2f else d * 0.50f
                val ry = if (isWide) h / 2f else d * 0.41f
                starCubics(cx, cy, rx, ry, innerRatio = 0.82f, points = 10, startDeg = -90.0, outerRounding = 0.46f, innerRounding = 0.10f)
            }

            WidgetShape.GHOSTISH -> ghostishCubics(0f, 0f, w, h)

            WidgetShape.BUN -> {
                val boxW = if (isWide) w else d * 0.92f
                val boxH = if (isWide) h else d
                val left = cx - boxW / 2f
                val right = cx + boxW / 2f
                val top = cy - boxH / 2f
                val bottom = cy + boxH / 2f
                bunCubics(left, top, right, bottom)
            }
        }
    }

    private fun ellipseCubics(cx: Float, cy: Float, rx: Float, ry: Float): List<CubicSeg> {
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        return listOf(
            CubicSeg(cx, cy - ry, cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy),
            CubicSeg(cx + rx, cy, cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
            CubicSeg(cx, cy + ry, cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
            CubicSeg(cx - rx, cy, cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
        )
    }

    private fun roundedRectCubics(l: Float, t: Float, r: Float, b: Float, radius: Float): List<CubicSeg> {
        val cr = radius.coerceIn(0f, min((r - l) / 2f, (b - t) / 2f))
        val k = cr * KAPPA
        val out = ArrayList<CubicSeg>(8)
        // Top edge
        if (r - l > 2f * cr + 0.1f) out.add(lineSeg(l + cr, t, r - cr, t))
        // Top-right corner
        out.add(CubicSeg(r - cr, t, r - cr + k, t, r, t + cr - k, r, t + cr))
        // Right edge
        if (b - t > 2f * cr + 0.1f) out.add(lineSeg(r, t + cr, r, b - cr))
        // Bottom-right corner
        out.add(CubicSeg(r, b - cr, r, b - cr + k, r - cr + k, b, r - cr, b))
        // Bottom edge
        if (r - l > 2f * cr + 0.1f) out.add(lineSeg(r - cr, b, l + cr, b))
        // Bottom-left corner
        out.add(CubicSeg(l + cr, b, l + cr - k, b, l, b - cr + k, l, b - cr))
        // Left edge
        if (b - t > 2f * cr + 0.1f) out.add(lineSeg(l, b - cr, l, t + cr))
        // Top-left corner
        out.add(CubicSeg(l, t + cr, l, t + cr - k, l + cr - k, t, l + cr, t))
        return out
    }

    private fun archCubics(
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        topRadius: Float,
        botRadius: Float
    ): List<CubicSeg> {
        val tr = topRadius.coerceAtMost(min((r - l) / 2f, (b - t) * 0.65f))
        val br = botRadius.coerceAtMost(min((r - l) / 2f, (b - t) - tr))
        val tk = tr * KAPPA
        val bk = br * KAPPA
        val out = ArrayList<CubicSeg>(8)
        if (r - l > 2f * tr + 0.1f) out.add(lineSeg(l + tr, t, r - tr, t))
        out.add(CubicSeg(r - tr, t, r - tr + tk, t, r, t + tr - tk, r, t + tr))
        if (b - t > tr + br + 0.1f) out.add(lineSeg(r, t + tr, r, b - br))
        out.add(CubicSeg(r, b - br, r, b - br + bk, r - br + bk, b, r - br, b))
        if (r - l > 2f * br + 0.1f) out.add(lineSeg(r - br, b, l + br, b))
        out.add(CubicSeg(l + br, b, l + br - bk, b, l, b - br + bk, l, b - br))
        if (b - t > tr + br + 0.1f) out.add(lineSeg(l, b - br, l, t + tr))
        out.add(CubicSeg(l, t + tr, l, t + tr - tk, l + tr - tk, t, l + tr, t))
        return out
    }

    private fun semicircleCubics(
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        botRadius: Float
    ): List<CubicSeg> {
        val cx = (l + r) / 2f
        val rx = (r - l) / 2f
        val br = botRadius.coerceAtMost((b - t) * 0.25f)
        val archBottomY = b - br
        val ry = (archBottomY - t).coerceAtLeast(1f)
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        val bk = br * KAPPA
        return listOf(
            // Top-right quarter ellipse
            CubicSeg(cx, t, cx + kx, t, r, archBottomY - ky, r, archBottomY),
            // Bottom-right small corner
            CubicSeg(r, archBottomY, r, archBottomY + bk, r - br + bk, b, r - br, b),
            // Flat bottom
            lineSeg(r - br, b, l + br, b),
            // Bottom-left small corner
            CubicSeg(l + br, b, l + br - bk, b, l, archBottomY + bk, l, archBottomY),
            // Top-left quarter ellipse
            CubicSeg(l, archBottomY, l, archBottomY - ky, cx - kx, t, cx, t)
        )
    }

    private fun fanCubics(l: Float, t: Float, r: Float, b: Float): List<CubicSeg> {
        val w = r - l
        val h = b - t
        val sr = min(w, h) * 0.15f
        val sk = sr * KAPPA
        val bigRx = (w - sr).coerceAtLeast(1f)
        val bigRy = (h - sr).coerceAtLeast(1f)
        return listOf(
            // Giant smooth quarter-circle arc from top-left-shoulder to bottom-right-shoulder
            CubicSeg(
                l + sr, t,
                l + sr + bigRx * KAPPA, t,
                r, b - sr - bigRy * KAPPA,
                r, b - sr
            ),
            // Bottom-right soft corner
            CubicSeg(r, b - sr, r, b - sr + sk, r - sr + sk, b, r - sr, b),
            // Bottom edge
            lineSeg(r - sr, b, l + sr, b),
            // Bottom-left soft corner
            CubicSeg(l + sr, b, l + sr - sk, b, l, b - sr + sk, l, b - sr),
            // Left edge
            lineSeg(l, b - sr, l, t + sr),
            // Top-left soft corner
            CubicSeg(l, t + sr, l, t + sr - sk, l + sr - sk, t, l + sr, t)
        )
    }

    private fun ghostishCubics(l: Float, t: Float, r: Float, b: Float): List<CubicSeg> {
        val w = r - l
        val h = b - t
        val cx = (l + r) / 2f
        val topR = min(w / 2f, h * 0.50f)
        val tk = topR * KAPPA
        val footY = b - h * 0.08f
        val archDipY = b - h * 0.13f
        val footR = min(w * 0.18f, h * 0.16f)
        return listOf(
            // Top flat (if wide)
            lineSeg(l + topR, t, r - topR, t),
            // Top-right arch
            CubicSeg(r - topR, t, r - topR + tk, t, r, t + topR - tk, r, t + topR),
            // Right side down
            lineSeg(r, t + topR, r, footY - footR * 0.5f),
            // Bottom-right rounded lobe
            CubicSeg(r, footY - footR * 0.5f, r, b + h * 0.02f, r - w * 0.22f, b + h * 0.01f, r - w * 0.30f, archDipY + h * 0.04f),
            // Gentle upward wave across bottom center
            CubicSeg(r - w * 0.30f, archDipY + h * 0.04f, cx + w * 0.12f, archDipY - h * 0.03f, cx - w * 0.12f, archDipY - h * 0.03f, l + w * 0.30f, archDipY + h * 0.04f),
            // Bottom-left rounded lobe
            CubicSeg(l + w * 0.30f, archDipY + h * 0.04f, l + w * 0.22f, b + h * 0.01f, l, b + h * 0.02f, l, footY - footR * 0.5f),
            // Left side up
            lineSeg(l, footY - footR * 0.5f, l, t + topR),
            // Top-left arch
            CubicSeg(l, t + topR, l, t + topR - tk, l + topR - tk, t, l + topR, t)
        )
    }

    private fun bunCubics(l: Float, t: Float, r: Float, b: Float): List<CubicSeg> {
        val w = r - l
        val h = b - t
        val cy = (t + b) / 2f
        val lobeR = h * 0.25f
        val waistInset = w * 0.14f
        val kl = lobeR * KAPPA
        val topMidY = t + lobeR
        val botMidY = b - lobeR
        return listOf(
            // Top edge
            lineSeg(l + lobeR, t, r - lobeR, t),
            // Upper-right semicircle top half
            CubicSeg(r - lobeR, t, r - lobeR + kl, t, r, topMidY - kl, r, topMidY),
            // Upper-right pinch into waist
            CubicSeg(r, topMidY, r, topMidY + lobeR * 0.72f, r - waistInset * 0.35f, cy - h * 0.04f, r - waistInset, cy),
            // Lower-right expand out from waist
            CubicSeg(r - waistInset, cy, r - waistInset * 0.35f, cy + h * 0.04f, r, botMidY - lobeR * 0.72f, r, botMidY),
            // Lower-right semicircle bottom half
            CubicSeg(r, botMidY, r, botMidY + kl, r - lobeR + kl, b, r - lobeR, b),
            // Bottom edge
            lineSeg(r - lobeR, b, l + lobeR, b),
            // Lower-left semicircle bottom half
            CubicSeg(l + lobeR, b, l + lobeR - kl, b, l, botMidY + kl, l, botMidY),
            // Lower-left pinch into waist
            CubicSeg(l, botMidY, l, botMidY - lobeR * 0.72f, l + waistInset * 0.35f, cy + h * 0.04f, l + waistInset, cy),
            // Upper-left expand out from waist
            CubicSeg(l + waistInset, cy, l + waistInset * 0.35f, cy - h * 0.04f, l, topMidY + lobeR * 0.72f, l, topMidY),
            // Upper-left semicircle top half
            CubicSeg(l, topMidY, l, topMidY - kl, l + lobeR - kl, t, l + lobeR, t)
        )
    }

    private fun regularPolygonCubics(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        sides: Int,
        startDeg: Double,
        rounding: Float
    ): List<CubicSeg> {
        val verts = ArrayList<VertexSpec>(sides)
        val step = 2.0 * PI / sides
        val startRad = Math.toRadians(startDeg)
        for (i in 0 until sides) {
            val a = startRad + i * step
            verts.add(VertexSpec(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat(), rounding))
        }
        return roundedPolygonCubics(verts)
    }

    private fun starCubics(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        innerRatio: Float,
        points: Int,
        startDeg: Double,
        outerRounding: Float,
        innerRounding: Float
    ): List<CubicSeg> {
        val total = points * 2
        val verts = ArrayList<VertexSpec>(total)
        val step = 2.0 * PI / total
        val startRad = Math.toRadians(startDeg)
        for (i in 0 until total) {
            val a = startRad + i * step
            val isOuter = (i % 2 == 0)
            val scale = if (isOuter) 1f else innerRatio
            verts.add(
                VertexSpec(
                    x = cx + rx * scale * cos(a).toFloat(),
                    y = cy + ry * scale * sin(a).toFloat(),
                    rounding = if (isOuter) outerRounding else innerRounding
                )
            )
        }
        return roundedPolygonCubics(verts)
    }

    private fun scallopRadialCubics(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        lobes: Int,
        amplitudeFraction: Float,
        startDeg: Double
    ): List<CubicSeg> {
        val ampX = rx * amplitudeFraction
        val ampY = ry * amplitudeFraction
        val baseX = rx - ampX
        val baseY = ry - ampY
        val steps = lobes * 2
        val dt = (2.0 * PI) / steps
        val tangentScale = (dt / 3.0).toFloat()
        val startRad = Math.toRadians(startDeg)

        fun pt(i: Int): Pair<Float, Float> {
            val theta = startRad + i * dt
            val sign = if (i % 2 == 0) 1f else -1f
            val curRx = baseX + sign * ampX
            val curRy = baseY + sign * ampY
            return (cx + curRx * cos(theta).toFloat()) to (cy + curRy * sin(theta).toFloat())
        }

        fun tan(i: Int): Pair<Float, Float> {
            val theta = startRad + i * dt
            val sign = if (i % 2 == 0) 1f else -1f
            val curRx = baseX + sign * ampX
            val curRy = baseY + sign * ampY
            return (-curRx * sin(theta).toFloat() * tangentScale) to (curRy * cos(theta).toFloat() * tangentScale)
        }

        val out = ArrayList<CubicSeg>(steps)
        for (i in 0 until steps) {
            val (p0x, p0y) = pt(i)
            val (t0x, t0y) = tan(i)
            val next = (i + 1) % steps
            val (p3x, p3y) = pt(next)
            val (t1x, t1y) = tan(next)
            out.add(CubicSeg(p0x, p0y, p0x + t0x, p0y + t0y, p3x - t1x, p3y - t1y, p3x, p3y))
        }
        return out
    }

    private fun scallopStadiumCubics(
        w: Float,
        h: Float,
        straightLobes: Int,
        amplitudeFraction: Float
    ): List<CubicSeg> {
        val maxR = h / 2f
        val amp = maxR * amplitudeFraction
        val baseR = maxR - amp
        val leftCx = maxR
        val rightCx = w - maxR
        val cy = maxR
        val straightSpan = (rightCx - leftCx).coerceAtLeast(1f)

        val semiSteps = 12
        val dt = PI / semiSteps
        val tangentScale = (dt / 3.0).toFloat()

        fun arcPt(centerX: Float, theta: Double, peak: Boolean): Pair<Float, Float> {
            val rad = baseR + if (peak) amp else -amp
            return (centerX + rad * cos(theta).toFloat()) to (cy + rad * sin(theta).toFloat())
        }

        fun arcTan(theta: Double, peak: Boolean): Pair<Float, Float> {
            val rad = baseR + if (peak) amp else -amp
            return (-rad * sin(theta).toFloat() * tangentScale) to (rad * cos(theta).toFloat() * tangentScale)
        }

        val out = ArrayList<CubicSeg>()
        val topPeakY = cy - (baseR + amp)
        val topValleyY = cy - (baseR - amp)
        val bottomPeakY = cy + (baseR + amp)
        val bottomValleyY = cy + (baseR - amp)

        val halfWaves = straightLobes * 2
        val segW = straightSpan / halfWaves
        val ctrlDx = segW / 3f

        for (i in 0 until halfWaves) {
            val x0 = leftCx + i * segW
            val x1 = leftCx + (i + 1) * segW
            val y0 = if (i % 2 == 0) topPeakY else topValleyY
            val y1 = if (i % 2 == 0) topValleyY else topPeakY
            out.add(CubicSeg(x0, y0, x0 + ctrlDx, y0, x1 - ctrlDx, y1, x1, y1))
        }
        for (i in 0 until semiSteps) {
            val t0 = -PI / 2.0 + i * dt
            val t1 = -PI / 2.0 + (i + 1) * dt
            val (p0x, p0y) = arcPt(rightCx, t0, i % 2 == 0)
            val (t0x, t0y) = arcTan(t0, i % 2 == 0)
            val (p3x, p3y) = arcPt(rightCx, t1, (i + 1) % 2 == 0)
            val (t1x, t1y) = arcTan(t1, (i + 1) % 2 == 0)
            out.add(CubicSeg(p0x, p0y, p0x + t0x, p0y + t0y, p3x - t1x, p3y - t1y, p3x, p3y))
        }
        for (i in 0 until halfWaves) {
            val x0 = rightCx - i * segW
            val x1 = rightCx - (i + 1) * segW
            val y0 = if (i % 2 == 0) bottomPeakY else bottomValleyY
            val y1 = if (i % 2 == 0) bottomValleyY else bottomPeakY
            out.add(CubicSeg(x0, y0, x0 - ctrlDx, y0, x1 + ctrlDx, y1, x1, y1))
        }
        for (i in 0 until semiSteps) {
            val t0 = PI / 2.0 + i * dt
            val t1 = PI / 2.0 + (i + 1) * dt
            val (p0x, p0y) = arcPt(leftCx, t0, i % 2 == 0)
            val (t0x, t0y) = arcTan(t0, i % 2 == 0)
            val (p3x, p3y) = arcPt(leftCx, t1, (i + 1) % 2 == 0)
            val (t1x, t1y) = arcTan(t1, (i + 1) % 2 == 0)
            out.add(CubicSeg(p0x, p0y, p0x + t0x, p0y + t0y, p3x - t1x, p3y - t1y, p3x, p3y))
        }
        return out
    }

    /**
     * Rounds a closed list of polygon vertices using smooth M3-style cubic corner fillets.
     */
    private fun roundedPolygonCubics(verts: List<VertexSpec>): List<CubicSeg> {
        val n = verts.size
        if (n < 3) return emptyList()

        val startsX = FloatArray(n)
        val startsY = FloatArray(n)
        val endsX = FloatArray(n)
        val endsY = FloatArray(n)

        for (i in 0 until n) {
            val prev = verts[(i - 1 + n) % n]
            val curr = verts[i]
            val next = verts[(i + 1) % n]

            val dxIn = curr.x - prev.x
            val dyIn = curr.y - prev.y
            val lenIn = hypot(dxIn.toDouble(), dyIn.toDouble()).toFloat().coerceAtLeast(1e-4f)

            val dxOut = next.x - curr.x
            val dyOut = next.y - curr.y
            val lenOut = hypot(dxOut.toDouble(), dyOut.toDouble()).toFloat().coerceAtLeast(1e-4f)

            val rFrac = curr.rounding.coerceIn(0f, 0.49f)
            val trim = min(lenIn, lenOut) * rFrac

            startsX[i] = curr.x - (dxIn / lenIn) * trim
            startsY[i] = curr.y - (dyIn / lenIn) * trim
            endsX[i] = curr.x + (dxOut / lenOut) * trim
            endsY[i] = curr.y + (dyOut / lenOut) * trim
        }

        val out = ArrayList<CubicSeg>(n * 2)
        val handleFactor = 0.66f
        for (i in 0 until n) {
            val curr = verts[i]
            val ax = startsX[i]
            val ay = startsY[i]
            val bx = endsX[i]
            val by = endsY[i]

            val prevEndIdx = (i - 1 + n) % n
            val px = endsX[prevEndIdx]
            val py = endsY[prevEndIdx]
            if (abs(ax - px) > 0.05f || abs(ay - py) > 0.05f) {
                out.add(lineSeg(px, py, ax, ay))
            }

            if (abs(bx - ax) > 0.05f || abs(by - ay) > 0.05f) {
                val c1x = ax + (curr.x - ax) * handleFactor
                val c1y = ay + (curr.y - ay) * handleFactor
                val c2x = bx + (curr.x - bx) * handleFactor
                val c2y = by + (curr.y - by) * handleFactor
                out.add(CubicSeg(ax, ay, c1x, c1y, c2x, c2y, bx, by))
            }
        }
        return out
    }

    private fun lineSeg(x0: Float, y0: Float, x1: Float, y1: Float): CubicSeg = CubicSeg(
        x0 = x0,
        y0 = y0,
        cx1 = x0 + (x1 - x0) / 3f,
        cy1 = y0 + (y1 - y0) / 3f,
        cx2 = x0 + 2f * (x1 - x0) / 3f,
        cy2 = y0 + 2f * (y1 - y0) / 3f,
        x1 = x1,
        y1 = y1
    )

    private fun rotateCubics(
        cubics: List<CubicSeg>,
        cx: Float,
        cy: Float,
        degrees: Double
    ): List<CubicSeg> {
        val rad = Math.toRadians(degrees)
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        fun rx(x: Float, y: Float) = cx + (x - cx) * c - (y - cy) * s
        fun ry(x: Float, y: Float) = cy + (x - cx) * s + (y - cy) * c
        return cubics.map { seg ->
            CubicSeg(
                x0 = rx(seg.x0, seg.y0), y0 = ry(seg.x0, seg.y0),
                cx1 = rx(seg.cx1, seg.cy1), cy1 = ry(seg.cx1, seg.cy1),
                cx2 = rx(seg.cx2, seg.cy2), cy2 = ry(seg.cx2, seg.cy2),
                x1 = rx(seg.x1, seg.y1), y1 = ry(seg.x1, seg.y1)
            )
        }
    }

    /**
     * Post-normalizes the actual evaluated cubic Bézier contour so corner fillets, rotated
     * diagonals, and polygon apothems never leave excess empty outer margin inside the widget box.
     */
    private fun fitCubicsToBox(
        cubics: List<CubicSeg>,
        w: Float,
        h: Float,
        fillBothAxes: Boolean,
        maxAspectDistortion: Float = 1.0f
    ): List<CubicSeg> {
        if (cubics.isEmpty()) return cubics
        val raw = contourFromCubics(cubics)
        val rawW = (raw.maxX - raw.minX).coerceAtLeast(1e-3f)
        val rawH = (raw.maxY - raw.minY).coerceAtLeast(1e-3f)
        val rawCx = (raw.minX + raw.maxX) / 2f
        val rawCy = (raw.minY + raw.maxY) / 2f

        val pad = max(1f, min(w, h) * 0.003f)
        val targetW = (w - 2f * pad).coerceAtLeast(1f)
        val targetH = (h - 2f * pad).coerceAtLeast(1f)

        val sxFull = targetW / rawW
        val syFull = targetH / rawH
        val (sx, sy) = if (fillBothAxes) {
            sxFull to syFull
        } else {
            val base = min(sxFull, syFull)
            if (maxAspectDistortion > 1.001f) {
                min(sxFull, base * maxAspectDistortion) to min(syFull, base * maxAspectDistortion)
            } else {
                base to base
            }
        }

        val cx = w / 2f
        val cy = h / 2f
        fun tx(x: Float) = cx + (x - rawCx) * sx
        fun ty(y: Float) = cy + (y - rawCy) * sy

        return cubics.map { seg ->
            CubicSeg(
                x0 = tx(seg.x0), y0 = ty(seg.y0),
                cx1 = tx(seg.cx1), cy1 = ty(seg.cy1),
                cx2 = tx(seg.cx2), cy2 = ty(seg.cy2),
                x1 = tx(seg.x1), y1 = ty(seg.y1)
            )
        }
    }

    private fun contourFromCubics(cubics: List<CubicSeg>): ShapeContour {
        if (cubics.isEmpty()) {
            return ShapeContour(emptyList(), FloatArray(0), FloatArray(0), 0f, 0f, 0f, 0f)
        }
        val samplesPerSeg = max(4, (192 / cubics.size).coerceIn(4, 16))
        val totalPts = cubics.size * samplesPerSeg
        val xs = FloatArray(totalPts)
        val ys = FloatArray(totalPts)
        var idx = 0
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (seg in cubics) {
            for (s in 0 until samplesPerSeg) {
                val t = s.toFloat() / samplesPerSeg.toFloat()
                val mt = 1f - t
                val mt2 = mt * mt
                val t2 = t * t
                val x = mt2 * mt * seg.x0 + 3f * mt2 * t * seg.cx1 + 3f * mt * t2 * seg.cx2 + t2 * t * seg.x1
                val y = mt2 * mt * seg.y0 + 3f * mt2 * t * seg.cy1 + 3f * mt * t2 * seg.cy2 + t2 * t * seg.y1
                xs[idx] = x
                ys[idx] = y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                idx++
            }
        }
        return ShapeContour(cubics, xs, ys, minX, maxX, minY, maxY)
    }
}
