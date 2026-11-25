package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.example.model.WidgetSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseDailyVerseWidgetProvider(
    private val defaultSize: WidgetSize
) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SelahWidgetUpdater.updateSpecificWidgets(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    appWidgetIds = appWidgetIds,
                    defaultSize = defaultSize
                )
                DailyVerseScheduler.scheduleDailyRefresh(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SelahWidgetUpdater.updateSpecificWidgets(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    appWidgetIds = intArrayOf(appWidgetId),
                    defaultSize = defaultSize
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DailyVerseScheduler.scheduleDailyRefresh(context)
    }
}

class DailyVerseSmallWidgetProvider : BaseDailyVerseWidgetProvider(WidgetSize.SMALL)

class DailyVerseMediumWidgetProvider : BaseDailyVerseWidgetProvider(WidgetSize.MEDIUM)

class DailyVerseLargeWidgetProvider : BaseDailyVerseWidgetProvider(WidgetSize.LARGE)
