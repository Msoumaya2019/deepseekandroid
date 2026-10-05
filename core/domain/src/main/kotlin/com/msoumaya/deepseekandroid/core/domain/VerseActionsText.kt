package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.effectiveDifficultyMarkers

/**
 * Les mots du panneau des actions d'un verset.
 *
 * Porté depuis `src/App.tsx` (le panneau `sessionPanel === 'verse'`, ligne 507 pour le titre et
 * ligne 510 pour les actions). Les chaînes sont reprises **caractère pour caractère** : ce sont
 * des textes d'interface validés par le propriétaire du projet.
 *
 * ## Le panneau s'ouvre avec la fiche, et non après elle
 *
 * Le client d'origine ouvre les deux d'un seul geste — l'appui long fait
 * `setSelectedVerse(id); setSessionPanel('verse')`. Ce n'est pas une redondance : la **fiche**
 * dit *ce qu'on a touché* (la référence et la traduction), le **panneau** dit *ce qu'on peut en
 * faire*. L'un sans l'autre laisse la personne devant un verset sans savoir quoi en faire, ou
 * devant des actions sans savoir sur quoi elles portent — c'est pourquoi le panneau répète le
 * numéro du verset ([verseLabel]).
 *
 * ## Deux règles voisines qu'il ne faut pas confondre
 *
 * [Review.isDifficult] répond « ce verset est-il difficile ? » et compte **deux** origines : le
 * marqueur de l'élève (`user`) et celui du professeur (`admin`). C'est la bonne règle pour
 * colorer une page — un verset signalé par le professeur doit se voir.
 *
 * Le **libellé du bouton**, lui, ne suit que `user`. C'est ce que fait le client d'origine, qui
 * teste `state.difficultyMarkers?.[id]?.user`, et ce n'est pas une négligence : le bouton
 * **annonce ce qu'il fera**, et [Review.toggleDifficulty] ne touche jamais au marqueur du
 * professeur. Un verset marqué par le professeur seul doit donc proposer « Marquer comme
 * difficile » — le bouton ajoute alors le marqueur de l'élève, à côté de celui du professeur.
 * Suivre `isDifficult` ici afficherait « Retirer des révisions prioritaires » sur un verset dont
 * l'appui **ajouterait** un marqueur : le libellé mentirait, et rien ne le dirait.
 *
 * Cet accord entre le libellé et la bascule est éprouvé par `VerseActionsTextTest`, sur les
 * quatre combinaisons d'origines.
 */
object VerseActionsText {

    /** Le titre du panneau, tel qu'il s'affiche. */
    const val TITLE: String = "Actions du verset"

    /** Le libellé du bouton de fermeture, lu par les lecteurs d'écran. */
    const val CLOSE: String = "Fermer les actions du verset"

    /**
     * La référence du verset, en tête du panneau.
     *
     * Elle est répétée depuis la fiche parce que le panneau est une feuille basse : la fiche
     * peut être sortie de l'écran par le défilement, et le panneau doit rester lisible seul.
     */
    fun verseLabel(ayah: Int): String = "Verset $ayah"

    // --- Les actions, dans l'ordre du client d'origine -----------------------

    /** Écouter ce verset, une fois. */
    const val LISTEN: String = "Écouter ce verset"

    /**
     * Régler la répétition avant de lancer.
     *
     * Le client d'origine n'ouvre pas un panneau de plus : il met le mode de répétition sur
     * « passage complet » et **ouvre les réglages d'écoute**, où l'on choisit le nombre
     * d'écoutes. La commande ne lance donc rien — c'est « Écouter » qui lance.
     */
    const val REPEAT: String = "Répéter ce verset"

    /** Désigner une plage à la main sur la page. */
    const val SELECT_RANGE: String = "Sélectionner un passage"

    /** Marquer le verset, quand il ne l'est pas encore par l'élève. */
    const val MARK_DIFFICULT: String = "Marquer comme difficile"

    /** Le retirer, quand il l'est. */
    const val UNMARK_DIFFICULT: String = "Retirer des révisions prioritaires"

    /**
     * Les actions connues, **dans l'ordre du client d'origine** : écouter, répéter, choisir une
     * plage, puis marquer.
     *
     * L'ordre n'est pas cosmétique : c'est celui du source, et il va du geste le plus courant au
     * plus rare. Le marquage est en dernier parce qu'il **modifie** les données, là où les trois
     * premiers ne font qu'écouter ou désigner.
     */
    enum class Action {
        LISTEN,
        REPEAT,
        SELECT_RANGE,
        MARK,
    }

    /**
     * Les versets portant le marqueur de l'**élève** — ceux dont le bouton annonce le retrait.
     *
     * C'est la question que pose le libellé du bouton de marquage, et **non** celle de
     * [Review.isDifficult] : voir le commentaire de l'objet. La route s'en sert pour dire au
     * lecteur, verset par verset, quel mot afficher — et [rows] le traduit en titre.
     *
     * Elle est nommée plutôt qu'écrite à l'écran parce qu'elle décide d'un mot : une seconde
     * copie de `marker.user != null` finirait par diverger de la première, et le bouton mentirait
     * sur un seul des deux écrans.
     */
    fun userMarkedIds(state: AppState): Set<Int> =
        state.effectiveDifficultyMarkers.entries
            .filter { it.value.user != null }
            .mapNotNull { it.key.toIntOrNull() }
            .toSet()

    // --- Ce que le panneau affiche ------------------------------------------

    /** Une entrée du panneau, telle qu'elle s'affiche. */
    data class Row(val action: Action, val title: String)

    /**
     * Les entrées à afficher, **dans l'ordre du client d'origine**.
     *
     * Même règle que la feuille d'options et que le panneau des marques-pages : une destination
     * absente de [available] est **retirée**, et non grisée — une entrée grisée laisse croire que
     * l'écran existe mais qu'il est momentanément indisponible. Aujourd'hui, « Sélectionner un
     * passage » suppose un choix de plage qui n'est pas porté : l'entrée disparaît au lieu de
     * mener nulle part.
     *
     * @param available les actions réellement branchées par l'appelant.
     * @param markedByUser l'état du marqueur de l'**élève** sur ce verset, qui décide du mot de
     *   la dernière entrée. C'est [userMarkedIds] qui le fournit, et lui seul.
     */
    fun rows(available: Set<Action>, markedByUser: Boolean): List<Row> = buildList {
        if (Action.LISTEN in available) add(Row(Action.LISTEN, LISTEN))
        if (Action.REPEAT in available) add(Row(Action.REPEAT, REPEAT))
        if (Action.SELECT_RANGE in available) add(Row(Action.SELECT_RANGE, SELECT_RANGE))
        if (Action.MARK in available) {
            add(Row(Action.MARK, if (markedByUser) UNMARK_DIFFICULT else MARK_DIFFICULT))
        }
    }

    /**
     * `true` si le panneau a au moins une action à proposer.
     *
     * Sert à ne pas l'ouvrir du tout quand rien n'y mène : un panneau réduit à son titre et à sa
     * poignée serait une impasse. Comme [Action] est un enum fermé, la question se réduit à
     * « l'ensemble est-il vide » — mais la fonction porte le nom de l'intention, et c'est elle
     * que l'appelant lit.
     */
    fun isUseful(available: Set<Action>): Boolean = available.isNotEmpty()
}
