package com.msoumaya.deepseekandroid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.Bookmarks
import com.msoumaya.deepseekandroid.core.domain.QuranSourceReady
import com.msoumaya.deepseekandroid.core.domain.ReciterPreference
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.VerseActionsText
import com.msoumaya.deepseekandroid.core.model.AudioPreferences
import com.msoumaya.deepseekandroid.core.model.MushafPageSource
import com.msoumaya.deepseekandroid.feature.reader.BookmarksScreen
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
 * ## Le marquage, et la distinction qui le gouverne
 *
 * Le panneau des actions d'un verset propose de le marquer difficile, ou de le retirer. Le
 * mot du bouton suit le marqueur de l'**élève** seul, tandis que la teinte de la page suit les
 * deux origines : ce sont deux questions différentes, et `VerseActionsText` les sépare. La
 * route fournit les deux ensembles, et n'en confond aucun.
 *
 * ## L'écran des signets, et pourquoi il ne remplace pas le lecteur
 *
 * Le client d'origine remplace le lecteur par l'écran des signets. Ici, l'écran est posé
 * **par-dessus** : le lecteur reste monté, donc la séance d'écoute en cours n'est pas
 * interrompue parce qu'on consulte ses marques-pages. Le prix est une fenêtre au lieu d'un
 * écran ; le gain est une lecture qui continue, et c'est celui qui compte.
 *
 * La page de reprise suit le **découpage de la source affichée** : les deux découpages du
 * projet ne placent pas les mêmes versets au même endroit — 56 versets sur 6 236 changent
 * de page — donc reprendre un signet à la page de l'autre découpage ouvrirait à côté.
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
    val storedReciterOwnerId by container.audioSettings.reciterOwnerId.collectAsStateWithLifecycle()
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
    // Le sous-ensemble marqué par l'**élève**. Il est distinct de `difficultIds`, qui compte
    // aussi le professeur : le premier dit « ce verset est difficile » et colore la page, le
    // second dit « l'élève l'a marqué » et décide du mot du bouton. Les confondre ferait mentir
    // le libellé — voir `VerseActionsText`.
    val userMarkedIds = remember(userState) {
        userState?.let { VerseActionsText.userMarkedIds(it) } ?: emptySet()
    }
    // Les lignes de l'écran des signets, résolues ici : c'est le seul endroit qui connaisse à
    // la fois l'état du compte et la source affichée — et la page d'un signet dépend de cette
    // source. La vue ne reçoit que du texte déjà résolu, et c'est `Bookmarks.rows` qui omet un
    // signet hors corpus au lieu de faire tomber l'écran.
    val bookmarkRows = remember(userState, source) {
        userState?.let { Bookmarks.rows(it, source) } ?: emptyList()
    }

    // Le récitateur, et laquelle de ses deux mémoires retenir. La règle vit dans `core:domain` :
    // ici, on ne fait que lui donner les quatre valeurs qu'elle demande. Elle décide laquelle
    // gagne — la valeur du compte fait écran, même quand son identifiant est inconnu —, à qui la
    // valeur appartient, et s'il faut la remonter au compte.
    val reciterResolution = remember(storedReciterId, storedReciterOwnerId, userState) {
        ReciterPreference.resolve(
            synced = userState?.audioPreferences?.reciterId,
            stored = storedReciterId,
            owner = storedReciterOwnerId,
            userId = userState?.userId,
        )
    }

    // Ce que le lecteur reçoit. Le défaut est **nommé** ici plutôt que laissé à `null` : c'est la
    // valeur avec laquelle le lecteur démarre, et c'est elle qui permet de reconnaître, plus bas,
    // qu'un récitateur a été **choisi**. La feuille appelle la même lambda pour un simple
    // changement de vitesse, et confondre les deux ferait écrire un choix que personne n'a fait.
    val reciterRetenu = reciterResolution.reciterId ?: Audio.defaultReciter.id

    // Ce que l'appareil apprend, et ce que le compte apprend — chacun au plus une fois. Les deux
    // écritures sont protégées : un disque plein ou un réseau coupé ne doivent pas emporter le
    // lecteur, qui affiche déjà le bon récitateur.
    LaunchedEffect(reciterResolution, userState) {
        val retenu = reciterResolution.reciterId
        val proprietaire = reciterResolution.ownerId
        if (retenu != null && proprietaire != null) {
            runCatching { container.audioSettings.remember(retenu, proprietaire) }
        }
        if (reciterResolution.pushToState && retenu != null && userState != null) {
            runCatching {
                container.userState.mutate { state ->
                    state.copy(audioPreferences = AudioPreferences(retenu))
                }
            }
        }
    }

    // La page est tenue ici, et non dans le lecteur : le choix de présentation en a besoin pour
    // vérifier que la page affichée existera encore dans l'autre source. Changer de
    // présentation ne doit pas ramener la personne à la page 1.
    var page by rememberSaveable { mutableIntStateOf(1) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    // L'écran des signets, et le verset qu'une reprise demande de sélectionner au retour.
    // Les deux sont **sauvegardés** : une rotation pendant qu'on consulte un signet ne doit
    // ni refermer la liste, ni perdre le verset qu'on vient de reprendre.
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    var pendingVerse by rememberSaveable { mutableStateOf<Int?>(null) }

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
        initialReciterId = reciterRetenu,
        onAudioSettingsChanged = { settings, reciterId ->
            // À qui appartient le choix : le compte, ou « l'invité » hors connexion. C'est le
            // `userId ?? 'guest'` de la clé d'origine, et il décide de ce que l'appareil retient.
            val proprietaire = userState?.userId ?: ReciterPreference.GUEST
            scope.launch {
                // Un disque plein ne doit pas emporter le lecteur : le réglage est **déjà**
                // appliqué à la séance, et c'est ce que la personne voit. Seul son
                // enregistrement échoue, et le lecteur n'a rien à en faire — l'annoncer
                // supposerait un endroit où le dire, qui n'existe pas encore.
                runCatching { container.audioSettings.save(settings, reciterId, proprietaire) }
                // Le compte ne reçoit qu'un **changement** de récitateur. La feuille appelle
                // cette lambda aussi pour la vitesse ou l'écart : réécrire l'état à chaque fois
                // ferait pousser une synchronisation pour un réglage qui tient à l'appareil.
                if (reciterId != reciterRetenu) {
                    runCatching {
                        container.userState.mutate { state ->
                            state.copy(audioPreferences = AudioPreferences(reciterId))
                        }
                    }
                }
            }
        },
        // Poser un signet : la règle est dans `core:domain`, l'écriture ici. La page
        // notée est celle de la **source affichée** — c'est ce qui permettra de rouvrir
        // le signet à la bonne page si la présentation change, les deux découpages ne
        // plaçant pas les mêmes versets au même endroit. Un disque plein ne doit pas
        // emporter le lecteur : le signet est perdu, mais l'écran reste utilisable.
        onSaveBookmark = { verseId ->
            scope.launch {
                runCatching {
                    container.userState.mutate { state ->
                        Bookmarks.saveBookmark(
                            state = state,
                            id = verseId,
                            sourcePage = source.persistedKey to page,
                        )
                    }
                }
            }
        },
        bookmarkIds = bookmarkIds,
        difficultIds = difficultIds,
        userMarkedIds = userMarkedIds,
        // Ouvrir la liste **oublie** le verset en attente : sans cela, reprendre deux fois le
        // même signet ne le sélectionnerait qu'une fois, la clé de l'effet n'ayant pas changé.
        onOpenBookmarks = {
            pendingVerse = null
            bookmarksOpen = true
        },
        initialVerse = pendingVerse,
        // Bascule le marqueur de difficulté de l'élève. Seule écriture du panneau des actions,
        // et la seule qui ne puisse pas vivre dans le lecteur : lui ne connaît ni le conteneur
        // ni le stockage. Comme pour le signet, un disque plein ne doit pas emporter le lecteur.
        onMarkDifficulty = { verseId ->
            scope.launch {
                runCatching {
                    container.userState.mutate { state -> Review.toggleDifficulty(state, verseId) }
                }
            }
        },
    )

    // L'écran des signets, par-dessus le lecteur — qui reste donc monté, et dont l'écoute n'est
    // pas interrompue. Voir la note de la route sur cet écart assumé.
    if (bookmarksOpen) {
        BookmarksScreen(
            rows = bookmarkRows,
            onClose = { bookmarksOpen = false },
            onResume = { id ->
                val target = userState?.let {
                    runCatching { Bookmarks.pageFor(it, source, id) }.getOrNull()
                }
                // Une conversion qui échoue laisse la liste ouverte : sauter à une page
                // devinée serait pire, puisque rien ne dirait qu'elle est fausse.
                if (target != null) {
                    scope.launch {
                        runCatching {
                            container.userState.mutate { state ->
                                Bookmarks.useBookmark(state, id, pageOverride = target)
                            }
                        }
                    }
                    pendingVerse = id
                    page = target
                    bookmarksOpen = false
                }
            },
            onDelete = { id ->
                scope.launch {
                    runCatching {
                        container.userState.mutate { state -> Bookmarks.deleteBookmark(state, id) }
                    }
                }
            },
        )
    }

    if (pickerOpen) {
        QuranSourcePickerDialog(
            onDismiss = { pickerOpen = false },
            currentPage = page,
            viewModel = viewModel,
        )
    }
}
