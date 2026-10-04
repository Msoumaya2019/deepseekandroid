package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Écran d'attente
// ---------------------------------------------------------------------------
// Un écran dont le contenu viendra plus tard doit le dire, et dire **quand**. Un écran vide
// laisserait croire à une panne ; une maquette figée laisserait croire que c'est terminé.
//
// Ce composant est temporaire par construction : chaque appel doit disparaître quand la phase
// correspondante livre l'écran. `ANDROID_MIGRATION.md` porte la liste de ces écrans.
// ---------------------------------------------------------------------------

/**
 * Écran annonçant qu'une fonctionnalité arrive à une phase ultérieure.
 *
 * @param title nom de la fonctionnalité, tel qu'il apparaîtra dans l'application.
 * @param phase phase du plan de travail qui la livrera (« Phase B », « Phase C »…).
 * @param description ce que l'écran fera, en une phrase.
 * @param details points déjà décidés — ce qui est écrit là est vérifiable dans le dépôt, pas une
 *   promesse en l'air.
 */
@Composable
fun PhasePlaceholder(
    title: String,
    phase: String,
    description: String,
    modifier: Modifier = Modifier,
    details: List<String> = emptyList(),
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.xl, vertical = spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppTitle(text = title, maxLines = Int.MAX_VALUE, color = colors.green)
        Spacer(Modifier.height(spacing.sm))
        AppLabel(
            text = phase,
            selectable = false,
            fontSize = AppTheme.typeScale.secondary,
            color = colors.paper,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(colors.green, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(spacing.lg))
        AppLabel(
            text = description,
            selectable = false,
            color = colors.muted,
            textAlign = TextAlign.Center,
        )

        if (details.isNotEmpty()) {
            Spacer(Modifier.height(spacing.lg))
            AppCard {
                AppSectionTitle(text = "Déjà décidé")
                Spacer(Modifier.height(spacing.sm))
                details.forEach { ligne ->
                    AppLabel(
                        text = "• $ligne",
                        selectable = false,
                        fontSize = AppTheme.typeScale.secondary,
                        color = colors.muted,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
