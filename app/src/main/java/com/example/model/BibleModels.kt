package com.example.model

enum class BibleTranslation(
    val code: String,
    val displayName: String,
    val description: String
) {
    WEB(
        code = "WEB",
        displayName = "World English Bible",
        description = "Clear, modern public-domain English"
    ),
    KJV(
        code = "KJV",
        displayName = "King James Version",
        description = "Classic literary cadence (1611)"
    ),
    ASV(
        code = "ASV",
        displayName = "American Standard Version",
        description = "Faithful, stately early-modern prose"
    ),
    BBE(
        code = "BBE",
        displayName = "Bible in Basic English",
        description = "Simple, calm, accessible phrasing"
    );

    companion object {
        fun fromCode(code: String?): BibleTranslation =
            entries.firstOrNull { it.code == code } ?: WEB
    }
}

enum class VerseTheme(val label: String) {
    PEACE("Peace"),
    STRENGTH("Strength"),
    WISDOM("Wisdom"),
    HOPE("Hope"),
    LOVE("Love"),
    GRATITUDE("Gratitude")
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
    val chapterPassages: Map<BibleTranslation, List<ChapterVerse>> = emptyMap()
) {
    val reference: String
        get() = "$book $chapter:$verseRange"

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
    val dimensionsLabel: String
) {
    SMALL("Small", "2×2"),
    MEDIUM("Medium", "4×2"),
    LARGE("Large", "4×4")
}

enum class WidgetColorStyle(
    val id: String,
    val label: String,
    val subtitle: String
) {
    DYNAMIC_TONAL(
        id = "dynamic_tonal",
        label = "Dynamic Tonal",
        subtitle = "Soft wallpaper surface with balanced contrast"
    ),
    DYNAMIC_EXPRESSIVE(
        id = "dynamic_expressive",
        label = "Expressive Accent",
        subtitle = "Rich Material You container wash"
    ),
    HIGH_CONTRAST(
        id = "high_contrast",
        label = "Crisp Contrast",
        subtitle = "Clean high-legibility surface"
    ),
    SOFT_GLASS(
        id = "soft_glass",
        label = "Soft Translucent",
        subtitle = "Gentle frosted veil over wallpaper"
    );

    companion object {
        fun fromId(id: String?): WidgetColorStyle =
            entries.firstOrNull { it.id == id } ?: DYNAMIC_TONAL
    }
}

enum class WidgetFontStyle(
    val id: String,
    val label: String,
    val subtitle: String
) {
    PIXEL_SANS("pixel_sans", "Pixel Sans", "Google Sans Flex · matches Pixel widgets"),
    EDITORIAL_SERIF("editorial_serif", "Editorial Serif", "Newsreader classic book serif");

    companion object {
        fun fromId(id: String?): WidgetFontStyle =
            entries.firstOrNull { it.id == id } ?: PIXEL_SANS
    }
}

enum class WidgetTextScale(
    val id: String,
    val label: String,
    val multiplier: Float
) {
    COMPACT("compact", "Compact", 0.90f),
    COMFORTABLE("comfortable", "Balanced", 1.0f),
    LARGE("large", "Large", 1.12f);

    companion object {
        fun fromId(id: String?): WidgetTextScale =
            entries.firstOrNull { it.id == id } ?: COMFORTABLE
    }
}

enum class WidgetShape(
    val id: String,
    val label: String,
    val supportsCurvedReference: Boolean = false,
    val curvedArcOuterRadiusFraction: Float = 0.415f,
    val prefersWideCanvasOnMedium: Boolean = false
) {
    CIRCLE("circle", "Circle", supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.442f),
    SQUARE("square", "Square", prefersWideCanvasOnMedium = true),
    SLANTED("slanted", "Slanted", prefersWideCanvasOnMedium = true),
    ARCH("arch", "Arch", prefersWideCanvasOnMedium = true),
    SEMICIRCLE("semicircle", "Semicircle", prefersWideCanvasOnMedium = true),
    OVAL("oval", "Oval", prefersWideCanvasOnMedium = true),
    PILL("pill", "Pill", prefersWideCanvasOnMedium = true),
    TRIANGLE("triangle", "Triangle"),
    ARROW("arrow", "Arrow"),
    FAN("fan", "Fan"),
    DIAMOND("diamond", "Diamond"),
    CLAMSHELL("clamshell", "Clamshell", prefersWideCanvasOnMedium = true),
    PENTAGON("pentagon", "Pentagon"),
    GEM("gem", "Gem"),
    VERY_SUNNY("very_sunny", "Very sunny", supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.382f),
    SUNNY("sunny", "Sunny", supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.412f),
    COOKIE_4("cookie_4", "4-sided cookie"),
    COOKIE_6("cookie_6", "6-sided cookie", supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.408f),
    COOKIE_7("cookie_7", "7-sided cookie", supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.412f),
    COOKIE_9("cookie_9", "9-sided cookie", supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.416f),
    COOKIE_12(
        id = "cookie_12",
        label = "12-sided cookie",
        supportsCurvedReference = true,
        curvedArcOuterRadiusFraction = 0.422f,
        prefersWideCanvasOnMedium = true
    ),
    CLOVER_4("clover_4", "4-leaf clover"),
    CLOVER_8("clover_8", "8-leaf clover", supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.410f),
    BURST("burst", "Burst"),
    SOFT_BURST("soft_burst", "Soft burst", supportsCurvedReference = true, curvedArcOuterRadiusFraction = 0.386f),
    FLOWER("flower", "Flower"),
    PUFFY("puffy", "Puffy", prefersWideCanvasOnMedium = true),
    GHOSTISH("ghostish", "Ghost-ish", prefersWideCanvasOnMedium = true),
    BUN("bun", "Bun");

    companion object {
        fun fromId(id: String?): WidgetShape = when (id) {
            "scallop" -> COOKIE_12
            "rounded" -> SQUARE
            else -> entries.firstOrNull { it.id == id } ?: COOKIE_12
        }
    }
}

enum class AppThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromId(id: String?): AppThemeMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

data class SelahPreferences(
    val translation: BibleTranslation = BibleTranslation.WEB,
    val favoriteVerseIds: Set<String> = emptySet(),
    val widgetShape: WidgetShape = WidgetShape.COOKIE_12,
    val widgetColorStyle: WidgetColorStyle = WidgetColorStyle.DYNAMIC_TONAL,
    val widgetFontStyle: WidgetFontStyle = WidgetFontStyle.PIXEL_SANS,
    val widgetTextScale: WidgetTextScale = WidgetTextScale.COMFORTABLE,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true
)
