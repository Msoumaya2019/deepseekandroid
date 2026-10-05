package com.msoumaya.deepseekandroid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.domain.Bookmarks
import com.msoumaya.deepseekandroid.core.domain.QuranSourceReady
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.model.MushafPageSource
import com.msoumaya.deepseekandroid.feature.reader.EmbeddedMushafPages
import com.msoumaya.deepseekandroid.feature.reader.ReaderScreen
import com.msoumaya.deepseekandroid.feature.sources.QuranDownloadPanel
import com.msoumaya.deepseekandroid.feature.sources.QuranSourcePickerDialog
import com.msoumaya.deepseekandroid.feature.sources.QuranSourceViewModel
import com.msoumaya.deepseekandroid.feature.sources.archiveMushafPages
import kotlinx.coroutines.launch

/**
 * La route du lecteur : la porte, le lecteur, et le choix de présentation.
 *
 * ## La porte
 *
 * C'est le portage de la condition du client d'origine :
 *
 * ```
 * if (isZipSource(mushaf) && !quranDownloaded()) return <QuranDownload />
 * ```
 *
 * Une source en paquet dont l'installation n'est pas en place **remplace la page** par le
 * panneau de téléchargement. C'est ce qui rend le cas supportable : le paquet pèse 102 Mo et
 * ne peut pas être embarqué dans l'application, donc un état peut parfaitement désigner
 * « Coran 1441 » sans que ses images soient là — un état restauré d'un autre appareil, une
 * réinstallation, un nettoyage du cache. Afficher une page blanche à ce moment-là ferait
 * croire à une panne d'affichage, alors qu'il n'y a qu'un téléchargement à lancer.
 *
 * ## Ce qui n'est pas réparable ici, et qui est dit
 *
 * Le client d'origine ne vérifie que le **témoin** d'installation, pas le contenu. Une
 * installation complète dont une image aurait disparu du disque passerait donc la porte, et le
 * lecteur afficherait une page à laquelle il manque une bande. Ce n'est pas un oubli de
 * portage : c'est le comportement de l'original, et le réparer supposerait un bouton
 * « réinstaller » qui n'existe dans aucun des deux clients. `QuranSourceReady` sait le dire —
 * la vérification existe et est éprouvée — mais aucun écran ne le lui demande encore.
 *
 * ## Les marques de la page
 *
 * La route **observe** l'état du compte et en tire deux ensembles : les versets en signet,
 * et les versets marqués difficiles. Les calculer ici plutôt que dans le lecteur tient à ce
 * que le lecteur ne connaît ni le conteneur ni le stockage — c'est ce qui lui permet d'être
 * éprouvé sans eux. Les deux règles de calcul, elles, vivent dans `core:domain`.
 *
 * L'état est observé et non lu une fois : poser un signet depuis un autre écran doit se voir
 * sans rouvrir le lecteur.
 *
 * ## Les réglages d'écoute
 *
 * Ils sont relus du disque par le conteneur au démarrage, et cette route les **observe** : la
 * lecture peut aboutir après l'ouverture du lecteur, et la valeur publiée est alors adoptée —
 * voir `ReaderScreen`, qui cesse de l'adopter dès que la personne règle quelque chose.
 *
 * Chaque changement est écrit ici, et non dans le lecteur : c'est le seul endroit qui connaisse
 * le conteneur. L'écriture est lancée sur la portée de la route, donc elle meurt avec l'écran —
 * ce qui est le bon comportement pour un geste : elle ne doit pas survivre à ce qui l'a
 * déclenchée.
 *
 * @param onClose ferme le lecteur. La route ne décide pas où l'on retourne : elle le demande à
 *   la coquille, qui seule connaît la pile.
 */
@Composable
fun ReaderRoute(
    onClose: () -> Unit,
    container: AppContainer = LocalAppContainer.current,
    // La fabrique reçoit **le paramètre** `container`, pas `LocalAppContainer.current` : les deux
    // valent la même chose par défaut, mais un appelant qui en passerait un autre verrait sinon
    // la route lire ses pages dans un conteneur et son état dans un second. Deux sources de
    // vérité pour la même chose, et l'écart ne se verrait qu'à l'exécution.
    viewModel: QuranSourceViewModel = viewModel(
        factory = QuranSourceViewModel.factory(container),
    ),
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()

    // Observés, et non lus une fois : la relecture du document se termine peut-être après
    // l'ouverture du lecteur. Le lecteur adopte alors la valeur publiée tant que rien n'a été
    // réglé à la main — le comportement ne dépend donc pas d'une course.
    val storedSettings by container.audioSettings.settings.collectAsStateWithLifecycle()
    val storedReciterId by container.audioSettings.reciterId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // L'état du compte, observé. Il vaut `null` tant que la lecture n'a rien publié : un
    // état encore inconnu ne doit pas se confondre avec un état vide, mais les deux
    // dessinent la même page — aucun marqueur.
    val userState by container.userState.state.collectAsStateWithLifecycle(initialValue = null)
    val bookmarkIds = remember(userState) {
        userState?.let { Bookmarks.bookmarkedIds(it) } ?: emptySet()
    }
    val difficultIds = remember(userState) {
        userState?.let { Review.difficultIds(it) } ?: emptySet()
    }

    // La page est tenue ici, et non dans le lecteur : le choix de présentation en a besoin pour
    // vérifier que la page affichée existera encore dans l'autre source. Changer de
    // présentation ne doit pas ramener la personne à la page 1.
    var page by rememberSaveable { mutableIntStateOf(1) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    // La porte est une décision pure sur deux valeurs — la source et l'installation — et elle
    // est relue à chaque recomposition. C'est `readiness`, appelée ailleurs, qui touche le
    // disque ; celle-ci ne lit rien.
    val needsDownload = QuranSourceReady.needsDownload(source, downloaded)

    if (needsDownload) {
        // `onReady` ne change pas la source : elle est **déjà** celle-ci — c'est justement pour
        // cela que la porte s'affiche. L'écran basculera tout seul quand `downloaded` passera à
        // vrai, et `onBack` offre la sortie que le panneau seul n'aurait pas.
        QuranDownloadPanel(onReady = {}, onBack = onClose, viewModel = viewModel)
        return
    }

    // `container` figure parmi les clés : la valeur mémorisée en dépend, et un conteneur qui
    // changerait sans que la source change garderait sinon un fournisseur branché sur l'ancien.
    val pages: MushafPageSource = remember(source, container) {
        if (QuranSourceReady.isZipSource(source)) archiveMushafPages(container.archive) else EmbeddedMushafPages
    }

    ReaderScreen(
        initialPage = page,
        source = source,
        pages = pages,
        onClose = onClose,
        onPageChanged = { page = it },
        onOpenSourcePicker = { pickerOpen = true },
        initialSettings = storedSettings,
        initialReciterId = storedReciterId,
        onAudioSettingsChanged = { settings, reciterId ->
            scope.launch {
                // Un disque plein ne doit pas emporter le lecteur : le réglage est **déjà**
                // appliqué à la séance, et c'est ce que la personne voit. Seul son
                // enregistrement échoue, et le lecteur n'a rien à en faire — l'annoncer
                // supposerait un endroit où le dire, qui n'existe pas encore.
                runCatching { container.audioSettings.save(settings, reciterId) }
            }
        },
        bookmarkIds = bookmarkIds,
        difficultIds = difficultIds,
    )

    if (pickerOpen) {
        QuranSourcePickerDialog(
            onDismiss = { pickerOpen = false },
            currentPage = page,
            viewModel = viewModel,
        )
    }
}
