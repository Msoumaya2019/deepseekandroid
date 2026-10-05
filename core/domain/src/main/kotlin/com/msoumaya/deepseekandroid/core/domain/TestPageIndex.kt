package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.Range
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Le découpage en pages de la source `coranTest` — « Coran avec règles de Tajwid ».
 *
 * Porté depuis `src/coranTest/model.ts`. Cette source **ne découpe pas le Coran comme le moushaf
 * de Médine** : son écran affiche une page de composition typographique, et la correspondance
 * verset/page se dérive des mots réellement présents sur chaque page, non d'une table de
 * l'édition imprimée.
 *
 * ## Ce qui est dérivé, et de quoi
 *
 * `verse-index.json` donne, pour chaque verset (`"sourate:verset"`), son identifiant global, les
 * pages où il apparaît, et les lignes qu'il y occupe. La plage d'une page est le **minimum et le
 * maximum** des identifiants des versets qui la portent — exactement ce que fait l'original, et
 * non une plage supposée contiguë.
 *
 * ## Deux pièges que l'original évite, et qui sont repris ici
 *
 * 1. **Un verset peut vivre sur plusieurs pages.** `testVersePage` reçoit donc la page courante
 *    et la **conserve** si elle porte déjà le verset : sans cela, le suivi automatique de la
 *    récitation ramènerait à la première page du verset à chaque changement, et sauterait en
 *    arrière au milieu d'une lecture.
 * 2. **Une page inconnue ne rend pas une plage vide.** `pageRange` refuse au lieu de rendre
 *    `Range(0, 0)`, qui ferait lire le verset 0 — un défaut qui ne se voit qu'à l'écoute.
 *
 * ## La dépendance au référentiel, et le message qui la nomme
 *
 * La clé `"sourate:verset"` se construit avec `Quran.verseAt`, donc à partir du référentiel
 * coranique. Si celui-ci n'est pas chargé, `versePage` lève une erreur d'initialisation, et non
 * « verset introuvable » : le même piège a déjà coûté du temps sur `ZipQuranSource`, où le
 * message envoyait chercher un défaut dans les données.
 */
object TestPageIndex {

    /** Dossier des ressources, à côté de `quran/` dans `core/domain/src/main/resources/`. */
    const val DIRECTORY = "coranTest"

    const val FILE = "verse-index.json"

    /** Les 604 pages de la composition. */
    const val PAGES = 604

    /**
     * Le canevas logique de présentation d'une page.
     *
     * Ce sont des **dimensions de rendu**, choisies pour le ratio du visuel fourni, et non des
     * dimensions extraites d'une image : la source est typographique et ne fournit aucune
     * largeur/hauteur. Elles servent à ne pas étirer les glyphes.
     */
    const val PAGE_WIDTH = 1000
    const val PAGE_HEIGHT = 2120

    /** Une entrée de l'index : le verset, ses pages, et ses lignes sur chacune. */
    data class Entry(val id: Int, val pages: List<Int>, val lines: List<List<Int>>)

    private val entries: Map<String, Entry> by lazy { load() }
    private val ranges: Map<Int, Range> by lazy { buildRanges() }

    /** Une page de la composition : un entier de 1 à 604. */
    fun validPage(page: Int): Boolean = page in 1..PAGES

    /**
     * Les pages à préparer autour de [page] : la précédente, elle, la suivante.
     *
     * Les bornes sont filtrées, donc la page 1 n'a que deux voisines et la 604 aussi. C'est ce
     * qui borne la préparation à trois documents au plus, jamais plus.
     */
    fun adjacentPages(page: Int): List<Int> = listOf(page - 1, page, page + 1).filter(::validPage)

    /** Toutes les pages portant un verset, dans l'ordre de l'index. */
    fun pagesOf(id: Int): List<Int> = entryOf(id)?.pages ?: emptyList()

    /**
     * L'identifiant global du verset qu'une clé `sourate:verset` désigne, ou `null` si l'index
     * ne le connaît pas.
     *
     * C'est la question que posera l'écran immersif : le document renvoie la clé du verset
     * touché, et il faut la traduire en identifiant global — celui du reste de l'application.
     * Une clé inconnue rend `null`, et l'appelant décide alors : l'original ouvre les options
     * au lieu de désigner un verset.
     */
    fun idOf(key: String): Int? = entries[key]?.id

