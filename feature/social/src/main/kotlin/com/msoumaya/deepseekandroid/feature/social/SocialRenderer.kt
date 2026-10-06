package com.msoumaya.deepseekandroid.feature.social

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.data.repository.SocialState
import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Social
import com.msoumaya.deepseekandroid.core.domain.SocialText
import com.msoumaya.deepseekandroid.core.model.FriendLink

// ---------------------------------------------------------------------------
// Rendu de l'écran « Amis »
// ---------------------------------------------------------------------------
// Portage de la liste d'amis de `FriendsScreen` (`src/SocialScreens.tsx:144-167`).
//
// **Pourquoi un objet à part, et pur.** Le client d'origine calculait la liste visible **dans
// le JSX** : le filtre, la recherche, le tri et la coupe à cinq entrées étaient écrits au
// milieu du rendu, dans une seule expression de quatre lignes. Ici, ce qui **décide** vit dans
// `core:domain/Social.kt` et s'éprouve là-bas ; ce qui **met en forme** — quel libellé de
// présence, quelle date, quel repli de nom — vit ici et s'éprouve de même, sans appareil.
//
// **Ce que ce fichier ne fait pas.** Il ne touche ni au réseau, ni à l'horloge de l'appareil :
// l'instant courant lui est **passé**, comme au rendu du programme, sinon un test dépendrait du
// jour où il tourne.
// ---------------------------------------------------------------------------

/**
 * Ce que la personne a saisi, et qui entre dans le calcul.
 *
 * Ces six valeurs sont tenues par le `ViewModel` et publiées : ce ne sont pas des états
 * d'interface oubliés dans un composable, mais des **entrées** du rendu. La distinction compte
 * pour la recherche : garder le texte dans le champ et la liste ailleurs ferait deux sources
 * pour la même décision, et l'une des deux finirait par retarder.
 */
@Immutable
internal data class SocialInputs(
    val query: String = "",
    val filter: Social.Filter = Social.Filter.ALL,
    val allFriends: Boolean = false,
    val optionsOpen: Boolean = false,
    val code: String = "",
    val groupName: String = "",
)

/** Rendu de la liste d'amis : filtrage, mise en forme, et ce qui reste à décider. */
internal object SocialRenderer {

    /**
     * Traduit l'état du dépôt en état affichable.
     *
     * @param nowIso instant courant, injecté. Il ne sert qu'à une chose : dire si une suspension
     *   est encore active. Une suspension sans terme est active pour toujours, et une suspension
     *   échue ne doit plus s'afficher — c'est la seule décision de cet écran qui dépende du temps.
     */
    fun render(
        state: SocialState,
        inputs: SocialInputs,
        nowIso: String = Dates.nowIso(),
    ): SocialUiState {
        // **Sans profil, aucune liste n'est calculée.** Le repli de `Social.otherId` rend le
        // demandeur quand on ne le reconnaît ni comme demandeur ni comme destinataire : avec un
        // identifiant vide, chaque lien rendrait donc son demandeur, et la liste afficherait
        // des gens au hasard. Le garde-fou est ici, à l'entrée, et pas chez chaque appelant.
        val me = state.profile?.id
        val links = if (me == null) emptyList() else state.links

        return SocialUiState(
            loading = state.loading,
            failure = state.failure,
            signedIn = state.signedIn,
            busy = state.busy,
            notice = state.notice,
            // La même vérité que `ConversationUiState.open`, lue par l'autre écran : ce n'est pas
            // l'écran qui décide de ce qui est ouvert, c'est le dépôt.
            conversationOpen = state.room != null,
            suspension = state.suspension
                ?.takeIf { Social.isSuspended(it, nowIso) }
                ?.let { SocialText.suspended(it.reason) },

            query = inputs.query,
            filterLabel = inputs.filter.label,
            allFriends = inputs.allFriends,
            optionsOpen = inputs.optionsOpen,
            code = inputs.code,
            groupName = inputs.groupName,

            inviteCode = state.profile?.inviteCode,

            friends = if (me == null) {
                emptyList()
            } else {
                Social.visibleFriends(
                    links = links,
                    me = me,
                    filter = inputs.filter,
                    query = inputs.query,
                    online = state.online,
                    summaries = state.summaries,
                    all = inputs.allFriends,
                ).map { friendRow(it, me, state) }
            },

            friendCount = Social.acceptedCount(links),

            invitations = if (me == null) {
                emptyList()
            } else {
                Social.receivedInvitations(links, me).map {
                    InvitationRow(
                        linkId = it.id,
                        label = SocialText.invitationFrom(it.other?.displayName ?: SocialText.A_MEMBER),
                    )
                }
            },

            sent = if (me == null) {
                emptyList()
            } else {
                Social.sentInvitations(links, me).map {
                    InvitationRow(
                        linkId = it.id,
                        label = SocialText.invitationTo(it.other?.displayName ?: SocialText.A_MEMBER),
                    )
                }
            },

            blocked = if (me == null) {
                emptyList()
            } else {
                Social.blockedLinks(links, me).map {
                    BlockedRow(
                        otherId = Social.otherId(it, me),
                        label = SocialText.blocked(it.other?.displayName ?: SocialText.MEMBER),
                    )
                }
            },

            circles = state.groups.map {
                CircleRow(id = it.id, name = it.name, isAdminContact = it.contactUserId != null)
            },

            // L'invitation part avec un code **non vide après rognage**, et pas seulement non
            // vide : une espace tapée par erreur activerait le bouton, et le serveur refuserait
            // un code qui n'existe pas.
            canSendInvitation = inputs.code.isNotBlank() && !state.busy,
            canCreateGroup = Social.validGroupName(inputs.groupName) && !state.busy,
        )
    }

    /**
     * Met un lien en forme pour la liste.
     *
     * Trois décisions, et chacune a une raison :
     *
     *  - le **sous-titre** suit l'ordre de l'original : la présence d'abord, puis la date du
     *    dernier message, puis l'invitation à commencer. La présence passe avant la date parce
     *    qu'elle est vraie **maintenant**, là où la date ne dit que le passé ;
     *  - la **présence** se lit sur `online[other] == true` : une entrée absente de la carte veut
     *    dire « on ne sait pas », et le filtre « En ligne » l'exclut pour cette raison. La
     *    pastille de la liste, elle, ne distingue pas les deux cas — elle est grise dans les
     *    deux, comme dans l'original, et c'est le filtre qui porte la différence ;
     *  - le **nom** retombe sur « Ami » quand le lien ne porte pas de profil. Le faire
     *    disparaître serait pire : la conversation existe, et la cacher ferait croire à une
     *    amitié perdue.
     */
    private fun friendRow(link: FriendLink, me: String, state: SocialState): FriendRow {
        val other = Social.otherId(link, me)
        val online = state.online[other] == true
        val summary = state.summaries[link.id]

        return FriendRow(
            id = link.id,
            otherId = other,
            name = link.other?.displayName ?: SocialText.FRIEND,
            online = online,
            subtitle = if (online) {
                SocialText.ONLINE
            } else {
                summary?.let { SocialText.dayStamp(it.createdAt) } ?: SocialText.START_CHAT
            },
            summary = summary?.body.orEmpty(),
            unread = summary?.unread ?: 0,
        )
    }
}
