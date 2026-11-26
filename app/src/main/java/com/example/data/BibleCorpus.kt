package com.example.data

import android.content.Context
import com.example.model.AppLanguage
import com.example.model.BibleTranslation
import com.example.model.BibleTranslation.ASV
import com.example.model.BibleTranslation.BBE
import com.example.model.BibleTranslation.BG40
import com.example.model.BibleTranslation.KJV
import com.example.model.BibleTranslation.LSG
import com.example.model.BibleTranslation.WEB
import com.example.model.ChapterVerse
import com.example.model.DailyVerse
import com.example.model.VerseTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.GZIPInputStream
import org.json.JSONObject

object BibleCorpus {

    private const val ASSET_FILE_NAME = "bible_corpus.bin"

    private data class ContextSpec(
        val bookIndex: Int,
        val chapter: Int,
        val entries: List<Pair<Int, Boolean>>
    )

    private data class LoadedCorpus(
        val verses: List<DailyVerse>,
        val byId: Map<String, DailyVerse>,
        val passagePool: Map<String, Array<String>>,
        val contextSpecs: Map<String, ContextSpec>
    )

    @Volatile
    private var loaded: LoadedCorpus? = null

    private val lock = Any()

    private val fallbackVerse: DailyVerse by lazy {
        DailyVerse(
            id = "psalm_23_1_3",
            book = "Psalm",
            chapter = 23,
            verseRange = "1",
            chapterHeading = "The Lord Is My Shepherd",
            theme = VerseTheme.PEACE,
            translations = mapOf(
                WEB to "Yahweh is my shepherd: I shall lack nothing.",
                KJV to "The LORD is my shepherd; I shall not want.",
                ASV to "Jehovah is my shepherd; I shall not want.",
                BBE to "The Lord takes care of me as his sheep; I will not be without any good thing.",
                LSG to "Cantique de David. L'Eternel est mon berger: je ne manquerai de rien.",
                BG40 to "Давидов псалом. Господ е Пастир мой; Няма да остана в нужда."
            ),
            chapterPassages = mapOf(
                WEB to listOf(ChapterVerse(1, "Yahweh is my shepherd: I shall lack nothing.", true)),
                KJV to listOf(ChapterVerse(1, "The LORD is my shepherd; I shall not want.", true)),
                ASV to listOf(ChapterVerse(1, "Jehovah is my shepherd; I shall not want.", true)),
                BBE to listOf(ChapterVerse(1, "The Lord takes care of me as his sheep; I will not be without any good thing.", true)),
                LSG to listOf(ChapterVerse(1, "Cantique de David. L'Eternel est mon berger: je ne manquerai de rien.", true)),
                BG40 to listOf(ChapterVerse(1, "Давидов псалом. Господ е Пастир мой; Няма да остана в нужда.", true))
            ),
            bookFr = "Psaume",
            bookBg = "Псалм",
            chapterHeadingFr = "L’Éternel est mon berger",
            chapterHeadingBg = "Господ е мой Пастир"
        )
    }

    /**
     * Loads the 1,000-verse verified offline public-domain Bible corpus from compressed app assets.
     * Safe to call multiple times from Application, ViewModel, or AppWidgetProvider.
     */
    fun ensureLoaded(context: Context) {
        if (loaded != null) return
        synchronized(lock) {
            if (loaded != null) return
            val jsonText = context.applicationContext.assets.open(ASSET_FILE_NAME).use { raw ->
                GZIPInputStream(raw).use { gzip ->
                    BufferedReader(InputStreamReader(gzip, Charsets.UTF_8)).readText()
                }
            }
            loaded = parseCorpus(jsonText)
        }
    }

    val verses: List<DailyVerse>
        get() = loaded?.verses ?: listOf(fallbackVerse)

    fun getDailyVerse(calendar: Calendar = Calendar.getInstance()): DailyVerse {
        val list = verses
        val year = calendar.get(Calendar.YEAR)
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val seed = (year * 366) + dayOfYear
        val index = Math.floorMod(seed, list.size)
        return list[index]
    }

    fun getById(id: String?): DailyVerse? {
        if (id.isNullOrBlank()) return null
        val current = loaded
        return if (current != null) {
            current.byId[id]
        } else {
            if (fallbackVerse.id == id) fallbackVerse else null
        }
    }

    fun loadChapterPassage(verseId: String, translation: BibleTranslation): List<ChapterVerse> {
        val current = loaded ?: return fallbackVerse.chapterPassages[translation].orEmpty()
        val spec = current.contextSpecs[verseId] ?: return emptyList()
        val tIdx = when (translation) {
            WEB -> 0
            KJV -> 1
            ASV -> 2
            BBE -> 3
            LSG -> 4
            BG40 -> 5
        }
        return spec.entries.mapNotNull { (verseNum, isFeatured) ->
            val key = "${spec.bookIndex}:${spec.chapter}:$verseNum"
            val texts = current.passagePool[key] ?: return@mapNotNull null
            if (tIdx >= texts.size) return@mapNotNull null
            ChapterVerse(
                number = verseNum,
                text = texts[tIdx],
                isFeatured = isFeatured
            )
        }
    }