    /** La plage des versets d'une page, ou un refus si la page n'existe pas. */
    fun pageRange(page: Int): Range = ranges[page] ?: throw IllegalArgumentException("Page invalide : $page")

    /**
     * La page d'un verset, en **conservant** [current] si elle porte déjà le verset.
     *
     * @throws IllegalArgumentException si le verset n'est pas dans le Mushaf.
     */
    fun versePage(id: Int, current: Int? = null): Int = pageFor(pagesOf(id), current)

    /**
     * La décision seule, sortie de [versePage] pour être éprouvable.
     *
     * ## Pourquoi elle est séparée
     *
     * Mesuré sur `verse-index.json` livré : **aucun** des 6 236 versets ne vit sur deux pages.
     * La branche qui conserve la page courante est donc **inexerçable sur ces données** — et un
     * contrôle qui ne passe jamais ne prouve rien. Extraite ici, elle s'éprouve sur une liste
     * écrite à la main, pendant qu'un autre contrôle mesure, sur les vraies données, le fait
     * qu'aucun verset n'est à cheval.
     */
    internal fun pageFor(pages: List<Int>, current: Int?): Int {
        require(pages.isNotEmpty()) { "Verset introuvable dans le Mushaf" }
        return if (current != null && pages.contains(current)) current else pages.first()
    }

    /** Le nombre de pages connues de l'index — 604 sur les données livrées. */
    fun knownPageCount(): Int = ranges.size

    /** Le nombre de versets indexés — 6 236 sur les données livrées. */
    fun knownVerseCount(): Int = entries.size

    /**
     * L'entrée d'un verset, ou `null` s'il n'est pas dans l'index.
     *
     * ## Deux vides distincts, et un refus qui nomme sa cause
     *
     * Un identifiant hors bornes et un référentiel non chargé ne sont pas la même panne : le
     * premier vient de l'appelant, le second d'une initialisation manquante. `Quran.verseAt`
     * lève un `IndexOutOfBoundsException` sur un identifiant hors bornes — donc **avant** que
     * la règle puisse refuser, avec un message qui ne dit rien du découpage. C'est mesuré, pas
     * supposé : le contrôle « un verset hors du Mushaf est refusé » a échoué exactement là
     * avant que cette borne existe.
     */
    private fun entryOf(id: Int): Entry? {
        val verses = Quran.verses
        if (verses.isEmpty()) error("Référentiel coranique non chargé")
        if (id !in 1..verses.size) return null
        val verse = verses[id - 1]
        return entries["${verse.surah}:${verse.ayah}"]
    }

    private fun buildRanges(): Map<Int, Range> {
        val out = HashMap<Int, Range>()
        for (entry in entries.values) {
            for (page in entry.pages) {
                val existing = out[page]
                out[page] = if (existing == null) {
                    Range(entry.id, entry.id)
                } else {
                    Range(minOf(existing.start, entry.id), maxOf(existing.end, entry.id))
                }
            }
        }
        return out
    }

    private fun load(): Map<String, Entry> {
        val stream = TestPageIndex::class.java.classLoader?.getResourceAsStream("$DIRECTORY/$FILE")
            ?: error("Ressource absente : $DIRECTORY/$FILE")
        val root = AppJson.parseToJsonElement(stream.use { it.readBytes().decodeToString() }).jsonObject
        val out = HashMap<String, Entry>(root.size)
        for ((key, value) in root) {
            val fields = value.jsonObject
            val id = fields["id"]?.jsonPrimitive?.intOrNull ?: continue
            val pages = (fields["pages"] as? JsonArray ?: JsonArray(emptyList()))
                .mapNotNull { it.jsonPrimitive.intOrNull }
            val lines = (fields["lines"] as? JsonArray ?: JsonArray(emptyList())).map { line ->
                (line as? JsonArray ?: JsonArray(emptyList())).mapNotNull { it.jsonPrimitive.intOrNull }
            }
            out[key] = Entry(id = id, pages = pages, lines = lines)
        }
        return out
    }
}
