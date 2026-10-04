package com.msoumaya.deepseekandroid.core.domain

/**
 * Les lignes de la feuille « Plus d'options » du lecteur.
 *
 * Porté depuis `src/ui/ReaderMoreSheet.tsx`. La feuille est le **carrefour** du lecteur : c'est
 * de là qu'on change de sourate, qu'on affiche la traduction, qu'on règle l'écoute et qu'on
 * choisit la présentation des pages. Le bouton « ⋯ » de la coquille l'ouvre, et c'est le seul
 * chemin vers le sélecteur de sourate dans le client d'origine.
 *
 * ## Une ligne dont la destination n'existe pas n'est pas affichée
 *
 * Le client d'origine propose toujours les quatre lignes, parce que ses quatre écrans existent.
 * Ici, la traduction française n'est pas encore écrite. Afficher une ligne qui ne mène nulle
 * part ferait douter du reste de l'écran — c'est le principe déjà tenu par la coquille et par
 * le mini-lecteur. [visible] **filtre** donc au lieu de griser, et l'ordre du client d'origine
 * est conservé pour les lignes qui restent.
 *
 * ## Ce que cette règle ne décide pas
 *
 * Elle ne dit pas quelles destinations sont branchées : c'est l'appelant qui le sait, et il les
 * passe à [visible]. La règle ne fait que traduire cet ensemble en lignes, dans le bon ordre.
 */
object ReaderOptionsText {

    /** Le titre de la feuille, apostrophe typographique comprise — c'est celle de l'original. */
    const val TITLE: String = "Plus d’options"

    /** Le libellé du bouton de fermeture, lu par les lecteurs d'écran. */
    const val CLOSE: String = "Fermer les options"

    /** Les quatre destinations que la feuille sait proposer. */
    enum class Action {
        /** Changer de sourate, ou aller directement à une page. */
        SURAH,

        /** Afficher ou masquer la traduction française de la page. */
        TRANSLATION,

        /** Récitateur, nombre d'écoutes, silence et vitesse. */
        AUDIO,

        /** Choisir la présentation des pages du Coran. */
        DISPLAY,
    }

    /** Une ligne de la feuille, telle qu'elle s'affiche. */
    data class Row(val action: Action, val title: String, val subtitle: String)

    /**
     * Les quatre lignes connues, **dans l'ordre du client d'origine**.
     *
     * Cet ordre n'est pas indifférent : il va du plus fréquent au plus rare, et le réordonner
     * changerait les habitudes prises sur l'application existante.
     */
    val ALL: List<Row> = listOf(
        Row(Action.SURAH, "Changer de sourate", "Choisir une sourate ou aller à une page"),
        Row(Action.TRANSLATION, "Traduction française", "Afficher / masquer la traduction"),
        Row(Action.AUDIO, "Réglages audio", "Récitateur, répétitions et vitesse"),
        Row(Action.DISPLAY, "Affichage du Coran", "Choisir le style, la taille et les options"),
    )

    /**
     * Les lignes à afficher, dans l'ordre du client d'origine.
     *
     * @param available les destinations réellement branchées par l'appelant. Une destination
     *   absente de cet ensemble voit sa ligne **retirée**, et non désactivée : une ligne grisée
     *   laisse croire que l'écran existe mais qu'il est momentanément indisponible.
     */
    fun visible(available: Set<Action>): List<Row> = ALL.filter { it.action in available }

    /**
     * `true` si la feuille a au moins une ligne à proposer.
     *
     * Sert à ne pas l'ouvrir du tout quand rien n'y mène : une feuille vide, ou réduite à son
     * titre et à sa poignée, serait une impasse.
     */
    fun isUseful(available: Set<Action>): Boolean = visible(available).isNotEmpty()
}
