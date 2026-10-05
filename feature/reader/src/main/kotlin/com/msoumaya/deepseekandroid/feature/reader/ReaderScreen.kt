package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.audio.AudioSessionController
import com.msoumaya.deepseekandroid.core.audio.ExoAudioOutput
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.Audio
import com.msoumaya.deepseekandroid.core.domain.AudioCount
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import com.msoumaya.deepseekandroid.core.domain.BookmarksText
import com.msoumaya.deepseekandroid.core.domain.MushafSourceNavigation
import com.msoumaya.deepseekandroid.core.domain.PageNavigation
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.ReaderData
import com.msoumaya.deepseekandroid.core.domain.ReaderGesture
import com.msoumaya.deepseekandroid.core.domain.ReaderLayout
import com.msoumaya.deepseekandroid.core.domain.ReaderOptionsText
import com.msoumaya.deepseekandroid.core.domain.ReaderTouch
import com.msoumaya.deepseekandroid.core.domain.ReaderZoomGeometry
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.domain.TestPageOverlay
import com.msoumaya.deepseekandroid.core.domain.Texts
import com.msoumaya.deepseekandroid.core.domain.TranslationPanel
import com.msoumaya.deepseekandroid.core.domain.VerseActionsText
import com.msoumaya.deepseekandroid.core.model.MushafPageSource
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.QuranPaper
import com.msoumaya.deepseekandroid.core.model.Range
import com.msoumaya.deepseekandroid.core.model.ReaderZoom
import com.msoumaya.deepseekandroid.core.model.RepeatMode
import com.msoumaya.deepseekandroid.core.model.ReviewGrade
import com.msoumaya.deepseekandroid.core.model.Surah
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Le moushaf de Médine compte 604 pages. Sert de repli si le référentiel n'est pas chargé. */
private const val DEFAULT_TOTAL_PAGES = 604

/**
 * Le lecteur de moushaf.
 *
 * C'est la **priorité absolue** du cahier des charges, et l'écran qui décide si l'application
 * est utilisable : on y passe le plus de temps, et c'est là qu'un défaut se sent.
 *
 * ## Ce qui est en place
 *
 * - **La page est centrée dans l'espace réellement disponible**, après retrait des barres
 *   système, des découpes d'écran et de la navigation par geste. Aucune marge fixe : la
 *   géométrie vient de `ReaderLayout.fitMushafPage`, qui préserve le rapport de la source.
 * - **La page n'est jamais déformée.** Le cadre de 4 px est compté dans les deux dimensions.
 * - **Un seul gestionnaire de gestes**, décrit dans `ReaderGestures` : balayage, pincement,
 *   appui et appui long ne peuvent pas se disputer les mêmes événements.
 * - **Trois pages en mémoire au maximum** — la courante et ses voisines — via `ReaderPreload`.
 * - **L'écoute de la page**, avec le mini-lecteur décrit dans `MiniPlayer`. La coquille et le
 *   mini-lecteur sont **dans le flux** : ils prennent leur hauteur au lieu de recouvrir la
 *   page, donc le dernier verset reste lisible sans rien faire disparaître.
 * - **Aucun réseau à l'ouverture.** Les 604 pages sont dans l'application : le lecteur s'ouvre
 *   en avion. Seule l'écoute demande une connexion, puisque les versets sont des fichiers
 *   distants — et c'est elle, et elle seule, qui le dit quand elle échoue.
 * - **Le carrefour des réglages de lecture.** Le bouton « ⋯ » de la coquille ouvre la feuille
 *   d'options décrite dans `ReaderOptionsSheet`, et c'est de là qu'on change de sourate
 *   (`SurahPickerScreen`), qu'on règle l'écoute (`AudioSettingsSheet`) ou qu'on choisit la
 *   présentation des pages. Une destination dont l'écran n'est pas écrit **n'apparaît pas**
 *   dans la feuille.
 * - **La traduction française se lit sans quitter la page.** Le panneau décrit dans
 *   `TranslationPanelSheet` donne, verset par verset, la référence et le sens du passage
 *   affiché. Quels versets exactement — la séance, ou la page — est une règle de
 *   `core:domain`, éprouvée là où elle vit.
 * - **Les versets marqués se voient sur la page.** Un verset en signet est teinté en vert
 *   et reçoit son signet en marge ; un verset marqué difficile est teinté en rouge. Les
 *   deux ensembles sont **reçus** et non calculés ici : ce qui compte comme marqué — une
 *   suppression logique, un marqueur posé par le professeur — est une règle de
 *   `core:domain`, éprouvée là où elle vit. L'ordre des couleurs est décrit dans
 *   `MushafPageView`.
 * - **Les réglages d'écoute s'appliquent à la séance en cours.** Changer de récitateur, de
 *   nombre d'écoutes, de mode ou de silence vaut pour la séance ouverte, sans avoir à la
 *   relancer : c'est le comportement du client d'origine, où les réglages sont relus à chaque
 *   rendu. Le verset en train de jouer n'est jamais coupé.
 * - **Les réglages d'écoute sont enregistrés sur l'appareil.** Chaque changement est écrit dans
 *   un document unique qui porte aussi le récitateur, et relu au démarrage de l'application.
 *   La relecture pouvant aboutir après l'ouverture du lecteur, la valeur publiée est adoptée
 *   **tant que la personne n'a rien réglé ici** : son premier geste prime sur la lecture. Ces
 *   réglages ne sont pas par compte — la façon d'écouter tient à l'appareil, comme dans le
 *   client d'origine, qui range ces deux clés hors de toute notion d'utilisateur.
 *
 * ## Ce qui viendra, et où
 *
 * Ce qui **reste** : la coquille d'étude — l'en-tête de séance et ses repères de marge, qui
 * supposent une séance ouverte — et l'**écran** des signets, c'est-à-dire la liste de ceux qu'on
 * a posés. Le mode de pose, lui, est en place : la coquille ouvre le panneau des marques-pages,
 * sa première entrée arme le geste, et le verset touché est rapporté à l'appelant, seul à savoir
 * où l'enregistrer. La seconde entrée attend son écran et disparaît en attendant — une entrée
 * qui ne mène nulle part n'est pas affichée.
 *
 * @param initialPage page ouverte au lancement. Bornée au moushaf.
 * @param source la source coranique affichée. Elle décide du **découpage** des pages : deux
 *   sources ne placent pas les mêmes versets sur la page 300. Elle ne décide pas des images,
 *   qui viennent de [pages].
 * @param pages où trouver les images. Le lecteur ne le sait pas : il reçoit des chemins déjà
 *   résolus. C'est ce qui lui permet de n'avoir ni stockage ni réseau.
 * @param onClose ferme le lecteur. L'écran ne connaît pas la navigation : c'est l'appelant
 *   qui décide où l'on retourne.
 * @param onPageChanged rapporte la page affichée. L'appelant en a besoin pour changer de
 *   présentation **sans faire perdre sa page** à la personne : c'est la page courante qui est
 *   vérifiée avant d'adopter une autre source.
 * @param onOpenSourcePicker ouvre le choix de présentation. `null` quand l'appelant n'en
 *   propose pas — le bouton est alors absent plutôt que présent et sans effet.
 * @param initialSettings les réglages d'écoute relus du disque. Valeur de **départ**, adoptée
 *   tant que la personne n'a rien réglé ici.
 * @param initialReciterId le récitateur enregistré, ou `null` s'il n'a jamais été choisi.
 * @param onAudioSettingsChanged rapporte chaque changement, avec le récitateur courant, pour
 *   qu'il soit écrit. Les deux partent ensemble : c'est un seul document.
 * @param bookmarkIds les versets portant un signet : ils sont teintés et reçoivent leur
 *   signet en marge. Vide par défaut — un appelant qui ne connaît pas les signets n'en
 *   dessine aucun.
 * @param difficultIds les versets marqués difficiles, quelle qu'en soit l'origine.
 * @param onSaveBookmark enregistre un signet sur le verset touché. `null` quand l'appelant ne
 *   sait pas le faire : ni le bouton de la coquille, ni l'entrée « Placer un marque-page » du
 *   panneau n'existent alors.
 * @param onOpenBookmarks ouvre la liste des signets. `null` tant qu'elle n'est pas écrite.
 * @param userMarkedIds les versets portant le marqueur de difficulté de **l'élève** : ils
 *   décident du mot de la dernière entrée du panneau des actions. Distinct de `difficultIds`,
 *   qui compte aussi le professeur — voir `VerseActionsText`.
 * @param onMarkDifficulty bascule le marqueur de difficulté de l'élève sur un verset. `null`
 *   quand l'appelant ne sait pas l'écrire : l'entrée de marquage est alors retirée du
 *   panneau au lieu de mener nulle part.
 * @param initialVerse le verset à désigner au premier rendu — celui qu'une reprise de signet
 *   vient de demander. `null` quand personne n'en attend : le lecteur s'ouvre sans fiche.
 */
