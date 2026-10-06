package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.SocialSource
import com.msoumaya.deepseekandroid.core.domain.Social
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.FriendProfile
import com.msoumaya.deepseekandroid.core.model.SocialSuspension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ---------------------------------------------------------------------------
// Dépôt « Amis »
// ---------------------------------------------------------------------------
// Portage de la partie chargement de `FriendsScreen` (`src/SocialScreens.tsx:78-113`) : ce qui
// est lu, dans quel ordre, et ce qu'un échec partiel fait à ce qui est déjà à l'écran.
//
// **Ce fichier ne décide d'aucun libellé et d'aucun filtre.** « En ligne », le tri, le nombre
// d'amis affichés sont dans `core:domain/Social.kt` ; ici il n'y a que l'ordonnancement des
// lectures et le sort des erreurs.
//
// **Ce que le client d'origine n'avait pas, et pourquoi il est ici.** Le client d'origine
// n'avait pas d'état d'échec : `profile` restait nul et l'écran affichait « Chargement de tes
// amis… » indéfiniment. Une liste vide, elle, se lit « tu n'as pas d'amis » — une affirmation
// fausse sur des tiers. Trois états distincts remplacent donc le booléen implicite :
//
//   - **rien à montrer et une lecture ratée** -> [SocialState.failure] : l'écran dit la panne ;
//   - **quelque chose à montrer et une relecture ratée** -> [SocialState.notice] : la liste
//     reste, parce qu'un échec de rafraîchissement ne rend pas faux ce qu'on a déjà lu ;
//   - **rien à montrer et personne de connecté** -> ni l'un ni l'autre : l'écran invite à se
//     connecter.
//
// Les compléments — cercles, suspension, boîte de réception — ne passent **jamais** par
// `failure` : ils sont lus après coup, et un cercle illisible ne doit pas effacer une liste
// d'amis déjà chargée.
// ---------------------------------------------------------------------------

/**
 * Ce que l'écran « Amis » a besoin de savoir, et rien de plus.
 *
 * `summaries` est indexé par lien et `online` par personne : deux clés différentes pour deux
 * tables différentes, et les confondre n'afficherait ni erreur ni ami — seulement des
 * conversations sans aperçu et des présences inconnues.
 */
data class SocialState(
    /** Vrai si un compte est ouvert. Faux aussi hors configuration Supabase. */
    val signedIn: Boolean = false,

    /** Vrai tant que le premier état n'a pas été lu. Le défaut est donc « en attente ». */
    val loading: Boolean = true,

    /** Vrai pendant qu'un geste est en cours. C'est ce qui empêche un double envoi. */
    val busy: Boolean = false,

    /** Dernier message d'information, ou `null`. Il n'est jamais effacé : comme à l'origine. */
    val notice: String? = null,

    /** Panne de la lecture **principale**, quand il n'y a rien à montrer à la place. */
    val failure: String? = null,

    val profile: FriendProfile? = null,
    val links: List<FriendLink> = emptyList(),
    val groups: List<FriendGroup> = emptyList(),
    val suspension: SocialSuspension? = null,
    val summaries: Map<String, ConversationSummary> = emptyMap(),
    val online: Map<String, Boolean> = emptyMap(),
)

/**
 * Charge et modifie l'état social, en gardant l'écran honnête sur ce qu'il sait.
 *
 * @param source accès aux données, ou `null` si aucun projet Supabase n'est configuré. Le
 *   `null` n'est pas une panne : c'est le mode hors ligne, et il doit se lire comme « pas de
 *   compte », jamais comme une erreur réseau.
 * @param session propriétaire courant. Observé : c'est lui qui déclenche le chargement, et
 *   c'est lui qui **vide** l'état à la déconnexion — sans quoi les amis du compte précédent
 *   resteraient affichés sous le compte suivant.
 * @param scope portée des travaux qui vivent aussi longtemps que l'application.
 */
