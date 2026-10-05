package com.msoumaya.deepseekandroid.core.domain

/**
 * Les mots de l'écran d'objectif.
 *
 * Porté depuis `src/ui/GoalScreen.tsx`. Les chaînes sont reprises **caractère pour caractère** :
 * ce sont des textes d'interface validés par le propriétaire du projet. Les apostrophes
 * typographiques (`’`) et le signe du rub‘ (`‘`) en font partie — les remplacer par des
 * apostrophes droites serait une faute de portage, pas une simplification.
 *
 * ## Pourquoi les mots sont ici, et non dans l'écran
 *
 * C'est la règle du dépôt : un mot d'interface se relit, il ne se réécrit pas. L'écran d'objectif
 * compose ses libellés — « Finir le Juz’ 30 » — à partir de la valeur choisie, et cette
 * composition est une règle : `finishDivision` reçoit l'unité **telle qu'elle s'affiche**, pour
 * qu'un libellé d'unité changé ici se propage partout au lieu de rester figé dans une phrase.
 */
object GoalText {

    // --- La bande d'en-tête -------------------------------------------------

    /** Le titre de l'écran. */
    const val TITLE: String = "Mon objectif"

    /** Le sous-titre de la bande d'en-tête. */
    const val SUBTITLE: String = "Définis ce que tu connais déjà et ce que tu veux atteindre."

    // --- Carte « Je connais déjà » ------------------------------------------

    /** Le titre de la carte des connaissances. */
    const val KNOWN_TITLE: String = "Je connais déjà"

    /** Ce que la carte dit quand rien n'est encore validé, et qu'on n'a rien saisi. */
    const val NOTHING_KNOWN: String = "Aucun verset validé pour le moment."

    /** Le libellé du champ quand l'unité est « Sourate ». */
    const val LAST_SURAH: String = "Dernière sourate apprise"

    /** Le libellé du second champ quand l'unité est « Sourate ». */
    const val LAST_VERSE: String = "Dernier verset appris"

    /**
     * Le libellé du champ unique pour les deux autres unités.
     *
     * @param unit l'unité **telle qu'elle s'affiche** — « Hizb » ou « Juz’ ».
     */
    fun lastDivision(unit: String): String = "Dernier $unit appris"

    /** La note qui explique que les connaissances ne sont pas écrasées. */
    const val KNOWN_NOTE: String =
        "Les connaissances existantes sont conservées. Pour gérer des passages séparés, " +
            "utilise les options avancées."

    // --- Carte « Mon objectif » ---------------------------------------------

    /** Le titre de la carte de l'objectif. Le même mot que le titre de l'écran, et c'est voulu. */
    const val GOAL_TITLE: String = "Mon objectif"

    /** Le libellé du champ de choix de l'objectif. */
    const val GOAL_FIELD: String = "Objectif"

    /**
     * L'option d'objectif pour une **sourate** : elle porte son nom, pas son numéro.
     *
     * @param name le nom de la sourate, tel que le référentiel le donne.
     */
    fun finishSurah(name: String): String = "Finir $name"

    /**
     * L'option d'objectif pour un **hizb** ou un **juz’**.
     *
     * @param unit l'unité telle qu'elle s'affiche.
     * @param number le numéro de la division.
     */
    fun finishDivision(unit: String, number: Int): String = "Finir le $unit $number"

    /**
     * Le rappel de l'objectif enregistré, affiché tant qu'on n'a rien changé.
     *
     * @param label le libellé persisté de l'objectif.
     */
    fun currentGoal(label: String): String = "Objectif actuel : $label"

    /** Le libellé du champ d'échéance. */
    const val DEADLINE: String = "Échéance"

    /** Le premier choix d'échéance : aucune date. */
    const val NO_DEADLINE: String = "Sans date"

    /** Le second choix d'échéance : on en choisit une. */
    const val PICK_DATE: String = "Choisir une date"

    /** Le texte d'aide du champ de date. */
    const val DATE_PLACEHOLDER: String = "AAAA-MM-JJ"

    /** La note qui dit à quoi sert la date, et à quoi elle ne sert pas. */
    const val DEADLINE_NOTE: String =
        "La date est un repère ; les séances sont calculées selon ton rythme."

    /** Le refus d'une date mal formée. */
    const val BAD_DATE: String = "Indique une date valide au format AAAA-MM-JJ."

    // --- Carte « Mon rythme » -----------------------------------------------

    /** Le titre de la carte du rythme. */
    const val PACE_TITLE: String = "Mon rythme"

    /**
     * Ce que la carte annonce comme rythme.
     *
     * @param label le libellé du rythme, pris à [Texts.paceLabels].
     */
    fun paceLine(label: String): String = "$label / jour"

    /** Le libellé vocal du bouton qui réduit le rythme. */
    const val PACE_DOWN: String = "Réduire le rythme"

    /** Le libellé vocal du bouton qui augmente le rythme. */
    const val PACE_UP: String = "Augmenter le rythme"

    // --- Carte « Programme généré » -----------------------------------------

    /** Le titre de la carte de l'aperçu. */
    const val PREVIEW_TITLE: String = "Programme généré"

    /** La note qui dit d'où l'aperçu est tiré. */
    const val PREVIEW_NOTE: String =
        "Selon ton objectif, ton rythme et tes jours d’apprentissage."

    /** Ce que l'aperçu annonce quand le programme n'a plus de séance à faire. */
    const val GOAL_REACHED: String = "Objectif atteint"

    /** Le bouton qui enregistre le programme. */
    const val SAVE: String = "Enregistrer mon programme"

    // --- Les trois unités ---------------------------------------------------

    /** L'unité « Sourate ». */
    const val SURAH: String = "Sourate"

    /** L'unité « Hizb ». */
    const val HIZB: String = "Hizb"

    /** L'unité « Juz’ », avec son apostrophe typographique. */
    const val JUZ: String = "Juz’"

    /** Les trois unités, dans l'ordre du sélecteur du client d'origine. */
    val units: List<String> = listOf(SURAH, HIZB, JUZ)

    // --- Les trois unités de rythme -----------------------------------------

    /** Le rythme exprimé en pages. */
    const val PER_PAGE: String = "Par page"

    /** Le rythme exprimé en versets. */
    const val PER_VERSE: String = "Par verset"

    /** Le rythme exprimé en rub‘. */
    const val PER_RUBU: String = "Par rubu‘"

    /** Les trois unités de rythme, dans l'ordre du sélecteur du client d'origine. */
    val paceUnits: List<String> = listOf(PER_PAGE, PER_VERSE, PER_RUBU)
}
