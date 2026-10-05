package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.Range
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Libellés du programme d'étude.
 *
 * Porté depuis les helpers de `src/ui/MainScreens.tsx` (`countText`) et de
 * `src/ui/StudySession.tsx` (les statuts d'une ligne de reprise), plus les quatre formats de
 * date du client d'origine.
 *
 * **Pourquoi ces libellés ne sont pas dans un composable.** « 5 versets », « mardi 10 mars
 * 2026 » et « À continuer » décident de ce que la personne croit lire : un compte faux d'un
 * verset annonce une séance qui n'existe pas, et un « À apprendre » posé sur une ligne entamée
 * contredit la progression enregistrée. Écrits dans un composable, ils ne s'éprouveraient qu'au
 * travers d'un test d'interface ; ici, ils se vérifient en une ligne.
 *
 * **Pourquoi les dates ne sont pas dans [Dates].** `Dates` porte de l'arithmétique — ajouter des
 * jours, compter un âge, comparer — et rien qui dépende de la langue. Une date « en toutes
 * lettres » est un texte d'interface : elle change avec la langue, pas avec le calendrier. Les
 * quatre formats viennent du client d'origine et sont reproduits à l'identique.
 */
object ProgramText {

    /**
     * Locale des formats, **fixée** et non celle de l'appareil.
     *
     * Le client d'origine demande explicitement `fr-FR` à `toLocaleDateString`, il ne prend pas
     * la locale du téléphone : une date resterait donc française sur un appareil réglé en
     * anglais. Reproduire ce choix est le seul moyen que les deux clients affichent la même
     * chose pour le même état.
     */
    private val FR = Locale.FRANCE

    /** « mardi 10 mars 2026 ». */
    private val LONG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", FR)

    /** « mardi 10 mars ». */
    private val MEDIUM = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)

    /** « mar. » — mis en majuscules par [shortWeekday]. */
    private val SHORT_WEEKDAY = DateTimeFormatter.ofPattern("EEE", FR)

    /** « 12 mars ». */
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", FR)

    /**
     * Libellé du nombre de versets d'une plage.
     *
     * « 1 verset » et non « 1 versets » : le client d'origine distingue les deux, et un pluriel
     * fautif se voit sur chaque carte de tâche.
     */
    fun verseCount(range: Range): String {
        val count = range.end - range.start + 1
        return "$count verset${if (range.end == range.start) "" else "s"}"
    }

    /** Date en toutes lettres, pour la carte du jour : « mardi 10 mars 2026 ». */
    fun longDate(key: String): String = format(key, LONG)

    /** Date sans l'année, pour la prochaine séance : « mardi 10 mars ». */
    fun mediumDate(key: String): String = format(key, MEDIUM)

    /** Initiale du jour, en majuscules, pour la pastille d'une séance : « MAR. ». */
    fun shortWeekday(key: String): String = format(key, SHORT_WEEKDAY).uppercase(FR)

    /**
     * Trois premières lettres du jour, en minuscules, pour une barre de graphique : « lun ».
     *
     * C'est le seul format qui **tronque** au lieu de formater, parce que le client d'origine le
     * fait ainsi : il demande le jour abrégé à `fr-FR` (« lun. ») puis en garde trois caractères.
     * Les sept jours français s'écrivent en trois lettres suivies d'un point, la troncature est
     * donc sans perte — et la reproduire évite d'inventer un format que l'original n'a pas.
     */
    fun weekdayInitials(key: String): String = format(key, SHORT_WEEKDAY).take(3)

    /** Jour et mois, pour une séance lointaine : « 12 mars ». */
    fun dayMonth(key: String): String = format(key, DAY_MONTH)

    /**
     * Statut d'une ligne du détail d'une reprise.
     *
     * Trois états et deux modes, donc cinq mots. Le mot suit le **mode** : une ligne entamée
     * d'une révision est « À continuer » comme celle d'un apprentissage, mais une ligne faite
     * est « Appris » ou « Révisée » selon ce qu'on était en train de faire.
     *
     * L'ordre des cas n'est pas interchangeable : une ligne **faite** ne peut pas être entamée,
     * et les deux drapeaux peuvent être vrais en même temps si l'appelant les calcule mal. Le
     * fait l'emporte.
     */
    fun rowStatus(done: Boolean, partial: Boolean, learning: Boolean): String = when {
        done -> if (learning) "Appris" else "Révisée"
        partial -> "À continuer"
        learning -> "À apprendre"
        else -> "À réviser"
    }

    /**
     * Met une clé `AAAA-MM-JJ` en forme, ou la rend **telle quelle** si ce n'en est pas une.
     *
     * Le repli est délibéré. Le client d'origine rendrait « Invalid Date » à l'écran ; ici, une
     * clé inattendue s'affiche telle qu'elle est arrivée. C'est plus laid et plus utile : la
     * valeur fautive reste lisible, au lieu d'être remplacée par un mot qui ne dit rien de ce
     * qui n'allait pas.
     */
    private fun format(key: String, formatter: DateTimeFormatter): String =
        runCatching { Dates.parse(key).format(formatter) }.getOrDefault(key)
}
