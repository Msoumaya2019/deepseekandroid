package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ce que l'écran immersif reçoit de ses pages, et ce qu'il en fait.
 *
 * Porté depuis `receive()` de `src/coranTest/CoranTestScreen.tsx`. Le document d'une page ne
 * peint pas seulement : il **mesure** les rectangles de ses mots, garde le résultat pour son
 * propre test de toucher, et renvoie à l'application ce que le doigt a désigné. Toute la
 * navigation de cet écran passe donc par ici.
 *
 * ## Le point qui décide de tout : à quelle page un message a le droit de parler
 *
 * Trois pages peuvent être montées à la fois — la courante et ses deux voisines — et **chacune
 * a le sien de pont**. Un message arrive donc accompagné de la page qui l'a émis, et il faut
 * décider si elle a le droit d'agir. L'original ne traite pas les cinq natures de message de la
 * même façon, et c'est ce qui est repris tel quel :
 *
 * - `ready` est accepté **de n'importe quelle page**, à une condition : que la page annoncée
 *   soit celle qui parle. Une voisine doit pouvoir se mesurer avant d'être affichée — sans quoi
 *   elle s'afficherait vide, puis se remplirait sous les yeux. La condition sur la page annoncée
 *   écarte, elle, le message d'une surface recyclée qui répondrait pour une page qu'elle ne
 *   porte plus ;
 * - `error` est accepté **de la page demandée seulement** — pas de la page affichée. Les deux
 *   diffèrent pendant un changement de page, et une voisine qui échoue pendant qu'on lit la
 *   courante ne doit pas faire croire que la page lue est cassée ;
 * - `tap`, `longpress` et `swipe` ne sont acceptés que de la page **affichée**. Les voisines
 *   sont montées mais invisibles et sans toucher ; accepter leur message désignerait un verset
 *   qu'on ne voit pas.
 *
 * ## Ce que le portage ne transporte pas, et pourquoi
 *
 * L'original joint à `ready` la liste des mots mesurés, et en dérive une carte de rectangles
 * côté React Native. **Cette carte n'est jamais lue** — mesuré : `overlay.current` n'est que
 * posée, élaguée et supprimée dans tout le fichier, et `verseRegions` n'est appelé qu'à cet
 * endroit. Ici, les rectangles restent dans le document, qui en a besoin pour son propre test
 * de toucher et pour sa surimpression ; rien n'a besoin d'en recevoir une copie.
 *
 * L'original joint aussi à `swipe` un booléen `fromEdge`, dont le seul lecteur est le geste de
 * retour par le bord d'iOS (`Platform.OS==='ios'`). Sur Android, le retour est celui du
 * système : le portage ne le transporte pas plutôt que de le lire sans rien en faire.
 */
object TestPageSession {

    /** Un message envoyé par le document d'une page. */
    sealed interface Message {

        /**
         * La page a fini de se peindre et s'est mesurée.
         *
         * @param page la page que le document **dit** porter. Elle sert à écarter le message
         *   d'une surface qui répondrait pour une autre — voir [route].
         */
        data class Ready(val page: Int) : Message

        /**
         * Un appui court.
         *
         * @param key le verset touché, ou `null` si le doigt n'était sur aucun mot.
         */
        data class Tap(val key: String?) : Message

        /** Un appui long, avec la même distinction entre un verset et le vide. */
        data class LongPress(val key: String?) : Message

        /** Un balayage horizontal, en pixels du document. */
        data class Swipe(val dx: Double, val dy: Double) : Message

        /** La page n'a pas pu se peindre. */
        data object Failed : Message
    }

    /** Ce que l'écran doit faire d'un message. */
    sealed interface Decision {

        /** Rien : le message ne concerne pas cette page, ou son type est inconnu. */
        data object Ignored : Decision

        /**
         * La page s'est mesurée.
         *
         * @param display la page mesurée est celle qu'on attendait : elle peut devenir la page
         *   affichée. Faux pour une voisine, qui se prépare sans se montrer.
         */
        data class Measured(val page: Int, val display: Boolean) : Decision

        /** Le verset touché doit être enregistré comme marque-page. */
        data class Select(val id: Int) : Decision

        /** Un appui court qui ne désigne aucun verset connu. */
        data object Tap : Decision

        /** Le verset touché doit ouvrir ses actions. */
        data class Mark(val id: Int) : Decision

        /** Un appui long hors de tout verset : c'est le geste qui ouvre les options. */
        data object BlankLongPress : Decision

