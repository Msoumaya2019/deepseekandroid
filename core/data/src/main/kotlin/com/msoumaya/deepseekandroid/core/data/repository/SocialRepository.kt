package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.ChatRoom
import com.msoumaya.deepseekandroid.core.data.remote.OwnerStore
import com.msoumaya.deepseekandroid.core.data.remote.SocialSource
import com.msoumaya.deepseekandroid.core.domain.Social
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.ChatMessage
import com.msoumaya.deepseekandroid.core.model.ChatMessageKind
import com.msoumaya.deepseekandroid.core.model.ConversationSummary
import com.msoumaya.deepseekandroid.core.model.FriendGroup
import com.msoumaya.deepseekandroid.core.model.FriendLink
import com.msoumaya.deepseekandroid.core.model.FriendLinkStatus
import com.msoumaya.deepseekandroid.core.model.FriendOverview
import com.msoumaya.deepseekandroid.core.model.FriendProfile
import com.msoumaya.deepseekandroid.core.model.GroupMember
import com.msoumaya.deepseekandroid.core.model.ReviewAppointment
import com.msoumaya.deepseekandroid.core.model.SharedGoal
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

    /**
     * Conversation ouverte, ou `null` si aucune ne l'est.
     *
     * Elle vit **dans** cet état, et non dans un second flux, parce que la conversation a besoin
     * de la liste d'amis : [Social.senderName] lit les liens **et** les membres pour nommer un
     * auteur, et c'est la liste des cercles qui dit si la pièce ouverte est celui de
     * l'administration. Deux flux séparés liraient deux versions de la même vérité, et un nom
     * s'afficherait avec la liste d'avant.
     */
    val room: RoomState? = null,
)

/**
 * Ce que la conversation ouverte sait.
 *
 * [history] porte les deux drapeaux de la pagination — « il reste des messages plus anciens » et
 * « on a atteint le début » —, et il est **publié** plutôt que gardé dans une variable locale du
 * chargement : c'est lui qui décide de l'affichage du bouton, donc il doit s'éprouver.
 *
 * [overview], [members], [goals] et [appointments] sont des **compléments**. Aucun n'est
 * nécessaire pour lire ou écrire un message, et c'est pour cela qu'un échec les concernant ne
 * doit **jamais** effacer [messages].
 */
