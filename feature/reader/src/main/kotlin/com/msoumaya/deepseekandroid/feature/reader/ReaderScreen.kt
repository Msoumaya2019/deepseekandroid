package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.msoumaya.deepseekandroid.core.design.component.PhasePlaceholder

/**
 * Écran `reader`, livré à la Phase B.
 *
 * Cet écran est un **emplacement assumé**, pas une maquette : il annonce ce qui viendra et ce
 * qui est déjà décidé. Il doit disparaître quand la Phase B livre le contenu réel, et
 * `ANDROID_MIGRATION.md` porte la ligne correspondante.
 */
@Composable
fun ReaderScreen(modifier: Modifier = Modifier) {
    PhasePlaceholder(
        title = "Lecteur de moushaf",
        phase = "Phase B",
        description = "Le lecteur de Coran, priorité absolue du cahier des charges : pages centrées, balayage fluide, préchargement de la page voisine, audio verset par verset.",
        modifier = modifier,
        details = listOf(
            "Sources : Coran 1441 et Coran de Médine en premier",
            "Préchargement limité à la page précédente, courante et suivante — jamais tout le moushaf en mémoire",
            "Les pages ne sont jamais déformées : aucun ratio imposé",
            "Audio par Media3, avec un mini-lecteur discret",
            "604 pages attendues ; 28 sont présentes pour le développement",
        ),
    )
}
