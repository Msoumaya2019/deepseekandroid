package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Range

/**
 * Le panneau « Traduction française » : quels versets il énumère, et ce que chacun porte.
 *
 * Porté depuis le panneau `sessionPanel === 'translation'` de `src/App.tsx`. C'est le seul endroit
 * où la page se lit en français sans quitter le moushaf : pour chaque verset, sa référence, puis
 * la traduction du sens.
 *
 * ## La plage : la séance, sinon la page
 *
 * Le client d'origine écrit `focused ? reader.range : sourcePageRange`, où `focused` est vrai
 * quand une séance d'apprentissage ou de révision est ouverte. Le panneau suit donc **la séance**
 * quand il y en a une, et **la page affichée** sinon — dans le découpage de la source affichée,
 * puisque deux sources ne placent pas les mêmes versets sur la page 300.
 *
 * La séance n'existe pas encore dans ce client : [verses] et [rows] prennent donc une plage de
 * séance **facultative**, et l'appelant passe `null` aujourd'hui. La règle est écrite et éprouvée
 * maintenant pour que la phase C n'ait pas à la réinventer — et surtout pour qu'elle ne se trompe
 * pas de plage le jour où elle la branchera.
 *
 * ## Ce que le panneau ne fait pas
 *
 * Il ne choisit pas la plage de la page : elle vient de `MushafSourceNavigation`, qui connaît le
 * découpage de la source. Et il n'affiche pas les notes de bas de page, alors que **1 330 versets**
 * en portent : le renvoi `[1]` reste dans le texte, la note elle-même n'est pas déroulée. C'est
 * le comportement du client d'origine, et c'est mesuré par `TranslationPanelTest`.
 */
object TranslationPanel {

    /**
     * Le titre de la feuille.
     *
     * C'est **le même texte** que la ligne qui l'ouvre dans la feuille d'options : une personne
     * qui appuie sur « Traduction française » doit arriver sur un écran qui porte ce nom.
     * `TranslationPanelTest` le vérifie en lisant [ReaderOptionsText], sans que l'un des deux
     * dépende de l'autre.
     */
    const val TITLE: String = "Traduction française"

    /** Le libellé du bouton de fermeture, lu par les lecteurs d'écran. */
    const val CLOSE: String = "Fermer la traduction"

    /** Une ligne du panneau : un verset, sa référence, et la traduction française de son sens. */
    data class Row(val verseId: Int, val reference: String, val translation: String)

    /**
     * Les versets énumérés, dans l'ordre croissant.
     *
     * **Bornée au corpus.** Le client d'origine construit sa liste par
     * `Array.from({length: end - start + 1})` sans rien vérifier, puis nomme chaque verset avec
     * `verseAt(id)` : une plage hors du corpus — un état de séance abîmé, une plage restaurée
     * d'une version antérieure — le fait donc **lever**, et c'est le panneau entier qui
     * disparaît. Ici, une plage qui déborde est **ramenée** aux versets qui existent, et une
     * plage entièrement hors du corpus rend une liste vide : le panneau dit qu'il n'a rien à
     * montrer au lieu de faire tomber l'écran.
     *
     * Une plage à l'envers (`start > end`) rend une liste vide, comme une plage vide. Un
     * référentiel non chargé en rend une aussi — `Quran` n'a alors aucun verset, et c'est le seul
     * résultat honnête ; cette branche-là n'est pas éprouvée, parce que la décharger dans un test
     * laisserait le référentiel vide pour les autres.
     */
    fun verses(session: Range?, page: Range): List<Int> {
        val total = Quran.verses.size
        if (total == 0) return emptyList()
        val wanted = session ?: page
        if (wanted.start > wanted.end) return emptyList()
        val first = wanted.start.coerceAtLeast(1)
        val last = wanted.end.coerceAtMost(total)
        return if (first > last) emptyList() else (first..last).toList()
    }

    /**
     * Les lignes du panneau, dans l'ordre des versets.
     *
     * C'est ici que la traduction est lue. `ReaderData` range ses lignes **par identifiant global
     * de verset**, dans le même ordre que le corpus ; l'accord entre les deux tables est vérifié
     * sur les 6 236 versets par `TranslationPanelTest`. Sans cette vérification, un décalage d'un
     * seul rang afficherait la traduction du verset voisin — un texte parfaitement lisible, et
     * faux.
     *
     * Une traduction absente donne une ligne **sans texte**, comme le client d'origine qui
     * n'affiche rien dans ce cas. C'est aujourd'hui inatteignable : les 6 236 versets en ont une,
     * et [verses] borne les identifiants au corpus.
     */
    fun rows(session: Range?, page: Range): List<Row> = verses(session, page).map { id ->
        Row(
            verseId = id,
            reference = reference(id),
            translation = ReaderData.frenchVerse(id)?.translation.orEmpty(),
        )
    }

    /**
     * La référence d'un verset, telle que le panneau l'écrit.
     *
     * C'est `Quran.reference` sur une plage d'un seul verset — donc, comme dans le client
     * d'origine, « Al Baqarah 248–248 » et non « Al Baqarah 248 ». La répétition est **celle de
     * l'original** : c'est un texte qu'on lit, et le corriger ici ferait diverger les deux clients
     * sur ce que voit la personne.
     *
     * L'identifiant doit venir de [verses] : hors du corpus, `Quran.reference` lève, et c'est
     * voulu — un appelant qui invente un identifiant a un défaut à corriger, pas un cas à couvrir.
     */
    private fun reference(verseId: Int): String = Quran.reference(Range(verseId, verseId))
}
