package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BibleTranslation
import com.example.model.DailyVerse
import com.example.ui.SelahViewModel
import com.example.ui.components.AboutSelahDialog
import com.example.ui.components.TranslationPickerSheet
import com.example.ui.screens.ChapterReaderScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.WidgetStudioScreen
import com.example.ui.theme.SelahTheme
import com.example.widget.SelahWidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SelahViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetDeepLink(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val appLanguage = uiState.preferences.appLanguage
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, appLanguage) {
                val config = Configuration(baseContext.resources.configuration).apply {
                    setLocale(appLanguage.locale)
                }
                baseContext.createConfigurationContext(config)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration
            ) {
                SelahTheme(
                    themeMode = uiState.preferences.appThemeMode,
                    dynamicColor = uiState.preferences.useDynamicColor,
                    language = appLanguage
                ) {
                    SelahApp(
                        viewModel = viewModel,
                        activityContext = this@MainActivity
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetDeepLink(intent)
    }

    private fun handleWidgetDeepLink(intent: Intent?) {
        val openChapter = intent?.getBooleanExtra(SelahWidgetUpdater.EXTRA_OPEN_CHAPTER, false) ?: false
        val verseId = intent?.getStringExtra(SelahWidgetUpdater.EXTRA_VERSE_ID)
        if (openChapter && !verseId.isNullOrBlank()) {
            viewModel.openReader(verseId)
        }
    }
}

private enum class SelahDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    TODAY(
        labelRes = R.string.nav_today,
        selectedIcon = Icons.Rounded.WbSunny,
        unselectedIcon = Icons.Outlined.WbSunny,
        tag = "nav_tab_today"
    ),
    WIDGET(
        labelRes = R.string.nav_widget,
        selectedIcon = Icons.Rounded.Widgets,
        unselectedIcon = Icons.Outlined.Widgets,
        tag = "nav_tab_widget"
    ),
    LIBRARY(
        labelRes = R.string.nav_library,
        selectedIcon = Icons.AutoMirrored.Rounded.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Outlined.MenuBook,
        tag = "nav_tab_library"
    )
}

@Composable
private fun SelahApp(
    viewModel: SelahViewModel,
    activityContext: Context
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by rememberSaveable { mutableStateOf(SelahDestination.TODAY) }
    var showTranslationSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val copiedMsg = stringResource(R.string.snackbar_copied)
    val savedMsg = stringResource(R.string.snackbar_saved)
    val removedMsg = stringResource(R.string.snackbar_removed)
    val pinRequestedMsg = stringResource(R.string.widget_pin_requested)
    val shareChooserTitle = stringResource(R.string.share_chooser_title)

    fun copyVerseToClipboard(verse: DailyVerse, translation: BibleTranslation) {
        val clipboard = activityContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val formatted = "\"${verse.textFor(translation)}\"\n— ${verse.referenceFor(translation)} (${translation.code})"
        clipboard?.setPrimaryClip(ClipData.newPlainText("Daily Bible Verse", formatted))
        scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
    }

    fun shareVerse(verse: DailyVerse, translation: BibleTranslation) {
        val formatted = "\"${verse.textFor(translation)}\"\n— ${verse.referenceFor(translation)} (${translation.code})"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, formatted)
        }
        activityContext.startActivity(Intent.createChooser(shareIntent, shareChooserTitle))
    }

    fun toggleFavoriteWithFeedback(verseId: String) {
        viewModel.toggleFavorite(verseId) { added ->
            scope.launch {
                snackbarHostState.showSnackbar(if (added) savedMsg else removedMsg)
            }
        }
    }

    val activeReaderVerse = uiState.activeReaderVerse
    if (activeReaderVerse != null) {
        ChapterReaderScreen(
            verse = activeReaderVerse,
            translation = uiState.preferences.translation,
            appLanguage = uiState.preferences.appLanguage,
            isFavorite = activeReaderVerse.id in uiState.preferences.favoriteVerseIds,
            onBack = { viewModel.closeReader() },
            onOpenTranslationPicker = { showTranslationSheet = true },
            onToggleFavorite = { toggleFavoriteWithFeedback(activeReaderVerse.id) },
            onCopyVerse = { copyVerseToClipboard(activeReaderVerse, uiState.preferences.translation) },
            onShareVerse = { shareVerse(activeReaderVerse, uiState.preferences.translation) }
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            SelahDestination.entries.forEach { dest ->
                                val selected = currentTab == dest
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            SelahDestination.entries.forEach { dest ->
                                val selected = currentTab == dest
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "main_tab_transition"
                        ) { destination ->
                            when (destination) {
                                SelahDestination.TODAY -> TodayScreen(
                                    uiState = uiState,
                                    onOpenTranslationPicker = { showTranslationSheet = true },
                                    onReadChapter = { viewModel.openReader(it) },
                                    onToggleFavorite = { toggleFavoriteWithFeedback(it) },
                                    onCopyVerse = {
                                        copyVerseToClipboard(
                                            uiState.todayVerse,
                                            uiState.preferences.translation
                                        )
                                    },
                                    onShareVerse = {
                                        shareVerse(
                                            uiState.todayVerse,
                                            uiState.preferences.translation
                                        )
                                    },
                                    onOpenWidgetStudio = { currentTab = SelahDestination.WIDGET },
                                    onOpenAbout = { showAboutDialog = true }
                                )

                                SelahDestination.WIDGET -> WidgetStudioScreen(
                                    uiState = uiState,
                                    onSelectPreviewSize = viewModel::selectPreviewSize,
                                    onRequestPinWidget = { size ->
                                        val ok = SelahWidgetUpdater.requestPinWidget(activityContext, size)
                                        if (ok) {
                                            scope.launch { snackbarHostState.showSnackbar(pinRequestedMsg) }
                                        }
                                        ok
                                    },
                                    onSelectLanguage = viewModel::setAppLanguage,
                                    onSelectShape = viewModel::setWidgetShape,
                                    onSelectTranslation = viewModel::selectTranslation,
                                    onSelectColorStyle = viewModel::setWidgetColorStyle,
                                    onSelectFontStyle = viewModel::setWidgetFontStyle,
                                    onSelectTextScale = viewModel::setWidgetTextScale,
                                    onSelectThemeMode = viewModel::setAppThemeMode,
                                    onToggleDynamicColor = viewModel::setUseDynamicColor,
                                    onOpenReader = { viewModel.openReader(it) },
                                    onOpenAbout = { showAboutDialog = true }
                                )

                                SelahDestination.LIBRARY -> LibraryScreen(
                                    uiState = uiState,
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onSelectFilter = viewModel::selectLibraryFilter,
                                    onSelectTheme = viewModel::selectThemeFilter,
                                    onOpenVerse = { viewModel.openReader(it) },
                                    onToggleFavorite = { toggleFavoriteWithFeedback(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTranslationSheet) {
        TranslationPickerSheet(
            selectedLanguage = uiState.preferences.appLanguage,
            selected = uiState.preferences.translation,
            onSelectLanguage = { viewModel.setAppLanguage(it) },
            onSelect = { viewModel.selectTranslation(it) },
            onDismiss = { showTranslationSheet = false }
        )
    }

    if (showAboutDialog) {
        AboutSelahDialog(onDismiss = { showAboutDialog = false })
    }
}
