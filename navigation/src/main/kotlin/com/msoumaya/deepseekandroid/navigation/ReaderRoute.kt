package com.msoumaya.deepseekandroid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.LocalAppContainer
import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.Bookmarks
import com.msoumaya.deepseekandroid.core.domain.MushafSourceNavigation
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranSourceReady
import com.msoumaya.deepseekandroid.core.domain.ReaderMemory
import com.msoumaya.deepseekandroid.core.domain.StudyProgressCalculator
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.domain.ReciterPreference
import com.msoumaya.deepseekandroid.core.domain.Review
import com.msoumaya.deepseekandroid.core.domain.VerseActionsText
import com.msoumaya.deepseekandroid.core.model.AudioPreferences
import com.msoumaya.deepseekandroid.core.model.LegacyReviewGrade
import com.msoumaya.deepseekandroid.core.model.MushafPageSource
import com.msoumaya.deepseekandroid.feature.reader.BookmarksScreen
import com.msoumaya.deepseekandroid.feature.reader.EmbeddedMushafPages
import com.msoumaya.deepseekandroid.feature.reader.ReaderScreen
import com.msoumaya.deepseekandroid.feature.reader.StudyChromeState
import com.msoumaya.deepseekandroid.feature.sources.QuranDownloadPanel
import com.msoumaya.deepseekandroid.feature.sources.QuranSourcePickerDialog
import com.msoumaya.deepseekandroid.feature.sources.QuranSourceViewModel
import com.msoumaya.deepseekandroid.feature.sources.SessionActions
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
 * ## La mémoire du lecteur
 *
 * En sortant, la route **écrit** où l'on s'est arrêté : la page rejoint les pages lues, la
 * dernière lecture est datée, et la source composée retient sa page. La règle vit dans
 * `core:domain` — `ReaderMemory` — et la route ne fait que lui donner la page affichée, le
 * verset d'ouverture, et le conteneur qui sait écrire.
 *
 * L'écriture **précède** la fermeture, et c'est délibéré. La portée de cette route meurt avec
 * l'écran — c'est le bon comportement pour un réglage, qui ne doit pas survivre au geste qui l'a
 * posé — donc fermer d'abord annulerait l'écriture en vol : `mutate` suspend sur une écriture de
 * fichier, et sa continuation serait annulée avec la portée. La position ne serait jamais
 * enregistrée, et rien ne le dirait. Le retour système passe par la même sortie, sans quoi
 * quitter au geste écrirait moins que quitter au bouton — et la perte ne se verrait que chez qui
 * quitte vite.
 *
 * ## La page d'ouverture
 *
 * Le lecteur ne s'ouvre plus à la première page : il s'ouvre là où l'on s'était arrêté. La
 * valeur est **adoptée** quand l'état du compte finit d'arriver, parce qu'il vaut `null` au
 * premier rendu — confondre un état encore inconnu avec un état vide ramènerait à la page 1 à
 * chaque ouverture. L'adoption cesse dès que la personne tourne une page : elle a choisi, et une
 * lecture de fichier qui aboutit ensuite ne doit pas la déplacer.
 *
 * @param startVerse le premier verset de ce sur quoi la lecture est ouverte, ou `null` quand
 *   elle l'est librement. Il n'est pas décoratif : c'est lui qui décide du verset **retenu** en
 *   fermant, donc de l'endroit où l'accueil rouvrira. Sans lui, une séance ouverte au verset 746
 *   se mémoriserait au premier verset de la page où l'on s'est arrêté — un verset que personne
 *   n'a lu.
 * @param session la séance que le lecteur sert, ou `null` pour une lecture libre. C'est elle
 *   qui décide si une validation est possible — sans identifiant de tâche, il n'y a pas de
 *   progression à écrire — et sa plage sert de repli tant que l'état du compte n'est pas arrivé.
 * @param onClose ferme le lecteur. La route ne décide pas où l'on retourne : elle le demande à
 *   la coquille, qui seule connaît la pile.
 */
