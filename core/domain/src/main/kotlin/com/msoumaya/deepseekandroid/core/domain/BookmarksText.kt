package com.msoumaya.deepseekandroid.core.domain

/**
 * Les mots des marques-pages : le panneau du lecteur, le mode de pose, et l'écran de la liste.
 *
 * Porté depuis `src/App.tsx` (le panneau `sessionPanel === 'bookmarks'`, la notice de pose) et
 * `src/BookmarksScreen.tsx`. Les chaînes sont reprises **caractère pour caractère** : ce sont
 * des textes d'interface validés par le propriétaire du projet.
 *
 * ## Trois mots voisins qui ne sont pas le même mot
 *
 * Le client d'origine écrit `Marque-page` au singulier pour l'action, `Marques-pages` pour le
 * panneau, et `Mes marques-pages` pour le titre de l'écran. Ce n'est pas une négligence à
 * corriger : le bouton du panneau et le titre de l'écran sont deux choses différentes, et
 * l'état vide renvoie à l'**action** — « Touche « Marque-page » » — donc au libellé du bouton
 * de la coquille. Uniformiser ferait diverger les deux clients à l'écran pour un gain nul.
 * Les trois constantes portent donc leur nom d'usage : [PLACE], [PANEL_TITLE], [TITLE].
 *
 * ## Une seule porte vers le mode de pose
 *
 * Le mode de pose ne s'active **jamais** directement depuis la coquille : il faut passer par
 * le panneau, qui propose exactement deux entrées — [PLACE] et [OPEN_LIST]. C'est ce que fait
 * le client d'origine, et c'est ce qui évite qu'un appui malencontreux sur la coquille fasse
 * entrer en mode de pose sans qu'on l'ait demandé.
 */
object BookmarksText {

    // --- Le panneau du lecteur ---------------------------------------------

    /** Le titre du panneau, tel qu'il s'affiche. */
    const val PANEL_TITLE: String = "Marques-pages"

    /** Première entrée du panneau : entrer en mode de pose. */
    const val PLACE: String = "Placer un marque-page sur un verset"

    /** Seconde entrée du panneau : ouvrir la liste. */
    const val OPEN_LIST: String = "Mes marques-pages"

    /**
     * La notice affichée pendant le mode de pose.
     *
     * Elle dit le geste exact à faire — toucher **le verset**, pas la page : un appui sur le
     * fond ne pose rien. C'est aussi ce que dit l'état vide de l'écran ([EMPTY]).
     */
    const val PLACE_NOTICE: String = "Touche le verset exact à enregistrer"

    /** La confirmation affichée après la pose. */
    const val SAVED_NOTICE: String = "Marque-page enregistré"

    /**
     * Durée d'affichage de [SAVED_NOTICE], en millisecondes.
     *
     * Elle est écrite ici, à côté du mot qu'elle gouverne, plutôt que dans la vue : la notice et
     * sa durée sont un seul comportement, et une durée réglée ailleurs se serait désynchronisée
     * du texte au premier changement. Le chiffre vient du `setTimeout(…, 2500)` du client
     * d'origine.
     */
    const val SAVED_NOTICE_MS: Long = 2500L

    // --- L'écran de la liste -----------------------------------------------

    /** Le titre de l'écran. */
    const val TITLE: String = "Mes marques-pages"

    /** Son sous-titre. */
    const val SUBTITLE: String = "Retrouve facilement tes passages enregistrés"

    /** La carte d'explication, en tête de liste. */
    const val EXPLANATION: String = "Chaque marque-page conserve le verset exact où reprendre ta lecture."

    /** L'état vide. Il renvoie au libellé du bouton, pas au titre du panneau. */
    const val EMPTY: String = "Aucun marque-page enregistré. Touche « Marque-page », puis un verset sur la page."

    /** La marque posée sur le signet le plus récemment repris. */
    const val LAST_USED: String = "Dernière reprise"

    /** Le bouton qui reprend la lecture à ce signet. */
    const val RESUME: String = "Reprendre"

    /** Le libellé du bouton de retour, lu par les lecteurs d'écran. */
    const val BACK: String = "Retour à la lecture"

    // --- La suppression -----------------------------------------------------

    const val DELETE_TITLE: String = "Supprimer ce marque-page ?"

    /**
     * Le corps de la confirmation.
     *
     * Il dit ce qui **n'arrive pas** — le verset reste dans le Coran — parce que c'est la
     * crainte réelle devant un bouton « Supprimer ». La suppression est de toute façon logique
     * (voir [Bookmarks.deleteBookmark]) : rien n'est perdu, et le message est exact.
     */
    const val DELETE_BODY: String = "Le verset restera disponible dans le Coran."

    const val DELETE_CANCEL: String = "Annuler"
    const val DELETE_CONFIRM: String = "Supprimer"

    // --- Libellés composés --------------------------------------------------

    /** Le repère d'une ligne : la page dans la source affichée, puis le verset. */
    fun pageLabel(page: Int, ayah: Int): String = "Page $page · Verset $ayah"

    /**
     * Le libellé lu par les lecteurs d'écran pour le bouton de suppression d'une ligne.
     *
     * Il nomme la sourate et le verset : « Supprimer » tout court, répété à chaque ligne,
     * ne dirait pas **quoi** il supprime.
     */
    fun deleteLabel(surahName: String, ayah: Int): String =
        "Supprimer le marque-page $surahName verset $ayah"
}
