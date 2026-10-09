package com.msoumaya.deepseekandroid.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.bandeauVisible
import com.msoumaya.deepseekandroid.core.domain.texteDuBandeau
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Bandeau de connectivité
// ---------------------------------------------------------------------------
// Portage du bandeau de `src/App.tsx`. L'original l'affiche en **premier enfant** de la vue
// racine, donc au-dessus de la barre supérieure et de tout écran — c'est sa place ici aussi, et
// `AppScaffold` le pose en tête de sa colonne.
//
// Trois choses méritent d'être dites, parce qu'elles ne se devinent pas :
//
//   1. **Le bandeau s'affiche pour deux raisons distinctes** : être hors ligne, ou venir de
//      l'être — dans ce second cas pendant trois secondes. Les deux textes ne sont donc pas deux
//      états du même message, et la règle est dans le domaine (`bandeauVisible`,
//      `texteDuBandeau`), pas ici.
//
//   2. **L'échéance se réarme.** Deux retours de réseau rapprochés ne doivent pas laisser deux
//      minuteurs vivants, dont le premier éteindrait le bandeau du second avant l'heure.
//      L'original écrit `clearTimeout(timer)` puis repose le minuteur ; ici, l'effet est
//      **claveté sur l'échéance elle-même** : une nouvelle échéance relance l'effet, et
//      l'ancien est annulé par Compose. Le minuteur orphelin est donc structurellement
//      impossible, comme il l'est dans le domaine.
//
//   3. **L'horloge est celle de l'observateur**, et pas une seconde horloge. L'échéance est un
//      instant de `ConnectivityObserver.horloge()` ; la lire ailleurs rendrait la question
//      « le bandeau est-il encore visible ? » indécidable dès que les deux diffèrent.
// ---------------------------------------------------------------------------

/**
 * Le bandeau d'état du réseau, ou rien.
 *
 * Il ne s'affiche que lorsque le domaine le dit : au repos, ce composable ne dessine rien du
 * tout, et n'occupe donc aucune place — un bandeau vide de hauteur nulle serait un écart de
 * mise en page invisible sur un appareil et visible sur un autre.
 *
 * @param container le conteneur de l'application. Paramètre et non `LocalAppContainer.current`,
 *   comme les autres routes : un test peut ainsi fournir un observateur à état fixé.
 */
@Composable
fun ConnectivityBanner(
    modifier: Modifier = Modifier,
    container: AppContainer = LocalAppContainer.current,
) {
    val etat by container.connectivity.state.collectAsStateWithLifecycle()
    val horloge = container.connectivity.horloge

    // L'instant courant, tenu à jour **à l'échéance seulement**. Le lire à chaque recomposition
    // ne servirait à rien : entre deux échéances, la réponse ne change pas.
    var maintenant by remember { mutableLongStateOf(horloge()) }

    LaunchedEffect(etat.retourJusquaMs) {
        val echeance = etat.retourJusquaMs ?: return@LaunchedEffect
        delay(maxOf(0L, echeance - horloge()))
        // Relire l'horloge plutôt que d'écrire `echeance` : si le fil a dormi plus longtemps que
        // prévu, c'est l'instant réel qui compte, et non celui qu'on avait calculé.
        maintenant = horloge()
    }

    if (!bandeauVisible(etat, maintenant)) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.soft)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            // Le `accessibilityLiveRegion="polite"` de l'original : le bandeau doit être annoncé
            // sans interrompre ce que le lecteur d'écran est en train de dire. Une coupure de
            // réseau est une information, pas une urgence.
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        AppLabel(
            text = texteDuBandeau(etat),
            modifier = Modifier.fillMaxWidth(),
            color = AppTheme.colors.muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}