@Composable
fun ReaderRoute(
    startVerse: Int? = null,
    // `null` est le cas **ordinaire** — ouvrir le Coran pour lire — et non un cas de bord : une
    // lecture libre n'a ni tâche ni progression, et c'est ce que le type dit.
    session: StudySession.Request? = null,
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
    var page by rememberSaveable { mutableIntStateOf(ReaderMemory.FIRST_PAGE) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    // La demande d'ouvrir le panneau de séance, adressée au lecteur. C'est un **compteur**, et non
    // un booléen : deux demandes successives de la même chose doivent compter pour deux, et un
    // booléen vaudrait déjà vrai à la seconde — il faudrait alors le remettre à zéro, donc un
    // second état à tenir en accord avec le premier, et un aller-retour pour rien.
    //
    // Il est en `remember` et non `rememberSaveable`, et c'est la différence avec les trois états
    // au-dessus : c'est un **événement**, pas un état. Une rotation ne doit pas rouvrir un panneau
    // qu'on venait de refermer — et le client d'origine ne le rouvre pas non plus, son panneau
    // étant un état de composant qui ne survit pas au remontage.
    var sessionPanelRequest by remember { mutableIntStateOf(0) }

    // La personne a-t-elle **tourné** une page ? Tant que non, la page mémorisée est adoptée
    // quand l'état du compte finit d'arriver — et non au premier rendu, où il vaut encore
    // `null`. Le drapeau est sauvegardé : une rotation ne doit pas ramener quelqu'un à la page
    // mémorisée après qu'il en a tourné une.
    var pageTournee by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(userState, source, session) {
        if (pageTournee) return@LaunchedEffect
        // Une séance s'ouvre sur **son** premier verset, et non là où la lecture s'était
        // arrêtée : c'est la séance qui dit où commencer, et reprendre ailleurs ferait relire
        // un passage qui n'est pas celui du jour. Une progression partielle est préférée quand
        // elle existe — `StudySession.opening` — et elle se passe de l'état tant qu'il n'est pas
        // arrivé, puisque la requête porte déjà ses bornes.
        val cible = if (session != null) {
            val ouverte = userState?.let { StudySession.opening(it, session) } ?: session
            runCatching { MushafSourceNavigation.versePage(source, ouverte.range.start) }.getOrNull()
        } else {
            userState?.let { ReaderMemory.openingPage(it, source) }
        } ?: return@LaunchedEffect
        if (cible != page) page = cible
    }

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

    // Sortir du lecteur : **écrire la mémoire d'abord**, fermer ensuite.
    //
    // L'ordre est la seule chose qui garantisse l'écriture. La portée de cette route meurt avec
    // l'écran — c'est écrit plus haut, et c'est voulu pour un réglage — donc `onClose()` lancé
    // avant l'écriture annulerait celle-ci en vol. Écrire d'abord coûte une écriture de quelques
    // kilo-octets avant que l'écran ne se retire : imperceptible, et déterministe.
    // Le verset « d'ouverture » — celui qui sera retenu en fermant. Pour une séance, c'est son
    // premier verset : c'est lui qui a été demandé, et retenir celui de la page où l'on s'est
    // arrêté ferait rouvrir l'accueil un verset plus haut, hors de la séance.
    val versetDeDepart = session?.range?.start ?: startVerse

    val quitter: () -> Unit = {
        val affichee = page
        scope.launch {
            // Un disque plein ne doit pas emporter la fermeture : la position est perdue, mais
            // l'écran se referme. L'inverse enfermerait la personne dans le lecteur.
            runCatching {
                container.userState.mutate { state ->
                    ReaderMemory.close(state, page = affichee, start = versetDeDepart)
                }
            }
            onClose()
        }
    }

    // Le retour système est une sortie comme une autre : sans ce branchement, quitter au geste
    // n'écrirait rien, et la position serait perdue précisément quand on quitte vite.
    //
    // Il est **désactivé** pendant que l'écran des signets est ouvert : celui-ci est posé
    // par-dessus le lecteur, qui reste monté — un retour doit alors refermer la liste, et non le
    // lecteur qu'elle recouvre.
    BackHandler(enabled = !bookmarksOpen) { quitter() }

    // La source sous la forme que les règles d'étude attendent. `sourceKey` est le seul endroit
    // qui sache replier les sources que ce client ne rend pas : la replier ici, à la main,
    // ferait diverger deux tables — et une page d'étude fausse ne se voit pas.
    val sourceEtude = StudyProgressCalculator.sourceKey(source)

    // La séance telle que la coquille d'étude la reçoit, ou `null` tant que l'état du compte
    // n'est pas arrivé : le bandeau a besoin de la plage **prévue** et du dernier verset validé,
    // qui vivent tous les deux dans l'état. Un bandeau affiché avant annoncerait « 0 / 0 », donc
    // un mensonge — et la feuille de validation calculerait un point d'arrêt sur une plage
    // inventée.
    val etude = remember(userState, source, session) {
        val etat = userState
        if (etat == null || session == null) {
            null
        } else {
            StudyChromeState(
                banner = StudySession.banner(etat, session, sourceEtude),
                // La requête voyage **entière**, et non seulement ce qu'on en a tiré : le panneau
                // de séance a besoin de ses quatre faits pour décider de ses entrées, et aucun
                // couple de bornes ne dit si la tâche était un apprentissage ou une révision.
                request = session,
                range = StudySession.plannedRange(etat, session),
                through = StudySession.through(etat, session),
                source = sourceEtude,
                // L'étape de consolidation, lue **une fois** pour les deux endroits qui l'affichent :
                // le bandeau en fait son titre, et le bouton du panneau son libellé. Deux appels
                // séparés à la même règle liraient deux fois le même état — donc deux vérités à
                // tenir en accord, pour rien.
                consolidationOffset = StudySession.consolidationOffset(etat, session),
            )
        }
    }

    ReaderScreen(
        initialPage = page,
        source = source,
        pages = pages,
        // La séance d'écoute vient du conteneur, et non du lecteur : elle vit donc aussi
        // longtemps que l'application. Revenir au lecteur retrouve la récitation en cours au
        // lieu de la faire repartir du premier verset.
        playback = container.playback,
        onClose = quitter,
        // Le premier rapport répète la page d'ouverture : ce n'est pas un geste, et le marquer
        // interdirait d'adopter la page mémorisée, qui arrive après l'état du compte. Les
        // rapports suivants viennent tous d'un geste — la clé de l'effet est la page.
        onPageChanged = { rapportee ->
            if (rapportee != page) {
                page = rapportee
                pageTournee = true
            }
        },
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
        // Le fond de page choisi, tel qu'il est dans l'état du compte. Le lecteur ne le connaît
        // pas — il ne connaît ni le conteneur ni le stockage — et c'est pourquoi la route le lui
        // passe. Il ne sert qu'à la source **composée** : les images du moushaf portent le leur.
        paper = userState?.reader?.paper,
        study = etude,
        // La demande d'ouvrir le panneau de séance, adressée au lecteur. Elle est passée **telle
        // quelle** : c'est le lecteur qui sait si le panneau est déjà ouvert, et c'est lui qui
        // possède l'état.
        sessionPanelRequest = sessionPanelRequest,
        // Valider la séance : la règle est dans `core:domain`, l'écriture ici. Comme pour la
        // sortie, on **écrit d'abord** — la portée meurt avec l'écran, donc fermer avant
        // annulerait l'écriture en vol, et le symptôme serait celui d'une validation qui
        // n'existe pas. Un disque plein ne doit pas emporter l'écran : la séance est perdue,
        // mais l'application reste utilisable.
        onValidateStudy = { through, note ->
            val requete = session
            if (requete == null) {
                quitter()
            } else {
                scope.launch {
                    runCatching {
                        container.userState.mutate { state ->
                            StudySession.validate(
                                state = state,
                                request = requete,
                                through = through,
                                source = sourceEtude,
                                grade = note,
                            )
                        }
                    }
                    // La sortie écrit la mémoire du lecteur, puis referme. C'est `quitter`, et
                    // non `onClose` : le même chemin que le bouton et le retour système — sans
                    // quoi valider une séance oublierait où l'on s'était arrêté.
                    quitter()
                }
            }
        },
        // Valider une étape de consolidation. L'étape visée est **relue** ici, et non reçue du
        // panneau : elle peut avoir changé depuis son ouverture — une synchronisation, une autre
        // validation — et c'est l'état présent qui dit quelle étape reste à faire.
        //
        // Elle est passée en `targetOffset`, et ce n'est pas un détail : sans elle,
        // `completeConsolidation` valide la première étape non faite, si bien qu'un second appui
        // validerait l'étape **suivante**. Le client d'origine passe le même argument, en
        // cinquième position.
        onValidateConsolidation = {
            val requete = session
            val cible = requete?.let { r -> userState?.let { etat -> StudySession.consolidationOffset(etat, r) } }
            // Les trois étapes faites, il n'y a plus rien à valider : on ne fait rien, et surtout
            // on **n'écrit pas**. Le lecteur ne propose d'ailleurs plus le bouton dans ce cas —
            // voir sa garde —, donc ce chemin n'est pas atteignable ; il est écrit quand même,
            // parce qu'un état peut changer entre le rendu et l'appui.
            if (requete != null && cible != null) {
                scope.launch {
                    runCatching {
                        container.userState.mutate { state ->
                            Review.completeConsolidation(
                                state = state,
                                range = requete.range,
                                targetOffset = cible,
                            )
                        }
                    }
                    // La consolidation **quitte le lecteur** : l'étape est faite, et il n'y a plus
                    // rien à valider sur cette plage. Même chemin que les autres validations.
                    quitter()
                }
            }
        },
        // « À réapprendre » : la révision repart à un jour, **et** le programme est régénéré.
        // Les deux, et dans cet ordre : c'est ce que fait le client d'origine, où `grade('relearn')`
        // enchaîne `generateProgram`. Sans la régénération, le verset repartirait à un jour sans
        // que le programme en tienne compte, et il ne serait proposé nulle part — un verset marqué
        // « à réapprendre » que plus rien ne reproposerait.
        onRelearn = {
            val revision = session?.revisionId
            if (revision != null) {
                scope.launch {
                    runCatching {
                        container.userState.mutate { state ->
                            Program.generateProgram(
                                Program.gradeRevision(state, revision, LegacyReviewGrade.RELEARN),
                            )
                        }
                    }
                    quitter()
                }
            }
        },
        // « Je dois encore le travailler » : la séance n'est **pas** finie. C'est
        // `completeSession` avec `memorized = false`, qui reporte — le verset déjà validé reste
        // appris, et le programme reproposera la séance. Le client d'origine passe exactement ce
        // booléen, et c'est aussi ce que fait sa fonction de clôture.
        onWorkAgain = {
            val seance = session?.sessionId
            if (seance != null) {
                scope.launch {
                    runCatching {
                        container.userState.mutate { state ->
                            Program.completeSession(state, seance, memorized = false)
                        }
                    }
                    quitter()
                }
            }
        },
        // « Reporter cette séance » : la séance entière repart au programme. Le report ne change
        // que le statut — la date prévue et les séances voisines ne bougent pas — et une séance
        // partiellement apprise reste reprise plutôt que reportée, ce que porte la règle du
        // domaine.
        onPostpone = {
            val seance = session?.sessionId
            if (seance != null) {
                scope.launch {
                    runCatching {
                        container.userState.mutate { state -> Program.postponeSession(state, seance) }
                    }
                    quitter()
                }
            }
        },
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
        // L'entrée « Actions de la séance ». Elle n'existe que si le lecteur **sert une tâche** —
        // c'est le `focused` du client d'origine, une séance d'apprentissage ou une révision — et
        // que sa plage a pu être nommée. Une référence irrésoluble ne donne pas une entrée
        // fautive : elle n'en donne aucune, et le sélecteur reste ce qu'il est, un choix de
        // présentation.
        //
        // La plage annoncée est celle **ouverte** — le reste d'une tâche reprise —, comme dans le
        // client d'origine, où le libellé est `reference(reader.range)` et où `reader.range` est
        // la plage de reprise. L'écrire sur la plage demandée annoncerait des versets qu'on ne
        // relira pas.
        val actionsSeance = session?.takeIf { it.focused }?.let { requete ->
            val ouverte = userState?.let { StudySession.opening(it, requete) } ?: requete
            runCatching { Quran.reference(ouverte.range) }.getOrNull()
        }?.let { reference ->
            SessionActions(
                reference = reference,
                onOpen = {
                    // Refermer **d'abord**, puis demander : deux fenêtres empilées donneraient
                    // deux voiles superposés, et un retour arrière qui ne rendrait pas la main au
                    // bon endroit. C'est l'ordre du client d'origine.
                    pickerOpen = false
                    sessionPanelRequest++
                },
            )
        }

        QuranSourcePickerDialog(
            onDismiss = { pickerOpen = false },
            currentPage = page,
            sessionActions = actionsSeance,
            viewModel = viewModel,
        )
    }
}
