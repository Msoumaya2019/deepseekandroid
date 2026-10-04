package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.msoumaya.deepseekandroid.core.design.component.PhasePlaceholder

/**
 * L'onglet « Coran » : la liste des sourates, livrée à la Phase B.
 *
 * Ce n'est **pas** le lecteur. Le dépôt d'origine a deux écrans distincts que leurs noms ne
 * distinguent pas :
 *
 *   `QuranScreen`   la liste — recherche, filtre Mecquoise/Médinoise, bascule Liste / Juz' / Hizb
 *   `ReaderScreen`  le moushaf page à page, en plein écran
 *
 * Le premier ouvre le second. Les deux vivent dans `feature:reader` parce qu'ils partagent la
 * même donnée de référence — la correspondance verset / sourate / page — et la séparer aurait
 * obligé à la charger deux fois.
 */
@Composable
fun QuranScreen(modifier: Modifier = Modifier) {
    PhasePlaceholder(
        title = "Le Coran",
        phase = "Phase B",
        description = "Liste des sourates, recherche, filtre par lieu de révélation, et bascule Liste / Juz' / Hizb.",
        modifier = modifier,
        details = listOf(
            "La correspondance verset / sourate / page / juz' / hizb est déjà portée et testée (132 tests)",
            "Les 114 sourates, 30 juz' et 60 hizb sont décrits en données, pas en dur dans l'écran",
            "Le moushaf de Médine est embarqué ; 28 pages sur 604 sont présentes à ce jour",
        ),
    )
}
