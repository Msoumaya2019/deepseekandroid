package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.LocalStateStore
import com.msoumaya.deepseekandroid.core.data.local.OperationKind
import com.msoumaya.deepseekandroid.core.data.local.OutboxStore
import com.msoumaya.deepseekandroid.core.data.local.Snapshot
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.RemoteStateSource
import com.msoumaya.deepseekandroid.core.domain.AccountSync
import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.AppState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Issue d'une tentative de synchronisation. */
sealed interface SyncResult {

    /** Le serveur et l'appareil portent le même état. */
    data object Synced : SyncResult

    /** Réseau indisponible. Rien n'a été modifié, rien n'est perdu. */
    data object Offline : SyncResult

    /** Aucun projet Supabase configuré dans cette compilation. */
    data object NotConfigured : SyncResult

    /** Aucune session ouverte : il n'y a pas de compte à synchroniser. */
    data object SignedOut : SyncResult

    data class Failed(val message: String) : SyncResult
}

/**
 * L'état applicatif, lu localement d'abord et synchronisé ensuite.
 *
 * **L'ordre est la règle.** L'application ne doit jamais attendre le réseau pour s'ouvrir :
 * [state] émet l'état du disque dès le premier collecteur, et [sync] est une opération
 * séparée, appelée en arrière-plan. Un lancement sans réseau affiche donc l'intégralité du
 * programme, de la progression et des réglages.
 *
 * **On ne pousse jamais sans avoir lu.** [sync] lit toujours le serveur avant d'écrire quoi
 * que ce soit. Un envoi à l'aveugle écraserait le travail fait sur l'autre appareil ; c'est
 * la faute la plus coûteuse possible dans ce système, et elle est rendue impossible par
 * construction plutôt que par vigilance.
 */