@Composable
fun ReaderScreen(
    modifier: Modifier = Modifier,
    initialPage: Int = 1,
    source: MushafSource = MushafSource.MEDINA,
    pages: MushafPageSource = EmbeddedMushafPages,
    onClose: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    onOpenSourcePicker: (() -> Unit)? = null,
    initialSettings: AudioSession = AudioSession(),
    initialReciterId: String? = null,
    onAudioSettingsChanged: (AudioSession, String) -> Unit = { _, _ -> },
    // Le verset à mettre en signet, rapporté à l'appelant : c'est le seul qui connaisse le
    // conteneur, donc le seul qui puisse l'enregistrer. `null` quand il ne le sait pas — le
    // panneau ne propose alors pas de poser de signet, et le bouton de la coquille disparaît.
    onSaveBookmark: ((Int) -> Unit)? = null,
    // Ouvre l'écran des signets. `null` tant que cet écran n'existe pas : l'entrée du panneau
    // est alors retirée au lieu de mener nulle part.
    onOpenBookmarks: (() -> Unit)? = null,
    // Le verset à désigner au premier rendu. Il vient de la **reprise d'un signet** : la
    // route ouvre la page, et désigne le verset qu'on venait chercher. `null` veut dire
    // « personne n'en attend » — et non « le premier verset ».
    initialVerse: Int? = null,
    bookmarkIds: Set<Int> = emptySet(),
    difficultIds: Set<Int> = emptySet(),
    userMarkedIds: Set<Int> = emptySet(),
    // Bascule le marqueur de difficulté de l'élève. Seule écriture du panneau des actions, et
    // donc la seule de ses entrées qui ne puisse pas vivre ici : le lecteur ne connaît ni le
    // conteneur ni le stockage. `null` retire l'entrée au lieu de la laisser mener nulle part.
    onMarkDifficulty: ((Int) -> Unit)? = null,
    // Le fond de page choisi. Il ne sert qu'à la source **composée**, qui peint son propre fond ;
    // les images du moushaf portent le leur. `null` veut dire « jamais choisi » et applique le
    // premier du catalogue — la même valeur que celle que le document porte déjà, donc rien ne
    // change à l'écran. Sans ce paramètre, le réglage existerait dans l'état sans jamais
    // atteindre la seule source qui puisse le montrer.
    paper: QuranPaper? = null,
    // La séance que le lecteur sert, ou `null` pour une lecture libre. Elle porte les
    // textes du bandeau **déjà résolus** — le lecteur ne calcule rien — et de quoi
    // composer la feuille de validation. Voir `StudyChromeState`.
    study: StudyChromeState? = null,
    // Valide la séance jusqu'au verset reçu, avec la note reçue. `null` quand l'appelant
    // ne sait pas écrire : le bandeau est alors **absent**, comme les autres actions sans
    // destination. Un bandeau qui s'annonce sans rien permettre serait un bouton mort.
    onValidateStudy: ((Int, ReviewGrade) -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val totalPages = remember { Quran.pages.size.takeIf { it > 0 } ?: DEFAULT_TOTAL_PAGES }

    // La source **composée** a son propre écran : elle ne peint pas d'image, elle compose une
    // page avec une police par page et mesure ses mots dans la WebView qui l'affiche. Les
    // gestes, le balayage et la surimpression y vivent donc dans le document, et non ici — voir
    // `TestPageView`. C'est la seule source dans ce cas, et la règle le dit plutôt que de le
    // laisser deviner par une comparaison recopiée.
    val immersive = MushafSourceNavigation.isImmersive(source)

    // Le fond de la composition. Il est écrit **une** fois en `#rrggbb` — c'est la forme que le
    // document valide — puis relu pour la vue, de façon que les deux ne puissent pas diverger :
    // un fond différent de celui de la page ferait clignoter l'écran au chargement.
    val paperHex = Texts.quranPaperColor(paper)
    val paperColor = TestPageColors.parse(paperHex)

    // Des **objets d'état**, et non des valeurs : le gestionnaire de gestes est installé une
    // seule fois et relit ces états à chaque événement. Capturer les valeurs le figerait sur
    // l'état du premier rendu, et la page ne tournerait plus qu'une fois.
    val pageState = rememberSaveable { mutableIntStateOf(initialPage.coerceIn(1, totalPages)) }
    val zoomState = remember { mutableStateOf(ReaderZoom()) }
    val chromeState = rememberSaveable { mutableStateOf(true) }
    val verseState = remember { mutableStateOf<Int?>(null) }

    // Le panneau ouvert, s'il y en a un. Un **seul** à la fois, et c'est le modèle du client
    // d'origine : sa feuille d'options et son sélecteur de sourate s'excluent, et refermer le
    // second rouvre la première. Deux fenêtres empilées donneraient deux voiles superposés et
    // un retour arrière qui ne rendrait pas la main au bon endroit.
    var panel by rememberSaveable { mutableStateOf(ReaderPanel.NONE) }

    // La feuille de validation est-elle ouverte ? **Sauvegardée** : tourner le
    // téléphone pendant qu'on choisit son point d'arrêt ne doit ni refermer la feuille,
    // ni perdre le choix qu'on venait d'y faire.
    var completionOpen by rememberSaveable { mutableStateOf(false) }

    // Le mode de pose, et la confirmation qui le suit.
    //
    // Le mode est **sauvegardé** : une rotation ne doit pas désarmer un geste que la personne
    // vient d'armer — elle toucherait un verset pour le marquer, et la coquille se masquerait
    // à la place. La confirmation, elle, ne l'est pas : c'est un message transitoire, et son
    // délai meurt avec l'écran, comme le `setTimeout` du client d'origine.
    var bookmarkMode by rememberSaveable { mutableStateOf(false) }
    var savedNotice by remember { mutableStateOf(false) }

    // La confirmation s'efface toute seule. La **durée** vit à côté du mot qu'elle gouverne
    // (`BookmarksText.SAVED_NOTICE_MS`) ; la règle d'affichage, elle, ne connaît pas d'horloge —
    // c'est ce qui la rend éprouvable sans attendre.
    LaunchedEffect(savedNotice) {
        if (savedNotice) {
            delay(BookmarksText.SAVED_NOTICE_MS)
            savedNotice = false
        }
    }

    // Le verset qu'une reprise de signet demande de désigner. L'effet est **clé sur le
    // verset** : il ne s'exécute donc qu'une fois par valeur reçue, et non à chaque
    // recomposition — sans quoi la fiche se rouvrirait à chaque frappe ailleurs. La route
    // remet la valeur à `null` avant chaque ouverture de la liste, ce qui garantit qu'une
    // même reprise, deux fois de suite, change bien la clé.
    //
    // Le panneau est refermé au passage, comme le `setSessionPanel(null)` du source : on
    // revient d'une **liste**, et non d'un panneau — laisser ouvert celui qu'on avait
    // quitté ferait réapparaître une feuille que personne n'a demandée. Ce n'est possible
    // que parce que le lecteur reste monté : ailleurs, l'écran aurait été reconstruit.
    LaunchedEffect(initialVerse) {
        if (initialVerse != null) {
            verseState.value = initialVerse
            panel = ReaderPanel.NONE
        }
    }

    // Le lecteur audio vit aussi longtemps que l'écran : c'est la portée qui décide, et
    // `DisposableEffect` libère le lecteur natif quand on quitte. Sans service d'avant-plan,
    // l'écoute s'arrête en quittant le lecteur — c'est honnête, et ce sera l'affaire des
    // notifications que de la poursuivre.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audio = remember(context) {
        AudioSessionController(ExoAudioOutput(context.applicationContext), scope)
    }
    DisposableEffect(audio) {
        onDispose { audio.release() }
    }
    val audioState by audio.state.collectAsState()

    // Les réglages d'écoute et le récitateur. Ils vivent **ici**, et non dans la feuille : c'est
    // ici qu'ils sont appliqués au contrôleur, et la feuille n'est qu'un moyen de les changer.
    // Sauvegardés, ils survivent à une rotation — sans quoi tourner le téléphone ramènerait le
    // premier récitateur de la liste, alors que la page, elle, est conservée.
    var settings by rememberSaveable(stateSaver = AudioSessionSaver) {
        mutableStateOf(initialSettings)
    }
    var reciterId by rememberSaveable { mutableStateOf(initialReciterId ?: Audio.defaultReciter.id) }

    // Le disque est relu au démarrage de l'application, et rien ne garantit que la lecture ait
    // abouti quand le lecteur s'ouvre. Tant que la personne n'a rien réglé **ici**, la valeur
    // publiée remplace donc celle du premier rendu ; dès qu'elle a touché un réglage, sa
    // décision prime — la relire écraserait son geste. Le comportement ne dépend ainsi pas d'une
    // course entre une lecture de fichier et un appui.
    var regleParLaPersonne by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialSettings, initialReciterId) {
        if (!regleParLaPersonne) {
            settings = initialSettings
            // `null` veut dire « jamais choisi » : le défaut affiché n'est pas un choix, et
            // l'écrire en serait un.
            initialReciterId?.let { reciterId = it }
        }
    }

    // Un identifiant inconnu retombe sur le récitateur par défaut, comme une préférence
    // enregistrée par une version antérieure : mieux vaut écouter le mauvais récitateur que
    // rien du tout, et la liste des récitateurs est visible deux appuis plus loin.
    val reciter = remember(reciterId) {
        Audio.reciters.firstOrNull { it.id == reciterId } ?: Audio.defaultReciter
    }
    val countLabel = remember(settings) { countLabelOf(settings) }

    // Poussé au contrôleur à la première composition, puis à chaque changement : le verset
    // suivant vient du bon récitateur, sans interrompre celui qui joue.
    LaunchedEffect(reciter) { audio.useReciter(reciter) }

    val page by pageState
    val zoom = zoomState.value
    val chromeVisible = chromeState.value
    val selectedVerse = verseState.value

    // La sourate au début de la page affichée : c'est ce qu'on cherche en tournant une page, et
    // c'est aussi ce que le sélecteur surligne à l'ouverture. Elle est lue dans le découpage de
    // la **source affichée** — la première page du paquet « Coran 1441 » ne porte pas le même
    // verset que la première page du moushaf de Médine.
    val surah: Surah? = surahFor(source, page)

    // Les destinations réellement branchées dans la feuille d'options. « Changer de sourate »
    // l'est toujours : le sélecteur sait gérer un référentiel non chargé et le dit. « Réglages
    // audio » et « Traduction française » aussi, depuis que leurs écrans existent. « Affichage
    // du Coran » dépend de l'appelant, car le lecteur ne connaît ni les sources ni le stockage.
    val options: Set<ReaderOptionsText.Action> = buildSet {
        add(ReaderOptionsText.Action.SURAH)
        add(ReaderOptionsText.Action.TRANSLATION)
        add(ReaderOptionsText.Action.AUDIO)
        if (onOpenSourcePicker != null) add(ReaderOptionsText.Action.DISPLAY)
    }
    val openOptions: (() -> Unit)? = if (ReaderOptionsText.isUseful(options)) {
        { panel = ReaderPanel.OPTIONS }
    } else {
        null
    }

    // Les entrées du panneau des marques-pages réellement branchées. Le panneau n'est proposé
    // que s'il en a au moins une : sans enregistrement branché, le bouton de la coquille serait
    // une impasse, et le panneau se réduirait à son titre.
    val bookmarkActions: Set<BookmarksText.PanelAction> = buildSet {
        if (onSaveBookmark != null) add(BookmarksText.PanelAction.PLACE)
        if (onOpenBookmarks != null) add(BookmarksText.PanelAction.OPEN_LIST)
    }
    val openBookmarks: (() -> Unit)? = if (BookmarksText.isPanelUseful(bookmarkActions)) {
        { panel = ReaderPanel.BOOKMARKS }
    } else {
        null
    }

    // Les actions du panneau du verset réellement branchées. Même règle que les deux autres
    // panneaux : une action sans destination est **retirée**, et le panneau ne s'ouvre que s'il
    // en reste au moins une. « Sélectionner un passage » n'y figure pas : le geste de
    // désignation d'une plage n'est pas porté, et son entrée disparaît plutôt que de mener
    // nulle part.
    //
    // « Écouter » et « Répéter » sont toujours là : le lecteur possède le contrôleur audio, et
    // la plage d'un verset est un fait connu — pas une conversion qui pourrait échouer, comme
    // celle d'une page. Seul le marquage dépend de l'appelant.
    val verseActions: Set<VerseActionsText.Action> = buildSet {
        add(VerseActionsText.Action.LISTEN)
        add(VerseActionsText.Action.REPEAT)
        if (onMarkDifficulty != null) add(VerseActionsText.Action.MARK)
    }

    // La notice au-dessus de la page : l'instruction du mode de pose, ou la confirmation d'un
    // signet enregistré. La règle est dans `BookmarksText` — ici, on ne fait que lui dire si le
    // mode est armé et si la confirmation est encore d'actualité.
    val notice: String? = BookmarksText.notice(placing = bookmarkMode, saved = savedNotice)

    // Ce que la source composée reçoit pour colorer sa page. Le **mode de pose** y figure, et il
    // y décide de deux choses à la fois : ce que le document colore, et ce qu'un appui désigne.
    // Les séparer les ferait diverger — la page colorerait autre chose que ce qu'un appui
    // enregistrerait — et c'est pourquoi `TestPageSession.route` lit le même `selecting`.
    val testMarkers = TestPageOverlay.Markers(
        selected = selectedVerse,
        // Le verset en cours d'écoute, s'il y en a une. La séance peut être en pause : la marque
        // reste, comme dans l'original, qui ne l'efface qu'à l'arrêt de la séance.
        playing = audioState.takeIf { it.isOpen }?.position?.verseId,
        bookmarks = bookmarkIds,
        difficult = difficultIds,
        selecting = bookmarkMode,
        background = paperHex,
        primary = TestPageColors.hex(colors.green),
        selection = TestPageColors.hex(colors.selected),
        gold = TestPageColors.hex(colors.gold),
    )

    // Changer de page remet le zoom à la page entière et ferme la fiche du verset : garder
    // l'agrandissement d'une autre page n'aurait aucun sens, et une fiche ouverte décrirait un
    // verset qui n'est plus à l'écran. Une seule définition, pour que le curseur de la coquille
    // et le sélecteur de sourate ne puissent pas diverger.
    //
    // Une **seule exception**, et elle est écrite juste après : une page demandée de l'extérieur
    // vient avec le verset qu'on allait chercher, et effacer celui-ci détruirait la reprise.
    //
    // Déclarée **avant** la colonne, et non dedans : le sélecteur de sourate s'affiche après
    // elle, hors de la colonne, et une déclaration faite dans la colonne n'y serait plus
    // visible.
    val goToPage: (Int) -> Unit = { target ->
        pageState.intValue = target.coerceIn(1, totalPages)
        zoomState.value = ReaderZoom()
        verseState.value = null
    }

    // Une page demandée **de l'extérieur** : la reprise d'un signet, ou la page mémorisée
    // adoptée quand l'état du compte finit d'arriver. Le lecteur la suit, et c'est nécessaire :
    // `initialPage` n'est lu qu'à la **première** composition — `pageState` est un
    // `rememberSaveable`, dont l'initialisation ne se rejoue pas. Sans cet effet, l'appelant
    // croirait à une page que l'écran n'affiche pas, et c'est cette page-là qu'il enregistrerait
    // en refermant le lecteur : une position que personne n'a vue.
    //
    // Elle **ne passe pas** par `goToPage`, et c'est la seule exception : celui-ci efface le
    // verset désigné, or une page demandée de l'extérieur vient précisément avec le verset qu'on
    // allait chercher. Le zoom, lui, se remet à la page entière comme partout ailleurs.
    //
    // La garde `!=` évite de rejouer au premier rendu, où la valeur reçue est celle qui a servi
    // à initialiser `pageState` : sans elle, l'effet referait au premier rendu ce qui vient
    // d'être fait, et remettrait le zoom à zéro sur une page qu'on venait d'ouvrir.
    LaunchedEffect(initialPage) {
        if (initialPage != pageState.intValue) {
            pageState.intValue = initialPage.coerceIn(1, totalPages)
            zoomState.value = ReaderZoom()
        }
    }

    // La page est rapportée à l'appelant à chaque changement, et non à chaque recomposition :
    // la clé de l'effet est la page elle-même.
    LaunchedEffect(page) { onPageChanged(page) }

    // `null` quand la page n'a pas de plage connue : l'action est alors absente plutôt que
    // présente et sans effet. La plage est celle du **découpage de la source affichée** : lire
    // une page du paquet avec la table du moushaf de Médine ferait commencer l'écoute au
    // mauvais verset.
    val listenAction: (() -> Unit)? = remember(page, settings, source) {
        runCatching { MushafSourceNavigation.pageRange(source, page) }.getOrNull()?.let { range ->
            { audio.start(range, settings) }
        }
    }

    // Le bandeau de séance n'existe que si une séance est servie **et** que la validation est
    // branchée : sans destination, son geste ne mènerait nulle part, et c'est ce que ce lecteur
    // refuse partout ailleurs. La valeur est tenue **hors** de la colonne : la feuille, posée à
    // la fin de cette fonction, a besoin des mêmes informations.
    val seance = study?.takeIf { onValidateStudy != null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceSecondary)
            // Barres système, découpes d'écran et barre de gestes : la page est centrée dans
            // l'espace **sûr**, jamais dessous. Le fond, lui, va jusqu'aux bords de l'écran.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Le bandeau, **dans** la colonne et avant la page : il prend sa hauteur, donc la page
        // reste entière. Le poser par-dessus masquerait le premier verset — celui qu'on vient
        // de commencer à apprendre, donc celui qu'on relit le plus.
        if (seance != null) {
            StudyBanner(banner = seance.banner, onPress = { completionOpen = true })
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // La source composée sort **ici**, avant tout ce qui suit : le reste de ce bloc
            // décrit une page peinte par une image — son référentiel, sa géométrie, son
            // préchargement, ses gestes. Le traverser ne serait pas seulement inutile : le
            // préchargement irait chercher trois pages du moushaf de Médine pour une source qui
            // n'en affiche aucune.
            if (immersive) {
                TestPageView(
                    page = page,
                    markers = testMarkers,
                    background = paperColor,
                    onPage = goToPage,
                    // Le mode de pose se désarme et la confirmation s'affiche **ici**, comme sur
                    // la page peinte : c'est le même geste, et il doit se terminer de la même
                    // façon. Le laisser au document ferait un geste qui enregistre sans le dire.
                    onSaveBookmark = onSaveBookmark?.let { save ->
                        { verseId ->
                            save(verseId)
                            bookmarkMode = false
                            savedNotice = true
                        }
                    },
                    // L'appui long désigne un verset, comme sur la page peinte : la fiche et le
                    // panneau suivent la même règle, et un panneau sans entrée ne s'ouvre pas.
                    onLongPressVerse = { verseId ->
                        verseState.value = verseId
                        panel = if (VerseActionsText.isUseful(verseActions)) {
                            ReaderPanel.VERSE
                        } else {
                            ReaderPanel.NONE
                        }
                    },
                    // L'appui long hors de tout verset est le geste d'ouverture des options du
                    // client d'origine. Il est ici parce que la composition n'a pas de coquille
                    // au-dessus d'elle : c'est le seul chemin vers les réglages depuis la page.
                    onBlankLongPress = { panel = ReaderPanel.OPTIONS },
                )
                return@BoxWithConstraints
            }

            val availableWidth = constraints.maxWidth.toFloat()
            val availableHeight = constraints.maxHeight.toFloat()

            // Les images de la page, telles que la source les fournit. `remember` sur la page
            // et sur la source : relire le référentiel à chaque recomposition ferait sauter
            // une image sur deux pendant un balayage.
            val mushafPage = remember(page, pages) { pages.page(page) }

            // Le gestionnaire de gestes est installé **une seule fois** — c'est la raison
            // d'être des objets d'état déclarés plus haut. Il doit donc relire la page à
            // chaque appui long : capturer `mushafPage` directement figerait les rectangles
            // sur la page du premier rendu, et après un balayage l'appui long désignerait un
            // verset de la page précédente — avec une fiche qui paraîtrait juste.
            val currentPage = rememberUpdatedState(mushafPage)

            val fitted = ReaderLayout.fitMushafPage(
                availableWidth = availableWidth.toDouble(),
                availableHeight = availableHeight.toDouble(),
                // Le rapport de la **source affichée** : la page embarquée et celle du paquet
                // n'ont pas le même. Utiliser l'un pour l'autre déformerait la page.
                sourceWidth = mushafPage.sourceWidth,
                sourceHeight = mushafPage.sourceHeight,
            )
            val pageWidth = fitted.width.toFloat()
            val pageHeight = fitted.height.toFloat()

            PreloadMushafPages(
                page = page,
                source = pages,
                widthPx = (pageWidth - ReaderLayout.FRAME).roundToInt(),
                heightPx = (pageHeight - ReaderLayout.FRAME).roundToInt(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Rejoué quand la géométrie change : les coordonnées d'appui en
                    // dépendent, et une rotation doit les recalculer.
                    .pointerInput(pageWidth, pageHeight) {
                        readerGesture(
                            zoomed = { zoomState.value.scale > ReaderGesture.ZOOMED_THRESHOLD },
                            onTransform = { centroid, pan, factor ->
                                val current = zoomState.value
                                val anchored = ReaderZoomGeometry.zoomAt(
                                    current = current,
                                    scale = current.scale * factor,
                                    anchorX = centroid.x,
                                    anchorY = centroid.y,
                                    width = pageWidth,
                                    height = pageHeight,
                                )
                                zoomState.value = ReaderZoomGeometry.constrain(
                                    scale = anchored.scale,
                                    x = anchored.x + pan.x,
                                    y = anchored.y + pan.y,
                                    width = pageWidth,
                                    height = pageHeight,
                                )
                            },
                            onPan = { delta ->
                                val current = zoomState.value
                                zoomState.value = ReaderZoomGeometry.constrain(
                                    scale = current.scale,
                                    x = current.x + delta.x,
                                    y = current.y + delta.y,
                                    width = pageWidth,
                                    height = pageHeight,
                                )
                            },
                            onTap = { position ->
                                // Le rappel est lu une fois : c'est lui qui décide si l'appui
                                // peut enregistrer quelque chose. Le mode de pose n'est armable
                                // que si l'enregistrement est branché — l'entrée du panneau
                                // n'existe que dans ce cas — et l'écrire ici rend l'invariant
                                // visible plutôt que de s'y fier.
                                val save = onSaveBookmark
                                when {
                                    // En mode de pose, l'appui a un sens **déclaré** : il
                                    // désigne le verset à marquer. Un appui qui ne désigne aucun
                                    // verset ne fait rien et laisse le mode armé — c'est ce que
                                    // dit la notice, et enregistrer autre chose serait pire que
                                    // de ne rien faire.
                                    bookmarkMode && save != null -> {
                                        val current = zoomState.value
                                        val shown = currentPage.value
                                        val touched = ReaderTouch.verseAt(
                                            x = position.x.toDouble(),
                                            y = position.y.toDouble(),
                                            zoom = current,
                                            availableWidth = availableWidth.toDouble(),
                                            availableHeight = availableHeight.toDouble(),
                                            pageWidth = pageWidth.toDouble(),
                                            pageHeight = pageHeight.toDouble(),
                                            rows = shown.rows,
                                            sourceWidth = shown.sourceWidth,
                                            sourceHeight = shown.sourceHeight,
                                        )
                                        if (touched != null) {
                                            save(touched)
                                            bookmarkMode = false
                                            savedNotice = true
                                        }
                                    }

                                    // Une fiche de verset ouverte se ferme au premier appui :
                                    // c'est ce que la fiche annonce, et un appui qui ne ferait
                                    // que masquer la coquille laisserait la fiche en place.
                                    verseState.value != null -> verseState.value = null

                                    else -> chromeState.value = !chromeState.value
                                }
                            },
                            onLongPress = { position ->
                                // Le doigt est dans le repère de l'écran, le verset se cherche
                                // dans celui de la page : le dé-zoom, le dé-centrage et le
                                // passage à l'espace de la source sont la règle de
                                // `ReaderTouch`, éprouvée dans `core:domain`. Elle est partagée
                                // avec le mode de pose, qui fait exactement le même calcul — et
                                // deux copies auraient fini par désigner deux versets différents.
                                val shown = currentPage.value
                                val touched = ReaderTouch.verseAt(
                                    x = position.x.toDouble(),
                                    y = position.y.toDouble(),
                                    zoom = zoomState.value,
                                    availableWidth = availableWidth.toDouble(),
                                    availableHeight = availableHeight.toDouble(),
                                    pageWidth = pageWidth.toDouble(),
                                    pageHeight = pageHeight.toDouble(),
                                    rows = shown.rows,
                                    sourceWidth = shown.sourceWidth,
                                    sourceHeight = shown.sourceHeight,
                                )
                                verseState.value = touched
                                // Le panneau s'ouvre **dans le même geste**, comme dans le
                                // client d'origine, où l'appui long fait `setSelectedVerse(id)`
                                // puis `setSessionPanel('verse')` : l'appui désigne un verset, et
                                // l'écran doit aussitôt dire ce qu'on peut en faire. Un verset
                                // introuvable n'ouvre rien — un panneau décrivant « Verset 0 »
                                // serait pire que pas de panneau.
                                panel = if (touched != null && VerseActionsText.isUseful(verseActions)) {
                                    ReaderPanel.VERSE
                                } else {
                                    ReaderPanel.NONE
                                }
                            },
                            onSwipe = { dx, dy ->
                                // Le seuil, le rapport et la borne viennent du domaine.
                                val current = pageState.intValue
                                val next = PageNavigation.pageAfterSwipe(current, dx, dy, totalPages)
                                if (next != current) {
                                    pageState.intValue = next
                                    // Changer de page remet le zoom à la page entière :
                                    // garder l'agrandissement d'une autre page n'aurait aucun
                                    // sens et donnerait l'impression d'un lecteur perdu.
                                    zoomState.value = ReaderZoom()
                                    verseState.value = null
                                }
                            },
                        )
                    },
            ) {
                MushafPageView(
                    page = mushafPage,
                    zoom = zoom,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    selectedVerse = selectedVerse,
                    bookmarkIds = bookmarkIds,
                    difficultIds = difficultIds,
                )

                // La notice du mode de pose, au-dessus de la page et non dans la coquille :
                // elle décrit un geste à faire **sur la page**, et une coquille masquée
                // l'emporterait avec elle. Un `Text` sans gestionnaire de pointeurs ne capte
                // aucun toucher : le verset qu'elle invite à toucher reste atteignable dessous.
                notice?.let { text ->
                    Text(
                        text = text,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = 10.dp, end = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.soft)
                            .padding(10.dp),
                        color = colors.green,
                        fontSize = AppTheme.typeScale.metadata,
                    )
                }
            }
        }

        // Le mini-lecteur prend sa hauteur, comme la coquille : la page reste entière, et le
        // dernier verset ne passe jamais dessous.
        if (audioState.isOpen) {
            MiniPlayer(
                state = audioState,
                reciterName = reciter.name,
                countLabel = countLabel,
                onToggle = { audio.toggle() },
                onStop = { audio.close() },
            )
        }

        // La fiche du verset cède la place au panneau des actions : le panneau porte déjà le
        // numéro du verset, et deux affichages superposés donneraient un texte faux — la fiche
        // annonce qu'un appui sur la page la referme, alors que le voile du panneau capterait ce
        // toucher. Elle réapparaît à la fermeture, avec sa traduction.
        selectedVerse?.takeIf { panel != ReaderPanel.VERSE }?.let { verseId ->
            VerseCard(verseId = verseId)
        }

        if (chromeVisible) {
            ReaderChrome(
                page = page,
                totalPages = totalPages,
                surahName = surah?.name ?: DEFAULT_SURAH_NAME,
                zoomed = zoom.scale > ReaderGesture.ZOOMED_THRESHOLD,
                onPage = goToPage,
                onClose = onClose,
                onResetZoom = { zoomState.value = ReaderZoom() },
                onOpenSourcePicker = onOpenSourcePicker,
                onListen = listenAction,
                onOpenOptions = openOptions,
                onOpenBookmarks = openBookmarks,
                bookmarkActive = bookmarkMode,
            )
        }
    }

    // Le panneau ouvert. Un seul à la fois : refermer le sélecteur rend la main à la feuille
    // d'options, comme dans le client d'origine, et non au lecteur.
    when (panel) {
        ReaderPanel.NONE -> Unit

        ReaderPanel.OPTIONS -> ReaderOptionsSheet(
            onClose = { panel = ReaderPanel.NONE },
            onSurah = { panel = ReaderPanel.SURAH },
            onTranslation = { panel = ReaderPanel.TRANSLATION },
            onAudio = { panel = ReaderPanel.AUDIO },
            // Le client d'origine referme la feuille **puis** ouvre le choix de présentation :
            // les deux ne s'empilent pas, et deux voiles superposés assombriraient l'écran.
            onDisplay = onOpenSourcePicker?.let { open ->
                {
                    panel = ReaderPanel.NONE
                    open()
                }
            },
        )

        ReaderPanel.AUDIO -> AudioSettingsSheet(
            settings = settings,
            reciter = reciter,
            // Chaque appui remonte les réglages au contrôleur, qui les applique à la séance en
            // cours : c'est le comportement du client d'origine, où les réglages sont relus à
            // chaque rendu. Un réglage qui ne vaudrait qu'après avoir relancé la séance serait
            // un piège, puisque rien à l'écran ne le dirait.
            onSettings = {
                regleParLaPersonne = true
                settings = it
                audio.updateSettings(it)
                onAudioSettingsChanged(it, reciterId)
            },
            onReciter = {
                regleParLaPersonne = true
                reciterId = it.id
                onAudioSettingsChanged(settings, it.id)
            },
            // Lancer part de la plage de la **page** affichée : c'est ce qu'on voit, et c'est ce
            // que le bouton annonce. `null` quand la plage n'est pas connue — le bouton est
            // alors absent, plutôt que présent et sans effet.
            onLaunch = listenAction,
            // Redémarrer repart de la plage de la **séance** ouverte, et non de la page : si
            // l'auditeur a tourné la page pendant l'écoute, la séance ne l'a pas suivi. Le
            // client d'origine reprend `rangeRef.current`, et le contrôleur la connaît.
            onRestart = audioState.range?.let { session -> { audio.start(session, settings) } },
            onClose = { panel = ReaderPanel.NONE },
        )

        ReaderPanel.TRANSLATION -> {
            // Les lignes sont calculées **ici**, et non à la composition du lecteur : la table
            // de traduction pèse 1,5 Mo, et une personne qui n'ouvre jamais ce panneau n'a pas à
            // en payer la lecture. Les versets, eux, viennent de la plage de la page affichée,
            // dans le découpage de la source — la règle vit dans `TranslationPanel`, éprouvée
            // dans `core:domain`.
            //
            // `session = null` : aucune séance n'existe encore dans ce client. Le jour où la
            // phase C en ouvrira une, c'est ici qu'elle se branchera — et la règle est déjà
            // écrite pour la recevoir.
            val rows = remember(page, source) {
                runCatching { MushafSourceNavigation.pageRange(source, page) }
                    .map { TranslationPanel.rows(session = null, page = it) }
                    .getOrDefault(emptyList())
            }
            TranslationPanelSheet(rows = rows, onClose = { panel = ReaderPanel.NONE })
        }

        ReaderPanel.BOOKMARKS -> BookmarksSheet(
            // Armer le mode de pose **referme** le panneau : c'est ce que fait le client
            // d'origine, et sans cela le voile resterait devant la page qu'on demande de
            // toucher.
            onPlace = onSaveBookmark?.let {
                {
                    panel = ReaderPanel.NONE
                    bookmarkMode = true
                }
            },
            onOpenList = onOpenBookmarks?.let { open ->
                {
                    panel = ReaderPanel.NONE
                    open()
                }
            },
            onClose = { panel = ReaderPanel.NONE },
        )

        ReaderPanel.VERSE -> selectedVerse?.let { verseId ->
            // Le numéro **dans sa sourate**, tel que la fiche l'affiche : c'est le même calcul,
            // et le panneau doit dire la même chose que la fiche. Une référence non résoluble ne
            // dessine rien — le panneau ne s'ouvre alors pas du tout, ce que le geste a déjà
            // décidé.
            val ayah = runCatching { Quran.verseAt(verseId) }.getOrNull()?.ayah
            if (ayah != null) {
                VerseActionsSheet(
                    ayah = ayah,
                    // Le marqueur de l'**élève**, et non `difficultIds` : c'est ce que le libellé
                    // annonce, et ce que la bascule écrit.
                    markedByUser = verseId in userMarkedIds,
                    onClose = { panel = ReaderPanel.NONE },
                    // Écouter et répéter **referment** le panneau et effacent la fiche : c'est ce
                    // que fait le client d'origine, où les deux passent par `audioAction`, qui
                    // termine par `setSelectedVerse(null)`. Sans cela, la fiche du verset
                    // réapparaîtrait sous le lecteur audio.
                    onListen = {
                        // Le client d'origine force ici `count:1, mode:'passage', autoStop:true`
                        // sur une plage d'**un seul** verset, puis démarre. Ces trois réglages
                        // sont écrits, et pas seulement appliqués à la séance : c'est ce que fait
                        // `setCountChoice(1)`, dont l'effet d'enregistrement suit.
                        val single = settings.copy(
                            countChoice = AudioCount.ONE,
                            mode = RepeatMode.PASSAGE,
                            autoStop = true,
                        )
                        regleParLaPersonne = true
                        settings = single
                        audio.start(Range(verseId, verseId), single)
                        onAudioSettingsChanged(single, reciterId)
                        verseState.value = null
                        panel = ReaderPanel.NONE
                    },
                    onRepeat = {
                        // Le client d'origine ne lance **rien** ici : il met le mode de répétition
                        // sur « passage » et ouvre les réglages d'écoute, où l'on choisit le nombre
                        // d'écoutes. C'est « Écouter » qui lance.
                        val passage = settings.copy(mode = RepeatMode.PASSAGE)
                        regleParLaPersonne = true
                        settings = passage
                        audio.updateSettings(passage)
                        onAudioSettingsChanged(passage, reciterId)
                        verseState.value = null
                        panel = ReaderPanel.AUDIO
                    },
                    // Le marquage ne referme **pas** le panneau, et n'efface pas la fiche : c'est
                    // ce que fait le client d'origine, et c'est ce qui permet au libellé de
                    // basculer sous les yeux — l'état observé par la route republie
                    // `userMarkedIds`, et l'entrée se met à annoncer le retrait.
                    onMark = onMarkDifficulty?.let { mark -> { mark(verseId) } },
                )
            }
        }

        ReaderPanel.SURAH -> SurahPickerScreen(
            // Un référentiel non chargé ne fait pas tomber l'écran : la première sourate est
            // surlignée et la liste est vide, ce que le sélecteur sait déjà montrer.
            currentSurah = surah?.number ?: 1,
            currentPage = page,
            onPage = { target ->
                goToPage(target)
                panel = ReaderPanel.NONE
            },
            onSelect = { chosen ->
                // Le client d'origine arrête l'écoute et efface la fiche du verset avant de
                // changer de sourate : sans cela, on continuerait d'écouter — ou de décrire —
                // un verset d'une sourate qu'on vient de quitter.
                val target = runCatching {
                    MushafSourceNavigation.versePage(source, chosen.start)
                }.getOrNull()
                // Une conversion qui échoue laisse le lecteur où il est. Sauter à une page
                // devinée serait pire : rien ne dirait qu'elle est fausse.
                if (target != null) {
                    audio.close()
                    goToPage(target)
                }
                panel = ReaderPanel.NONE
            },
            // Refermer le sélecteur rend la main à la feuille d'options, et non au lecteur :
            // c'est de là qu'on venait.
            onClose = { panel = ReaderPanel.OPTIONS },
            totalPages = totalPages,
        )
    }

    // La feuille de validation, par-dessus le lecteur. Elle ne s'ouvre que par le
    // bandeau, donc elle suppose la même condition : une séance servie, et de quoi
    // l'écrire.
    if (completionOpen && seance != null && onValidateStudy != null) {
        StudyCompletionSheet(
            learning = seance.learning,
            range = seance.range,
            through = seance.through,
            source = seance.source,
            // La page affichée : c'est elle qui propose le point d'arrêt par défaut,
            // puisque c'est ce que la personne vient de finir de lire.
            currentPage = page,
            onClose = { completionOpen = false },
            onValidate = onValidateStudy,
        )
    }
}

