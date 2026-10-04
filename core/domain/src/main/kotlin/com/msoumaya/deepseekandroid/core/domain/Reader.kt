package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.MarginRegion
import com.msoumaya.deepseekandroid.core.model.ReaderZoom
import com.msoumaya.deepseekandroid.core.model.TajweedSpan
import com.msoumaya.deepseekandroid.core.model.VerseBoundsRow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Géométrie et données du lecteur.
 *
 * Porté depuis `src/core/readerLayout.ts`, `readerZoom.ts`, `pageNavigation.ts`,
 * `marginAnnotations.ts`, `ayahMarker.ts` et `readerData.ts`.
 *
 * Toutes ces fonctions sont pures : elles ne dépendent ni du rendu, ni de la source
 * affichée. C'est ce qui permet de les tester sans écran et de garantir que le centrage,
 * le zoom et la détection d'appui long restent identiques d'un appareil à l'autre.
 */

/** Mise à l'échelle de la page imprimée dans la zone disponible. */
object ReaderLayout {

    const val PAGE_WIDTH = 1920
    const val PAGE_HEIGHT = 3106

    /** Marge de cadre, identique au client d'origine. */
    const val FRAME = 4

    data class Size(val width: Double, val height: Double)

    /**
     * Ajuste la page dans la zone disponible **sans jamais la déformer**.
     *
     * Le rapport `(width - frame) / (height - frame)` reste égal à celui de la source. La
     * marge de 4 pixels est comptée dans les deux dimensions : c'est ce qui évite la dérive
     * observée quand on enchaîne plusieurs changements de page.
     *
     * Aucune marge fixe n'est appliquée : les dimensions reçues sont celles réellement
     * disponibles après prise en compte des barres système, des découpes d'écran et du
     * mini-player.
     */
    fun fitMushafPage(
        availableWidth: Double,
        availableHeight: Double,
        sourceWidth: Int = PAGE_WIDTH,
        sourceHeight: Int = PAGE_HEIGHT,
    ): Size {
        if (availableWidth <= FRAME || availableHeight <= FRAME || sourceWidth <= 0 || sourceHeight <= 0) {
            return Size(0.0, 0.0)
        }
        val scale = minOf(
            (availableWidth - FRAME) / sourceWidth,
            (availableHeight - FRAME) / sourceHeight,
        )
        return Size(sourceWidth * scale + FRAME, sourceHeight * scale + FRAME)
    }
}

/** Zoom et déplacement de la page. Échelle bornée à 1..3. */
object ReaderZoomGeometry {

    const val MIN_SCALE = 1f
    const val MAX_SCALE = 3f

    fun constrain(scale: Float, x: Float, y: Float, width: Float, height: Float): ReaderZoom {
        val s = if (scale.isFinite()) scale.coerceIn(MIN_SCALE, MAX_SCALE) else MIN_SCALE
        return ReaderZoom(
            scale = s,
            x = x.coerceIn(width * (1 - s), 0f),
            y = y.coerceIn(height * (1 - s), 0f),
        )
    }

    /**
     * Zoome en conservant le point visé sous le doigt.
     * Le ratio est appliqué au décalage, puis contraint : sans cette contrainte, un zoom
     * rapide ferait sortir la page de l'écran.
     */
    fun zoomAt(
        current: ReaderZoom,
        scale: Float,
        anchorX: Float,
        anchorY: Float,
        width: Float,
        height: Float,
    ): ReaderZoom {
        val s = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = if (current.scale == 0f) 1f else s / current.scale
        return constrain(
            s,
            anchorX - (anchorX - current.x) * ratio,
            anchorY - (anchorY - current.y) * ratio,
            width,
            height,
        )
    }
}

/** Décision de changement de page après un glissement horizontal. */
object PageNavigation {

    /** Distance horizontale minimale, en pixels, pour considérer un glissement. */
    const val MIN_DX = 60

    /** Le glissement doit être au moins 1,5 fois plus horizontal que vertical. */
    const val HORIZONTAL_RATIO = 1.5

    fun pageAfterSwipe(page: Int, dx: Float, dy: Float, totalPages: Int = 604): Int {
        if (kotlin.math.abs(dx) < MIN_DX || kotlin.math.abs(dx) < kotlin.math.abs(dy) * HORIZONTAL_RATIO) return page
        return (page + if (dx < 0) -1 else 1).coerceIn(1, totalPages)
    }
}

/** Marqueurs de marge : ils ne modifient jamais la géométrie de la page. */
object MarginAnnotations {

    data class Item(val id: Int, val ayah: Int, val done: Boolean)

    data class Group(val y: Float, val height: Float, val items: List<Item>, val bottom: Float)

