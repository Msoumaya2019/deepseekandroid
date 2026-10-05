package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.SessionStatus
import com.msoumaya.deepseekandroid.core.model.StudyMode
import com.msoumaya.deepseekandroid.core.model.effectiveStudyProgress

/**
 * Jours d'activité, et série en cours.
 *
 * Porté depuis `activity` (`src/ui/MainScreens.tsx`). Cette fonction alimentait deux choses de
 * l'écran « Progrès » — les sept pastilles de régularité et le compteur « jours actifs » — et
 * elle vit ici plutôt que dans le composable pour la même raison que [ProgramText] : un
 * décompte faux d'un jour annonce une série qui n'existe pas, et cela ne se vérifie pas à l'œil.
 *
 * **Ce qu'est un jour actif.** Deux sources, et deux seulement :
 *
 *  - une **séance terminée** (`status = done`), datée par `completedDate`, à défaut par les dix
 *    premiers caractères de `completedAt`, à défaut par la date prévue ;
 *  - une **validation d'apprentissage**, c'est-à-dire une ligne de `studyProgress` en mode
 *    `learning`, datée par sa validation.
 *
 * Une séance de **révision** n'entre pas par la première porte : elle n'a pas de `status = done`
 * — les révisions se suivent par `revisions`, pas par `sessions`. C'est le comportement du client
 * d'origine, et il est conservé : la série mesure l'assiduité à l'apprentissage.
 *
 * **Pourquoi la série peut commencer hier.** La série part d'aujourd'hui ; si aujourd'hui est
 * vide, elle part d'**hier**. Sans ce repli, ouvrir l'application le matin afficherait « 0 jour
 * d'affilée » alors que la veille a été travaillée, et la personne croirait avoir perdu sa série.
 * Le repli ne va pas plus loin : deux jours vides d'affilée donnent bien zéro.
 *
 * **Le nom.** L'objet porte le nom de la règle d'origine (`activity`) et de ce qu'il mesure.
 * `core:domain` est du Kotlin/JVM pur, sans `android.app.Activity` : le seul fichier du dépôt qui
 * importe cette classe est `MainActivity`, dans `:app`, et il n'a pas besoin de cette règle. Un
 * futur fichier qui aurait besoin des deux le dira à la compilation, pas en silence.
 */
object Activity {

    /**
     * Résultat de [activity].
     *
     * @param dates les jours actifs, sans doublon et sans ordre garanti. Le nombre de jours
     *   actifs est `dates.size` ; c'est ce que l'écran affiche.
     * @param streak la longueur de la série qui se termine aujourd'hui ou hier, `0` si les deux
     *   sont vides.
     */
    data class Streak(val dates: Set<String>, val streak: Int)

    /**
     * Jours actifs et série en cours.
     *
     * @param at jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
     */
    fun activity(state: AppState, at: String = Dates.todayLocal()): Streak {
        val dates = LinkedHashSet<String>()

        for (session in state.sessions) {
            if (session.status != SessionStatus.DONE) continue
            dates += session.completedDate
                ?: session.completedAt?.take(10)
                ?: session.date
        }

        for (record in state.effectiveStudyProgress.values) {
            if (record.mode != StudyMode.LEARNING) continue
            for (validation in record.validations) dates += validation.date
        }

        var streak = 0
        var day = at
        if (day !in dates) day = Dates.addDays(day, -1)
        while (day in dates) {
            streak++
            day = Dates.addDays(day, -1)
        }

        return Streak(dates = dates, streak = streak)
    }
}
