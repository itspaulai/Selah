package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BibleCorpus
import com.example.data.SelahPreferencesRepository
import com.example.model.AppThemeMode
import com.example.model.BibleTranslation
import com.example.model.DailyVerse
import com.example.model.SelahPreferences
import com.example.model.VerseTheme
import com.example.model.WidgetColorStyle
import com.example.model.WidgetFontStyle
import com.example.model.WidgetShape
import com.example.model.WidgetSize
import com.example.model.WidgetTextScale
import com.example.widget.DailyVerseScheduler
import com.example.widget.SelahWidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter {
    ALL,
    SAVED
}

data class SelahUiState(
    val todayVerse: DailyVerse = BibleCorpus.getDailyVerse(),
    val todayDateFull: String = BibleCorpus.formattedDateFull(),
    val todayDateShort: String = BibleCorpus.formattedDateShort(),
    val preferences: SelahPreferences = SelahPreferences(),
    val allVerses: List<DailyVerse> = BibleCorpus.verses,
    val searchQuery: String = "",
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,
    val selectedThemeFilter: VerseTheme? = null,
    val selectedPreviewSize: WidgetSize = WidgetSize.MEDIUM,
    val activeReaderVerseId: String? = null
) {
    val activeReaderVerse: DailyVerse?
        get() = BibleCorpus.getById(activeReaderVerseId)

    val filteredVerses: List<DailyVerse>
        get() {
            val base = when {
                libraryFilter == LibraryFilter.SAVED ->
                    allVerses.filter { it.id in preferences.favoriteVerseIds }
                selectedThemeFilter != null ->
                    allVerses.filter { it.theme == selectedThemeFilter }
                else -> allVerses
            }
            val query = searchQuery.trim()
            if (query.isEmpty()) return base
            return base.filter { verse ->
                verse.reference.contains(query, ignoreCase = true) ||
                    verse.book.contains(query, ignoreCase = true) ||
                    verse.chapterHeading.contains(query, ignoreCase = true) ||
                    verse.theme.label.contains(query, ignoreCase = true) ||
                    verse.textFor(preferences.translation).contains(query, ignoreCase = true)
            }
        }
}

class SelahViewModel(application: Application) : AndroidViewModel(application) {

    init {
        BibleCorpus.ensureLoaded(application.applicationContext)
    }

    private val repository = SelahPreferencesRepository(application.applicationContext)

    private val searchQuery = MutableStateFlow("")
    private val libraryFilter = MutableStateFlow(LibraryFilter.ALL)
    private val selectedThemeFilter = MutableStateFlow<VerseTheme?>(null)
    private val selectedPreviewSize = MutableStateFlow(WidgetSize.MEDIUM)
    private val activeReaderVerseId = MutableStateFlow<String?>(null)

    private data class LocalScreenState(
        val searchQuery: String,
        val libraryFilter: LibraryFilter,
        val selectedThemeFilter: VerseTheme?,
        val selectedPreviewSize: WidgetSize,
        val activeReaderVerseId: String?
    )

    private val localStateFlow = combine(
        searchQuery,
        libraryFilter,
        selectedThemeFilter,
        selectedPreviewSize,
        activeReaderVerseId
    ) { query, filter, theme, previewSize, readerId ->
        LocalScreenState(query, filter, theme, previewSize, readerId)
    }

    val uiState: StateFlow<SelahUiState> = combine(
        repository.preferencesFlow,
        localStateFlow
    ) { prefs, local ->
        SelahUiState(
            todayVerse = BibleCorpus.getDailyVerse(),
            todayDateFull = BibleCorpus.formattedDateFull(),
            todayDateShort = BibleCorpus.formattedDateShort(),
            preferences = prefs,
            allVerses = BibleCorpus.verses,
            searchQuery = local.searchQuery,
            libraryFilter = local.libraryFilter,
            selectedThemeFilter = local.selectedThemeFilter,
            selectedPreviewSize = local.selectedPreviewSize,
            activeReaderVerseId = local.activeReaderVerseId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SelahUiState()
    )

    init {
        DailyVerseScheduler.scheduleDailyRefresh(application.applicationContext)
        SelahWidgetUpdater.updateAllWidgetsAsync(application.applicationContext)
    }

    fun selectTranslation(translation: BibleTranslation) {
        viewModelScope.launch {
            repository.setTranslation(translation)
            SelahWidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun toggleFavorite(verseId: String, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val added = repository.toggleFavorite(verseId)
            onResult?.invoke(added)
        }
    }

    fun setWidgetShape(shape: WidgetShape) {
        viewModelScope.launch {
            repository.setWidgetShape(shape)
            SelahWidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun setWidgetColorStyle(style: WidgetColorStyle) {
        viewModelScope.launch {
            repository.setWidgetColorStyle(style)
            SelahWidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun setWidgetFontStyle(style: WidgetFontStyle) {
        viewModelScope.launch {
            repository.setWidgetFontStyle(style)
            SelahWidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun setWidgetTextScale(scale: WidgetTextScale) {
        viewModelScope.launch {
            repository.setWidgetTextScale(scale)
            SelahWidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.setAppThemeMode(mode)
        }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.setUseDynamicColor(enabled)
        }
    }

    fun selectPreviewSize(size: WidgetSize) {
        selectedPreviewSize.value = size
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectLibraryFilter(filter: LibraryFilter) {
        libraryFilter.value = filter
        selectedThemeFilter.value = null
    }

    fun selectThemeFilter(theme: VerseTheme) {
        libraryFilter.value = LibraryFilter.ALL
        selectedThemeFilter.value = if (selectedThemeFilter.value == theme) null else theme
    }

    fun openReader(verseId: String) {
        activeReaderVerseId.value = verseId
    }

    fun closeReader() {
        activeReaderVerseId.value = null
    }
}
