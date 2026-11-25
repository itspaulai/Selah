package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import com.example.MainActivity
import com.example.R
import com.example.data.BibleCorpus
import com.example.data.SelahPreferencesRepository
import com.example.model.DailyVerse
import com.example.model.SelahPreferences
import com.example.model.WidgetColorStyle
import com.example.model.WidgetSize
import com.example.util.WidgetTextRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SelahWidgetUpdater {

    const val EXTRA_VERSE_ID = "com.example.EXTRA_VERSE_ID"
    const val EXTRA_OPEN_CHAPTER = "com.example.EXTRA_OPEN_CHAPTER"

    fun updateAllWidgetsAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            updateAllWidgets(appContext)
        }
    }

    suspend fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        BibleCorpus.ensureLoaded(appContext)
        val manager = AppWidgetManager.getInstance(appContext)
        val repo = SelahPreferencesRepository(appContext)
        val prefs = repo.getSnapshot()
        val dailyVerse = BibleCorpus.getDailyVerse()

        updateProvider(appContext, manager, DailyVerseSmallWidgetProvider::class.java, WidgetSize.SMALL, dailyVerse, prefs)
        updateProvider(appContext, manager, DailyVerseMediumWidgetProvider::class.java, WidgetSize.MEDIUM, dailyVerse, prefs)
        updateProvider(appContext, manager, DailyVerseLargeWidgetProvider::class.java, WidgetSize.LARGE, dailyVerse, prefs)
    }

    suspend fun updateSpecificWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        defaultSize: WidgetSize
    ) {
        val appContext = context.applicationContext
        BibleCorpus.ensureLoaded(appContext)
        val repo = SelahPreferencesRepository(appContext)
        val prefs = repo.getSnapshot()
        val dailyVerse = BibleCorpus.getDailyVerse()

        for (widgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val views = buildResponsiveRemoteViews(
                context = appContext,
                verse = dailyVerse,
                prefs = prefs,
                options = options,
                fallbackSize = defaultSize
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun updateProvider(
        context: Context,
        manager: AppWidgetManager,
        providerClass: Class<*>,
        defaultSize: WidgetSize,
        verse: DailyVerse,
        prefs: SelahPreferences
    ) {
        val component = ComponentName(context, providerClass)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            val views = buildResponsiveRemoteViews(context, verse, prefs, options, defaultSize)
            manager.updateAppWidget(id, views)
        }
    }

    fun requestPinWidget(context: Context, size: WidgetSize): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false

        val providerClass = when (size) {
            WidgetSize.SMALL -> DailyVerseSmallWidgetProvider::class.java
            WidgetSize.MEDIUM -> DailyVerseMediumWidgetProvider::class.java
            WidgetSize.LARGE -> DailyVerseLargeWidgetProvider::class.java
        }
        val component = ComponentName(context, providerClass)
        return manager.requestPinAppWidget(component, null, null)
    }

    private fun buildResponsiveRemoteViews(
        context: Context,
        verse: DailyVerse,
        prefs: SelahPreferences,
        options: Bundle?,
        fallbackSize: WidgetSize
    ): RemoteViews {
        val optWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val optHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizeMap = LinkedHashMap<SizeF, RemoteViews>()

            val reportedSizes = options?.let {
                BundleCompat.getParcelableArrayList(
                    it,
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    SizeF::class.java
                )
            }?.filter { it.width >= 80f && it.height >= 80f }.orEmpty()

            if (reportedSizes.isNotEmpty()) {
                for (reported in reportedSizes.take(3)) {
                    val category = classifySize(reported.width, reported.height, fallbackSize)
                    sizeMap[reported] = buildSingleSizeViews(
                        context = context,
                        verse = verse,
                        prefs = prefs,
                        size = category,
                        widthDp = reported.width,
                        heightDp = reported.height
                    )
                }
                if (sizeMap.keys.none { classifySize(it.width, it.height, fallbackSize) == WidgetSize.MEDIUM }) {
                    val medSize = SizeF(260f, 140f)
                    sizeMap[medSize] = buildSingleSizeViews(
                        context = context,
                        verse = verse,
                        prefs = prefs,
                        size = WidgetSize.MEDIUM,
                        widthDp = medSize.width,
                        heightDp = medSize.height
                    )
                }
            } else if (optWidthDp >= 80 && optHeightDp >= 80) {
                val exactSize = SizeF(optWidthDp.toFloat(), optHeightDp.toFloat())
                val category = classifySize(exactSize.width, exactSize.height, fallbackSize)
                sizeMap[exactSize] = buildSingleSizeViews(
                    context = context,
                    verse = verse,
                    prefs = prefs,
                    size = category,
                    widthDp = exactSize.width,
                    heightDp = exactSize.height
                )
                if (category != WidgetSize.MEDIUM) {
                    val medSize = SizeF(260f, 140f)
                    sizeMap[medSize] = buildSingleSizeViews(
                        context = context,
                        verse = verse,
                        prefs = prefs,
                        size = WidgetSize.MEDIUM,
                        widthDp = medSize.width,
                        heightDp = medSize.height
                    )
                }
            } else {
                val defaults = listOf(
                    SizeF(140f, 140f) to WidgetSize.SMALL,
                    SizeF(270f, 140f) to WidgetSize.MEDIUM,
                    SizeF(250f, 250f) to WidgetSize.LARGE
                )
                for ((sizeF, category) in defaults) {
                    sizeMap[sizeF] = buildSingleSizeViews(
                        context = context,
                        verse = verse,
                        prefs = prefs,
                        size = category,
                        widthDp = sizeF.width,
                        heightDp = sizeF.height
                    )
                }
            }

            RemoteViews(sizeMap)
        } else {
            val resolvedSize = resolveLegacySize(options, fallbackSize)
            val w = if (optWidthDp > 0) optWidthDp.toFloat() else defaultWidthDp(resolvedSize)
            val h = if (optHeightDp > 0) optHeightDp.toFloat() else defaultHeightDp(resolvedSize)
            buildSingleSizeViews(context, verse, prefs, resolvedSize, w, h)
        }
    }

    private fun classifySize(widthDp: Float, heightDp: Float, fallback: WidgetSize): WidgetSize {
        if (widthDp <= 0f || heightDp <= 0f) return fallback
        return when {
            widthDp < 205f && heightDp < 205f -> WidgetSize.SMALL
            widthDp >= 205f && heightDp < 195f -> WidgetSize.MEDIUM
            widthDp >= 205f && heightDp >= 195f -> WidgetSize.LARGE
            else -> fallback
        }
    }

    private fun defaultWidthDp(size: WidgetSize): Float = when (size) {
        WidgetSize.SMALL -> 148f
        WidgetSize.MEDIUM -> 285f
        WidgetSize.LARGE -> 265f
    }

    private fun defaultHeightDp(size: WidgetSize): Float = when (size) {
        WidgetSize.SMALL -> 148f
        WidgetSize.MEDIUM -> 148f
        WidgetSize.LARGE -> 265f
    }

    private fun resolveLegacySize(options: Bundle?, fallback: WidgetSize): WidgetSize {
        if (options == null) return fallback
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        if (minWidth == 0 && minHeight == 0) return fallback
        return classifySize(minWidth.toFloat(), minHeight.toFloat(), fallback)
    }

    private fun buildSingleSizeViews(
        context: Context,
        verse: DailyVerse,
        prefs: SelahPreferences,
        size: WidgetSize,
        widthDp: Float,
        heightDp: Float
    ): RemoteViews {
        val layoutRes = when (size) {
            WidgetSize.SMALL -> R.layout.widget_daily_verse_small
            WidgetSize.MEDIUM -> R.layout.widget_daily_verse_medium
            WidgetSize.LARGE -> R.layout.widget_daily_verse_large
        }
        val views = RemoteViews(context.packageName, layoutRes)

        val verseText = verse.textFor(prefs.translation)
        val palette = resolvePalette(context, prefs.widgetColorStyle)

        // Clear root background so the borderless Material 3 Expressive shape renders directly
        // over the user's wallpaper with zero rectangular backing or border lines.
        views.setInt(R.id.widget_root, "setBackgroundColor", Color.TRANSPARENT)

        // Render both the exact Material 3 Expressive shape fill and the bundled Google Sans Flex /
        // Newsreader typography into a single transparent PNG so Home Screen and Widget Studio
        // match 100% pixel-for-pixel across all 29 shapes and every Android launcher.
        val density = context.resources.displayMetrics.density
        val verseTypeface = WidgetTextRenderer.loadVerseTypeface(context, prefs.widgetFontStyle)
        val refTypeface = WidgetTextRenderer.loadReferenceTypeface(context)
        val pngBytes = WidgetTextRenderer.renderWidgetCompositePng(
            verseText = verseText,
            referenceText = verse.reference,
            widthDp = widthDp,
            heightDp = heightDp,
            deviceDensity = density,
            shape = prefs.widgetShape,
            size = size,
            textScale = prefs.widgetTextScale,
            containerColor = palette.containerColor,
            primaryTextColor = palette.primaryTextColor,
            secondaryTextColor = palette.secondaryTextColor,
            verseTypeface = verseTypeface,
            referenceTypeface = refTypeface
        )

        views.setViewVisibility(R.id.widget_content_container, View.GONE)
        views.setViewVisibility(R.id.widget_text_overlay, View.VISIBLE)
        views.setImageViewIcon(
            R.id.widget_text_overlay,
            Icon.createWithData(pngBytes, 0, pngBytes.size)
        )
        views.setContentDescription(R.id.widget_text_overlay, "$verseText — ${verse.reference}")

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_VERSE_ID, verse.id)
            putExtra(EXTRA_OPEN_CHAPTER, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            size.ordinal + 100,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        return views
    }

    private data class ResolvedWidgetPalette(
        val containerColor: Int,
        val primaryTextColor: Int,
        val secondaryTextColor: Int
    )

    private fun resolvePalette(context: Context, style: WidgetColorStyle): ResolvedWidgetPalette {
        return when (style) {
            WidgetColorStyle.DYNAMIC_TONAL -> ResolvedWidgetPalette(
                containerColor = ContextCompat.getColor(context, R.color.widget_bg_tonal),
                primaryTextColor = ContextCompat.getColor(context, R.color.widget_text_primary),
                secondaryTextColor = ContextCompat.getColor(context, R.color.widget_text_secondary)
            )
            WidgetColorStyle.DYNAMIC_EXPRESSIVE -> ResolvedWidgetPalette(
                containerColor = ContextCompat.getColor(context, R.color.widget_bg_expressive),
                primaryTextColor = ContextCompat.getColor(context, R.color.widget_expressive_text),
                secondaryTextColor = ContextCompat.getColor(context, R.color.widget_expressive_secondary)
            )
            WidgetColorStyle.HIGH_CONTRAST -> ResolvedWidgetPalette(
                containerColor = ContextCompat.getColor(context, R.color.widget_bg_contrast),
                primaryTextColor = ContextCompat.getColor(context, R.color.widget_contrast_text),
                secondaryTextColor = ContextCompat.getColor(context, R.color.widget_contrast_secondary)
            )
            WidgetColorStyle.SOFT_GLASS -> ResolvedWidgetPalette(
                containerColor = ContextCompat.getColor(context, R.color.widget_bg_translucent),
                primaryTextColor = ContextCompat.getColor(context, R.color.widget_text_primary),
                secondaryTextColor = ContextCompat.getColor(context, R.color.widget_text_secondary)
            )
        }
    }
}
