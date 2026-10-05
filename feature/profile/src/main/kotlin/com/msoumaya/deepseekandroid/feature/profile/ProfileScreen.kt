package com.msoumaya.deepseekandroid.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.msoumaya.deepseekandroid.core.design.component.PhasePlaceholder

/**
 * Les écrans d'outil que le dépôt d'origine rangeait sous un seul état `utilityView`.
 *
 * `ProfileScreen` recevait déjà un paramètre `mode` en React Native ; il est conservé sous forme
 * d'énumération plutôt que de chaîne, pour que les valeurs soient connues du compilateur.
 * `APPEARANCE` avait son propre fichier à l'origine (`AppearanceScreen`) : il rejoindra son module
 * quand il sera écrit, et cette énumération perdra alors sa dernière valeur.
 *
 * `GOAL` a disparu le jour où l'écran d'objectif a été livré : il a désormais son propre fichier —
 * `GoalScreen.kt` —, et la route `AppRoutes.GOAL` le sert directement. Une valeur d'énumération
 * qu'aucune route n'atteint serait un branchement mort.
 */
enum class ProfileMode {
    /** Le profil : identité, photo, déconnexion. */
    PROFILE,

    /** Les réglages : notifications, source du Coran, récitateur. */
    SETTINGS,

    /** L'apparence : thème, accent, police d'interface. */
    APPEARANCE,
}

/**
 * Écran `profile`, livré à la Phase D.
 *
 * Cet écran est un **emplacement assumé**, pas une maquette : il annonce ce qui viendra et ce
 * qui est déjà décidé. Il doit disparaître quand la Phase D livre le contenu réel, et
 * `ANDROID_MIGRATION.md` porte la ligne correspondante.
 *
 * @param mode laquelle des trois sections restantes afficher.
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
