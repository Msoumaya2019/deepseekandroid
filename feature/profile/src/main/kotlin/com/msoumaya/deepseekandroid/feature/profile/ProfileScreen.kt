package com.msoumaya.deepseekandroid.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.msoumaya.deepseekandroid.core.design.component.PhasePlaceholder

/**
 * Les quatre écrans d'outil que le dépôt d'origine rangeait sous un seul état `utilityView`.
 *
 * `ProfileScreen` recevait déjà un paramètre `mode` en React Native ; il est conservé sous forme
 * d'énumération plutôt que de chaîne, pour que les quatre valeurs soient connues du compilateur.
 * `APPEARANCE` et `GOAL` avaient leur propre fichier à l'origine (`AppearanceScreen`,
 * `GoalScreen`) : ils rejoindront leur module quand ils seront écrits, et cette énumération
 * perdra alors ses deux dernières valeurs.
 */
enum class ProfileMode {
    /** Le profil : identité, photo, déconnexion. */
    PROFILE,

    /** Les réglages : notifications, source du Coran, récitateur. */
    SETTINGS,

    /** L'apparence : thème, accent, police d'interface. */
    APPEARANCE,

    /** L'objectif d'apprentissage : sourates connues, rythme, objectif de la semaine. */
    GOAL,
}

/**
 * Écran `profile`, livré à la Phase D.
 *
 * Cet écran est un **emplacement assumé**, pas une maquette : il annonce ce qui viendra et ce
 * qui est déjà décidé. Il doit disparaître quand la Phase D livre le contenu réel, et
 * `ANDROID_MIGRATION.md` porte la ligne correspondante.
 *
 * @param mode laquelle des quatre sections afficher.
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    mode: ProfileMode = ProfileMode.PROFILE,
) {
    val (titre, description) = when (mode) {
        ProfileMode.PROFILE ->
            "Profil" to "Identité, prénom, photo, déconnexion."

        ProfileMode.SETTINGS ->
            "Réglages" to "Notifications, source du Coran, récitateur."

        ProfileMode.APPEARANCE ->
            "Apparence" to "Thème, accent et police d'interface."

        ProfileMode.GOAL ->
            "Mon objectif" to "Sourates connues, rythme d'apprentissage et objectif de la semaine."
    }

    PhasePlaceholder(
        title = titre,
        phase = "Phase D",
        description = description,
        modifier = modifier,
        details = listOf(
            "Les cinq thèmes et les quatre accents sont déjà portés et vérifiés",
            "Le thème choisi est enregistré dans `user_state.data`, donc partagé entre les appareils",
        ),
    )
}
