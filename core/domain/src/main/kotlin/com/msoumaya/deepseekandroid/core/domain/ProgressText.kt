package com.msoumaya.deepseekandroid.core.domain

/**
 * Libellés de l'écran « Progrès ».
 *
 * Porté depuis `ProgressScreen` (`src/ui/MainScreens.tsx`). Comme [ProgramText], ces chaînes
 * vivent dans le domaine et non dans le composable : « Versets appris ce mois » ou « 3 jours
 * d'affilée » disent à la personne ce qu'elle a fait, et un mot faux y est une affirmation fausse
 * sur son propre travail. Écrites ici, elles se vérifient en une ligne.
 *
 * **Une correction assumée.** Le client d'origine écrit `` `${streak} jours d’affilée` `` — au
 * pluriel quelle que soit la valeur, donc « 1 jours d’affilée » le premier jour. Son propre écran
 * d'accueil, lui, accorde (`jour{s>1?'s':''}`) : l'incohérence est dans le dépôt d'origine, et
 * c'est l'accord qui est conservé. Zéro reste au singulier, comme le veut l'usage français et
 * comme le fait déjà l'accueil.
 */
object ProgressText {

    /**
     * Période observée.
     *
     * L'ordre est celui du client d'origine — `['Jour','Semaine','Mois']` — et il est **visible** :
     * c'est celui du sélecteur segmenté.
     */
    enum class Period(val label: String) {
        DAY("Jour"),
        WEEK("Semaine"),
        MONTH("Mois"),
    }

    const val TITLE = "Ma progression"
    const val SUBTITLE = "Suis ton évolution pas à pas."

    /** Sous le nombre de versets mémorisés, dans la carte de l'anneau. */
    const val MEMORIZED_VERSES = "versets mémorisés"

    const val REGULARITY = "Régularité"
    const val MEMORIZED_PAGES = "Pages mémorisées"
    const val REVISIONS_DONE = "Révisions faites"

    const val SHOW_GRAPH = "Voir le graphique de la période"
    const val HIDE_GRAPH = "Masquer le graphique"

    const val GOALS_TITLE = "Mes objectifs"
    const val SEE_ALL = "Voir tout"
    const val CURRENT_GOAL = "Objectif en cours"

    const val STATISTICS = "Statistiques"
    const val JUZ_DONE = "Juz’ complétés"
    const val ACTIVE_DAYS = "Jours actifs"
    const val PAGES_READ = "Pages lues"
    const val VERSES_MEMORIZED = "Versets mémorisés"

    /** Titre de la carte du nombre de versets appris, qui suit la période choisie. */
    fun learnedTitle(period: Period): String = when (period) {
        Period.DAY -> "Versets appris aujourd’hui"
        Period.WEEK -> "Versets appris cette semaine"
        Period.MONTH -> "Versets appris ce mois"
    }

    /**
     * Titre du graphique.
     *
     * Les trois libellés ne suivent pas le nom de la période : « Jour » décrit une fenêtre
     * glissante de sept jours, et « Mois » le mois calendaire en cours. C'est le texte du client
     * d'origine, et il est plus juste que le nom du sélecteur.
     */
    fun graphTitle(period: Period): String = when (period) {
        Period.DAY -> "Les 7 derniers jours"
        Period.WEEK -> "Cette semaine"
        Period.MONTH -> "Ce mois"
    }

    /** Libellé d'une barre du graphique mensuel : « S1 » … « S5 ». */
    fun weekLabel(index: Int): String = "S${index + 1}"

    /** Bouton du graphique, dans les deux états. */
    fun graphToggle(shown: Boolean): String = if (shown) HIDE_GRAPH else SHOW_GRAPH

    /** Pourcentage entier, comme le client d'origine : `Math.round` puis « % ». */
    fun percent(value: Int): String = "$value %"

    /**
     * Série de jours consécutifs.
     *
     * Voir la note de classe : le pluriel suit la valeur, et zéro reste au singulier.
     */
    fun streak(days: Int): String = "$days jour${if (days > 1) "s" else ""} d’affilée"

    /** Dénominateur du compteur de versets : « / 6236 ». */
    fun ofTotal(total: Int): String = "/ $total"
}
