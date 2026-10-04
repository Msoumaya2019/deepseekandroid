package com.msoumaya.deepseekandroid.feature.social

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.msoumaya.deepseekandroid.core.design.component.PhasePlaceholder

/**
 * Écran `social`, livré à la Phase D.
 *
 * Cet écran est un **emplacement assumé**, pas une maquette : il annonce ce qui viendra et ce
 * qui est déjà décidé. Il doit disparaître quand la Phase D livre le contenu réel, et
 * `ANDROID_MIGRATION.md` porte la ligne correspondante.
 */
@Composable
fun SocialScreen(modifier: Modifier = Modifier) {
    PhasePlaceholder(
        title = "Mes amis",
        phase = "Phase D",
        description = "Liste d'amis, demandes, profils, messages, partages et défis.",
        modifier = modifier,
        details = listOf(
            "Réutilise les tables `friend_*` du projet Supabase existant",
            "Aucune table ni colonne nouvelle n'est créée pour l'application Android",
            "Le même `user_id` que le client React Native, donc les mêmes amis",
        ),
    )
}
