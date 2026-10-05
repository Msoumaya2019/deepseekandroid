package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * L'état de surimpression envoyé au document d'une page immersive.
 *
 * Porté depuis `readerOverlayState()` de `src/coranTest/model.ts`. C'est le **seul** endroit par
 * lequel l'application parle au document : les versets en signet, les versets difficiles, le
 * verset sélectionné et celui qui joue lui sont transmis sous forme de clés `sourate:verset`,
 * et c'est le document qui décide de la teinte et de l'emplacement — il est le seul à connaître
 * ses propres rectangles.
 *
 * ## Pourquoi une clé, et non un identifiant
 *
 * Le document ne connaît pas la numérotation globale du Mushaf : ses mots portent la clé de leur
 * verset (`data-verse="2:5"`), pas son rang. Traduire l'identifiant global en clé est donc
 * obligatoire, et c'est aussi ce qui rend le contrat lisible à l'œil dans le document.
 *
 * ## Un verset hors du corpus est **omis**, et non fatal
 *
 * L'original appelle `verseAt(id)` sans borne : un identifiant hors du Mushaf fait lever, et
 * l'écran immersif tombe entier. C'est atteignable — les marqueurs viennent de l'état du
 * compte, qui peut en porter un écrit par une autre version — et faire tomber la lecture pour
 * un marqueur orphelin serait le pire des échanges. Le portage **omet** l'élément, comme
 * `Bookmarks.rows` omet un signet hors corpus au lieu de faire tomber son écran.
 *
 * ## Ce qui n'est pas envoyé, et pourquoi
 *
 * L'original envoie aussi `session`, `sessionThrough`, `sessionDone` et `sessionColor`, qui
 * nourrissent le **bandeau de séance**. Le document porté ne les lit pas — il n'a pas de
 * bandeau — donc les envoyer serait du faux : un champ que personne ne lit laisse croire que
 * quelque chose s'en sert. Ils arriveront avec la coquille d'étude, en même temps que son
 * lecteur.
 */
object TestPageOverlay {

    /**
     * Ce que l'application sait des marques d'une page.
     *
     * Les couleurs arrivent déjà écrites en `#rrggbb` : le domaine ne connaît pas Compose, et
     * c'est l'appelant qui traduit un jeton de thème en texte. Le document valide la forme de
     * `background` et retombe sur son propre fond si elle est fausse.
     */
    data class Markers(
        val selected: Int? = null,
        val playing: Int? = null,
        val bookmarks: Set<Int> = emptySet(),
        val difficult: Set<Int> = emptySet(),
        val selecting: Boolean = false,
        val background: String,
        val primary: String,
        val selection: String,
        val gold: String,
    )

    /**
     * Le texte JSON à passer à `window.applyReaderState`.
     *
     * @param keyOf traduit un identifiant global en clé `sourate:verset`. Passé en paramètre
     *   plutôt que lu ici, pour la même raison que dans [TestPageSession.route] : la règle
     *   s'éprouve alors sur des clés fabriquées.
     */
    fun json(markers: Markers, keyOf: (Int) -> String? = ::keyOf): String {
        val fields: Map<String, JsonElement> = linkedMapOf(
            "enabled" to JsonPrimitive(true),
            "selecting" to JsonPrimitive(markers.selecting),
            // `null` et non absent : le client d'origine écrit `null` quand rien ne joue, et le
            // document compare la clé d'un verset à cette valeur. Un champ absent rendrait
            // `undefined`, ce qui donne le même résultat par accident — le même résultat obtenu
            // par accident est un contrat qu'on ne peut pas relire.
            "playing" to nullableKey(markers.playing, keyOf),
            "selected" to nullableKey(markers.selected, keyOf),
            "bookmarks" to keys(markers.bookmarks, keyOf),
            "difficulty" to keys(markers.difficult, keyOf),
            "primary" to JsonPrimitive(markers.primary),
            "selection" to JsonPrimitive(markers.selection),
            "gold" to JsonPrimitive(markers.gold),
            "background" to JsonPrimitive(markers.background),
        )
        return AppJson.encodeToString(JsonObject.serializer(), JsonObject(fields))
    }

    /**
     * La clé `sourate:verset` d'un identifiant global, ou `null` s'il est hors du Mushaf.
     *
     * Volontairement tolérant : c'est ce qui permet à un marqueur orphelin d'être omis au lieu
     * de faire tomber l'écran. Un référentiel non chargé rend `null` pour tout, et la page
     * s'affiche alors sans aucune marque — ce qui est exact, puisque rien n'est connu.
     */
    private fun keyOf(id: Int): String? {
        val verse = Quran.verses.getOrNull(id - 1) ?: return null
        return "${verse.surah}:${verse.ayah}"
    }

    private fun nullableKey(id: Int?, keyOf: (Int) -> String?): JsonElement =
        id?.let(keyOf)?.let { JsonPrimitive(it) } ?: JsonNull

    /**
     * Les clés d'une liste de marques, rangées dans un ordre fixe.
     *
     * L'ordre n'importe pas au document, qui cherche par appartenance. Il importe au message :
     * le même état doit produire le même texte, sinon rien de tout ceci ne s'éprouve.
     */
    private fun keys(ids: Set<Int>, keyOf: (Int) -> String?): JsonArray =
        JsonArray(ids.mapNotNull(keyOf).sorted().map { JsonPrimitive(it) })
}
