package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeMode
import com.example.model.BibleTranslation
import com.example.model.SelahPreferences
import com.example.model.WidgetColorStyle
import com.example.model.WidgetFontStyle
import com.example.model.WidgetShape
import com.example.model.WidgetTextScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.selahDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selah_preferences"
)

class SelahPreferencesRepository(private val context: Context) {

    private object Keys {
        val TRANSLATION = stringPreferencesKey("translation")
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_verse_ids")
        val WIDGET_SHAPE = stringPreferencesKey("widget_shape")
        val WIDGET_COLOR_STYLE = stringPreferencesKey("widget_color_style")
        val WIDGET_FONT_STYLE = stringPreferencesKey("widget_font_style_v2")
        val WIDGET_TEXT_SCALE = stringPreferencesKey("widget_text_scale")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }

    val preferencesFlow: Flow<SelahPreferences> = context.selahDataStore.data.map { prefs ->
        SelahPreferences(
            translation = BibleTranslation.fromCode(prefs[Keys.TRANSLATION]),
            favoriteVerseIds = prefs[Keys.FAVORITE_IDS] ?: emptySet(),
            widgetShape = WidgetShape.fromId(prefs[Keys.WIDGET_SHAPE]),
            widgetColorStyle = WidgetColorStyle.fromId(prefs[Keys.WIDGET_COLOR_STYLE]),
            widgetFontStyle = WidgetFontStyle.fromId(prefs[Keys.WIDGET_FONT_STYLE]),
            widgetTextScale = WidgetTextScale.fromId(prefs[Keys.WIDGET_TEXT_SCALE]),
            appThemeMode = AppThemeMode.fromId(prefs[Keys.APP_THEME_MODE]),
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true
        )
    }

    suspend fun getSnapshot(): SelahPreferences = preferencesFlow.first()

    suspend fun setTranslation(translation: BibleTranslation) {
        context.selahDataStore.edit { it[Keys.TRANSLATION] = translation.code }
    }

    suspend fun toggleFavorite(verseId: String): Boolean {
        var added = false
        context.selahDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_IDS]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(verseId)) {
                current.remove(verseId)
                added = false
            } else {
                current.add(verseId)
                added = true
            }
            prefs[Keys.FAVORITE_IDS] = current
        }
        return added
    }

    suspend fun setWidgetShape(shape: WidgetShape) {
        context.selahDataStore.edit { it[Keys.WIDGET_SHAPE] = shape.id }
    }

    suspend fun setWidgetColorStyle(style: WidgetColorStyle) {
        context.selahDataStore.edit { it[Keys.WIDGET_COLOR_STYLE] = style.id }
    }

    suspend fun setWidgetFontStyle(style: WidgetFontStyle) {
        context.selahDataStore.edit { it[Keys.WIDGET_FONT_STYLE] = style.id }
    }

    suspend fun setWidgetTextScale(scale: WidgetTextScale) {
        context.selahDataStore.edit { it[Keys.WIDGET_TEXT_SCALE] = scale.id }
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        context.selahDataStore.edit { it[Keys.APP_THEME_MODE] = mode.id }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.selahDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }
}
