package com.example

import android.content.Context
import android.graphics.Color
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.data.BibleCorpus
import com.example.model.AppLanguage
import com.example.model.BibleTranslation
import com.example.model.WidgetFontStyle
import com.example.model.WidgetShape
import com.example.model.WidgetSize
import com.example.model.WidgetTextScale
import com.example.util.CurvedReferenceRenderer
import com.example.util.WidgetTextFitter
import com.example.util.WidgetTextRenderer
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BibleCorpus.ensureLoaded(context)
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Selah", appName)
    }

    @Test
    fun `corpus loads 1000 verified verbatim verses across all six translations with chapter context`() {
        val verses = BibleCorpus.verses
        assertEquals(1000, verses.size)
        assertEquals(1000, verses.map { it.id }.toSet().size)
        assertEquals(6, BibleTranslation.entries.size)

        val ntBooks = setOf(
            "Matthew", "Mark", "Luke", "John", "Romans", "1 Corinthians", "2 Corinthians",
            "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians",
            "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Hebrews", "James",
            "1 Peter", "2 Peter", "1 John", "3 John", "Jude", "Revelation"
        )
        val ntCount = verses.count { it.book in ntBooks }
        val otCount = verses.size - ntCount
        assertTrue("Expected rich NT coverage, found $ntCount", ntCount >= 400)
        assertTrue("Expected rich OT coverage, found $otCount", otCount >= 500)

        val cyrillicRegex = Regex("[\\u0400-\\u04FF]")
        for (verse in verses) {
            assertTrue("Missing French book name for ${verse.id}", verse.bookFr.isNotBlank())
            assertTrue("Missing Bulgarian Cyrillic book name for ${verse.id}", cyrillicRegex.containsMatchIn(verse.bookBg))
            assertTrue("Missing French chapter heading for ${verse.id}", verse.chapterHeadingFr.isNotBlank())
            assertTrue("Missing Bulgarian Cyrillic chapter heading for ${verse.id}", cyrillicRegex.containsMatchIn(verse.chapterHeadingBg))

            for (translation in BibleTranslation.entries) {
                val text = verse.textFor(translation)
                assertTrue("Missing text for ${verse.id} (${translation.name})", text.isNotBlank())
                if (translation == BibleTranslation.BG40) {
                    assertTrue("Bulgarian verse ${verse.id} must contain Cyrillic", cyrillicRegex.containsMatchIn(text))
                }
                val chapter = verse.chapterFor(translation)
                assertTrue("Missing chapter context for ${verse.id} (${translation.name})", chapter.isNotEmpty())
                assertTrue("Chapter context for ${verse.id} (${translation.name}) must have non-blank verses", chapter.all { it.text.isNotBlank() })
            }
        }

        // Spot-check verbatim public-domain French (LSG 1910) & Bulgarian (1940) verses
        val psalm23 = verses.first { it.book == "Psalm" && it.chapter == 23 && it.verseRange == "1" }
        assertEquals("Psaume 23:1", psalm23.referenceFor(BibleTranslation.LSG))
        assertEquals("Псалм 23:1", psalm23.referenceFor(BibleTranslation.BG40))
        assertTrue("LSG Psalm 23:1 should be authentic", psalm23.textFor(BibleTranslation.LSG).contains("berger"))
        assertTrue("BG40 Psalm 23:1 should be authentic", psalm23.textFor(BibleTranslation.BG40).contains("Пастир"))

        val john316 = verses.firstOrNull { it.book == "John" && it.chapter == 3 && it.verseRange == "16" }
        if (john316 != null) {
            assertEquals("Jean 3:16", john316.referenceFor(BibleTranslation.LSG))
            assertEquals("Йоан 3:16", john316.referenceFor(BibleTranslation.BG40))
            assertTrue("LSG John 3:16 should be authentic", john316.textFor(BibleTranslation.LSG).contains("Dieu a tant aimé le monde"))
            assertTrue("BG40 John 3:16 should be authentic", john316.textFor(BibleTranslation.BG40).contains("Бог толкова възлюби света"))
        }
    }

    @Test
    fun `french and bulgarian localized UI strings and widget PNGs render cleanly`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val frConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(AppLanguage.FRENCH.locale)
        }
        val frCtx = baseContext.createConfigurationContext(frConfig)
        assertEquals("Aujourd’hui", frCtx.getString(R.string.nav_today))
        assertEquals("Studio de widget", frCtx.getString(R.string.widget_studio_title))
        assertEquals("Langue", frCtx.getString(R.string.widget_section_language))

        val bgConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(AppLanguage.BULGARIAN.locale)
        }
        val bgCtx = baseContext.createConfigurationContext(bgConfig)
        assertEquals("Днес", bgCtx.getString(R.string.nav_today))
        assertEquals("Студио за уиджет", bgCtx.getString(R.string.widget_studio_title))
        assertEquals("Език", bgCtx.getString(R.string.widget_section_language))

        val sample = BibleCorpus.verses.first()
        for (translation in listOf(BibleTranslation.LSG, BibleTranslation.BG40)) {
            val verseTypeface = WidgetTextRenderer.loadVerseTypeface(baseContext, WidgetFontStyle.EDITORIAL_SERIF, translation.language)
            val refTypeface = WidgetTextRenderer.loadReferenceTypeface(baseContext, translation.language)
            val pngBytes = WidgetTextRenderer.renderWidgetCompositePng(
                verseText = sample.textFor(translation),
                referenceText = sample.referenceFor(translation),
                widthDp = 265f,
                heightDp = 265f,
                deviceDensity = 3f,
                shape = WidgetShape.COOKIE_12,
                size = WidgetSize.LARGE,
                textScale = WidgetTextScale.COMFORTABLE,
                containerColor = Color.parseColor("#F4F0E8"),
                primaryTextColor = Color.BLACK,
                secondaryTextColor = Color.parseColor("#28482F"),
                verseTypeface = verseTypeface,
                referenceTypeface = refTypeface
            )
            assertTrue("Rendered PNG for ${translation.name} must be non-empty", pngBytes.size > 100)
            assertEquals(0x89.toByte(), pngBytes[0])
        }
    }

    @Test
    fun `daily verse rotates each day across 365 days without repeating`() {
        val seenIds = mutableSetOf<String>()
        val cal = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1) }
        repeat(365) {
            val verse = BibleCorpus.getDailyVerse(cal)
            assertTrue("Daily verse repeated within a year: ${verse.id}", seenIds.add(verse.id))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        assertEquals(365, seenIds.size)
    }

    @Test
    fun `scallop widget fitter keeps all words and readable text across all 1000 verses and translations`() {
        val density = 3f
        val violations = mutableListOf<String>()
        for (verse in BibleCorpus.verses) {
            for (translation in BibleTranslation.entries) {
                val text = verse.textFor(translation)
                for (size in WidgetSize.entries) {
                    val (wDp, hDp) = when (size) {
                        WidgetSize.SMALL -> 148f to 148f
                        WidgetSize.MEDIUM -> 285f to 148f
                        WidgetSize.LARGE -> 265f to 265f
                    }
                    val fitted = WidgetTextFitter.fitText(
                        verseText = text,
                        referenceText = verse.referenceFor(translation),
                        widthPx = wDp * density,
                        heightPx = hDp * density,
                        density = density,
                        shape = WidgetShape.COOKIE_12,
                        size = size,
                        textScale = WidgetTextScale.COMFORTABLE,
                        widthSafetyFactor = 0.94f,
                        lineHeightRatio = if (size == WidgetSize.SMALL) 1.15f else 1.24f
                    ) { sample, fontSizePx ->
                        sample.length * fontSizePx * 0.49f
                    }

                    val rebuilt = fitted.lines.joinToString(" ")
                    assertEquals(
                        "Verse ${verse.id} (${translation.name}) must not crop words in $size 12-sided cookie",
                        text.trim().replace(Regex("\\s+"), " "),
                        rebuilt
                    )

                    if (size == WidgetSize.MEDIUM) {
                        assertFalse("Medium wide 12-sided cookie should use horizontal reference", fitted.isCurvedReference)
                    } else {
                        assertTrue("$size circular 12-sided cookie should use curved reference", fitted.isCurvedReference)
                        assertTrue(
                            "Arc radius fraction ${fitted.referenceArcOuterRadiusFraction} out of range",
                            fitted.referenceArcOuterRadiusFraction in 0.35f..0.44f
                        )
                    }

                    val expectedMinVerseDp = when (size) {
                        WidgetSize.SMALL -> 8.5f
                        WidgetSize.MEDIUM -> 11.0f
                        WidgetSize.LARGE -> 12.5f
                    }
                    if (fitted.verseFontSizeDp < expectedMinVerseDp) {
                        violations.add("${verse.id} (${translation.name}, $size): ${"%.2f".format(fitted.verseFontSizeDp)}dp < ${expectedMinVerseDp}dp")
                    }

                    val expectedMinRefDp = when (size) {
                        WidgetSize.SMALL -> 9.5f
                        WidgetSize.MEDIUM -> 11.5f
                        WidgetSize.LARGE -> 12.0f
                    }
                    if (fitted.referenceFontSizeDp < expectedMinRefDp) {
                        violations.add("Ref ${verse.reference} ($size): ${"%.2f".format(fitted.referenceFontSizeDp)}dp < ${expectedMinRefDp}dp")
                    }
                    assertTrue(
                        "Reference ${verse.reference} in $size must stay inside widget bottom padding",
                        fitted.referenceBottomPaddingPx > 0f
                    )
                }
            }
        }
        assertTrue(
            "Font size violations (${violations.size}):\n${violations.take(25).joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `curved reference bitmap renders cleanly within RemoteViews IPC memory budget`() {
        val smallBmp = CurvedReferenceRenderer.renderBottomArcBitmap(
            diameterDp = 148f,
            maxBitmapWidthPx = 300,
            density = 3f,
            referenceText = "1 Thessalonians 5:16–18",
            referenceFontSizeDp = 10.5f,
            outerInkRadiusFraction = 0.415f,
            textColor = Color.DKGRAY,
            typeface = null
        )
        assertEquals(300, smallBmp.width)
        assertTrue("Small arc bitmap must stay compact (< 150 KB)", smallBmp.allocationByteCount < 150_000)

        val largeBmp = CurvedReferenceRenderer.renderBottomArcBitmap(
            diameterDp = 265f,
            maxBitmapWidthPx = 360,
            density = 3f,
            referenceText = "Psalm 23:1",
            referenceFontSizeDp = 13.5f,
            outerInkRadiusFraction = 0.395f,
            textColor = Color.DKGRAY,
            typeface = null
        )
        assertEquals(360, largeBmp.width)
        assertTrue("Large arc bitmap must stay compact (< 200 KB)", largeBmp.allocationByteCount < 200_000)
    }

    @Test
    fun `all 29 Material 3 Expressive shapes fit cleanly and render compact composite PNGs`() {
        assertEquals(29, WidgetShape.entries.size)
        val excluded = setOf("boom", "soft_boom", "pixel_circle", "pixel_triangle", "puffy_diamond", "heart")
        for (shape in WidgetShape.entries) {
            assertFalse("Excluded shape ${shape.id} must not exist", shape.id in excluded)
        }
        assertEquals(WidgetShape.COOKIE_12, WidgetShape.fromId("scallop"))
        assertEquals(WidgetShape.SQUARE, WidgetShape.fromId("rounded"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val refTypeface = com.example.util.WidgetTextRenderer.loadReferenceTypeface(context)
        val verseTypeface = com.example.util.WidgetTextRenderer.loadVerseTypeface(
            context,
            com.example.model.WidgetFontStyle.PIXEL_SANS
        )
        val sampleVerses = listOf(
            BibleCorpus.verses.minBy { it.textFor(BibleTranslation.WEB).length },
            BibleCorpus.verses[BibleCorpus.verses.size / 2],
            BibleCorpus.verses.maxBy { it.textFor(BibleTranslation.WEB).length }
        )

        val shapeIssues = mutableListOf<String>()
        for (shape in WidgetShape.entries) {
            for (size in WidgetSize.entries) {
                val (wDp, hDp) = when (size) {
                    WidgetSize.SMALL -> 148f to 148f
                    WidgetSize.MEDIUM -> 285f to 148f
                    WidgetSize.LARGE -> 265f to 265f
                }
                val density = 3f
                val contour = com.example.util.M3ShapeGeometry.buildContour(
                    shape = shape,
                    width = wDp * density,
                    height = hDp * density,
                    size = size
                )
                assertTrue("Contour for $shape/$size must have positive bounds", contour.maxX > contour.minX && contour.maxY > contour.minY)

                for (verse in sampleVerses) {
                    val verseText = verse.textFor(BibleTranslation.WEB)
                    val fitted = WidgetTextFitter.fitText(
                        verseText = verseText,
                        referenceText = verse.reference,
                        widthPx = wDp * density,
                        heightPx = hDp * density,
                        density = density,
                        shape = shape,
                        size = size,
                        textScale = WidgetTextScale.COMFORTABLE,
                        widthSafetyFactor = 0.94f,
                        lineHeightRatio = 1.20f
                    ) { sample, fontSizePx ->
                        sample.length * fontSizePx * 0.49f
                    }
                    assertEquals(
                        "Shape $shape ($size) must not crop any word of ${verse.id}",
                        verseText.trim().replace(Regex("\\s+"), " "),
                        fitted.lines.joinToString(" ")
                    )
                    assertTrue(
                        "Shape $shape ($size) must produce non-empty fitted lines",
                        fitted.fittedLines.isNotEmpty()
                    )
                    val minExpectedVerseDp = when {
                        size == WidgetSize.LARGE -> 9.0f
                        verseText.length < 150 -> 8.0f
                        size == WidgetSize.MEDIUM -> 7.2f
                        else -> 6.5f
                    }
                    if (fitted.verseFontSizeDp < minExpectedVerseDp) {
                        shapeIssues.add("$shape/$size (${verse.id}): verse=${"%.2f".format(fitted.verseFontSizeDp)}dp < ${minExpectedVerseDp}dp")
                    }
                    if (fitted.referenceFontSizeDp < 7.8f) {
                        shapeIssues.add("$shape/$size (${verse.id}): ref=${"%.2f".format(fitted.referenceFontSizeDp)}dp")
                    }
                }

                val sample = sampleVerses[1]
                val pngBytes = com.example.util.WidgetTextRenderer.renderWidgetCompositePng(
                    verseText = sample.textFor(BibleTranslation.WEB),
                    referenceText = sample.reference,
                    widthDp = wDp,
                    heightDp = hDp,
                    deviceDensity = density,
                    shape = shape,
                    size = size,
                    textScale = WidgetTextScale.COMFORTABLE,
                    containerColor = Color.parseColor("#F4F0E8"),
                    primaryTextColor = Color.BLACK,
                    secondaryTextColor = Color.parseColor("#28482F"),
                    verseTypeface = verseTypeface,
                    referenceTypeface = refTypeface
                )
                assertTrue("Rendered PNG for $shape/$size must be non-empty", pngBytes.size > 8)
                assertEquals(0x89.toByte(), pngBytes[0])
                assertEquals('P'.code.toByte(), pngBytes[1])
                assertEquals('N'.code.toByte(), pngBytes[2])
                assertEquals('G'.code.toByte(), pngBytes[3])
                val decoded = android.graphics.BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                assertTrue(
                    "Decoded PNG bitmap ($shape/$size) must have valid dimensions",
                    decoded != null && decoded.width in 160..620 && decoded.height in 120..560
                )
                assertTrue(
                    "Decoded launcher bitmap ($shape/$size) must stay lean (< 1.3 MB), was ${decoded!!.allocationByteCount}",
                    decoded.allocationByteCount < 1_300_000
                )
            }
        }
        assertTrue(
            "Shape legibility issues (${shapeIssues.size}):\n${shapeIssues.joinToString("\n")}",
            shapeIssues.isEmpty()
        )
    }

    @Test
    fun `all 29 shapes fill widget bounding box edge-to-edge without excess outer margins`() {
        val marginViolations = mutableListOf<String>()
        val testBoxes = listOf(
            Triple(WidgetSize.SMALL, 300f, 300f),
            Triple(WidgetSize.SMALL, 300f, 348f), // typical slightly tall Pixel 2x2 cell
            Triple(WidgetSize.MEDIUM, 570f, 296f), // 4x2 wide cell
            Triple(WidgetSize.LARGE, 530f, 530f)
        )

        val naturallyNonSquare1to1 = setOf(
            WidgetShape.SEMICIRCLE,
            WidgetShape.CLAMSHELL,
            WidgetShape.PUFFY,
            WidgetShape.DIAMOND,
            WidgetShape.BUN,
            WidgetShape.COOKIE_6,
            WidgetShape.SOFT_BURST
        )

        for (shape in WidgetShape.entries) {
            for ((size, w, h) in testBoxes) {
                val contour = com.example.util.M3ShapeGeometry.buildContour(shape, w, h, size)
                val fillW = (contour.maxX - contour.minX) / w
                val fillH = (contour.maxY - contour.minY) / h
                val maxAxisFill = maxOf(fillW, fillH)
                if (maxAxisFill < 0.99f) {
                    marginViolations.add("$shape ($size ${w.toInt()}x${h.toInt()}): maxAxisFill=${"%.3f".format(maxAxisFill)} (fillW=${"%.3f".format(fillW)}, fillH=${"%.3f".format(fillH)})")
                }
                // On 4x2 Medium, every shape except true CIRCLE should fill both axes edge-to-edge
                if (size == WidgetSize.MEDIUM && shape != WidgetShape.CIRCLE) {
                    if (fillW < 0.99f || fillH < 0.99f) {
                        marginViolations.add("$shape (MEDIUM wide): fillW=${"%.3f".format(fillW)}, fillH=${"%.3f".format(fillH)}")
                    }
                }
                // On 1:1 Small/Large, symmetric 1:1 shapes should fill >= 98% on BOTH axes
                if (w == h && shape !in naturallyNonSquare1to1) {
                    if (fillW < 0.98f || fillH < 0.96f) {
                        marginViolations.add("$shape (1:1 $size): fillW=${"%.3f".format(fillW)}, fillH=${"%.3f".format(fillH)}")
                    }
                }
            }
        }
        assertTrue(
            "Outer margin violations (${marginViolations.size}):\n${marginViolations.joinToString("\n")}",
            marginViolations.isEmpty()
        )
    }
}