data class RoomState(
    /** La pièce ouverte : un lien, ou un cercle. Jamais les deux, jamais aucun. */
    val room: ChatRoom,

    /** Vrai pendant la première lecture de la pièce. */
    val loading: Boolean = false,

    /** Panne de la lecture **des messages**, quand il n'y a rien à montrer à la place. */
    val failure: String? = null,

    /** Les messages, du plus ancien au plus récent. */
    val messages: List<ChatMessage> = emptyList(),

    /** Reste-t-il des messages plus anciens ? Voir [Social.History]. */
    val history: Social.History = Social.History(),

    /** Vrai pendant la lecture d'une page plus ancienne. */
    val loadingOlder: Boolean = false,

    /** Position de lecture de l'autre, pour l'accusé de mes messages. Liens seulement. */
    val otherReadAt: String? = null,

    /** Ce que l'ami accepte de montrer. `null` s'il ne partage rien, ou dans un cercle. */
    val overview: FriendOverview? = null,

    /** Membres du cercle. Vide dans un lien. */
    val members: List<GroupMember> = emptyList(),

    /** Objectifs partagés du lien, du plus récent au plus ancien. Vide dans un cercle. */
    val goals: List<SharedGoal> = emptyList(),

    /** Rendez-vous à venir du lien. Vide dans un cercle. */
    val appointments: List<ReviewAppointment> = emptyList(),
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
     * Ouvre une pièce et lit sa page la plus récente.
     *
     * **L'état de la pièce précédente est remis à zéro, pas réutilisé.** Sans cela, ouvrir un
     * ami après un autre montrerait les messages du premier ; et si la seconde lecture échouait,
     * ils resteraient à l'écran **sous le nom du second** — un message d'un ami attribué à un
     * autre, ce qui est la faute la plus grave que cet écran puisse commettre.
     */
    fun openRoom(room: ChatRoom) {
        // **Sans source, il n'y a rien à lire.** Poser `loading` quand même laisserait un
        // chargement sans fin à l'écran — exactement l'attente que `refresh()` évite en effaçant
        // l'état. Le geste ne fait donc rien. Il n'est de toute façon pas atteignable : sans
        // source il n'y a pas de compte, et la liste affiche « Connecte-toi à ton compte ».
        if (source == null) return
        _state.value = _state.value.copy(room = RoomState(room = room, loading = true))
        scope.launch { loadRoom() }
    }

    /**
     * Ferme la conversation.
     *
     * Les messages sont oubliés. L'original les gardait en mémoire sans les afficher — l'écran
     * les remet de toute façon à zéro à la réouverture —, mais les garder ici laisserait une
     * conversation lisible dans l'état après la fermeture, pour personne.
     */
    fun closeRoom() {
        _state.value = _state.value.copy(room = null)
    }

    /** Relit la page la plus récente de la pièce ouverte. Sans pièce ouverte, ne fait rien. */
    suspend fun refreshRoom() {
        if (_state.value.room == null) return
        loadRoom()
    }

    /**
     * Lit une page **plus ancienne**.
     *
     * Le curseur est l'instant du message **le plus ancien déjà affiché**, et la requête demande
     * ce qui lui est **strictement** antérieur : la page reçue ne recouvre donc jamais la
     * précédente, et la fusion reste sans doublon.
     *
     * Trois refus, et chacun évite un aller-retour inutile : pas de pièce ouverte, pas encore de
     * message à partir duquel remonter, et une lecture déjà en cours.
     */
    suspend fun loadOlder() {
        val room = _state.value.room ?: return
        val oldest = room.messages.firstOrNull() ?: return
        if (room.loadingOlder) return
        val api = source ?: return
        val open = room.room

        _state.value = _state.value.copy(room = room.copy(loadingOlder = true))
        try {
            val older = api.messages(open, before = oldest.createdAt)
            publish(open) {
                it.copy(
                    messages = Social.mergeMessages(it.messages, older),
                    history = Social.afterOlderPage(it.history, older.size),
                    loadingOlder = false,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            publish(open) { it.copy(loadingOlder = false) }
            notice(error)
        }
    }

    /**
     * Envoie un message.
     *
     * @param success bandeau publié en cas de succès. **Vide pour un message ordinaire**, comme
     *   dans l'original : un message qu'on vient d'envoyer se voit dans la conversation, et un
     *   bandeau par-dessus serait du bruit. Vide veut aussi dire « efface le bandeau précédent »,
     *   et non « n'y touche pas ». Le **partage d'étape**, lui, ne se voit pas — il s'ajoute à une
     *   liste —, et il porte donc sa propre confirmation.
     */
    suspend fun sendMessage(
        body: String,
        kind: ChatMessageKind = ChatMessageKind.TEXT,
        success: String = "",
    ): Boolean {
        val room = _state.value.room?.room ?: return false
        return actInRoom(success) { it.sendMessage(room, body, kind) }
    }

    /**
     * Partage une récitation dans la conversation du lien [linkId].
     *
     * Rend `null` quand le partage a abouti, et la **raison** de l'échec sinon.
     *
     * **C'est le seul geste de ce dépôt qui rend son erreur au lieu de la publier**, et la raison
     * est précise : le partage se déclenche depuis l'**écran des récitations**, qui a son propre
     * bandeau. Publier ici un avis ferait apparaître la panne sur l'écran « Amis », où personne
     * n'a rien demandé — et laisserait l'écran des récitations muet, ce que ce dépôt s'interdit.
     *
     * **Et il ne relit rien.** Les autres gestes passent par [act], qui recharge l'espace social
     * entier ; ici ce serait quatre lectures pour un message déposé dans une conversation qui
     * n'est peut-être même pas ouverte. Rien de ce que l'écran des récitations affiche n'en
     * dépend : sa liste vient de `RecitationRepository`, pas d'ici.
     */
    suspend fun shareRecitation(
        linkId: String,
        recitationId: String,
        description: String,
    ): String? {
        val api = source ?: return SocialText.CONNECTION_NEEDED
        _state.value = _state.value.copy(busy = true)
        return try {
            api.shareRecitation(linkId, recitationId, description)
            _state.value = _state.value.copy(busy = false)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(busy = false)
            describe(error)
        }
    }

    /** Supprime un message. */
    suspend fun deleteMessage(messageId: String): Boolean =
        actInRoom(SocialText.SAVED) { it.deleteMessage(messageId) }

    /** Signale un message à la modération. */
    suspend fun reportMessage(messageId: String, reason: String): Boolean =
        actInRoom(SocialText.REPORT_SENT) { it.reportMessage(messageId, reason) }

    /**
     * Masque un message **pour moi seul**.
     *
     * La ligne reste en base : c'est une préférence d'affichage, pas une suppression. Le message
     * disparaît donc à la relecture, et pour personne d'autre.
     */
    suspend fun hideMessage(messageId: String): Boolean =
        actInRoom(SocialText.SAVED) { it.hideMessageForMe(messageId) }

    suspend fun inviteToGroup(groupId: String, friendId: String): Boolean =
        actInRoom(SocialText.SAVED) { it.inviteGroupMember(groupId, friendId) }

    suspend fun acceptGroupInvite(groupId: String): Boolean =
        actInRoom(SocialText.SAVED) { it.acceptGroupInvite(groupId) }

    suspend fun declineGroupInvite(groupId: String): Boolean =
        actInRoom(SocialText.SAVED) { it.declineGroupInvite(groupId) }

    suspend fun setModerator(groupId: String, memberId: String, enabled: Boolean): Boolean =
        actInRoom(SocialText.SAVED) { it.setGroupModerator(groupId, memberId, enabled) }

    suspend fun removeGroupMember(groupId: String, memberId: String): Boolean =
        actInRoom(SocialText.SAVED) { it.removeGroupMember(groupId, memberId) }

    /**
     * Supprime le cercle, et referme la conversation.
     *
     * **Seul geste de conversation qui relit aussi la liste d'amis** : le cercle disparaît de
     * « Cercles privés », et l'y laisser ferait ouvrir une pièce qui n'existe plus. Il passe donc
     * par le geste commun, puis ferme la pièce — dans cet ordre, sinon la relecture publierait un
     * état dont la pièce ouverte n'existe plus.
     */
    suspend fun deleteGroup(groupId: String): Boolean {
        val ok = act(SocialText.SAVED) { it.deleteGroup(groupId) }
        if (ok) closeRoom()
        return ok
    }

    suspend fun proposeSharedGoal(linkId: String, weekStart: String, targetSessions: Int): Boolean =
        actInRoom(SocialText.SAVED) { it.proposeSharedGoal(linkId, weekStart, targetSessions) }

    suspend fun acceptSharedGoal(goalId: String): Boolean =
        actInRoom(SocialText.SAVED) { it.acceptSharedGoal(goalId) }

    suspend fun proposeAppointment(linkId: String, startsAt: String): Boolean =
        actInRoom(SocialText.SAVED) { it.proposeAppointment(linkId, startsAt) }

    suspend fun acceptAppointment(appointmentId: String): Boolean =
        actInRoom(SocialText.SAVED) { it.acceptAppointment(appointmentId) }

    suspend fun cancelAppointment(appointmentId: String): Boolean =
        actInRoom(SocialText.SAVED) { it.cancelAppointment(appointmentId) }

    /**
     * Lit la page la plus récente de la pièce ouverte, puis ses compléments.
     *
     * **Ce qui est essentiel, et ce qui ne l'est pas.** Les messages d'abord : leur échec, quand
     * rien n'est encore affiché, est une panne. Tout le reste — membres, aperçu de l'ami,
     * objectifs, rendez-vous, position de lecture de l'autre — est lu **après**, et son échec ne
     * publie qu'un avis. Un aperçu illisible ne doit pas effacer une conversation en cours de
     * lecture : c'est la même règle que pour la liste d'amis, et elle a la même raison.
     */
    private suspend fun loadRoom() {
        val api = source ?: return
        val room = _state.value.room ?: return
        val open = room.room

        val latest = try {
            api.messages(open, before = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val current = roomOf(open) ?: return
            if (current.messages.isEmpty()) {
                _state.value = _state.value.copy(
                    room = current.copy(loading = false, failure = describe(error)),
                )
            } else {
                // Une relecture ratée ne rend pas faux ce qui est déjà affiché.
                _state.value = _state.value.copy(room = current.copy(loading = false))
                notice(error)
            }
            return
        }

        publish(open) {
            it.copy(
                loading = false,
                failure = null,
                messages = Social.mergeMessages(it.messages, latest),
                history = Social.afterLatestPage(it.history, latest.size),
            )
        }

        loadRoomExtras(open, isLink = open.linkId != null)
    }

    /**
     * Lit les compléments de la pièce ouverte.
     *
     * **L'ordre suit celui de l'original, et il a une raison.** Dans un **cercle**, on lit les
     * membres. Dans un **lien**, on marque la conversation comme lue, puis on lit la position de
     * l'autre — et, sauf dans le cercle de l'administration, les objectifs partagés, les
     * rendez-vous et l'aperçu de l'ami.
     *
     * Chaque complément est **isolé** : le premier qui échoue n'empêche pas les suivants, et
     * aucun ne publie de panne. Un aperçu d'ami illisible ne doit pas empêcher de lire un
     * cercle, ni de voir la conversation.
     */
    private suspend fun loadRoomExtras(open: ChatRoom, isLink: Boolean) {
        val api = source ?: return
        val adminContact = isAdminContact(open)

        if (!isLink) {
            complement { api.members(open.groupId!!) }?.let { members ->
                publish(open) { it.copy(members = members) }
            }
            return
        }

        val linkId = open.linkId!!

        // Marquer comme lu **avant** de lire la position de l'autre : ce sont deux colonnes
        // distinctes — la mienne et la sienne —, mais l'original les enchaîne dans cet ordre.
        val marked = complement { api.markConversationRead(linkId); true } != null
        if (marked) {
            // **Écart assumé.** Le client d'origine rappelle ici une fonction serveur qui
            // recompte les non-lus. Ce portage n'a pas d'équivalent : le compteur de la liste est
            // mis **à zéro localement**, ce qui donne le même effet sans un aller-retour, et la
            // prochaine relecture de la liste le confirmera.
            //
            // C'est la **seule** modification de cette méthode qui ne porte pas sur la pièce mais
            // sur la liste d'amis : c'est pourquoi elle écrit l'état entier au lieu de passer par
            // `publish`. Un lien inconnu n'a pas de résumé, et l'absence de résumé est un no-op.
            val summary = _state.value.summaries[linkId]
            if (summary != null) {
                _state.value = _state.value.copy(
                    summaries = _state.value.summaries + (linkId to summary.copy(unread = 0)),
                )
            }
        }

        // La position de lecture de l'autre n'est lue que si le lien est **connu** : sans lui, on
        // ne saurait pas qui interroger. L'original fait le même garde-fou.
        val me = _state.value.profile?.id
        val link = _state.value.links.firstOrNull { it.id == linkId }
        if (link != null && me != null) {
            val other = Social.otherId(link, me)
            if (!adminContact) {
                complement { api.overview(other) }?.let { overview ->
                    publish(open) { it.copy(overview = overview) }
                }
            }
            complement { api.otherReadAt(linkId, other) }?.let { at ->
                publish(open) { it.copy(otherReadAt = at) }
            }
        }

        if (adminContact) return

        // Deux listes indépendantes, lues l'une après l'autre : l'original les demande en
        // parallèle, mais leur échec est **séparé** dans les deux cas, et c'est ce qui compte.
        complement { api.sharedGoals(linkId) }?.let { goals ->
            publish(open) { it.copy(goals = goals) }
        }
        complement { api.appointments(linkId) }?.let { dates ->
            publish(open) { it.copy(appointments = dates) }
        }
    }

    /**
     * Exécute un geste de conversation, puis relit la pièce.
     *
     * Il ne relit **pas** la liste d'amis : rien de ce qu'on y fait ne la change. Le seul geste
     * qui la change est la suppression d'un cercle, et il passe pour cette raison par le chemin
     * commun.
     */
    private suspend fun actInRoom(success: String, block: suspend (SocialSource) -> Any?): Boolean {
        val api = source ?: return false
        _state.value = _state.value.copy(busy = true)
        return try {
            block(api)
            _state.value = _state.value.copy(busy = false, notice = success.ifEmpty { null })
            loadRoom()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(busy = false, notice = describe(error))
            false
        }
    }

    /**
     * Lit un complément sans jamais publier de panne.
     *
     * Rend `null` en cas d'échec, et publie alors un **avis**. C'est la règle commune de tous les
     * compléments : ils ne sont pas la conversation, et les perdre ne doit pas coûter les
     * messages déjà affichés.
     */
    private suspend fun <T> complement(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            notice(error)
            null
        }

    /** La pièce ouverte, si c'est bien celle qu'on croit — sinon `null`. */
    private fun roomOf(open: ChatRoom): RoomState? = _state.value.room?.takeIf { it.room == open }

    /** Applique une modification à la pièce **si c'est toujours celle qui est ouverte**. */
    private fun publish(open: ChatRoom, change: (RoomState) -> RoomState) {
        val current = roomOf(open) ?: return
        _state.value = _state.value.copy(room = change(current))
    }

    /**
     * La pièce ouverte est-elle le cercle de l'administration ?
     *
     * La question se répond avec la **liste des cercles**, et non avec un drapeau porté par la
     * pièce : c'est le serveur qui marque ce cercle (`contactUserId`), et une copie locale
     * pourrait mentir. L'original fait exactement cette lecture.
     *
     * **La règle vit au domaine**, et celle-ci ne fait que la lire. Le rendu de la conversation
     * a besoin de la même réponse pour choisir ses blocs : deux copies auraient divergé sans le
     * dire, et l'une des deux aurait affiché l'entraide dans le cercle de l'administration.
     */
    private fun isAdminContact(open: ChatRoom): Boolean =
        Social.isAdminContact(_state.value.groups, open.groupId)

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