        /** Tourner la page. La page suivante se calcule ailleurs, avec `PageNavigation`. */
        data class Turn(val dx: Double, val dy: Double) : Decision

        /** La page demandée a échoué : l'écran doit le dire et proposer de réessayer. */
        data object Failed : Decision
    }

    /**
     * Les documents à **demander**, la page demandée étant mesurée ou non.
     *
     * C'est la condition de l'original, `readyPage===page?neighbors:[page]`. Tant que la page
     * demandée n'est pas mesurée, on ne demande qu'elle : préparer ses voisines pendant qu'on
     * attend encore la page qu'on a demandée ne ferait qu'ajouter trois encodages de police à
     * l'attente. Dès qu'elle est là, les deux voisines se préparent — c'est ce qui rend le
     * balayage suivant instantané.
     */
    fun toLoad(page: Int, measured: Boolean): List<Int> =
        if (measured) TestPageIndex.adjacentPages(page) else listOf(page)

    /**
     * Les documents à **garder** autour d'une page.
     *
     * Trois au plus, jamais plus : l'écran ne doit pas conserver la mémoire de toutes les pages
     * visitées. La règle est celle de l'index, et elle est éprouvée là-bas.
     */
    fun toKeep(page: Int): Set<Int> = TestPageIndex.adjacentPages(page).toSet()

    /**
     * Analyse le texte envoyé par le document.
     *
     * @return `null` quand le message n'est pas exploitable : texte illisible, type inconnu, ou
     *   champ indispensable absent. Un message incompris doit être **ignoré**, jamais deviné —
     *   un `swipe` sans distance ferait sinon tourner la page au hasard.
     */
    fun parse(json: String): Message? {
        val root = runCatching { AppJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        return when (root.string("type")) {
            "ready" -> root.int("page")?.let { Message.Ready(it) }
            "error" -> Message.Failed
            "tap" -> Message.Tap(root.string("key"))
            "longpress" -> Message.LongPress(root.string("key"))
            "swipe" -> {
                val dx = root.double("dx")
                val dy = root.double("dy")
                if (dx != null && dy != null) Message.Swipe(dx, dy) else null
            }

            else -> null
        }
    }

    /**
     * Ce qu'il faut faire d'un message émis par la page [target].
     *
     * @param current la page demandée à l'écran. Elle diffère de [displayed] pendant un
     *   changement de page : la seconde est celle qu'on voit encore, la première celle qu'on
     *   attend.
     * @param displayed la page réellement affichée.
     * @param selecting le mode de pose d'un marque-page est armé : un appui désigne alors un
     *   verset au lieu de ne rien faire.
     * @param known traduit une clé `sourate:verset` en identifiant global. Passé en paramètre
     *   plutôt que lu ici : c'est ce qui permet d'éprouver la règle sur des clés fabriquées,
     *   sans dépendre du référentiel coranique.
     */
    fun route(
        target: Int,
        current: Int,
        displayed: Int,
        selecting: Boolean,
        message: Message,
        known: (String) -> Int? = TestPageIndex::idOf,
    ): Decision = when (message) {
        // La page annoncée doit être celle qui parle : une surface recyclée qui répondrait pour
        // une page qu'elle ne porte plus serait sinon prise pour la page affichée.
        is Message.Ready ->
            if (message.page != target) Decision.Ignored
            else Decision.Measured(message.page, display = target == current)

        is Message.Tap -> when {
            target != displayed -> Decision.Ignored
            else -> {
                val id = message.key?.let(known)
                // Un appui qui ne désigne aucun verset connu n'est pas une erreur : le doigt
                // était dans la marge. C'est `onTap` de l'original, et l'appelant décide.
                if (selecting && id != null) Decision.Select(id) else Decision.Tap
            }
        }

        is Message.LongPress ->
            if (target != displayed) Decision.Ignored
            else message.key?.let(known)?.let { Decision.Mark(it) } ?: Decision.BlankLongPress

        is Message.Swipe ->
            if (target != displayed) Decision.Ignored else Decision.Turn(message.dx, message.dy)

        Message.Failed ->
            if (target != current) Decision.Ignored else Decision.Failed
    }

    private fun JsonObject.string(field: String): String? =
        this[field]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(field: String): Int? =
        this[field]?.jsonPrimitive?.intOrNull

    private fun JsonObject.double(field: String): Double? =
        this[field]?.jsonPrimitive?.doubleOrNull
}
