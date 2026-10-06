package com.msoumaya.deepseekandroid.feature.progress

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppHeading
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Le bloc « Quiz » de l'écran « Progrès »
// ---------------------------------------------------------------------------
// Portage de `QuizStats` (`src/ui/QuizScreen.tsx:15`).
//
// **Pourquoi un fichier à part.** Le bloc vit à l'écran « Progrès », mais il ne compte rien : les
// trois lignes arrivent déjà écrites dans [QuizSummary], calculées par `ProgressRenderer` à partir
// de `Quiz.statistics`. Ce fichier ne pose donc que trois `AppLabel` et un titre — le séparer de
// `ProgressScreen.kt` évite d'y ajouter quatre imports pour un bloc qui se lit seul.
//
// **La hiérarchie est celle de l'original, et elle compte.** Le titre est une `Heading` de 19, la
// première ligne est du texte courant de 13, la deuxième est de 12 en gris, la troisième de 12
// sans gris. Replier les trois en une seule chaîne ferait perdre ces trois niveaux — et c'est
// exactement ce qui rend le bloc lisible d'un coup d'œil.
//
// **Les quatre espacements viennent de la source** (`marginTop:7`, `5` et `8`) et ne suivent pas
// l'échelle du thème : 7 et 5 n'y existent pas. Les remplacer par `AppTheme.spacing.sm` (8) et
// `xs` (4) rapprocherait les lignes sans que rien ne le signale.
// ---------------------------------------------------------------------------

/**
 * Le bloc « Quiz » : un titre et trois lignes de cumul.
 *
 * Il est composé par l'écran seulement quand l'état porte un résumé — voir
 * [ProgressUiState.quiz]. Sans instantané lu, il n'y a rien à compter, et le bloc disparaît
 * plutôt que d'annoncer des zéros.
 *
 * @param summary les trois lignes, déjà mises en forme.
 */
@Composable
internal fun QuizStats(summary: QuizSummary, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors

    AppCard(modifier = modifier.padding(top = AppTheme.spacing.sm)) {
        AppHeading(text = summary.title, size = 19.sp)

        AppLabel(
            text = summary.daily,
            selectable = false,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 7.dp),
        )
        AppLabel(
            text = summary.rate,
            selectable = false,
            fontSize = 12.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 5.dp),
        )
        AppLabel(
            text = summary.challenges,
            selectable = false,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