class SocialRepository(
    private val source: SocialSource?,
    private val session: OwnerStore,
    private val scope: CoroutineScope,
) {

    /**
     * Sérialise les chargements.
     *
     * Sans lui, deux chargements concurrents — l'ouverture de l'écran et un geste qui
     * rafraîchit — écriraient l'état dans l'ordre où le réseau répond, et le plus ancien
     * pourrait écraser le plus récent.
     */
    private val lock = Mutex()

    private val _state = MutableStateFlow(SocialState())

    /** État affichable. */
    val state: StateFlow<SocialState> = _state.asStateFlow()

    init {
        scope.launch {
            session.ownerId.distinctUntilChanged().collect { owner ->
                if (owner == null) clear() else refresh()
            }
        }
    }

    /**
     * Relit tout : profil, liens, puis cercles, suspension et boîte de réception.
     *
     * @return vrai si la lecture principale a abouti.
     */
    suspend fun refresh(): Boolean = lock.withLock {
        val api = source
        val owner = session.currentOwner()
        if (api == null || owner == null) {
            clear()
            return@withLock false
        }

        // « Première lecture » se juge sur ce qui est **déjà** à l'écran, et non sur un drapeau :
        // c'est la présence d'un profil qui décide si un échec doit dire la panne ou seulement
        // la signaler.
        val firstLoad = _state.value.profile == null
        _state.value = _state.value.copy(loading = firstLoad, failure = null)

        val essentials = try {
            api.ensureProfile() to api.links(owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (firstLoad) {
                _state.value = _state.value.copy(loading = false, failure = describe(error))
            } else {
                notice(error)
            }
            return@withLock false
        }

        val (profile, links) = essentials
        _state.value = _state.value.copy(
            signedIn = true,
            loading = false,
            failure = null,
            profile = profile,
            links = links,
        )

        // Les compléments, chacun pour soi. Un échec ici **n'efface rien** : les cercles et la
        // suspension ne sont pas la liste d'amis, et les perdre ne doit pas coûter la liste.
        try {
            val groups = api.groups()
            val suspension = api.suspension(owner)
            _state.value = _state.value.copy(groups = groups, suspension = suspension)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            notice(error)
        }

        // On ne demande pas la boîte de réception sans ami accepté : la fonction serveur
        // rendrait un tableau vide, au prix d'un aller-retour. La règle vit ici plutôt que dans
        // la source Supabase parce qu'elle se mesure — une doublure compte ses appels.
        if (links.any { it.status == FriendLinkStatus.ACCEPTED }) {
            try {
                val inbox = api.inbox(links)
                _state.value = _state.value.copy(
                    summaries = inbox.summaries,
                    online = inbox.statuses,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notice(error)
            }
        } else {
            _state.value = _state.value.copy(summaries = emptyMap(), online = emptyMap())
        }

        true
    }

    /** Envoie une demande d'ami. Rend vrai si le serveur l'a acceptée. */
    suspend fun requestFriend(code: String): Boolean =
        act(SocialText.INVITATION_SENT) { it.requestFriend(code) }

    /** Accepte une invitation reçue. */
    suspend fun acceptInvitation(linkId: String): Boolean =
        act(SocialText.SAVED) { it.acceptFriend(linkId) }

    /** Refuse une invitation reçue. */
    suspend fun declineInvitation(linkId: String): Boolean =
        act(SocialText.SAVED) { it.declineFriend(linkId) }

    /** Retire une amitié. */
    suspend fun removeFriend(linkId: String): Boolean =
        act(SocialText.SAVED) { it.removeFriend(linkId) }

    /** Bloque un compte. */
    suspend fun block(otherId: String): Boolean =
        act(SocialText.SAVED) { it.blockFriend(otherId) }

    /** Débloque un compte que j'avais bloqué. */
    suspend fun unblock(otherId: String): Boolean =
        act(SocialText.SAVED) { it.unblockFriend(otherId) }

    /** Crée un cercle. */
    suspend fun createGroup(name: String): Boolean =
        act(SocialText.SAVED) { it.createGroup(name) }

    /**
     * Ouvre le cercle « contact administrateur » et rend son identifiant, ou `null` si le
     * serveur a refusé.
     *
     * Ce geste ne passe pas par [act] : il ne confirme rien — il **ouvre** quelque chose —, et
     * un « Enregistré. » y annoncerait une réussite que la personne ne verrait pas.
     */
    suspend fun openAdminContact(): String? {
        val api = source ?: return null
        _state.value = _state.value.copy(busy = true)
        return try {
            val id = api.openAdminContact()
            _state.value = _state.value.copy(busy = false)
            refresh()
            id
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(busy = false, notice = describe(error))
            null
        }
    }

    /**
     * Exécute un geste, puis relit.
     *
     * `busy` est posé **avant** l'appel et retiré après, dans les deux cas : c'est ce qui rend
     * un double appui inoffensif, l'écran désactivant ses boutons tant qu'il vaut vrai.
     */
    private suspend fun act(success: String, block: suspend (SocialSource) -> Any?): Boolean {
        val api = source ?: return false
        _state.value = _state.value.copy(busy = true)
        return try {
            block(api)
            _state.value = _state.value.copy(busy = false, notice = success)
            refresh()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(busy = false, notice = describe(error))
            false
        }
    }

    /** État « personne de connecté » : rien à montrer, et ce n'est pas une panne. */
    private fun clear() {
        _state.value = SocialState(signedIn = false, loading = false)
    }

    /**
     * Publie un message d'information sans toucher au réseau.
     *
     * C'est ce qui permet à l'écran de confirmer un geste qui n'est pas une écriture distante —
     * la copie du code d'invitation dans le presse-papiers. Le bandeau vit **ici**, et non dans
     * l'écran, pour qu'il n'y ait qu'une règle d'affichage : le dernier message écrit gagne.
     * Deux sources concurrentes — l'une du dépôt, l'autre de l'écran — se masqueraient l'une
     * l'autre selon l'ordre des recompositions.
     */
    fun announce(message: String) {
        _state.value = _state.value.copy(notice = message)
    }

    private fun notice(error: Throwable) {
        _state.value = _state.value.copy(notice = describe(error))
    }

    /**
     * Message lisible d'une erreur.
     *
     * Seul `message` est lu. Le client d'origine inspectait aussi `details` et `code`, qui sont
     * les champs d'un objet d'erreur JavaScript : le client Supabase de Kotlin ne les présente
     * pas de la même façon, et fabriquer un « code » à partir d'un texte serait pire que de ne
     * pas en avoir — c'est le rôle de [Social.errorText], qui retombe sur un message générique.
     */
    private fun describe(error: Throwable): String = Social.errorText(message = error.message)
}
