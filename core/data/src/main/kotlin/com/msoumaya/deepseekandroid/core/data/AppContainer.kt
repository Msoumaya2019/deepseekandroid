package com.msoumaya.deepseekandroid.core.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.msoumaya.deepseekandroid.core.data.local.BlobFile
import com.msoumaya.deepseekandroid.core.data.local.DailyContentCacheStore
import com.msoumaya.deepseekandroid.core.data.local.GuestFavoritesStore
import com.msoumaya.deepseekandroid.core.data.local.HttpArchiveTransport
import com.msoumaya.deepseekandroid.core.data.local.JsonFileStore
import com.msoumaya.deepseekandroid.core.data.local.LocalStateStore
import com.msoumaya.deepseekandroid.core.data.local.Outbox
import com.msoumaya.deepseekandroid.core.data.local.OutboxStore
import com.msoumaya.deepseekandroid.core.data.local.ProblemReportStore
import com.msoumaya.deepseekandroid.core.data.local.QuizCacheStore
import com.msoumaya.deepseekandroid.core.data.local.QuizOutbox
import com.msoumaya.deepseekandroid.core.data.local.QuizOutboxStore
import com.msoumaya.deepseekandroid.core.data.local.QuranArchiveInstaller
import com.msoumaya.deepseekandroid.core.data.local.RecitationNoticeStore
import com.msoumaya.deepseekandroid.core.data.local.RecitationStore
import com.msoumaya.deepseekandroid.core.data.remote.AuthGateway
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.RemoteStateSource
import com.msoumaya.deepseekandroid.core.data.remote.SessionPreferences
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseAuthGateway
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseConfig
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseDailyContentFavoriteSource
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseDailyContentMediaSource
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseDailyContentSource
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseProvider
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseProblemReportSender
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseQuizSource
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseRecitationSource
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseRecitationUploader
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseSocialSource
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseStateSource
import com.msoumaya.deepseekandroid.core.data.remote.UnavailableAuthGateway
import com.msoumaya.deepseekandroid.core.data.remote.VaultSessionManager
import com.msoumaya.deepseekandroid.core.data.repository.AudioSettingsRepository
import com.msoumaya.deepseekandroid.core.data.repository.AuthRepository
import com.msoumaya.deepseekandroid.core.data.repository.DailyContentFavoritesRepository
import com.msoumaya.deepseekandroid.core.data.repository.DailyContentMediaRepository
import com.msoumaya.deepseekandroid.core.data.repository.DailyContentRepository
import com.msoumaya.deepseekandroid.core.data.repository.ProblemReportRepository
import com.msoumaya.deepseekandroid.core.data.repository.QuizRepository
import com.msoumaya.deepseekandroid.core.data.repository.QuranArchiveStore
import com.msoumaya.deepseekandroid.core.data.repository.RecitationRepository
import com.msoumaya.deepseekandroid.core.data.repository.SocialRepository
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.data.security.SecretVault
import com.msoumaya.deepseekandroid.core.data.system.ConnectivityObserver
import com.msoumaya.deepseekandroid.core.data.system.ForegroundObserver
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranArchive
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.domain.StoredAudioSettings
import com.msoumaya.deepseekandroid.core.audio.AudioOutput
import com.msoumaya.deepseekandroid.core.audio.AudioRecorder
import com.msoumaya.deepseekandroid.core.audio.RecitationPlayer
import com.msoumaya.deepseekandroid.core.playback.AudioSessionHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

// ---------------------------------------------------------------------------
// Conteneur d'injection
// ---------------------------------------------------------------------------
// Un conteneur écrit à la main, sans bibliothèque. Pour un projet tenu par une seule personne,
// Hilt ou Koin ajouteraient une génération de code, une version de plugin à suivre et une
// couche d'indirection à franchir à chaque lecture de trace — pour remplacer un graphe de dix
// objets qui se lit ici d'un seul écran.
//
// Le conteneur vit dans `core:data` et non dans `:app` parce que les fonctionnalites doivent y
// accéder, et qu'aucune fonctionnalite ne peut dependre de `:app` — ce serait un cycle. Il est
// publie par un `CompositionLocal` : c'est le seul mecanisme qui traverse la frontiere d'un
// `@Composable` sans faire passer le graphe en parametre de chaque ecran.
// ---------------------------------------------------------------------------

