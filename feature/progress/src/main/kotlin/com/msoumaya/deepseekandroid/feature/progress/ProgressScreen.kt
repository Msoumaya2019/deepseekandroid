package com.msoumaya.deepseekandroid.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.msoumaya.deepseekandroid.core.design.component.PhasePlaceholder

/**
 * Écran `progress`, livré à la Phase C.
 *
 * Cet écran est un **emplacement assumé**, pas une maquette : il annonce ce qui viendra et ce
 * qui est déjà décidé. Il doit disparaître quand la Phase C livre le contenu réel, et
 * `ANDROID_MIGRATION.md` porte la ligne correspondante.
 */
@Composable
fun ProgressScreen(modifier: Modifier = Modifier) {
    PhasePlaceholder(
        title = "Ma progression",
        phase = "Phase C",
        description = "Progression, révisions, consolidations et versets difficiles.",
        modifier = modifier,
        details = listOf(
            "Cycles de révision à 7, 14, 21 et 30 jours, plus 1 Nisf, 1 Hizb, 1 Juz et 2 Juz",
            "Consolidation à J+1, J+3 et J+7, sur un passage cliquable",
            "Un verset marqué difficile le reste jusqu'à ce que l'utilisateur le retire",
        ),
    )
}