    fun formattedDateFull(
        calendar: Calendar = Calendar.getInstance(),
        language: AppLanguage = AppLanguage.fromSystemLocale()
    ): String {
        val pattern = when (language) {
            AppLanguage.ENGLISH -> "EEEE, MMMM d"
            AppLanguage.FRENCH -> "EEEE d MMMM"
            AppLanguage.BULGARIAN -> "EEEE, d MMMM"
        }
        val formatter = SimpleDateFormat(pattern, language.locale)
        val formatted = formatter.format(calendar.time)
        return formatted.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(language.locale) else ch.toString()
        }
    }

    fun formattedDateShort(
        calendar: Calendar = Calendar.getInstance(),
        language: AppLanguage = AppLanguage.fromSystemLocale()
    ): String {
        val pattern = when (language) {
            AppLanguage.ENGLISH -> "MMM d"
            AppLanguage.FRENCH, AppLanguage.BULGARIAN -> "d MMM"
        }
        val formatter = SimpleDateFormat(pattern, language.locale)
        return formatter.format(calendar.time).uppercase(language.locale)
    }

    private fun parseCorpus(jsonText: String): LoadedCorpus {
        val root = JSONObject(jsonText)
        val booksArr = root.getJSONArray("books")
        val books = Array(booksArr.length()) { i -> booksArr.getString(i) }

        val booksFrArr = root.optJSONArray("books_fr")
        val booksFr = Array(books.size) { i -> booksFrArr?.optString(i).takeUnless { it.isNullOrBlank() } ?: books[i] }

        val booksBgArr = root.optJSONArray("books_bg")
        val booksBg = Array(books.size) { i -> booksBgArr?.optString(i).takeUnless { it.isNullOrBlank() } ?: books[i] }

        val headingsFrObj = root.optJSONObject("headings_fr")
        val headingsBgObj = root.optJSONObject("headings_bg")

        val poolObj = root.getJSONObject("pool")
        val poolMap = HashMap<String, Array<String>>(poolObj.length() * 2)
        val poolKeys = poolObj.keys()
        while (poolKeys.hasNext()) {
            val key = poolKeys.next()
            val arr = poolObj.getJSONArray(key)
            poolMap[key] = Array(arr.length()) { idx -> arr.getString(idx) }
        }

        val themes = VerseTheme.entries
        val versesArr = root.getJSONArray("verses")
        val verseList = ArrayList<DailyVerse>(versesArr.length())
        val byId = HashMap<String, DailyVerse>(versesArr.length() * 2)
        val specs = HashMap<String, ContextSpec>(versesArr.length() * 2)

        for (i in 0 until versesArr.length()) {
            val item = versesArr.getJSONObject(i)
            val id = item.getString("id")
            val bookIndex = item.getInt("b")
            val chapter = item.getInt("c")
            val verseRange = item.getString("v")
            val heading = item.getString("h")
            val themeIdx = item.getInt("t").coerceIn(0, themes.lastIndex)

            val translations = mutableMapOf(
                WEB to item.getString("w"),
                KJV to item.getString("k"),
                ASV to item.getString("a"),
                BBE to item.getString("e")
            )
            val frText = item.optString("f")
            if (frText.isNotBlank()) translations[LSG] = frText
            val bgText = item.optString("bg")
            if (bgText.isNotBlank()) translations[BG40] = bgText

            val verse = DailyVerse(
                id = id,
                book = books[bookIndex],
                chapter = chapter,
                verseRange = verseRange,
                chapterHeading = heading,
                theme = themes[themeIdx],
                translations = translations,
                bookFr = booksFr[bookIndex],
                bookBg = booksBg[bookIndex],
                chapterHeadingFr = headingsFrObj?.optString(heading).takeUnless { it.isNullOrBlank() } ?: heading,
                chapterHeadingBg = headingsBgObj?.optString(heading).takeUnless { it.isNullOrBlank() } ?: heading
            )
            verseList.add(verse)
            byId[id] = verse

            val ctxArr = item.getJSONArray("ctx")
            val ctxEntries = ArrayList<Pair<Int, Boolean>>(ctxArr.length())
            for (j in 0 until ctxArr.length()) {
                val pair = ctxArr.getJSONArray(j)
                ctxEntries.add(pair.getInt(0) to (pair.getInt(1) == 1))
            }
            specs[id] = ContextSpec(bookIndex, chapter, ctxEntries)
        }

        return LoadedCorpus(
            verses = verseList,
            byId = byId,
            passagePool = poolMap,
            contextSpecs = specs
        )
    }
}
