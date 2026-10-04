package com.msoumaya.deepseekandroid

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.theme.DeepSeekTheme
import com.msoumaya.deepseekandroid.core.model.UiFont
import com.msoumaya.deepseekandroid.core.model.effectiveTheme
import com.msoumaya.deepseekandroid.core.model.effectiveUiFont
import com.msoumaya.deepseekandroid.feature.auth.AccountGate
import com.msoumaya.deepseekandroid.navigation.AppScaffold
import com.msoumaya.deepseekandroid.core.model.AppTheme as ThemeName

// ---------------------------------------------------------------------------
// Activité principale
// ---------------------------------------------------------------------------
// Il n'y a qu'une seule activité, et il n'y en aura pas d'autre : la navigation est interne.
// Une activité par écran multiplierait les points d'entrée et rendrait impossible le maintien
// de l'état du lecteur pendant une rotation.
//
// L'état du thème est lu **avant** de composer l'arbre, à un seul endroit : `DeepSeekTheme`
// pose les couleurs, et tout ce qui suit les lit. Le dépôt d'origine appliquait le thème en
// mutant un objet global (`applyTheme` faisait un `Object.assign` sur `colors`) au début du
// rendu de `App` — ce qui obligeait chaque composant à être re-rendu pour voir le changement,
// et rendait le thème invisible aux composants mémoïsés.
// ---------------------------------------------------------------------------

/** Point d'entrée de l'interface. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bord à bord : l'application dessine sous la barre d'état et sous la barre de
        // navigation. C'est ce que le lecteur de moushaf exige — une page qui ne va pas sous
        // les barres système perd plusieurs millimètres de hauteur utile, et c'est précisément
        // ce qui déforme ou rogne une page de moushaf.
        //
        // Les marges sont ensuite reposées explicitement, écran par écran : la coquille absorbe
        // la barre d'état, la barre d'onglets absorbe la barre de navigation gestuelle.
        enableEdgeToEdge()

        val container = (application as DeepSeekApplication).container

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                DeepSeekRoot(activity = this, container = container)
            }
        }
    }
}

/**
 * Applique le thème enregistré puis affiche la porte d'entrée.
 *
 * Le premier rendu se fait avec le thème blanc, parce que la préférence vit dans un fichier et
 * que la lire est une opération d'entrée-sortie. Attendre cette lecture avant d'afficher quoi
 * que ce soit donnerait un écran vide ; afficher d'abord puis corriger donne au pire un
 * changement de couleur, et seulement si l'utilisateur n'utilise pas le thème blanc.
 *
 * `AccountGate` **enveloppe** la coquille au lieu d'être appelée depuis elle : aucun écran de
 * l'application ne peut donc être atteint sans compte, et l'ordre est visible d'ici.
 */
@Composable
private fun DeepSeekRoot(activity: Activity, container: AppContainer) {
    val state by container.userState.state.collectAsStateWithLifecycle(initialValue = null)
    val theme = state?.effectiveTheme ?: ThemeName.WHITE
    val isDark = theme == ThemeName.NIGHT

    SystemBars(activity = activity, isDark = isDark)

    DeepSeekTheme(
        theme = theme,
        accent = state?.accent,
        uiFont = state?.effectiveUiFont ?: UiFont.ELEGANT,
    ) {
        AccountGate {
            AppScaffold(firstName = state?.profile?.firstName)
        }
    }
}

/**
 * Couleur des icônes des barres système.
 *
 * Le dépôt d'origine imposait `dark-content` en toutes circonstances, parce que son application
 * est déclarée en mode clair (`userInterfaceStyle: "light"` dans `app.json`) — mais elle laisse
 * l'utilisateur choisir un thème sombre, et les icônes y devenaient invisibles. Ici la couleur
 * suit le thème : c'est un écart assumé, et le seul endroit où le portage corrige un défaut
 * visible plutôt que de le reproduire.
 */
@Composable
private fun SystemBars(activity: Activity, isDark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}
