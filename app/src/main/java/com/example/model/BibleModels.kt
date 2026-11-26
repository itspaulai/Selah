package com.example.model

import androidx.annotation.StringRes
import com.example.R
import java.util.Locale

enum class AppLanguage(
    val id: String,
    val nativeName: String,
    val locale: Locale
) {
    ENGLISH("en", "English", Locale.ENGLISH),
    FRENCH("fr", "Français", Locale.FRENCH),
    BULGARIAN("bg", "Български", Locale("bg", "BG"));

    val defaultTranslation: BibleTranslation
        get() = when (this) {
            ENGLISH -> BibleTranslation.WEB
            FRENCH -> BibleTranslation.LSG
            BULGARIAN -> BibleTranslation.BG40
        }

    companion object {
        fun fromId(id: String?): AppLanguage =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: fromSystemLocale()

        fun fromSystemLocale(locale: Locale = Locale.getDefault()): AppLanguage {
            return when (locale.language.lowercase(Locale.ROOT)) {
                "fr" -> FRENCH
                "bg" -> BULGARIAN
                else -> ENGLISH
            }
        }
    }
}

enum class BibleTranslation(
    val code: String,
    val displayName: String,
    val description: String,
    val language: AppLanguage
) {
    WEB(
        code = "WEB",
        displayName = "World English Bible",
        description = "Clear, modern public-domain English",
        language = AppLanguage.ENGLISH
    ),
    KJV(
        code = "KJV",
        displayName = "King James Version",
        description = "Classic literary cadence (1611)",
        language = AppLanguage.ENGLISH
    ),
    ASV(
        code = "ASV",
        displayName = "American Standard Version",
        description = "Faithful, stately early-modern prose",
        language = AppLanguage.ENGLISH
    ),
    BBE(
        code = "BBE",
        displayName = "Bible in Basic English",
        description = "Simple, calm, accessible phrasing",
        language = AppLanguage.ENGLISH
    ),
    LSG(
        code = "LSG",
        displayName = "Louis Segond (1910)",
        description = "Traduction française classique du domaine public",
        language = AppLanguage.FRENCH
    ),
    BG40(
        code = "ББ40",
        displayName = "Библия (1940)",
        description = "Класически български превод (1940 г.)",
        language = AppLanguage.BULGARIAN
    );

    companion object {
        fun fromCode(code: String?, fallbackLanguage: AppLanguage = AppLanguage.ENGLISH): BibleTranslation {
            if (code.isNullOrBlank()) return fallbackLanguage.defaultTranslation
            return entries.firstOrNull {
                it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true)
            } ?: fallbackLanguage.defaultTranslation
        }

        fun forLanguage(language: AppLanguage): List<BibleTranslation> =
            entries.filter { it.language == language }
    }
}

enum class VerseTheme(
    val label: String,
    @StringRes val labelRes: Int
) {
    PEACE("Peace", R.string.verse_theme_peace),
    STRENGTH("Strength", R.string.verse_theme_strength),
    WISDOM("Wisdom", R.string.verse_theme_wisdom),
    HOPE("Hope", R.string.verse_theme_hope),
    LOVE("Love", R.string.verse_theme_love),
    GRATITUDE("Gratitude", R.string.verse_theme_gratitude)
}

data class ChapterVerse(
    val number: Int,
    val text: String,
    val isFeatured: Boolean = false
)

data class DailyVerse(
    val id: String,
    val book: String,
    val chapter: Int,
    val verseRange: String,
    val chapterHeading: String,
    val theme: VerseTheme,
    val translations: Map<BibleTranslation, String>,
    val chapterPassages: Map<BibleTranslation, List<ChapterVerse>> = emptyMap(),
    val bookFr: String = book,
    val bookBg: String = book,
    val chapterHeadingFr: String = chapterHeading,
    val chapterHeadingBg: String = chapterHeading
) {
    val reference: String
        get() = "$book $chapter:$verseRange"

    fun bookFor(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> book
        AppLanguage.FRENCH -> bookFr
        AppLanguage.BULGARIAN -> bookBg
    }

    fun bookFor(translation: BibleTranslation): String = bookFor(translation.language)

    fun referenceFor(language: AppLanguage): String = "${bookFor(language)} $chapter:$verseRange"

    fun referenceFor(translation: BibleTranslation): String = referenceFor(translation.language)

    fun chapterHeadingFor(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> chapterHeading
        AppLanguage.FRENCH -> chapterHeadingFr
        AppLanguage.BULGARIAN -> chapterHeadingBg
    }

    fun textFor(translation: BibleTranslation): String =
        translations[translation] ?: translations[BibleTranslation.WEB].orEmpty()

    fun chapterFor(translation: BibleTranslation): List<ChapterVerse> {
        val inline = chapterPassages[translation]
        if (!inline.isNullOrEmpty()) return inline
        return com.example.data.BibleCorpus.loadChapterPassage(id, translation)
    }
}