/**
 * Fiche du verset touché : référence et traduction française du sens.
 *
 * Elle apparaît à l'appui long, et se ferme au prochain appui sur la page. Volontairement
 * discrète : elle ne doit pas couvrir la page qu'on est en train de lire.
 */
@Composable
private fun VerseCard(verseId: Int) {
    val colors = AppTheme.colors
    val surah = runCatching { Quran.surahAt(verseId) }.getOrNull()
    val verse = runCatching { Quran.verseAt(verseId) }.getOrNull()
    val translation = remember(verseId) { ReaderData.frenchVerse(verseId) }

    AppCard(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = listOfNotNull(surah?.name, verse?.let { "verset ${it.ayah}" }).joinToString(" · "),
                fontSize = AppTheme.typeScale.secondary,
                color = colors.gold,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = translation?.translation ?: "Traduction indisponible pour ce verset.",
                fontSize = AppTheme.typeScale.body,
                color = colors.text,
            )
            translation?.footnotes?.takeIf { it.isNotBlank() }?.let { footnote ->
                Text(
                    text = footnote,
                    fontSize = AppTheme.typeScale.metadata,
                    color = colors.muted,
                )
            }
            Text(
                text = "Toucher la page pour fermer",
                fontSize = AppTheme.typeScale.metadata,
                color = colors.muted,
            )
        }
    }
}

