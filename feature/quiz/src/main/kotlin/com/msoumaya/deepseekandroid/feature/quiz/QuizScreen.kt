package com.msoumaya.deepseekandroid.feature.quiz

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.msoumaya.deepseekandroid.core.design.component.PhasePlaceholder

/**
 * Écran `quiz`, livré à la Phase D.
 *
 * Cet écran est un **emplacement assumé**, pas une maquette : il annonce ce qui viendra et ce
 * qui est déjà décidé. Il doit disparaître quand la Phase D livre le contenu réel, et
 * `ANDROID_MIGRATION.md` porte la ligne correspondante.
 */
@Composable
fun QuizScreen(modifier: Modifier = Modifier) {
    PhasePlaceholder(
        title = "Quiz",
        phase = "Phase D",
        description = "Question du jour et défis asynchrones entre amis.",
        modifier = modifier,
        details = listOf(
            "Question du jour : une seule participation, réponse verte ou rouge, explication et source",
            "Défi : 5 ou 10 questions, 10 par défaut",
            "Les deux joueurs reçoivent exactement les mêmes questions",
        ),
    )
}