enum class WidgetSize(
    val label: String,
    @StringRes val labelRes: Int,
    val dimensionsLabel: String
) {
    SMALL("Small", R.string.widget_size_small, "2×2"),
    MEDIUM("Medium", R.string.widget_size_medium, "4×2"),
    LARGE("Large", R.string.widget_size_large, "4×4")
}

enum class WidgetColorStyle(
    val id: String,
    val label: String,
    val subtitle: String,
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int
) {
    DYNAMIC_TONAL(
        id = "dynamic_tonal",
        label = "Dynamic Tonal",
        subtitle = "Soft wallpaper surface with balanced contrast",
        labelRes = R.string.surface_dynamic_tonal_label,
        subtitleRes = R.string.surface_dynamic_tonal_desc
    ),
    DYNAMIC_EXPRESSIVE(
        id = "dynamic_expressive",
        label = "Expressive Accent",
        subtitle = "Rich Material You container wash",
        labelRes = R.string.surface_dynamic_expressive_label,
        subtitleRes = R.string.surface_dynamic_expressive_desc
    ),
    HIGH_CONTRAST(
        id = "high_contrast",
        label = "Crisp Contrast",
        subtitle = "Clean high-legibility surface",
        labelRes = R.string.surface_high_contrast_label,
        subtitleRes = R.string.surface_high_contrast_desc
    ),
    SOFT_GLASS(
        id = "soft_glass",
        label = "Soft Translucent",
        subtitle = "Gentle frosted veil over wallpaper",
        labelRes = R.string.surface_soft_glass_label,
        subtitleRes = R.string.surface_soft_glass_desc
    );

    companion object {
        fun fromId(id: String?): WidgetColorStyle =
            entries.firstOrNull { it.id == id } ?: DYNAMIC_TONAL
    }
}

enum class WidgetFontStyle(
    val id: String,
    val label: String,
    val subtitle: String,
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int
) {
    PIXEL_SANS(
        id = "pixel_sans",
        label = "Pixel Sans",
        subtitle = "Clean modern sans · matches Pixel widgets",
        labelRes = R.string.font_pixel_sans_label,
        subtitleRes = R.string.font_pixel_sans_desc
    ),
    EDITORIAL_SERIF(
        id = "editorial_serif",
        label = "Editorial Serif",
        subtitle = "Classic book serif",
        labelRes = R.string.font_editorial_serif_label,
        subtitleRes = R.string.font_editorial_serif_desc
    );

    companion object {
        fun fromId(id: String?): WidgetFontStyle =
            entries.firstOrNull { it.id == id } ?: PIXEL_SANS
    }
}

enum class WidgetTextScale(
    val id: String,
    val label: String,
    @StringRes val labelRes: Int,
    val multiplier: Float
) {
    COMPACT("compact", "Compact", R.string.scale_compact, 0.90f),
    COMFORTABLE("comfortable", "Balanced", R.string.scale_comfortable, 1.0f),
    LARGE("large", "Large", R.string.scale_large, 1.12f);

    companion object {
        fun fromId(id: String?): WidgetTextScale =
            entries.firstOrNull { it.id == id } ?: COMFORTABLE
    }
}

