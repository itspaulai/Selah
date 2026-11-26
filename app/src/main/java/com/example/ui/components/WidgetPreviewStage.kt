package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.DailyVerse
import com.example.model.SelahPreferences
import com.example.model.WidgetColorStyle
import com.example.model.WidgetShape
import com.example.model.WidgetSize
import com.example.util.WidgetTextRenderer

@Composable
fun WidgetPreviewStage(
    verse: DailyVerse,
    preferences: SelahPreferences,
    previewSize: WidgetSize,
    onWidgetClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxCardHeight: androidx.compose.ui.unit.Dp? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("widget_preview_stage")
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_wallpaper_preview_1795563952049),
            contentDescription = "Simulated home screen wallpaper preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.14f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.24f)
                ) {
                    Text(
                        text = "${preferences.widgetShape.label} · ${previewSize.label} ${previewSize.dimensionsLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { idx ->
                        Box(
                            modifier = Modifier
                                .size(if (idx == 0) 14.dp else 6.dp, 6.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.White.copy(alpha = if (idx == 0) 0.9f else 0.45f)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = previewSize to preferences.widgetShape,
                transitionSpec = {
                    (fadeIn(spring()) + scaleIn(initialScale = 0.94f, animationSpec = spring()))
                        .togetherWith(fadeOut())
                },
                label = "widget_size_shape_preview_transition"
            ) { (targetSize, targetShape) ->
                val baseBoxHeight = when (targetSize) {
                    WidgetSize.SMALL -> 192.dp
                    WidgetSize.MEDIUM -> 164.dp
                    WidgetSize.LARGE -> 238.dp
                }
                val resolvedBoxHeight = if (maxCardHeight != null) {
                    minOf(baseBoxHeight, maxCardHeight)
                } else {
                    baseBoxHeight
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(resolvedBoxHeight),
                    contentAlignment = Alignment.Center
                ) {
                    LiveWidgetCard(
                        verse = verse,
                        preferences = preferences,
                        size = targetSize,
                        widgetShape = targetShape,
                        maxHeight = resolvedBoxHeight - 6.dp,
                        onClick = onWidgetClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.34f))
                    )
                }
            }
        }
    }
}

private data class PreviewColors(
    val container: Color,
    val primaryText: Color,
    val secondaryText: Color
)

@Composable
private fun resolvePreviewColors(style: WidgetColorStyle): PreviewColors {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isLight = scheme.background.luminance() > 0.5f

    // Match the exact darker Material 3 Expressive wallpaper color used by the home screen widget
    // (system_accent1_700 in light mode, system_accent1_200 in dark mode on Android 12+).
    val expressiveReferenceColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val sysColorRes = if (isLight) {
            android.R.color.system_accent1_700
        } else {
            android.R.color.system_accent1_200
        }
        Color(ContextCompat.getColor(context, sysColorRes))
    } else {
        scheme.primary
    }

    return when (style) {
        WidgetColorStyle.DYNAMIC_TONAL -> PreviewColors(
            container = scheme.surfaceContainerLow,
            primaryText = scheme.onSurface,
            secondaryText = expressiveReferenceColor
        )
        WidgetColorStyle.DYNAMIC_EXPRESSIVE -> PreviewColors(
            container = scheme.primaryContainer,
            primaryText = scheme.onPrimaryContainer,
            secondaryText = expressiveReferenceColor
        )
        WidgetColorStyle.HIGH_CONTRAST -> PreviewColors(
            container = scheme.surfaceContainerLowest,
            primaryText = scheme.onSurface,
            secondaryText = expressiveReferenceColor
        )
        WidgetColorStyle.SOFT_GLASS -> PreviewColors(
            container = scheme.surface.copy(alpha = 0.84f),
            primaryText = scheme.onSurface,
            secondaryText = expressiveReferenceColor
        )
    }
}

@Composable
private fun LiveWidgetCard(
    verse: DailyVerse,
    preferences: SelahPreferences,
    size: WidgetSize,
    widgetShape: WidgetShape,
    maxHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val colors = resolvePreviewColors(preferences.widgetColorStyle)
    val verseText = verse.textFor(preferences.translation)
    val resolvedShape = remember(widgetShape, size) {
        M3ExpressiveComposeShape(widgetShape, size)
    }

    val cardModifier = when (size) {
        WidgetSize.SMALL -> Modifier.size(minOf(186.dp, maxHeight))
        WidgetSize.MEDIUM -> Modifier
            .fillMaxWidth()
            .height(minOf(158.dp, maxHeight))
        WidgetSize.LARGE -> Modifier.size(minOf(232.dp, maxHeight))
    }

    Surface(
        modifier = cardModifier
            .clip(resolvedShape)
            .clickable(onClick = onClick)
            .testTag("live_widget_card_${widgetShape.id}_${size.name.lowercase()}"),
        shape = resolvedShape,
        color = colors.container,
        border = null,
        shadowElevation = 0.dp
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current

        val verseTypeface = remember(context, preferences.widgetFontStyle) {
            WidgetTextRenderer.loadVerseTypeface(context, preferences.widgetFontStyle)
        }
        val refTypeface = remember(context) {
            WidgetTextRenderer.loadReferenceTypeface(context)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            val fitted = remember(
                verseText,
                verse.reference,
                widthPx,
                heightPx,
                widgetShape,
                size,
                preferences.widgetFontStyle,
                preferences.widgetTextScale
            ) {
                WidgetTextRenderer.fitForWidget(
                    verseText = verseText,
                    referenceText = verse.reference,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    density = density.density,
                    shape = widgetShape,
                    size = size,
                    textScale = preferences.widgetTextScale,
                    verseTypeface = verseTypeface
                )
            }

            val primaryColorArgb = colors.primaryText.toArgb()
            val secondaryColorArgb = colors.secondaryText.toArgb()

            // Render via the exact same Canvas typography renderer as the home screen widget
            // so Widget Studio and Home Screen match 100% identically in font, weight, and spacing.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "$verseText — ${verse.reference}" }
            ) {
                drawIntoCanvas { canvas ->
                    WidgetTextRenderer.drawWidgetText(
                        canvas = canvas.nativeCanvas,
                        widthPx = this.size.width,
                        heightPx = this.size.height,
                        density = density.density,
                        fitted = fitted,
                        referenceText = verse.reference,
                        shape = widgetShape,
                        size = size,
                        primaryTextColor = primaryColorArgb,
                        secondaryTextColor = secondaryColorArgb,
                        verseTypeface = verseTypeface,
                        referenceTypeface = refTypeface
                    )
                }
            }
        }
    }
}
