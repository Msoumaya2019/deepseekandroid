package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.RecitationStore
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploadRow
import com.msoumaya.deepseekandroid.core.data.remote.RecitationUploader
import com.msoumaya.deepseekandroid.core.data.remote.restMessage
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.domain.Recitations
import com.msoumaya.deepseekandroid.core.model.LocalRecitation
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

// ---------------------------------------------------------------------------
// Dépôt « Récitations »
// ---------------------------------------------------------------------------
// Portage de la partie locale de `src/services/recitations.ts` : enregistrer, retirer, et
// **déposer ce qui ne l'est pas encore**.
//
// **Ce fichier ne décide d'aucune règle.** Les bornes, l'extension, le type MIME, l'adresse dans
// le compartiment, les bornes distantes et la tolérance d'un fichier déjà déposé sont dans
// `core:domain/Recitations.kt` ; ici il n'y a que l'ordonnancement, le sort des erreurs et le
// passage hors ligne.
//
// ## Ce que la file garantit, et ce qu'elle ne garantit pas
//
// Elle garantit qu'une récitation enregistrée sans réseau **repartira** : elle est écrite sur le
// disque avant tout appel, et rien ne la retire tant que le serveur n'a pas accepté les deux
// gestes — le fichier **et** la ligne.
//
// Elle ne garantit pas qu'une tentative aboutisse du premier coup, et c'est voulu : un échec
// marque l'élément en échec **et passe au suivant**. L'original fait de même, et l'inverse serait
// pire — une récitation de dix minutes qui échoue une fois bloquerait toutes celles d'après.
//
// ## Ce qui protège réellement d'un double envoi
//
// Deux choses, et la seconde compte plus que la première :
//
//   1. la garde [guard] — un second appel **rend la main** au lieu d'attendre, comme le booléen
//      de module de l'original ;
//   2. l'idempotence du serveur — le dépôt du fichier n'écrase pas, et l'écriture de la ligne
//      ignore un identifiant déjà présent.
//
// L'original relâche sa garde **après** deux `await` (la session, puis la lecture du registre),
// donc deux appels concurrents peuvent la franchir tous les deux et envoyer deux fois les mêmes
// octets. Rien n'est faux au bout du compte — le serveur absorbe le doublon —, mais c'est un
// envoi de plusieurs mégaoctets payé deux fois sur un forfait mobile. Ici la garde est prise
// **avant** toute attente : la protection devient réelle au lieu d'être seulement apparente.
// C'est une divergence assumée, et elle se mesure — le résultat observable, lui, est identique.
// ---------------------------------------------------------------------------

/**
 * Ce que l'écran « Mes récitations » a besoin de savoir, et rien de plus.
 *
 * [items] est la liste locale du compte ouvert, **statuts compris** : c'est elle qui dit ce qui
 * est déposé, ce qui attend et ce qui a échoué. Elle est relue après chaque tentative de dépôt,
 * sans quoi l'écran continuerait d'afficher « en attente » sur une récitation déjà arrivée.
 */
data class RecitationState(
    /** L'identifiant du compte ouvert, ou `null`. C'est **la** source de l'identité de la liste. */
    val ownerId: String? = null,

    /** Vrai tant que le registre local n'a pas été lu. Le défaut est donc « en attente ». */
    val loading: Boolean = true,

    /** Vrai pendant un geste — enregistrer, retirer. C'est ce qui empêche un double appui. */
    val busy: Boolean = false,

    /** Vrai pendant un dépôt. L'écran peut alors dire que quelque chose part. */
    val syncing: Boolean = false,

    /** Dernier message d'information, ou `null`. Il est effacé au **début** d'un geste. */
    val notice: String? = null,

    /** Les récitations du compte, de la plus récente à la plus ancienne. */
    val items: List<LocalRecitation> = emptyList(),
)