/** Magasin de réglages simples de la session. La délégation doit être au niveau du fichier. */
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Le graphe d'objets de l'application, construit une fois au démarrage.
 *
 * **Le chargement du référentiel coranique n'est pas supposé réussir.** Il lit des ressources
 * embarquées ; si elles manquent, toutes les règles du domaine deviennent fausses — pas
 * seulement indisponibles. L'échec est donc capturé et exposé ([quranFailure]) au lieu de faire
 * tomber l'application : l'écran peut alors dire ce qui ne va pas, ce qu'un plantage au
 * démarrage ne permet pas.
 *
 * **Le client Supabase peut être absent.** [supabase] vaut [SupabaseConfig.PLACEHOLDER] tant que
 * la clé publique n'est pas renseignée. L'application fonctionne alors entièrement hors ligne,
 * et les dépôts le savent : [AuthGateway] est remplacé par [UnavailableAuthGateway] et le dépôt
 * distant par `null`. C'est le mode de fonctionnement normal tant que la clé manque, pas une
 * panne.
 *
 * @param context contexte, réduit au contexte applicatif dès la construction.
 * @param supabase paramètres de connexion. Par défaut, le projet partagé sans clé publique.
 */
class AppContainer(
    context: Context,
    private val supabase: SupabaseConfig = SupabaseConfig.PLACEHOLDER,
    /**
     * Le lecteur natif, construit par `:app`.
     *
     * Il est **reçu**, et non construit ici : `ExoAudioOutput` demande un `Context` et Media3,
     * et le conteneur doit rester éprouvable sans appareil. Un défaut `null` laisse le
     * conteneur utilisable dans les tests qui n'écoutent rien.
     */
    audioOutput: AudioOutput? = null,
    /**
     * Le lecteur d'une récitation enregistrée, construit par `:app`.
     *
     * Il est **distinct** du lecteur d'enchaînement, et reçu pour la même raison que lui :
     * `ExoRecitationPlayer` demande un `Context` et Media3, et le conteneur doit rester éprouvable
     * sans appareil. Un défaut `null` laisse le conteneur utilisable dans les tests qui n'écoutent
     * rien — l'écran des récitations sait alors qu'il n'y a rien à piloter, et n'offre pas de
     * bouton de lecture.
     */
    val recitationPlayer: RecitationPlayer? = null,
    /**
     * L'enregistreur natif, construit par `:app`.
     *
     * Reçu pour la même raison que les deux lecteurs : `MediaRecorder` ne tourne pas sur la JVM,
     * et le conteneur doit rester éprouvable sans appareil. Un défaut `null` laisse le conteneur
     * utilisable dans les tests qui n'enregistrent rien — l'appelant sait alors qu'il n'y a pas de
     * micro à ouvrir, et n'offre pas « Ma voix » au lieu de l'offrir sans effet.
     *
     * Il est **distinct** des deux lecteurs, et pour une raison plus forte que leur séparation
     * mutuelle : il ne partage avec eux ni le matériel ni le cycle de vie. Un enregistreur occupé
     * ne doit pas empêcher d'écouter, et une lecture en cours ne doit pas empêcher de capter — le
     * client d'origine arrête d'ailleurs l'audio actif au moment de commencer, ce qui montre bien
     * que les deux peuvent se disputer le même haut-parleur.
     */
    val recorder: AudioRecorder? = null,
    /**
     * La version de l'application, telle qu'elle part dans un signalement de problème.
     *
     * Elle est **reçue**, et non lue ici : `core:data` n'a pas de `BuildConfig`, et le seul module
     * qui connaisse le paquet est `:app`. C'est la même règle que pour les lecteurs natifs — ce
     * qui demande le contexte d'une application est construit par elle.
     *
     * **Le défaut est vide, et il est inerte.** La colonne `app_version` est bornée à 32
     * caractères et n'exige rien de plus (`supabase/problem-reports.sql`, ligne 8) : une version
     * absente ne fait donc pas refuser le signalement, elle le prive seulement d'un renseignement
     * de diagnostic. C'est le bon défaut pour un conteneur construit sans application — et
     * `:app`, lui, fournit toujours la vraie.
     */
    private val appVersion: String = "",
) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, ROOT_DIRECTORY)

    /**
     * Portée du conteneur, pour les travaux qui vivent aussi longtemps que l'application.
     *
     * `SupervisorJob` : l'échec du chargement du référentiel ne doit pas annuler les autres
     * travaux du conteneur. `Dispatchers.Default` et non `IO` : analyser du JSON est un travail
     * de calcul, pas d'attente disque.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * La séance d'écoute de l'application — **une seule**, pour toute la vie du processus.
     *
     * Elle vivait dans un `remember` du lecteur et mourait avec l'écran : quitter la page
     * coupait la récitation, et y revenir la faisait repartir du premier verset. Elle est ici
     * parce que le conteneur vit aussi longtemps que l'application, et que la portée est
     * exactement ce qui décidait du défaut.
     *
     * Elle est déclarée **après** [scope], dont elle dépend : l'initialiser plus haut compilerait
     * à tort si `scope` devenait un jour `const`, et l'ordre dit la dépendance.
     *
     * `null` quand aucun lecteur natif n'a été fourni : les tests qui n'écoutent rien n'ont
     * pas à en construire un, et les appelants doivent alors savoir qu'il n'y a rien à piloter.
     */
    val playback: AudioSessionHolder? = audioOutput?.let { AudioSessionHolder(it, scope) }

    // -----------------------------------------------------------------------
    // Stockage local
    // -----------------------------------------------------------------------

    /** État applicatif : un fichier par compte, plus l'état anonyme. */
    val localState: LocalStateStore = LocalStateStore(root)

    /** Propriétaire courant, relu au démarrage. Partagé par l'authentification et l'état. */
    private val session: OwnerStore = SessionPreferences(appContext.sessionDataStore)

    /** File d'attente des modifications faites hors ligne. */
    private val outbox: OutboxStore = OutboxStore(
        store = JsonFileStore(
            file = File(root, "outbox.json"),
            serializer = Outbox.serializer(),
            default = { Outbox() },
        ),
        ownerId = { session.currentOwner() },
        nowIso = { Dates.nowIso() },
        newId = { UUID.randomUUID().toString() },
    )

    // -----------------------------------------------------------------------
    // Réseau
    // -----------------------------------------------------------------------

    /**
     * Client Supabase, ou `null` si aucun projet n'est configuré.
     *
     * Construit seulement quand la clé publique est présente : instancier un client avec une
     * clé vide ne produirait pas une erreur claire au démarrage, mais une suite d'appels
     * refusés difficiles à rattacher à leur cause.
     */
    private val supabaseClient = if (supabase.isConfigured) {
        SupabaseProvider.create(
            config = supabase,
            sessionManager = VaultSessionManager(
                vault = SecretVault(),
                blob = BlobFile(File(root, "session.bin")),
            ),
        )
    } else {
        null
    }

    /** Accès à l'état distant, ou `null` hors configuration. */
    private val remoteState: RemoteStateSource? =
        supabaseClient?.let { SupabaseStateSource(it) }

    // -----------------------------------------------------------------------
    // Dépôts
    // -----------------------------------------------------------------------

    /** Authentification. Bascule sur un guichet indisponible si le projet n'est pas configuré. */
    val auth: AuthRepository = AuthRepository(
        gateway = supabaseClient?.let { SupabaseAuthGateway(it) } ?: UnavailableAuthGateway,
        session = session,
    )

    /** L'état affichable : local d'abord, synchronisé ensuite. */
    val userState: UserRepository = UserRepository(
        stores = localState,
        session = session,
        outbox = outbox,
        remote = remoteState,
    )

    /**
     * Les amis, les cercles et l'aperçu des conversations.
     *
     * Il est construit **sans source** quand aucun projet n'est configuré : le dépôt sait alors
     * répondre « personne de connecté » sans qu'aucune requête ne parte, et l'écran affiche
     * l'invitation à se connecter plutôt qu'une erreur réseau — qui serait fausse, puisqu'il n'y
     * a pas de réseau à atteindre.
     */
    val social: SocialRepository = SocialRepository(
        source = supabaseClient?.let { SupabaseSocialSource(it) },
        session = session,
        scope = scope,
    )

    /**
     * Le Quiz : question du jour, défis entre amis, statistiques.
     *
     * Même règle que pour l'espace social : construit **sans source** quand aucun projet n'est
     * configuré, il répond « personne de connecté » sans qu'aucune requête ne parte.
     *
     * **Sa file d'attente est distincte de celle de l'état applicatif**, et ce n'est pas un
     * doublon. La file commune rend ses opérations par compte **sans filtrer leur nature**, et le
     * worker de synchronisation les envoie toutes comme des instantanés d'état : une réponse de
     * Quiz versée dedans serait poussée comme un état complet — acceptée par le serveur, et une
     * progression écrasée. La raison complète est dans `QuizOutboxStore`.
     */
    val quiz: QuizRepository = QuizRepository(
        source = supabaseClient?.let { SupabaseQuizSource(it) },
        session = session,
        cache = QuizCacheStore(root),
        outbox = QuizOutboxStore(
            store = JsonFileStore(
                file = File(root, "quiz_outbox.json"),
                serializer = QuizOutbox.serializer(),
                default = { QuizOutbox() },
            ),
            nowIso = { Dates.nowIso() },
        ),
        scope = scope,
    )

    /**
     * Les signalements de problème : la boîte d'envoi, et son dépôt.
     *
     * Construit **sans expéditeur** quand aucun projet n'est configuré : le dépôt sait alors
     * garder le signalement et le dire, sans qu'aucune requête ne parte — c'est la même règle que
     * pour l'espace social, le Quiz et les récitations.
     *
     * **Sa lecture du réseau est celle du bandeau, et c'est une référence en avant.** Le dépôt
     * reçoit une lambda qui lit l'état de l'observateur — déclaré **plus bas** —, et non une
     * lecture `NetInfo` de plus : deux lectures de la même vérité finiraient par diverger, et le
     * bandeau pourrait annoncer une coupure pendant que la file essaie quand même. La lambda n'est
     * appelée qu'au moment d'un envoi, donc bien après la construction : la déclaration plus bas
     * de l'observateur n'est pas un cycle, c'est un ordre de lecture.
     *
     * **L'observateur du réseau, lui, la rappelle** : c'est ce qui fait partir un signalement mis
     * de côté hors connexion dès que le réseau revient. Les deux se référencent donc l'un l'autre,
     * et c'est la même forme que celle du Quiz — à ceci près qu'elle est écrite à l'envers, parce
     * que le signalement a besoin de l'état du réseau **pendant** un geste, alors que le Quiz n'en
     * a besoin qu'au retour de celui-ci.
     */
    val problemReports: ProblemReportRepository = ProblemReportRepository(
        store = ProblemReportStore(root),
        sender = supabaseClient?.let { SupabaseProblemReportSender(it) },
        session = session,
        scope = scope,
        appVersion = appVersion,
        horsLigne = { connectivity.state.value.horsLigne },
    )

    /**
     * L'état du réseau, et le bandeau qui le montre.
     *
     * Il est **observé** et non interrogé à la demande : le bandeau doit s'allumer et s'éteindre
     * tout seul, et un écran qui poserait la question au moment de se dessiner ne verrait jamais
     * la coupure survenue pendant qu'il est ouvert.
     *
     * **Ce que le retour du réseau déclenche.** L'original appelle `flushPendingSync()` à cet
     * instant — la file des modifications d'état —, son propre observateur de Quiz relit à ce
     * moment-là, et son observateur de signalements en fait autant. Les trois sont donc appelés
     * ici, dans cet ordre : l'état d'abord, parce qu'une modification faite hors ligne est ce qui
     * risque de se perdre, et parce qu'une relecture du Quiz sur un état non poussé montrerait une
     * progression que le serveur n'a pas encore ; les signalements ensuite, et leur rang n'est
     * qu'une commodité — la boîte est indépendante des deux autres, et l'original la vide depuis
     * un écouteur à elle.
     *
     * **Ce qui reste, et qui est nommé pour ne pas être cru fait.** L'observateur du Quiz de
     * l'original se déclenche à **quatre** moments : à l'ouverture, au retour du réseau, au
     * passage au premier plan, et toutes les trente secondes. Le réseau passe par ici et le retour
     * au premier plan par [premierPlan] ; l'ouverture et le minuteur relèvent de la tranche du
     * Quiz, dont le dépôt décide quand relire (voir `QuizSource`). L'observateur des
     * **signalements** a les mêmes quatre moments, et **les quatre sont portés** : l'ouverture et
     * le minuteur par `ProblemReportRepository.start()`, le réseau par ici, et le retour au premier
     * plan par [premierPlan].
     *
     * **Aucun effet si le projet Supabase n'est pas configuré.** `sync` rend alors
     * `NotConfigured` sans qu'aucune requête ne parte, `refresh` fait de même, et `flush` rend
     * `false` sans expéditeur : le bandeau annonce une coupure de réseau qui est réelle, et la
     * reprise ne tente rien qui ne puisse aboutir.
     */
    val connectivity: ConnectivityObserver = ConnectivityObserver(
        context = appContext,
        scope = scope,
        onRestore = {
            userState.sync()
            quiz.refresh()
            problemReports.flush()
        },
    )

    /**
     * Les récitations enregistrées : le registre local, sa file de dépôt, et la lecture du
     * serveur.
     *
     * Construit **sans déposant ni lecteur** quand aucun projet n'est configuré : le dépôt sait
     * alors répondre « connexion requise » sans qu'aucune requête ne parte — c'est la même règle
     * que pour l'espace social et le Quiz, et elle évite d'annoncer une panne réseau là où il n'y
     * a simplement pas de réseau à atteindre.
     *
     * Le registre, ses fichiers audio et sa file de dépôt vivent sous `root`, **hors** de tout
     * dossier de compte : c'est le registre lui-même qui porte le `userId` de chaque ligne, et
     * c'est ce qui permet à une récitation enregistrée par un compte de rester sur l'appareil
     * quand un autre compte s'ouvre.
     */
    val recitations: RecitationRepository = RecitationRepository(
        store = RecitationStore(root),
        uploader = supabaseClient?.let { SupabaseRecitationUploader(it) },
        source = supabaseClient?.let { SupabaseRecitationSource(it) },
        session = session,
        scope = scope,
    )

    /**
     * Les contenus du jour : les rappels et les invocations.
     *
     * Construit **sans source** quand aucun projet n'est configuré : le dépôt publie alors le
     * document du disque et n'essaie rien — c'est la même règle que pour l'espace social, le Quiz,
     * les récitations et les signalements, et elle évite d'annoncer une panne réseau là où il n'y a
     * simplement pas de réseau à atteindre.
     *
     * **Il ne reçoit pas la session, et ce n'est pas un oubli.** Les contenus du jour sont
     * **publics** : `supabase/daily-contents.sql` ouvre leur lecture à `anon`, et le document est
     * donc le même pour tout le monde. L'identifiant du compte n'entrera ici qu'avec les
     * **favoris**, qui sont, eux, une table par compte — et c'est aussi pourquoi le cache n'est pas
     * un fichier par compte, contrairement à celui du Quiz.
     *
     * **Son cache vit à la racine de l'état**, à côté de celui des réglages d'écoute et pour une
     * raison plus forte : il ne porte aucun compte, donc il n'a rien à assainir et rien à déplacer
     * quand un compte s'ouvre ou se ferme.
     */
    val dailyContents: DailyContentRepository = DailyContentRepository(
        source = supabaseClient?.let { SupabaseDailyContentSource(it) },
        cache = DailyContentCacheStore(root),
        scope = scope,
    )

    /**
     * Les médias des contenus du jour : une vignette ou une écoute, signée puis retenue.
     *
     * Construit **sans source** quand aucun projet n'est configuré, comme le dépôt ci-dessus, et
     * avec la même conséquence : `resolve` rend alors chaque adresse **telle quelle**. C'est le
     * bon repli — sans projet il n'y a pas de racine pour reconnaître le compartiment, et refuser
     * une adresse retirerait de l'écran une vignette que l'original aurait gardée.
     *
     * **L'adresse passée est `supabase.url`, celle avec laquelle le client a été construit** — et
     * non `SupabaseConfig.PROJECT_URL`. Les deux valent la même chose sur une compilation
     * ordinaire, et c'est précisément ce qui rendrait la confusion invisible : `PROJECT_URL` est
     * le **repli** de `BuildConfig.SUPABASE_URL`, pas sa source. Composer la racine du
     * compartiment sur la constante ferait, sur une compilation pointée ailleurs, échouer la
     * reconnaissance de **toutes** les adresses ; l'adresse serait rendue telle quelle, la
     * vignette demanderait un objet privé sans signature, et l'écran montrerait une case vide sans
     * qu'aucune erreur n'ait été levée. Voir `DailyContentMedia` pour la règle complète.
     *
     * **Il ne reçoit pas la session**, comme le dépôt des contenus : les médias éditoriaux
     * n'appartiennent à personne, et `daily_media_active_read` ouvre leur lecture à `anon` tant
     * qu'un contenu actif cite l'objet. Un compte n'y changerait donc rien.
     */
    val dailyContentMedia: DailyContentMediaRepository = DailyContentMediaRepository(
        source = supabaseClient?.let { SupabaseDailyContentMediaSource(it, supabase.url) },
    )

    /**
     * Les favoris des contenus du jour, **par compte** — et par appareil pour un visiteur.
     *
     * **C'est le seul objet des contenus du jour qui reçoit la session, et c'est tout son objet.**
     * `content_favorites` est la seule table de cette fonctionnalité qui soit propre à un compte :
     * `supabase/daily-contents.sql` accorde `select,insert,update,delete` à `authenticated` et
     * **rien** à `anon`, et sa politique `favorites_own` compare `user_id` à `auth.uid()`. Le
     * dépôt a donc besoin de savoir **qui** lit, et c'est `session` qui le lui dit — relu à chaque
     * appel, sans observation de fond : rien n'est publié ici, et l'écran tient son propre état.
     *
     * **Le document du visiteur vit à la racine de l'état**, à côté de `daily-content.json`, et
     * non sous un dossier de compte : le visiteur n'a pas de compte, et c'est justement pour cela
     * que sa liste ne peut pas être serveur. La nommer d'après un propriétaire serait lui en
     * inventer un.
     *
     * Il ne rejoint pas la reprise du premier plan ni celle du réseau : l'original relit ses
     * favoris **à l'ouverture d'un écran** et au changement de compte, pas à un réveil de fond.
     * Un `refresh` ici demanderait une méthode qui n'existe pas dans l'original, et la fenêtre de
     * trente secondes rend la question sans objet pour un écran qui se rouvre.
     */
    val dailyContentFavorites: DailyContentFavoritesRepository = DailyContentFavoritesRepository(
        source = supabaseClient?.let { SupabaseDailyContentFavoriteSource(it) },
        guest = GuestFavoritesStore(root),
        session = session,
    )

    /**
     * Ce que l'appareil retient des notices de récitation déjà lues, **par compte**.
     *
     * Il est ici, à côté du registre, parce qu'il porte la même règle de partage : une récitation
     * appartient à un compte, et la notice qui dit ce qu'elle devient aussi. Le fichier vit à la
     * racine de l'état, comme celui du registre et pour la même raison — c'est le **document** qui
     * porte le compte, et non son emplacement. Un dossier par compte demanderait de déplacer les
     * fichiers au changement de compte, et une récitation enregistrée hors ligne se retrouverait
     * alors dans un dossier qui n'est plus le sien.
     */
    val recitationNotice: RecitationNoticeStore = RecitationNoticeStore(root)

    /**
     * Les réglages d'écoute de l'appareil.
     *
     * Ils ne sont **pas** par compte, et le fichier est donc à la racine de l'état plutôt que
     * dans un dossier de compte : la façon d'écouter tient à l'appareil et à l'oreille de celui
     * qui le tient, pas au compte ouvert. C'est aussi ce que fait le client d'origine, qui range
     * ces clés hors de toute notion d'utilisateur.
     */
    val audioSettings: AudioSettingsRepository = AudioSettingsRepository(
        store = JsonFileStore(
            file = File(root, "audio.json"),
            serializer = StoredAudioSettings.serializer(),
            default = { StoredAudioSettings() },
        ),
    )

    /**
     * Le retour au premier plan, et ce qu'il réveille.
     *
     * **Le seul moment qui réveille une file endormie sans attendre.** Le minuteur des
     * signalements passe toutes les trente secondes, et l'observateur du réseau part au retour de
     * la connexion : ni l'un ni l'autre ne sait que la personne vient de revenir. Une récitation
     * enregistrée hors connexion dans le métro, ou un signalement écrit en avion, attendent donc le
     * retour **réel** — c'est ce que l'original branche dans `App.tsx`
     * (`AppState.addEventListener('change', …)`), et ce que ce déclencheur porte.
     *
     * **Ce qu'il appelle, et dans quel ordre.** Les cinq lectures que ce portage réveille à ce
     * moment : l'état, les récitations, le Quiz, les signalements et les **contenus du jour**.
     * L'état vient **d'abord**, et c'est la règle de la reprise du réseau — relire le Quiz avant
     * aurait montré une progression que le serveur n'a pas encore reçue. Le rang des quatre autres
     * n'est qu'une commodité : leurs lectures sont indépendantes.
     *
     * **Les contenus du jour sont le cinquième, et l'original ne les met pas là.** Son
     * `TodayContents` recharge à l'ouverture, au retour au premier plan et au changement de date —
     * **pas** au retour du réseau. C'est ce dernier moment qui manque à ce portage : aucune tranche
     * ne porte encore le minuteur, et l'ouverture appartient à l'écran, qui n'existe pas. Le retour
     * au premier plan est donc, aujourd'hui, le seul moment où les contenus du jour sont relus — et
     * c'est ce qui fait que `ConnectivityObserver`, lui, ne les appelle pas.
     *
     * **L'échec ne remonte pas, et n'arrête pas non plus la séquence.** L'observateur absorbe
     * l'exception : un dépôt de fond qui échoue n'a rien à dire à la personne qui vient de revenir,
     * et ne doit pas faire tomber l'application. Un appel qui lève interrompt en revanche les
     * suivants, qui attendent la prochaine occasion — au plus tard le minuteur des signalements,
     * trente secondes plus tard. C'est la forme et la conséquence de la reprise du réseau.
     *
     * **Aucun effet si le projet Supabase n'est pas configuré.** Les cinq appels savent alors
     * répondre sans qu'aucune requête ne parte : `sync` rend `NotConfigured`, les deux `refresh`
     * font de même, et les deux files gardent leurs entrées.
     *
     * **Déclaré en dernier, et ce n'est pas un hasard.** Il réveille quatre objets déclarés
     * au-dessus de lui : il n'a donc besoin d'**aucune** référence en avant, contrairement à la
     * boîte des signalements qui lit l'observateur du réseau. Une lambda qui capturerait un objet
     * non encore construit compilerait et ne se verrait pas ; ici, l'ordre des déclarations suffit
     * à l'interdire.
     */
    val premierPlan: ForegroundObserver = ForegroundObserver(
        context = appContext,
        scope = scope,
        onReprise = {
            userState.sync()
            recitations.syncInBackground()
            quiz.refresh()
            problemReports.flush()
            dailyContents.refresh()
        },
    )

    // -----------------------------------------------------------------------
    // Référentiel coranique
    // -----------------------------------------------------------------------

    /**
     * État du chargement du référentiel.
     *
     * **Le chargement n'est pas fait dans le constructeur.** Il lit et analyse `verses.json`
     * (6 236 versets) et `meta.json` : sur le fil principal, cela retarderait la première image
     * de plusieurs centaines de millisecondes, et l'utilisateur verrait une fenêtre creme avant
     * que l'application n'apparaisse. Le travail part donc sur un fil d'arrière-plan et l'écran
     * affiche son propre etat d'attente.
     *
     * **Tant que le référentiel n'est pas pret, les regles du domaine mentent.** `Quran` rend
     * `QuranData.EMPTY` : `surahs` est vide, `verseAt` leve une exception. Aucun appelant ne
     * doit donc lire le domaine avant [QuranState.Ready] — c'est la raison d'etre de ce flux,
     * qui remplace un booleen que l'on aurait pu oublier de consulter.
     */
    private val _quranState = MutableStateFlow<QuranState>(QuranState.Loading)

    /** État du chargement du référentiel coranique. */
    val quranState: StateFlow<QuranState> = _quranState.asStateFlow()

    init {
        scope.launch {
            _quranState.value = runCatching { Quran.initialize(QuranDataLoader.loadFromClasspath()) }
                .fold(onSuccess = { QuranState.Ready }, onFailure = { QuranState.Failed(it) })
        }

        // Les réglages d'écoute sont relus **au démarrage**, et non à l'ouverture du lecteur :
        // le lecteur doit pouvoir partir des valeurs enregistrées sans attendre le disque, et le
        // premier réglage de la personne ne doit pas écraser un choix antérieur — ce qu'un
        // départ sur les valeurs par défaut ferait sans le dire. Le document fait quelques
        // centaines d'octets, et la lecture ne touche pas le fil principal.
        scope.launch { audioSettings.prime() }
    }

    // -----------------------------------------------------------------------
    // Paquet « Coran 1441 »
    // -----------------------------------------------------------------------

    /**
     * Le paquet de la source « Coran 1441 », téléchargé à la demande.
     *
     * Le dossier est sous la même racine que les autres ressources — `quran/coran_1441` à côté
     * de `quran/pages` — et le transport réel est branché **ici, et nulle part ailleurs** :
     * c'est ce qui permet d'éprouver toute l'installation sans réseau, avec une doublure.
     */
    val archive: QuranArchiveStore = QuranArchiveStore(
        installer = QuranArchiveInstaller(
            directory = File(root, QuranArchive.DIRECTORY),
            transport = HttpArchiveTransport(),
        ),
        scope = scope,
    )

    private companion object {
        /**
         * Sous-dossier des données applicatives.
         *
         * Les fichiers ne sont pas posés à la racine de `filesDir` : les pages de moushaf et
         * l'audio viendront s'y ajouter, et une racine encombrée rend le diagnostic pénible.
         */
        const val ROOT_DIRECTORY = "state"
    }
}

