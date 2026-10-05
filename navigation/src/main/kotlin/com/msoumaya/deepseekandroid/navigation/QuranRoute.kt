package com.msoumaya.deepseekandroid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.data.QuranState
import com.msoumaya.deepseekandroid.core.data.quranFailureMessage
import com.msoumaya.deepseekandroid.core.domain.MushafSourceNavigation
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.ReaderMemory
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.feature.reader.QuranScreen
import kotlinx.coroutines.launch

/**
 * La route de l'écran « Coran » : la liste des sourates, celle des Juz', celle des Hizb.
 *
 * ## Pourquoi la route existe, et pourquoi l'écran ne pouvait pas la porter
 *
 * `feature:reader` ne déclare **aucune** dépendance à `core:data` : c'est ce qui permet au
 * lecteur de s'ouvrir en avion, et c'est écrit dans son `build.gradle.kts`. Cet écran appartient
 * au même module, donc il ne peut atteindre ni le conteneur, ni l'état du compte, ni le
 * référentiel. Il reçoit ce dont il a besoin et ne calcule rien d'autre — ce qui le rend
 * éprouvable sans conteneur. C'est exactement le découpage de `ReaderRoute`, et il est repris tel
 * quel plutôt que réinventé.
 *
 * ## Les deux états observés, et ce que chacun décide
 *
 *  - **le référentiel coranique** (`QuranState`) décide si l'écran est calculable. La liste des
 *    sourates se lit dans `Quran.surahs`, donc un référentiel encore vide ne rendrait pas une
 *    attente mais des **listes vides** — et une liste vide ressemble à une recherche sans
 *    résultat, pas à un chargement. Les deux ne se disent pas de la même façon, donc l'écran ne
 *    reçoit un état que lorsque le référentiel est prêt ;
 *  - **l'état du compte** porte les connaissances, la dernière lecture et la source affichée. Il
 *    vaut `null` tant que la lecture n'a rien publié, et l'écran l'affiche comme une attente.
 *
 * ## La carte « Coran avec règles de Tajwid »
 *
 * C'est le seul geste de cet écran qui **écrive**. Il adopte la source composée, puis ouvre le
 * lecteur à la page que cette source a mémorisée.
 *
 * ### L'écriture précède l'ouverture
 *
 * Même raison que pour la fermeture du lecteur, écrite dans `ReaderRoute` : la portée de cette
 * route meurt quand le lecteur la recouvre — le `NavHost` ne compose que la destination
 * courante. Ouvrir d'abord annulerait donc l'écriture en vol, le lecteur s'afficherait avec
 * l'ancienne source, et rien ne le dirait. L'écriture est enveloppée : un disque plein ne doit
 * pas rendre la carte inerte. Elle est **best-effort**, et c'est assumé — si elle échoue, le
 * lecteur s'ouvre sur la source précédente, sans message. Aucun écran du projet n'a de surface
 * d'erreur pour une écriture de réglage, et en inventer une ici serait inventer un comportement
 * que le client d'origine n'a pas.
 *
 * ### La page est traduite dans le découpage de la source adoptée
 *
 * **C'est ici que le portage corrige un défaut du client d'origine.** Sa carte écrivait
 * `mushaf:'coranTest'` puis ouvrait `pageRange(testPage)` — la fonction **sans source**, donc le
 * découpage du moushaf de Médine — alors que la source qu'elle venait d'adopter est la
 * composition typographique. Tout le reste du projet passe par `sourcePageRange(source,page)`,
 * qui route `coranTest` vers `testPageRange` ; cette carte était le seul appelant à ne pas le
 * faire.
 *
 * Le défaut n'est pas théorique, et il est mesuré sur les données livrées — les deux nombres sont
 * figés par `MushafSourceNavigationTest` : les deux découpages divergent sur **36 pages sur 604**,
 * et **56 versets sur 6 236** changent de page. La première divergence est la page 120, où le
 * moushaf s'arrête au verset 745 là où la composition va jusqu'à 746. Ouvrir la plage du mauvais
 * découpage pour la page 121 désigne donc le verset 746, qui appartient à la page **120** de la
 * composition : la carte ouvrait à côté de la page mémorisée, sur plus d'une trentaine d'endroits
 * du Mushaf.
 *
 * ### La page mémorisée vient du domaine, et non d'une lecture directe
 *
 * `ReaderMemory.openingPage` est la règle « à quelle page ouvrir cette source » : elle sait que la
 * source composée a **sa** mémoire propre — `testPage` — là où les autres sources projettent leur
 * page depuis le dernier verset lu. La relire ici à la main ferait une seconde règle, et les deux
 * finiraient par diverger.
 *
 * Une page hors bornes — un état restauré d'un autre client, ou écrit par une version antérieure —
 * ne rend pas la carte inerte : elle ouvre la première page, qui existe toujours. `openingPage`
 * ne borne pas volontairement (« c'est le lecteur qui borne »), donc c'est ici, au moment de la
 * traduire en plage, que la borne doit exister.
 *
 * @param onOpenReader ouvre le lecteur sur une plage. La route ne navigue pas elle-même : elle
 *   demande, et la coquille décide — c'est elle qui connaît la pile.
 * @param onEditKnowledge ouvre l'écran d'objectif, où les connaissances se modifient. Le crayon de
 *   la carte « J'ai appris jusqu'à » y mène ; la carte, elle, n'est pas cliquable, comme dans le
 *   client d'origine.
 * @param container le conteneur de l'application. Paramètre et non `LocalAppContainer.current`
 *   en dur : un appelant qui en passerait un autre verrait sinon l'écran lire un état et la route
 *   en écrire un second.
 */
@Composable
fun QuranRoute(
    onOpenReader: (Range) -> Unit,
    onEditKnowledge: () -> Unit,
    container: AppContainer = LocalAppContainer.current,
) {
    // Observés, et non lus une fois : la lecture du référentiel se termine peut-être après
    // l'ouverture de l'écran, et l'état du compte arrive du disque puis du serveur.
    val userState by container.userState.state.collectAsStateWithLifecycle(initialValue = null)
    val corpus by container.quranState.collectAsStateWithLifecycle()

    val failure = (corpus as? QuranState.Failed)?.let { quranFailureMessage(it.cause) }
    val state = if (corpus is QuranState.Ready) userState else null

    val scope = rememberCoroutineScope()

    QuranScreen(
        state = state,
        failure = failure,
        onOpen = onOpenReader,
        onEditKnowledge = onEditKnowledge,
        onSwitchToTest = {
            val page = userState
                ?.let { ReaderMemory.openingPage(it, MushafSource.CORAN_TEST) }
                ?: ReaderMemory.FIRST_PAGE
            val range = runCatching {
                MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, page)
            }.getOrElse {
                MushafSourceNavigation.pageRange(MushafSource.CORAN_TEST, ReaderMemory.FIRST_PAGE)
            }
            scope.launch {
                runCatching {
                    container.userState.mutate { Program.adoptComposedSource(it) }
                }
                onOpenReader(range)
            }
        },
    )
}
