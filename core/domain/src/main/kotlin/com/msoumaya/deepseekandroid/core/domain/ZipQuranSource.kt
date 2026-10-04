package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.Range
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Source « Coran 1441 ».
 *
 * Porté depuis `src/core/quranSources.ts`. Cette source n'est pas livrée avec l'application :
 * ses 9 060 images sont téléchargées à la demande, puis lues hors connexion. Les coordonnées
 * des versets proviennent de `coran_1441-bounds.json`, exprimées dans l'espace 1440×2320.
 *
 * Format d'une ligne : `[sourate, verset, ligne, x1, x2, y1, y2]`.
 */
object ZipQuranSource {

    const val ID = "coran_1441"
    const val LABEL = "Coran 1441"

    /** Dimensions d'une page de la source. */
    data class PageData(val rows: List<Row>, val width: Double, val height: Double)

    data class Row(
        val surah: Int,
        val ayah: Int,
        val line: Int,
        val x1: Double,
        val x2: Double,
        val y1: Double,
        val y2: Double,
    ) {
        val verseId: Int? get() = Quran.verseId(surah, ayah)
    }

    private val bounds: Map<Int, List<Row>> by lazy { loadBounds() }
    private val dimensions: Map<Int, Pair<Double, Double>> by lazy { loadDimensions() }
    private val versePagesIndex: Map<Int, List<Int>> by lazy { buildVersePagesIndex() }

    fun pageData(page: Int): PageData {
        val (w, h) = dimensions[page] ?: (1.0 to 1.0)
        return PageData(bounds[page] ?: emptyList(), w, h)
    }

    /** Toutes les pages contenant un verset, dans l'ordre croissant. */
    fun versePages(id: Int): List<Int> = versePagesIndex[id] ?: emptyList()

    /** Page d'un verset : la page courante si elle le contient, sinon la première. */
    fun versePage(id: Int, current: Int? = null): Int {
        val pages = versePages(id)
        return if (current != null && pages.contains(current)) current else pages.firstOrNull() ?: 1
    }

    fun pageRange(page: Int): Range {
        val ids = pageData(page).rows.mapNotNull { it.verseId }
        if (ids.isEmpty()) throw IllegalArgumentException("Page $page sans coordonnées de verset")
        return Range(ids.min(), ids.max())
    }

    /** Rectangles normalisés (0..1) d'un verset sur une page. */
    fun verseRegions(page: Int, id: Int): List<com.msoumaya.deepseekandroid.core.model.MarginRegion> {
        val data = pageData(page)
        return data.rows
            .filter { it.verseId == id }
            .map {
                com.msoumaya.deepseekandroid.core.model.MarginRegion(
                    id = id,
                    ayah = it.ayah,
                    x = (it.x1 / data.width).toFloat(),
                    y = (it.y1 / data.height).toFloat(),
                    width = ((it.x2 - it.x1) / data.width).toFloat(),
                    height = ((it.y2 - it.y1) / data.height).toFloat(),
                    line = it.line,
                )
            }
    }

    private fun buildVersePagesIndex(): Map<Int, List<Int>> {
        val map = HashMap<Int, MutableList<Int>>()
        for (page in 1..604) {
            for (row in bounds[page] ?: emptyList()) {
                val id = row.verseId ?: continue
                val pages = map.getOrPut(id) { mutableListOf() }
                if (pages.lastOrNull() != page) pages.add(page)
            }
        }
        return map
    }

    private fun resource(name: String): JsonElement {
        val stream = ZipQuranSource::class.java.classLoader
            ?.getResourceAsStream("${QuranDataLoader.DIRECTORY}/$name")
            ?: error("Ressource absente : $name")
        return AppJson.parseToJsonElement(stream.use { it.readBytes().decodeToString() })
    }

    private fun loadBounds(): Map<Int, List<Row>> {
        val root = resource("coran_1441-bounds.json").jsonObject
        val out = HashMap<Int, List<Row>>(root.size)
        for ((key, value) in root) {
            val page = key.toIntOrNull() ?: continue
            out[page] = (value as? JsonArray ?: JsonArray(emptyList())).map { element ->
                val row = element.jsonArray
                Row(
                    surah = row[0].jsonPrimitive.intOrNull ?: 0,
                    ayah = row[1].jsonPrimitive.intOrNull ?: 0,
                    line = row[2].jsonPrimitive.intOrNull ?: 0,
                    x1 = row[3].jsonPrimitive.doubleOrNull ?: 0.0,
                    x2 = row[4].jsonPrimitive.doubleOrNull ?: 0.0,
                    y1 = row[5].jsonPrimitive.doubleOrNull ?: 0.0,
                    y2 = row[6].jsonPrimitive.doubleOrNull ?: 0.0,
                )
            }
        }
        return out
    }

    private fun loadDimensions(): Map<Int, Pair<Double, Double>> {
        val root = resource("coran_1441-dimensions.json").jsonObject
        val out = HashMap<Int, Pair<Double, Double>>(root.size)
        for ((key, value) in root) {
            val page = key.toIntOrNull() ?: continue
            val pair = (value as? JsonArray) ?: continue
            if (pair.size < 2) continue
            out[page] = (pair[0].jsonPrimitive.doubleOrNull ?: 1.0) to (pair[1].jsonPrimitive.doubleOrNull ?: 1.0)
        }
        return out
    }

    /** Nombre de lignes d'une page, utilisé pour dimensionner la grille de rendu. */
    fun lineCount(page: Int): Int = (bounds[page] ?: emptyList()).maxOfOrNull { it.line } ?: 0

    fun isAvailable(): Boolean = bounds.isNotEmpty()
}

/** Nom de fichier d'une ligne de page dans le paquet téléchargé. */
fun zipLineFileName(page: Int, line: Int): String =
    String.format(java.util.Locale.ROOT, "%03d-%02d.png", page, line)

/** Nombre de lignes attendues par page pour la source Coran 1441. */
const val ZIP_LINES_PER_PAGE = 15

/** Nombre total de fichiers du paquet Coran 1441 (604 pages × 15 lignes). */
const val ZIP_TOTAL_FILES = 9060

/** Empreinte attendue du paquet Coran 1441, en octets. */
const val ZIP_ARCHIVE_BYTES = 102608011L

/** URL du paquet Coran 1441. */
const val ZIP_ARCHIVE_URL = "https://files.quran.app/hafs/madani_1441/zips/images_1440.zip"

/** Version du marqueur d'installation locale. */
const val ZIP_READY_VERSION = 1

/** Un `JsonPrimitive` booléen n'existe pas : aide de lecture pour les drapeaux. */
internal fun JsonElement.asBooleanOrNull(): Boolean? =
    (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