class UserRepository(
    private val stores: LocalStateStore,
    private val session: OwnerStore,
    private val outbox: OutboxStore,
    private val remote: RemoteStateSource?,
) {

    private val lock = Mutex()

    /**
     * État affichable, local d'abord.
     *
     * Le magasin suivi dépend du propriétaire courant : la connexion ou la déconnexion fait
     * donc basculer le flux sur le bon fichier sans que l'appelant ait à s'en occuper. Chaque
     * compte a son propre fichier, donc un utilisateur qui se reconnecte retrouve sa
     * progression même hors ligne, et le `userId` enregistré est revérifié à chaque émission.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<AppState> = session.ownerId
        .distinctUntilChanged()
        .flatMapLatest { owner ->
            if (owner == null) stores.anonymous.changes else stores.accountChangesFor(owner)
        }

    /** Lit l'état local. Ne touche pas au réseau. */
    suspend fun load(): AppState = stores.storeFor(session.currentOwner()).current()

    /**
     * Vrai si un état a déjà été écrit sur cet appareil pour [ownerId].
     *
     * La porte d'entrée s'en sert pour refuser d'ouvrir un compte qui n'a jamais été
     * rapatrié : ouvrir afficherait un programme vide, et la première séance validée dans ce
     * programme vide écraserait le vrai compte au moment de la synchronisation. Le détail du
     * raisonnement est dans `LocalStateStore.hasStoredStateFor`.
     */
    fun hasStoredState(ownerId: String): Boolean = stores.hasStoredStateFor(ownerId)

    /**
     * Applique une modification à l'état local et la met en file pour le serveur.
     *
     * [Program.touch] n'est pas décoratif : il fait avancer `updatedAt`. Sans lui, une
     * modification faite avec une horloge en retard serait vue comme plus ancienne que le
     * serveur et écrasée à la synchronisation suivante.
     */
    suspend fun mutate(transform: (AppState) -> AppState): AppState {
        val owner = session.currentOwner()
        val next = stores.storeFor(owner).update { current -> stamp(Program.touch(transform(current)), owner) }
        enqueue(owner, next)
        return next
    }

    /** Remise à zéro de la progression, en conservant identité, signets, profil et apparence. */
    suspend fun resetProgress(): AppState {
        val owner = session.currentOwner()
        val next = stores.storeFor(owner).update { previous -> stamp(Program.resetAllProgress(previous), owner) }
        enqueue(owner, next)
        return next
    }

    /**
     * Inscrit le propriétaire dans l'état.
     *
     * L'état porte son `userId` : c'est le garde-fou qui empêche `Program.accountState` de
     * mélanger deux comptes, et `LocalStateStore` refuse déjà de servir un état qui ne
     * correspond pas. Un état écrit pour un compte connecté doit donc toujours porter cet
     * identifiant — y compris lorsqu'il part d'un état par défaut, cas d'une première
     * ouverture ou d'une remise à zéro. Sans cette estampille, l'état resterait anonyme
     * alors qu'il appartient à quelqu'un, et le garde-fou ne saurait plus le reconnaître.
     */
    private fun stamp(state: AppState, owner: String?): AppState =
        if (owner != null && state.userId != owner) state.copy(userId = owner) else state

    /** Nombre d'opérations en attente d'envoi, pour le diagnostic et l'indicateur d'état. */
    suspend fun pendingCount(): Int {
        val owner = session.currentOwner() ?: return 0
        return outbox.list(owner).size
    }

    /**
     * Synchronise avec le serveur.
     *
     * Déroulé, et pourquoi il est dans cet ordre :
     *  1. lire le serveur — en cas d'échec réseau, on s'arrête là et **rien** n'est modifié ;
     *  2. fusionner (`AccountSync`) et écrire localement — le travail des deux appareils est
     *     réuni, aucune des deux parties n'est écrasée ;
     *  3. pousser si nécessaire — un échec ici ne défait pas l'écriture locale, et la base de
     *     fusion n'avance pas, donc la tentative suivante retrouvera les modifications
     *     locales comme non envoyées ;
     *  4. faire avancer la base et vider la file — l'état fusionné est désormais ce que le
     *     serveur porte, les opérations en attente y sont incluses.
     */
    suspend fun sync(): SyncResult = lock.withLock {
        val source = remote ?: return@withLock SyncResult.NotConfigured
        val owner = session.currentOwner() ?: return@withLock SyncResult.SignedOut

        val remoteState = try {
            source.loadState(owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (offline: IOException) {
            return@withLock SyncResult.Offline
        } catch (error: Exception) {
            return@withLock SyncResult.Failed(describe(error))
        }

        val store = stores.storeFor(owner)
        val baseStore = stores.baseStoreFor(owner)
        // **Lire avant de tester l'existence**, et dans cet ordre : c'est la lecture qui met de
        // côté un document illisible, et `BlobFile.exists()` répond faux pour un fichier vide.
        // Tester avant donnerait « vrai » pour un fichier qu'on vient précisément d'archiver.
        val stored = store.current()
        // Un état par défaut n'est pas un état local : c'est l'absence d'état local. La
        // distinction est masquée par `LocalStateStore`, qui rend toujours un document — un
        // compte sans fichier reçoit `defaultState().copy(userId = id)`.
        //
        // La confondre ne perd pas de donnée, et c'est vérifié : `defaultState()` porte
        // `updatedAt = EPOCH`, donc le distant gagne toujours à la réconciliation, et la
        // fusion à trois voies fait hériter du serveur tout champ absent du local. Mais elle
        // coûte cher quand même. Le cas mesuré est un fichier d'état archivé comme corrompu
        // alors que la base de fusion a survécu :
        //
        //  1. le local est lu comme l'état par défaut, et `base.onboardingDone` vaut vrai —
        //     `mergeOfflineState` voit alors une remise à zéro volontaire et renvoie ce local
        //     vide, qui est **écrit sur le disque**, avec la base écrasée par ce vide ;
        //  2. l'application affiche donc un programme vide jusqu'à la synchronisation
        //     suivante, et une séance validée entre-temps se fusionne contre une base
        //     détruite.
        //
        // En passant `null`, `Program.accountState` retrouve la règle « appareil neuf » et
        // adopte l'état du serveur **en une seule synchronisation** : rien de vide n'est écrit,
        // et la base reste celle du serveur. C'est aussi exactement ce que faisait le client
        // d'origine, dont le `loadAccountState()` rendait `null` en l'absence de fichier.
        val cached = stored.takeIf { store.exists() }
        val base = baseStore.current().state
        val pending = outbox.list(owner).isNotEmpty()

        val outcome = AccountSync.merge(
            userId = owner,
            cached = cached,
            remote = remoteState,
            base = base,
            pending = pending,
        )
        store.update { outcome.state }

        if (outcome.shouldPush) {
            try {
                source.saveState(owner, outcome.state)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (offline: IOException) {
                return@withLock SyncResult.Offline
            } catch (error: Exception) {
                return@withLock SyncResult.Failed(describe(error))
            }
        }

        baseStore.update { Snapshot(outcome.state) }
        outbox.acknowledge(outbox.list(owner).map { it.id })

        SyncResult.Synced
    }

    /**
     * Met en file l'instantané complet.
     *
     * Le remplacement par type d'opération borne la file à une entrée par utilisateur : une
     * longue période hors ligne n'accumule pas des dizaines d'instantanés dont seul le
     * dernier compte. Et comme l'envoi est un `upsert` sur `user_id`, un renvoi après une
     * coupure ne duplique rien.
     */
    private suspend fun enqueue(owner: String?, state: AppState) {
        if (owner == null) return
        outbox.enqueueReplacing(
            kind = OperationKind.STATE,
            userId = owner,
            payload = AppJson.encodeToString(AppState.serializer(), state),
        )
    }

    private fun describe(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}