enum class WidgetShape(
    val id: String,
    val label: String,
    @StringRes val labelRes: Int,
    val supportsCurvedReference: Boolean = false,
    val curvedArcOuterRadiusFraction: Float = 0.415f,
    val prefersWideCanvasOnMedium: Boolean = false
) {
    CIRCLE("circle", "Circle", R.string.shape_circle, supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.442f),
    SQUARE("square", "Square", R.string.shape_square, prefersWideCanvasOnMedium = true),
    SLANTED("slanted", "Slanted", R.string.shape_slanted, prefersWideCanvasOnMedium = true),
    ARCH("arch", "Arch", R.string.shape_arch, prefersWideCanvasOnMedium = true),
    SEMICIRCLE("semicircle", "Semicircle", R.string.shape_semicircle, prefersWideCanvasOnMedium = true),
    OVAL("oval", "Oval", R.string.shape_oval, prefersWideCanvasOnMedium = true),
    PILL("pill", "Pill", R.string.shape_pill, prefersWideCanvasOnMedium = true),
    TRIANGLE("triangle", "Triangle", R.string.shape_triangle),
    ARROW("arrow", "Arrow", R.string.shape_arrow),
    FAN("fan", "Fan", R.string.shape_fan),
    DIAMOND("diamond", "Diamond", R.string.shape_diamond),
    CLAMSHELL("clamshell", "Clamshell", R.string.shape_clamshell, prefersWideCanvasOnMedium = true),
    PENTAGON("pentagon", "Pentagon", R.string.shape_pentagon),
    GEM("gem", "Gem", R.string.shape_gem),
    VERY_SUNNY("very_sunny", "Very sunny", R.string.shape_very_sunny, supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.382f),
    SUNNY("sunny", "Sunny", R.string.shape_sunny, supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.412f),
    COOKIE_4("cookie_4", "4-sided cookie", R.string.shape_cookie_4),
    COOKIE_6("cookie_6", "6-sided cookie", R.string.shape_cookie_6, supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.408f),
    COOKIE_7("cookie_7", "7-sided cookie", R.string.shape_cookie_7, supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.412f),
    COOKIE_9("cookie_9", "9-sided cookie", R.string.shape_cookie_9, supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.416f),
    COOKIE_12(
        id = "cookie_12",
        label = "12-sided cookie",
        labelRes = R.string.shape_cookie_12,
        supportsCurvedReference = true,
        curvedArcOuterRadiusFraction = 0.422f,
        prefersWideCanvasOnMedium = true
    ),
    CLOVER_4("clover_4", "4-leaf clover", R.string.shape_clover_4),
    CLOVER_8("clover_8", "8-leaf clover", R.string.shape_clover_8, supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.410f),
    BURST("burst", "Burst", R.string.shape_burst),
    SOFT_BURST("soft_burst", "Soft burst", R.string.shape_soft_burst, supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.386f),
    FLOWER("flower", "Flower", R.string.shape_flower),
    PUFFY("puffy", "Puffy", R.string.shape_puffy, prefersWideCanvasOnMedium = true),
    GHOSTISH("ghostish", "Ghost-ish", R.string.shape_ghostish, prefersWideCanvasOnMedium = true),
    BUN("bun", "Bun", R.string.shape_bun);

    companion object {
        fun fromId(id: String?): WidgetShape = when (id) {
            "scallop" -> COOKIE_12
            "rounded" -> SQUARE
            else -> entries.firstOrNull { it.id == id } ?: COOKIE_12
        }
    }
}

enum class AppThemeMode(
    val id: String,
    val label: String,
    @StringRes val labelRes: Int
) {
    SYSTEM("system", "System", R.string.theme_system),
    LIGHT("light", "Light", R.string.theme_light),
    DARK("dark", "Dark", R.string.theme_dark);

    companion object {
        fun fromId(id: String?): AppThemeMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

data class SelahPreferences(
    val appLanguage: AppLanguage = AppLanguage.fromSystemLocale(),
    val translation: BibleTranslation = appLanguage.defaultTranslation,
    val favoriteVerseIds: Set<String> = emptySet(),
    val widgetShape: WidgetShape = WidgetShape.COOKIE_12,
    val widgetColorStyle: WidgetColorStyle = WidgetColorStyle.DYNAMIC_TONAL,
    val widgetFontStyle: WidgetFontStyle = WidgetFontStyle.PIXEL_SANS,
    val widgetTextScale: WidgetTextScale = WidgetTextScale.COMFORTABLE,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true
)
