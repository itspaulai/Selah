package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.BibleTranslation
import com.example.model.WidgetColorStyle
import com.example.model.WidgetFontStyle
import com.example.model.WidgetShape
import com.example.model.WidgetSize
import com.example.model.WidgetTextScale
import com.example.ui.SelahUiState
import com.example.ui.components.M3ExpressiveComposeShape
import com.example.ui.components.WidgetPreviewStage
import com.example.ui.theme.bodySansFontFamily
import com.example.ui.theme.editorialSerifFontFamily
import com.example.ui.theme.uiSansFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetStudioScreen(
    uiState: SelahUiState,
    onSelectPreviewSize: (WidgetSize) -> Unit,
    onRequestPinWidget: (WidgetSize) -> Boolean,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectShape: (WidgetShape) -> Unit,
    onSelectTranslation: (BibleTranslation) -> Unit,
    onSelectColorStyle: (WidgetColorStyle) -> Unit,
    onSelectFontStyle: (WidgetFontStyle) -> Unit,
    onSelectTextScale: (WidgetTextScale) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    var showManualPinDialog by remember { mutableStateOf(false) }
    var showShapeGallerySheet by remember { mutableStateOf(false) }
    val shapeCarouselState = rememberLazyListState()
    val controlsScrollState = rememberScrollState()
    val isControlsScrolled by remember {
        derivedStateOf { controlsScrollState.value > 4 }
    }
    val dividerAlpha by animateFloatAsState(
        targetValue = if (isControlsScrolled) 0.55f else 0f,
        label = "studio_sticky_divider_alpha"
    )

    LaunchedEffect(prefs.widgetShape) {
        val index = WidgetShape.entries.indexOf(prefs.widgetShape)
        if (index >= 0) {
            shapeCarouselState.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
    }

    val controlsContent: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { horizontalPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(controlsScrollState)
                .padding(horizontal = horizontalPadding)
                .padding(top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Size Selector: Small | Medium | Large
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("widget_size_selector")
            ) {
                WidgetSize.entries.forEachIndexed { index, size ->
                    SegmentedButton(
                        selected = uiState.selectedPreviewSize == size,
                        onClick = { onSelectPreviewSize(size) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = WidgetSize.entries.size
                        ),
                        modifier = Modifier.testTag("widget_size_option_${size.name.lowercase()}")
                    ) {
                        Text(
                            text = "${stringResource(size.labelRes)} ${size.dimensionsLabel}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Primary 1-Tap Pin Widget Button
            Button(
                onClick = {
                    val pinned = onRequestPinWidget(uiState.selectedPreviewSize)
                    if (!pinned) {
                        showManualPinDialog = true
                    }
                },
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_widget_home_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddToHomeScreen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        R.string.widget_add_button,
                        stringResource(uiState.selectedPreviewSize.labelRes)
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Section 1: Material 3 Expressive Shapes (29 shapes)
            StudioSectionCard(
                title = stringResource(R.string.widget_section_shape),
                subtitle = stringResource(prefs.widgetShape.labelRes),
                headerAction = {
                    FilledTonalButton(
                        onClick = { showShapeGallerySheet = true },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("studio_shape_browse_all")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                R.string.widget_shape_all_button,
                                WidgetShape.entries.size
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            ) {
                LazyRow(
                    state = shapeCarouselState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_shape_carousel"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(
                        items = WidgetShape.entries,
                        key = { it.id }
                    ) { shapeOption ->
                        ShapePickerTile(
                            shapeOption = shapeOption,
                            selected = prefs.widgetShape == shapeOption,
                            onClick = { onSelectShape(shapeOption) },
                            modifier = Modifier.width(86.dp)
                        )
                    }
                }
            }

            // Section 2: App & Verse Language
            StudioSectionCard(
                title = stringResource(R.string.widget_section_language),
                subtitle = stringResource(R.string.widget_section_language_subtitle)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_language_selector")
                ) {
                    AppLanguage.entries.forEachIndexed { index, language ->
                        SegmentedButton(
                            selected = prefs.appLanguage == language,
                            onClick = { onSelectLanguage(language) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AppLanguage.entries.size
                            ),
                            modifier = Modifier.testTag("studio_language_${language.id}")
                        ) {
                            Text(
                                text = language.nativeName,
                                fontFamily = bodySansFontFamily(language),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Section 3: Bible Translation
            StudioSectionCard(title = stringResource(R.string.widget_section_translation)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BibleTranslation.entries.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { translation ->
                                val selected = prefs.translation == translation
                                val itemFont = bodySansFontFamily(translation.language)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { onSelectTranslation(translation) }
                                        .testTag("studio_translation_${translation.name.lowercase()}"),
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = translation.code,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = itemFont
                                                ),
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = stringResource(R.string.cd_selected),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = translation.language.id.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        Text(
                                            text = translation.displayName,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = itemFont
                                            ),
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Widget Surface Style
            StudioSectionCard(title = stringResource(R.string.widget_section_surface)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetColorStyle.entries.forEach { style ->
                        val selected = prefs.widgetColorStyle == style
                        val swatchColor = when (style) {
                            WidgetColorStyle.DYNAMIC_TONAL -> MaterialTheme.colorScheme.surfaceContainerHigh
                            WidgetColorStyle.DYNAMIC_EXPRESSIVE -> MaterialTheme.colorScheme.primaryContainer
                            WidgetColorStyle.HIGH_CONTRAST -> MaterialTheme.colorScheme.surfaceContainerLowest
                            WidgetColorStyle.SOFT_GLASS -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onSelectColorStyle(style) }
                                .testTag("studio_surface_${style.id}"),
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(style.labelRes),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(style.subtitleRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.cd_selected),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Typography & Scale
            StudioSectionCard(title = stringResource(R.string.widget_section_typography)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WidgetFontStyle.entries.forEach { fontStyle ->
                            val selected = prefs.widgetFontStyle == fontStyle
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onSelectFontStyle(fontStyle) }
                                    .testTag("studio_font_${fontStyle.id}"),
                                shape = RoundedCornerShape(20.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLowest
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (prefs.translation.language == AppLanguage.BULGARIAN) "Аа" else "Aa",
                                        fontFamily = if (fontStyle == WidgetFontStyle.EDITORIAL_SERIF) {
                                            editorialSerifFontFamily(prefs.translation.language)
                                        } else {
                                            uiSansFontFamily(prefs.translation.language)
                                        },
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = stringResource(fontStyle.labelRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = stringResource(fontStyle.subtitleRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_text_scale_selector")
                    ) {
                        WidgetTextScale.entries.forEachIndexed { index, scale ->
                            SegmentedButton(
                                selected = prefs.widgetTextScale == scale,
                                onClick = { onSelectTextScale(scale) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = WidgetTextScale.entries.size
                                )
                            ) {
                                Text(
                                    text = stringResource(scale.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Section 6: App Theme
            StudioSectionCard(title = stringResource(R.string.widget_section_theme)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_theme_mode_selector")
                    ) {
                        AppThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = prefs.appThemeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = AppThemeMode.entries.size
                                )
                            ) {
                                Text(
                                    text = stringResource(mode.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ToggleRow(
                            title = stringResource(R.string.toggle_dynamic_color),
                            subtitle = stringResource(R.string.toggle_dynamic_color_desc),
                            checked = prefs.useDynamicColor,
                            onCheckedChange = onToggleDynamicColor,
                            testTag = "toggle_dynamic_color"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val useTwoPane = maxWidth >= 720.dp
        val adaptiveMaxCardHeight = (maxHeight * 0.34f).coerceIn(136.dp, 240.dp)

        if (useTwoPane) {
            Row(
                modifier = Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                        .padding(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    WidgetStudioHeader(onOpenAbout = onOpenAbout)
                    WidgetPreviewStage(
                        verse = uiState.todayVerse,
                        preferences = prefs,
                        previewSize = uiState.selectedPreviewSize,
                        onWidgetClick = { onOpenReader(uiState.todayVerse.id) }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                ) {
                    controlsContent(0.dp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
            ) {
                // Pinned Header + Live Interactive Preview Stage
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 20.dp)
                        .padding(top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WidgetStudioHeader(onOpenAbout = onOpenAbout)

                    WidgetPreviewStage(
                        verse = uiState.todayVerse,
                        preferences = prefs,
                        previewSize = uiState.selectedPreviewSize,
                        maxCardHeight = adaptiveMaxCardHeight,
                        onWidgetClick = { onOpenReader(uiState.todayVerse.id) }
                    )
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = dividerAlpha)
                )

                // Independently Scrollable Customization Controls
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    controlsContent(20.dp)
                }
            }
        }
    }

    if (showManualPinDialog) {
        AlertDialog(
            onDismissRequest = { showManualPinDialog = false },
            title = { Text(stringResource(R.string.widget_pin_manual_title)) },
            text = { Text(stringResource(R.string.widget_pin_manual_body)) },
            confirmButton = {
                TextButton(onClick = { showManualPinDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showShapeGallerySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showShapeGallerySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.widget_shape_gallery_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.widget_shape_gallery_subtitle,
                            WidgetShape.entries.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 82.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .testTag("studio_shape_gallery_grid"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(
                        items = WidgetShape.entries,
                        key = { it.id }
                    ) { shapeOption ->
                        ShapePickerTile(
                            shapeOption = shapeOption,
                            selected = prefs.widgetShape == shapeOption,
                            onClick = {
                                onSelectShape(shapeOption)
                                showShapeGallerySheet = false
                            },
                            testTagPrefix = "gallery_shape"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetStudioHeader(
    onOpenAbout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.widget_studio_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onOpenAbout,
            modifier = Modifier.testTag("widget_about_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.cd_about_selah),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShapePickerTile(
    shapeOption: WidgetShape,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "studio_shape"
) {
    val thumbShape = remember(shapeOption) {
        M3ExpressiveComposeShape(shapeOption, WidgetSize.SMALL)
    }
    Surface(
        modifier = modifier
            .heightIn(min = 100.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("${testTagPrefix}_${shapeOption.id}"),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(thumbShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        )
                )
                val shapeLabel = stringResource(shapeOption.labelRes)
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "${stringResource(R.string.cd_selected)} $shapeLabel",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = stringResource(shapeOption.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun StudioSectionCard(
    title: String,
    subtitle: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (headerAction != null) {
                    headerAction()
                }
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
