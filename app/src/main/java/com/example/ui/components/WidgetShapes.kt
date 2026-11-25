package com.example.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.model.WidgetShape
import com.example.model.WidgetSize
import com.example.util.M3ShapeGeometry

/**
 * Jetpack Compose [Shape] wrapper backed by [M3ShapeGeometry] so every Material Design 3
 * Expressive shape renders identically across Widget Studio previews, shape picker thumbnails,
 * and home screen RemoteViews widgets.
 */
class M3ExpressiveComposeShape(
    private val widgetShape: WidgetShape,
    private val widgetSize: WidgetSize = WidgetSize.SMALL
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val androidPath = M3ShapeGeometry.buildAndroidPath(
            shape = widgetShape,
            width = size.width,
            height = size.height,
            size = widgetSize
        )
        return Outline.Generic(androidPath.asComposePath())
    }
}