    /**
     * Regroupe les ancres de versets par ligne.
     *
     * Pour un verset qui s'étale sur plusieurs lignes, seule la **première** ancre est
     * retenue (ligne la plus petite, puis `y` le plus petit). Tous les versets d'une même
     * ligne partagent un marqueur qui porte leurs numéros.
     */
    fun marginAnnotations(
        regions: List<MarginRegion>,
        start: Int,
        end: Int,
        through: Int = 0,
    ): List<Group> {
        val first = LinkedHashMap<Int, MarginRegion>()
        for (region in regions) {
            if (region.id < start || region.id > end) continue
            val old = first[region.id]
            if (old == null || region.line < old.line || (region.line == old.line && region.y < old.y)) {
                first[region.id] = region
            }
        }
        val groups = LinkedHashMap<Int, MutableList<MarginRegion>>()
        for (region in first.values) groups.getOrPut(region.line) { mutableListOf() }.add(region)
        return groups.values
            .sortedBy { it.minOf { r -> r.y } }
            .map { group ->
                val sorted = group.sortedBy { it.id }
                val ids = sorted.map { it.id }.toSet()
                Group(
                    y = sorted.minOf { it.y },
                    height = sorted.maxOf { it.height },
                    items = sorted.map { Item(it.id, it.ayah, it.id <= through) },
                    bottom = regions.filter { it.id in ids }.maxOf { it.y + it.height },
                )
            }
    }
}

/** Numérotation arabe orientale des versets. */
object AyahMarker {

    private const val DIGITS = "٠١٢٣٤٥٦٧٨٩"

    fun easternArabicNumber(value: Int): String {
        require(value >= 1) { "Numéro de verset invalide." }
        return value.toString().map { c ->
            if (c in '0'..'9') DIGITS[c - '0'] else c
        }.joinToString("")
    }

    /** Taille de police du numéro : réduite au-delà de deux chiffres. */
    fun fontSize(ayah: Int): Int = if (easternArabicNumber(ayah).length > 2) 16 else 19
}

/**
 * Données de lecture : coordonnées des versets, traduction et annotations Tajweed.
 *
 * Les fichiers sont volumineux (≈ 7 Mo au total). Ils sont chargés **à la première
 * utilisation** et non au démarrage : l'ouverture de l'application ne dépend donc pas de
 * la lecture de plusieurs mégaoctets de JSON.
 */
object ReaderData {

    private val bounds: Map<Int, List<VerseBoundsRow>> by lazy { loadBounds() }
    private val translations: List<TranslationRow> by lazy { loadTranslations() }
    private val tajweedText: List<TajweedTextRow> by lazy { loadTajweedText() }
    private val tajweedRules: List<TajweedRulesRow> by lazy { loadTajweedRules() }

    data class TranslationRow(val surah: Int, val ayah: Int, val translation: String, val footnotes: String?)

    data class TajweedRuleSpan(val start: Int, val end: Int, val rule: String)

    data class TajweedTextRow(val surah: Int, val ayah: Int, val text: String)

    data class TajweedRulesRow(val surah: Int, val ayah: Int, val annotations: List<TajweedRuleSpan>)

    /** Coordonnées des versets d'une page du moushaf de Médine (espace 1920×3106). */
    fun pageRows(page: Int): List<VerseBoundsRow> = bounds[page] ?: emptyList()

    fun frenchVerse(id: Int): TranslationRow? = translations.getOrNull(id - 1)

    fun tajweedVerse(id: Int): Pair<TajweedTextRow, List<TajweedRuleSpan>>? {
        val row = tajweedText.getOrNull(id - 1) ?: return null
        val rules = tajweedRules.getOrNull(id - 1) ?: return null
        if (row.surah != rules.surah || row.ayah != rules.ayah) return null
        return row to rules.annotations
    }

    /** Couleur d'une règle de Tajweed, identique au client d'origine. */
    fun tajweedColor(rule: String): String = when {
        rule.startsWith("madd") -> "#B45375"
        rule.startsWith("ikhfa") || rule == "iqlab" -> "#3A779B"
        rule.startsWith("idghaam") || rule == "ghunnah" -> "#6F5FA5"
        rule == "qalqalah" -> "#B05E32"
        rule == "silent" -> "#A2A2A2"
        else -> "#A26C44"
    }

    /**
     * Découpe un verset en fragments de Tajweed.
     * La concaténation des fragments reconstitue le texte original, caractère pour caractère.
     */
    fun tajweedSpans(id: Int): List<TajweedSpan> {
        val (row, annotations) = tajweedVerse(id) ?: return emptyList()
        val chars = row.text.toCharArray()
        val rules = arrayOfNulls<String>(chars.size)
        for (annotation in annotations) {
            for (i in annotation.start until annotation.end) {
                if (i in rules.indices) rules[i] = annotation.rule
            }
        }
        val spans = ArrayList<TajweedSpan>()
        for (i in chars.indices) {
            val previous = spans.lastOrNull()
            if (previous != null && previous.rule == rules[i]) {
                spans[spans.size - 1] = previous.copy(text = previous.text + chars[i])
            } else {
                spans.add(TajweedSpan(chars[i].toString(), rules[i]))
            }
        }
        return spans
    }