/**
 * Avancement du chargement du référentiel coranique.
 *
 * Trois états, pas deux : « en cours » et « échoué » demandent des écrans différents. Un booléen
 * `isReady` les confondrait, et l'échec s'afficherait comme une attente sans fin.
 */
sealed interface QuranState {

    /** Le chargement est en cours. */
    data object Loading : QuranState

    /** Le référentiel est utilisable : les règles du domaine sont fiables. */
    data object Ready : QuranState

    /** Le chargement a échoué. Les règles du domaine ne sont pas utilisables. */
    data class Failed(val cause: Throwable) : QuranState
}

/**
 * Message affiché quand le référentiel coranique n'a pas pu être chargé.
 *
 * Il vit **ici**, à côté de l'état qu'il décrit, parce que deux écrans le posent désormais —
 * l'accueil et le programme. Deux copies d'une même phrase finiraient par décrire la même panne
 * de deux façons, et c'est la copie qu'on ne relit pas qui resterait.
 */
fun quranFailureMessage(cause: Throwable): String =
    "Le référentiel coranique n'a pas pu être chargé : " +
        (cause.message ?: cause::class.simpleName.orEmpty())

/**
 * Le conteneur, accessible depuis n'importe quel `@Composable`.
 *
 * `staticCompositionLocalOf` et non `compositionLocalOf` : la valeur est posée une fois au
 * démarrage et ne change jamais. `compositionLocalOf` suivrait les lectures pour les
 * recomposer, ce qui n'a aucun sens ici et coûterait une observation à chaque accès.
 *
 * La valeur par défaut lève une erreur explicite : un `CompositionLocal` oublié produirait
 * sinon un `null` silencieux, puis un plantage loin de sa cause.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer absent : DeepSeekTheme et le conteneur doivent être fournis par MainActivity.")
}