/**
 * La sourate au début d'une page : c'est ce qu'on cherche en tournant une page.
 *
 * La page est lue dans le découpage de la source affichée : la première page du paquet
 * « Coran 1441 » ne porte pas le même verset que la première page du moushaf de Médine.
 *
 * `null` quand elle ne peut pas être déterminée — référentiel non chargé, page hors bornes.
 * C'est à l'appelant de décider quoi montrer alors ; rendre un nom inventé ferait croire à une
 * page juste.
 */
private fun surahFor(source: MushafSource, page: Int): Surah? = runCatching {
    Quran.surahAt(MushafSourceNavigation.pageRange(source, page).start)
}.getOrNull()

/** Ce qu'affiche la coquille quand la sourate n'est pas déterminable. */
private const val DEFAULT_SURAH_NAME = "Le Coran"

/**
 * Le panneau ouvert par-dessus le lecteur. Un seul à la fois.
 *
 * ## Un écart assumé : la fiche du verset survit à la fermeture
 *
 * Dans le client d'origine, un **seul** gestionnaire ferme les six panneaux, et il efface au
 * passage le verset sélectionné : `setSessionPanel(null); setSelectedVerse(null)`. Ici, la fiche
 * du verset vit de son côté, et fermer un panneau la laisse à l'écran. Rien n'est perdu ni
 * menti — la fiche dit elle-même qu'un appui sur la page la referme — et la personne retrouve le
 * verset qu'elle venait de toucher. La rattacher à la fermeture des panneaux ferait
 * disparaître une information qu'aucun d'eux n'a remplacée.
 *
 * ## Le panneau des actions est le seul ouvert *avec* la fiche
 *
 * L'appui long pose le verset sélectionné **et** ouvre [VERSE], comme le client d'origine.
 * Mais la fiche n'est alors pas dessinée : le panneau porte déjà le numéro du verset, et le
 * voile capterait le toucher que la fiche annonce. Elle réapparaît à la fermeture — le
 * panneau ne fait donc que la recouvrir, il ne la remplace pas.
 */