/**
 * Enregistre les récitations de l'appareil et dépose celles qui ne le sont pas.
 *
 * @param store registre local et fichiers.
 * @param uploader accès au serveur, ou `null` si aucun projet Supabase n'est configuré. Le `null`
 *   n'est pas une panne : c'est le mode hors ligne, et il doit se lire comme « pas de compte »,
 *   jamais comme une erreur réseau.
 * @param session propriétaire courant. Observé : c'est lui qui déclenche la lecture, et c'est lui
 *   qui **vide** la liste à la déconnexion — sans quoi les récitations du compte précédent
 *   resteraient affichées sous le compte suivant.
 * @param scope portée des travaux qui vivent aussi longtemps que l'application.
 */
class RecitationRepository(
    private val store: RecitationStore,
    private val uploader: RecitationUploader?,
    private val session: OwnerStore,
    private val scope: CoroutineScope,
) {

    /**
     * Sérialise les dépôts.
     *
     * Sans elle, deux dépôts concurrents — le retour au premier plan et la fin d'un
     * enregistrement — liraient la même liste d'éléments à envoyer et enverraient deux fois les
     * mêmes octets. La boucle suppose en effet qu'elle est seule à écrire les statuts.
     */
    private val guard = Mutex()

    /**
     * Nombre d'opérations du dépôt **en vol** : lecture du registre et passe de dépôt.
     *
     * Ce compteur existe à cause d'un échec d'intégration continue qu'il a fallu expliquer, et non
     * contourner. **L'état publié ne dit pas qu'un travail est en cours.** `syncing` passe à vrai
     * et à faux autour d'une passe, mais il reste vrai pendant toute sa durée, et les étapes d'une
     * passe — marquage `uploading`, envoi des octets, écriture de la ligne, marquage `synced` — ne
     * changent rien d'autre. « L'état ne bouge plus » ne veut donc pas dire « la passe est finie » :
     * une attente fondée là-dessus rend la main au milieu du dépôt, et le test qui suit lit un
     * statut `uploading` en croyant lire un statut final.
     *
     * Ce que ça coûtait, mesuré : en ralentissant la doublure de 200 ms sur un vrai répartiteur —
     * ce que fait un appel réseau —, deux tests tombaient, et ils passaient sur une machine rapide.
     *
     * Le compte est incrémenté **avant la première suspension** de l'opération et décrémenté après
     * la dernière, dans la coroutine appelante : il ne peut donc pas valoir zéro pendant qu'un
     * travail vit encore, sur quelque fil que ce soit. C'est ce qui le rend sûr là où une lecture
     * d'état ne l'était pas.
     *
     * Un compteur atomique et non un `Int` : deux opérations peuvent se chevaucher — le retour au
     * premier plan et la fin d'un enregistrement — et une incrémentation non atomique en perdrait
     * une. Un compteur faussé rendrait [enTravail] menteur, donc l'attente menteuse.
     */
    private val enVol = AtomicInteger()

    /**
     * Vrai si une opération du dépôt est en cours.
     *
     * Publique parce que l'attente d'un test ne peut pas se fonder sur l'état publié (voir [enVol]),
     * et parce qu'un écran a le droit de savoir qu'un travail tourne sans lire la liste.
     */
    val enTravail: Boolean get() = enVol.get() > 0

    /**
     * Compte une opération du dépôt, de sa première à sa dernière suspension.
     *
     * Le bloc est exécuté **dans la coroutine appelante** : le compteur suit donc le travail, y
     * compris à travers un `withContext(Dispatchers.IO)`, sans rien changer à son ordonnancement.
     */
    private suspend fun <T> compte(bloc: suspend () -> T): T {
        enVol.incrementAndGet()
        try {
            return bloc()
        } finally {
            enVol.decrementAndGet()
        }
    }

    private val _state = MutableStateFlow(RecitationState())

    /** État affichable. */
    val state: StateFlow<RecitationState> = _state.asStateFlow()

    init {
        scope.launch {
            session.ownerId.distinctUntilChanged().collect { owner ->
                if (owner == null) {
                    clear()
                } else {
                    refresh()
                    // L'original tente le dépôt **à chaque changement de compte**, et de nouveau
                    // chaque fois que l'application revient au premier plan : c'est ainsi qu'une
                    // récitation faite dans le métro part une fois le réseau revenu.
                    syncPending()
                }
            }
        }
    }

    /**
     * Relit le registre local du compte ouvert.
     *
     * Il n'y a **rien à demander au serveur** pour afficher cette liste : elle vient du disque,
     * donc elle s'affiche sans réseau, statuts compris.
     *
     * @return vrai si un compte était ouvert.
     */
    suspend fun refresh(): Boolean = compte { lireRegistre() }

    /** Corps de [refresh], compté comme travail en vol. */
    private suspend fun lireRegistre(): Boolean {
        val owner = session.currentOwner() ?: return false
        _state.value = _state.value.copy(
            ownerId = owner,
            loading = false,
            items = store.list(owner),
        )
        return true
    }

    /**
     * Copie l'enregistrement de [sourcePath] et l'inscrit au registre.
     *
     * **L'écran n'attend pas le dépôt.** La copie et l'écriture du registre sont locales ; le
     * dépôt part ensuite, en arrière-plan, et l'écran affiche la récitation tout de suite avec
     * son statut « en attente ». C'est ce qui permet d'enregistrer sans réseau.
     *
     * @param invocationId invocation enregistrée, ou `null` pour un passage du Coran. La nature
     *   s'en déduit — voir `RecitationStore.add`.
     * @return vrai si la récitation est inscrite.
     */
    suspend fun save(
        sourcePath: String,
        start: Int,
        end: Int,
        durationMs: Long,
        invocationId: String? = null,
    ): Boolean {
        val owner = session.currentOwner()
        if (owner == null) {
            _state.value = _state.value.copy(notice = RecitationText.SIGNED_OUT)
            return false
        }
        if (_state.value.busy) return false
        _state.value = _state.value.copy(busy = true, notice = null)

        return try {
            store.add(sourcePath, start, end, durationMs, owner, invocationId)
            _state.value = _state.value.copy(busy = false, items = store.list(owner))
            syncInBackground()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                busy = false,
                notice = RecitationText.INVALID_RECORDING,
            )
            false
        }
    }

    /**
     * Retire une récitation de l'appareil : son fichier et son entrée au registre.
     *
     * Ce geste ne touche **pas** au serveur. Une récitation déjà déposée reste sur le serveur, et
     * c'est délibéré : le retrait distant est un autre geste, qui supprime d'abord le fichier du
     * compartiment puis la ligne — et que l'écran appelle séparément.
     */
    suspend fun delete(item: LocalRecitation): Boolean {
        val owner = session.currentOwner() ?: return false
        if (_state.value.busy) return false
        _state.value = _state.value.copy(busy = true, notice = null)
        return try {
            store.remove(item)
            _state.value = _state.value.copy(busy = false, items = store.list(owner))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(busy = false)
            false
        }
    }

    /**
     * Dépose les récitations qui ne le sont pas encore, et rend la main si un dépôt est en cours.
     *
     * La liste des éléments à envoyer est **tout ce qui n'est pas `synced`** — y compris ce qui a
     * déjà échoué, et y compris ce qui est resté en plein dépôt parce que l'application a été
     * tuée. C'est la règle de l'original, et elle est nécessaire : une récitation laissée en
     * `uploading` par une coupure ne repartirait jamais si l'on ne considérait comme « à envoyer »
     * que ce qui est marqué `pending`.
     *
     * @return vrai si un dépôt a été mené, faux si aucun déposant n'existe ou si un autre dépôt
     *   tenait déjà la garde.
     */
    suspend fun syncPending(): Boolean = compte { passeDeDepot() }

    /** Corps de [syncPending], compté comme travail en vol. */
    private suspend fun passeDeDepot(): Boolean {
        val api = uploader ?: return false
        if (!guard.tryLock()) return false
        try {
            val owner = session.currentOwner() ?: return false
            val pending = store.list(owner).filter { it.syncStatus.awaitsUpload }
            if (pending.isEmpty()) return true

            _state.value = _state.value.copy(syncing = true)
            try {
                for (item in pending) {
                    // Un compte peut changer pendant un dépôt : le vérifier entre deux éléments
                    // évite de pousser les récitations d'un compte sous le jeton d'un autre.
                    if (session.currentOwner() != owner) break
                    push(api, owner, item)
                }
            } finally {
                // La liste est relue **dans tous les cas** : les statuts ont changé, même si un
                // élément a échoué, et même si la boucle s'est arrêtée en chemin. Ne pas le faire
                // laisserait l'écran annoncer « en attente » sur une récitation déjà arrivée.
                _state.value = _state.value.copy(
                    syncing = false,
                    items = if (session.currentOwner() == owner) {
                        store.list(owner)
                    } else {
                        _state.value.items
                    },
                )
            }
        } finally {
            guard.unlock()
        }
        return true
    }

    /**
     * Tente un dépôt sans attendre le résultat.
     *
     * C'est ce qu'appellent l'enregistrement et le retour au premier plan : dans les deux cas, la
     * personne n'attend pas le réseau, et un échec n'a rien à lui dire — le statut de la
     * récitation le lui dira.
     */
    fun syncInBackground() {
        scope.launch { runCatching { syncPending() } }
    }

    /**
     * Dépose **une** récitation.
     *
     * L'ordre des gestes est celui de l'original : marquer `uploading`, déposer les octets,
     * écrire la ligne, marquer `synced`. Le marquage `uploading` a lieu **avant** le premier
     * appel : si l'application est tuée pendant le dépôt, l'élément reste dans cet état, et la
     * prochaine tentative le reprendra — `awaitsUpload` le compte comme à envoyer.
     */
    private suspend fun push(api: RecitationUploader, owner: String, item: LocalRecitation) {
        store.markStatus(item.id, RecitationSyncStatus.UPLOADING)
        try {
            val bytes = store.bytesOf(item)
                ?: throw IllegalStateException(RecitationText.LOCAL_FILE_MISSING)

            val extension = Recitations.extensionFor(item.uri)
            val path = Recitations.storagePath(owner, item.id, extension)

            try {
                api.upload(path, bytes, Recitations.contentType(extension))
            } catch (cancelled: CancellationException) {
                // L'annulation passe **avant** l'examen du message : `CancellationException` est
                // une `Exception`, et une interruption dont le texte contiendrait par hasard
                // « already exists » serait avalée — la synchronisation continuerait alors qu'on
                // vient de la demander d'arrêter.
                throw cancelled
            } catch (error: Exception) {
                // Un fichier déjà présent n'est pas une panne : les octets sont arrivés, et
                // c'est le geste suivant qui manquait. Voir `Recitations.uploadFailureIsBenign`.
                if (!Recitations.uploadFailureIsBenign(error.restMessage())) throw error
            }

            api.upsert(uploadRow(item, owner, path))
            store.markStatus(item.id, RecitationSyncStatus.SYNCED)
        } catch (cancelled: CancellationException) {
            // Une annulation laisse l'élément en `uploading`, et c'est ce qu'il faut : la
            // prochaine tentative le reprendra. Le marquer en échec ferait croire à un refus du
            // serveur là où il n'y a eu qu'une interruption.
            throw cancelled
        } catch (error: Exception) {
            store.markStatus(item.id, RecitationSyncStatus.FAILED)
        }
    }

    /**
     * La ligne distante d'une récitation locale.
     *
     * **Le propriétaire est celui de la session, et il sert deux fois** : comme `user_id` de la
     * ligne, et comme premier segment de l'adresse dans le compartiment. La contrainte
     * `recitation_own_path` de la table exige que l'adresse commence par le `user_id` de la ligne
     * (`supabase/recitations.sql`, ligne 11), et les politiques du compartiment comparent ce même
     * segment à `auth.uid()`. Les prendre d'une seule variable rend cet accord indéfaisable depuis
     * le client — deux sources différentes auraient pu produire une ligne que le serveur refuse.
     */
    private fun uploadRow(item: LocalRecitation, owner: String, path: String): RecitationUploadRow {
        val (start, end) = Recitations.remoteBounds(item.kind, item.start, item.end)
        return RecitationUploadRow(
            id = item.id,
            userId = owner,
            startVerseId = start,
            endVerseId = end,
            recordingType = item.kind,
            invocationId = item.invocationId,
            durationMs = item.durationMs,
            storagePath = path,
            createdAt = item.createdAt,
        )
    }

    /** État « personne de connecté » : rien à montrer, et ce n'est pas une panne. */
    private fun clear() {
        _state.value = RecitationState(loading = false)
    }
}