    /**
     * Verset situé sous un point de l'image.
     *
     * Le plus petit rectangle contenant le point est retenu : sur une page où deux versets
     * se chevauchent visuellement, c'est le plus précis qui doit gagner.
     */
    fun verseAtImagePoint(
        rows: List<VerseBoundsRow>,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        sourceWidth: Int = ReaderLayout.PAGE_WIDTH,
        sourceHeight: Int = ReaderLayout.PAGE_HEIGHT,
    ): Int? {
        if (width <= 0 || height <= 0 || x < 0 || y < 0 || x > width || y > height) return null
        val sourceX = x / width * sourceWidth
        val sourceY = y / height * sourceHeight
        val matches = rows.filter { sourceX >= it.left && sourceX <= it.right && sourceY >= it.top && sourceY <= it.bottom }
        if (matches.isEmpty()) return null
        val best = matches.minBy { (it.right - it.left) * (it.bottom - it.top) }
        return Quran.verseId(best.surah, best.ayah)
    }

    // -----------------------------------------------------------------------
    // Chargement
    // -----------------------------------------------------------------------

    private fun resource(name: String): JsonElement {
        val stream = ReaderData::class.java.classLoader
            ?.getResourceAsStream("${QuranDataLoader.DIRECTORY}/$name")
            ?: error("Ressource absente : $name")
        return AppJson.parseToJsonElement(stream.use { it.readBytes().decodeToString() })
    }

    private fun loadBounds(): Map<Int, List<VerseBoundsRow>> {
        val root = resource("bounds.json").jsonObject
        val out = HashMap<Int, List<VerseBoundsRow>>(root.size)
        for ((key, value) in root) {
            val page = key.toIntOrNull() ?: continue
            out[page] = (value as? JsonArray ?: JsonArray(emptyList())).map { element ->
                val row = element.jsonArray
                VerseBoundsRow(
                    surah = row[0].jsonPrimitive.intOrNull ?: 0,
                    ayah = row[1].jsonPrimitive.intOrNull ?: 0,
                    line = row[2].jsonPrimitive.intOrNull ?: 0,
                    x1 = (row[3].jsonPrimitive.doubleOrNull ?: 0.0).toInt(),
                    x2 = (row[4].jsonPrimitive.doubleOrNull ?: 0.0).toInt(),
                    y1 = (row[5].jsonPrimitive.doubleOrNull ?: 0.0).toInt(),
                    y2 = (row[6].jsonPrimitive.doubleOrNull ?: 0.0).toInt(),
                )
            }
        }
        return out
    }

    private fun loadTranslations(): List<TranslationRow> =
        resource("translation-fr-rashid.json").jsonArray.map { element ->
            val o = element.jsonObject
            TranslationRow(
                surah = o["surah"]?.jsonPrimitive?.intOrNull ?: 0,
                ayah = o["ayah"]?.jsonPrimitive?.intOrNull ?: 0,
                translation = o["translation"]?.jsonPrimitive?.content ?: "",
                footnotes = o["footnotes"]?.jsonPrimitive?.content,
            )
        }

    private fun loadTajweedText(): List<TajweedTextRow> =
        resource("tajweed-text.json").jsonArray.map { element ->
            val o = element.jsonObject
            TajweedTextRow(
                surah = o["surah"]?.jsonPrimitive?.intOrNull ?: 0,
                ayah = o["ayah"]?.jsonPrimitive?.intOrNull ?: 0,
                text = o["text"]?.jsonPrimitive?.content ?: "",
            )
        }

    private fun loadTajweedRules(): List<TajweedRulesRow> =
        resource("tajweed-rules.json").jsonArray.map { element ->
            val o = element.jsonObject
            TajweedRulesRow(
                surah = o["surah"]?.jsonPrimitive?.intOrNull ?: 0,
                ayah = o["ayah"]?.jsonPrimitive?.intOrNull ?: 0,
                annotations = (o["annotations"] as? JsonArray ?: JsonArray(emptyList())).map { a ->
                    val r = a.jsonObject
                    TajweedRuleSpan(
                        start = r["start"]?.jsonPrimitive?.intOrNull ?: 0,
                        end = r["end"]?.jsonPrimitive?.intOrNull ?: 0,
                        rule = r["rule"]?.jsonPrimitive?.content ?: "",
                    )
                },
            )
        }
}