private enum class ReaderPanel {
    /** Aucun : le lecteur est nu. */
    NONE,

    /** La feuille « Plus d'options ». */
    OPTIONS,

    /** Le sélecteur de sourate. */
    SURAH,

    /** Les réglages d'écoute. */
    AUDIO,

    /** La traduction française de la page. */
    TRANSLATION,

    /** Les marques-pages : en poser un, ou ouvrir la liste. */
    BOOKMARKS,

    /** Les actions du verset touché : écouter, répéter, marquer. */
    VERSE,
}

/**
 * Comment les réglages d'écoute survivent à une rotation.
 *
 * Le lecteur est recréé quand l'écran tourne. Sans cette sauvegarde, choisir un récitateur puis
 * tourner le téléphone le ramènerait au premier de la liste — alors que la page, elle, est
 * conservée, et que la séance d'écoute, elle, continue. Les six valeurs sont des primitives :
 * elles se posent directement dans le sac d'état, sans passer par du JSON.
 *
 * Une restauration qui échoue rend `null`, et Compose repart de la valeur initiale. Un état
 * abîmé ne doit pas empêcher le lecteur de s'afficher.
 */
private val AudioSessionSaver: Saver<AudioSession, Any> = listSaver(
    save = { session ->
        listOf(
            session.countChoice.name,
            session.customCount,
            session.mode.name,
            session.gapSeconds,
            session.speed,
            session.autoStop,
        )
    },
    restore = { values ->
        runCatching {
            AudioSession(
                countChoice = AudioCount.valueOf(values[0] as String),
                customCount = values[1] as String,
                mode = RepeatMode.valueOf(values[2] as String),
                gapSeconds = values[3] as Int,
                speed = values[4] as Float,
                autoStop = values[5] as Boolean,
            )
        }.getOrNull()
    },
)
