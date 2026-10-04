package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.VerseBoundsRow
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

    /**
     * La plage des versets d'une page.
     *
     * ## Deux vides qui n'ont rien à voir, et un message qui les distingue
     *
     * L'identifiant d'un verset vient de `Quran.verseId`, donc d'un objet **global** rempli par
     * `Quran.initialize`. Une page dont les lignes existent mais dont aucun identifiant ne se
     * résout ne dit pas que les données sont fausses : elle dit que le référentiel coranique
     * n'est pas chargé. Les deux cas rendaient le même message — « page sans coordonnées de
     * verset » — et ce message a envoyé chercher un défaut dans `coran_1441-bounds.json` là où
     * il n'y avait qu'une initialisation manquante. Le message nomme donc la cause.
     */
    fun pageRange(page: Int): Range {
        val rows = pageData(page).rows
        val ids = rows.mapNotNull { it.verseId }
        if (ids.isEmpty()) {
            val cause = if (rows.isEmpty()) "la page ne porte aucune ligne" else "référentiel coranique non chargé"
            throw IllegalArgumentException("Page $page sans coordonnées de verset ($cause)")
        }
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

    /**
     * Les rectangles des versets d'une page, dans l'espace de la source (1440×2320).
     *
     * Rendus dans le **même type** que `ReaderData.pageRows`, pour que le lecteur n'ait qu'un
     * seul chemin de code : ce qui change d'une source à l'autre est l'espace de coordonnées et
     * les images, pas la façon de trouver le verset sous le doigt.
     *
     * Les coordonnées du référentiel sont décimales (`352.08`), celles du type sont entières.
     * La troncature vaut moins d'un pixel sur 1440 — la même que celle appliquée aux
     * coordonnées du moushaf de Médine, qui sont décimales elles aussi. Arrondir autrement ici
     * ferait diverger les deux sources pour un gain invisible.
     */
    fun verseRows(page: Int): List<VerseBoundsRow> =
        (bounds[page] ?: emptyList()).map { row ->
            VerseBoundsRow(
                surah = row.surah,
                ayah = row.ayah,
                line = row.line,
                x1 = row.x1.toInt(),
                x2 = row.x2.toInt(),
                y1 = row.y1.toInt(),
                y2 = row.y2.toInt(),
            )
        }

    /**
     * L'index de la **dernière** ligne portant un verset sur [page], ou `null` si la page n'en
     * porte aucun.
     *
     * ## Ce que cette fonction ne dit pas
     *
     * Elle ne dit **pas** combien de lignes porte la page, et le nom précédent — `lineCount` —
     * promettait le contraire. L'index du référentiel est **0-based** : une page complète porte
     * les index 0 à 14, soit quinze lignes, alors que son maximum vaut 14. Mesuré sur le
     * référentiel livré : 602 pages ont pour maximum 14, deux l'ont à 11 — et la page 1 n'a que
     * **sept** lignes distinctes pour un maximum de 11, parce que les lignes 0 à 11 d'Al-Fâtiha
     * n'en portent pas toutes un verset.
     *
     * Trois nombres différents, donc, et les confondre se paie : dessiner quatorze bandes sur
     * une page qui en compte quinze fait disparaître la dernière ligne du moushaf sans que rien
     * ne le signale. Le nombre de bandes d'une page est **constant** et se lit dans
     * `MushafPageShape.lines` ; ce qui se lit ici est la dernière ligne **utilisée**.
     */
    fun lastLineIndex(page: Int): Int? = (bounds[page] ?: emptyList()).maxOfOrNull { it.line }

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
