package com.msoumaya.deepseekandroid.core.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.msoumaya.deepseekandroid.core.data.local.BlobFile
import com.msoumaya.deepseekandroid.core.data.local.HttpArchiveTransport
import com.msoumaya.deepseekandroid.core.data.local.JsonFileStore
import com.msoumaya.deepseekandroid.core.data.local.LocalStateStore
import com.msoumaya.deepseekandroid.core.data.local.Outbox
import com.msoumaya.deepseekandroid.core.data.local.OutboxStore
import com.msoumaya.deepseekandroid.core.data.local.QuranArchiveInstaller
import com.msoumaya.deepseekandroid.core.data.remote.AuthGateway
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.RemoteStateSource
import com.msoumaya.deepseekandroid.core.data.remote.SessionPreferences
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseAuthGateway
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseConfig
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseProvider
import com.msoumaya.deepseekandroid.core.data.remote.SupabaseStateSource
import com.msoumaya.deepseekandroid.core.data.remote.UnavailableAuthGateway
import com.msoumaya.deepseekandroid.core.data.remote.VaultSessionManager
import com.msoumaya.deepseekandroid.core.data.repository.AudioSettingsRepository
import com.msoumaya.deepseekandroid.core.data.repository.AuthRepository
import com.msoumaya.deepseekandroid.core.data.repository.QuranArchiveStore
import com.msoumaya.deepseekandroid.core.data.repository.UserRepository
import com.msoumaya.deepseekandroid.core.data.security.SecretVault
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Quran
import com.msoumaya.deepseekandroid.core.domain.QuranArchive
import com.msoumaya.deepseekandroid.core.domain.QuranDataLoader
import com.msoumaya.deepseekandroid.core.domain.StoredAudioSettings
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
    val supabase: SupabaseConfig = SupabaseConfig.PLACEHOLDER,
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
