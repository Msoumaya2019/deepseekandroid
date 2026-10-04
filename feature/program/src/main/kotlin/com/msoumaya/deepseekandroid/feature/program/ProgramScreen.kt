package com.msoumaya.deepseekandroid.feature.program

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.msoumaya.deepseekandroid.core.design.component.PhasePlaceholder

/**
 * Écran `program`, livré à la Phase C.
 *
 * Cet écran est un **emplacement assumé**, pas une maquette : il annonce ce qui viendra et ce
 * qui est déjà décidé. Il doit disparaître quand la Phase C livre le contenu réel, et
 * `ANDROID_MIGRATION.md` porte la ligne correspondante.
 */
@Composable
fun ProgramScreen(modifier: Modifier = Modifier) {
    PhasePlaceholder(
        title = "Mon programme",
        phase = "Phase C",
        description = "Le programme calculé depuis l'objectif : séance du jour, séances à venir, rattrapage, historique.",
        modifier = modifier,
        details = listOf(
            "`scheduledDate` et `completedAt` sont deux champs distincts : une séance prévue mardi mais faite lundi garde sa date prévue",
            "Les 10 prochains jours seulement sont affichés ; rien n'est supprimé au-delà",
            "Une nouvelle semaine repart à 0 % sans effacer l'historique",
        ),
    )
}
