package com.msoumaya.deepseekandroid.feature.recitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.audio.RecitationPlayback
import com.msoumaya.deepseekandroid.core.audio.RecitationPlayer
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.repository.RecitationRepository
import com.msoumaya.deepseekandroid.core.data.repository.RecitationState
import com.msoumaya.deepseekandroid.core.data.repository.SocialRepository
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.domain.RecitationsList
import com.msoumaya.deepseekandroid.core.domain.Social
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Récitations enregistrées
// ---------------------------------------------------------------------------
// Portage de la liste de `RecitationsScreen` (`src/RecitationsScreen.tsx`).
//
// Ce fichier ne contient que le câblage : il relie l'état du dépôt aux saisies de la personne,
// puis délègue la mise en forme à `RecitationsRenderer`. Le calcul vit là-bas parce qu'il est pur,
// donc éprouvable sans coroutine ni horloge.
//
// **Les saisies sont tenues ici, et non dans l'écran.** Le filtre choisi, la ligne dépliée et le
// choix d'ami ouvert sont des **entrées** du rendu : la liste affichée et les cartes en dépendent.
// Les garder dans les composables ferait deux sources pour la même décision — la puce allumée à
// l'écran, et la liste calculée ailleurs —, et un changement de filtre publierait un état où les
// deux ne seraient pas d'accord.
//
// **Ce que ce fichier fait des corrections.** Il les **demande** quand une ligne s'ouvre, et les
// **étiquette** par cette ligne (`RecitationsDetails`). C'est la seule façon d'être sûr qu'une
// réponse arrivée en retard ne s'affichera pas sous une autre récitation : le rendu ne garde que
// les détails dont l'étiquette est celle de la ligne ouverte.
//
// **Ce que ce fichier ne décide pas.** *Ce qu'un appui sur le bouton de lecture doit faire* est une
// règle, et elle vit dans `RecitationsList.playbackAction` — pure, donc éprouvée sans lecteur. Ici
// on se contente de l'exécuter. La distinction n'est pas cosmétique : c'est cette règle qui
// distingue *reprendre* de *recharger*, et se tromper produit un bouton muet sur une piste
// terminée — un défaut qu'aucun test d'écran n'attraperait. Il en va de même du partage : *ce qui
// peut être partagé* vit dans `RecitationsList`, et *à qui* dans `Social.shareRecipients`.
//
// **La piste chargée appartient à la ligne ouverte.** Déplier une autre ligne, ou tout replier,
// arrête la lecture. Sans cela, le son d'une récitation continuerait sous une autre, et rien à
// l'écran ne dirait laquelle on entend.
//
// **Un seul message, et il vient du dernier geste.** L'échec de préparation de l'écoute et le
// résultat du partage écrivent dans le **même** canal. C'est l'original, qui n'a qu'un `setMessage`
// et où le dernier geste écrase le précédent : deux champs se masqueraient l'un l'autre selon
// l'ordre des recompositions, et la personne ne saurait pas lequel croire.
// ---------------------------------------------------------------------------

/**
 * Les sources du rendu, réunies pour être lues d'un seul geste.
 *
 * Un `combine` à six branches rendrait un `List<Any>` ou six paramètres de types différents à
 * re-croiser dans le corps ; nommer les morceaux dit ce qu'on assemble, et l'ordre des champs suit
 * celui des arguments du `combine`.
 */
private data class Sources(
    val etat: RecitationState,
    val saisies: RecitationsInputs,
    val chargees: RecitationsDetails,
    val lecture: RecitationPlayback,
    val message: String?,
    val amis: List<RecitationFriend>,
)

/**
 * Prépare l'affichage de la liste des récitations, conduit son écoute et son partage.
 *
 * @param repository source de l'état des récitations, et destination des gestes.
 * @param player le lecteur d'une récitation enregistrée, ou `null` si aucun n'a été fourni au
 *   conteneur. Nul, l'écran n'offre **pas** de bouton de lecture : un bouton qui ne joue rien est
 *   le geste mort que ce dépôt s'interdit.
 * @param social la couche sociale, d'où viennent les **destinataires** et par où part le partage.
 *   Nulle, l'écran n'offre aucun bouton de partage — même raison, et même garde que [player].
 */
class RecitationsViewModel(
    private val repository: RecitationRepository,
    private val player: RecitationPlayer? = null,
    private val social: SocialRepository? = null,
) : ViewModel() {

    /** Les saisies, qui entrent dans le calcul. */
    private val inputs = MutableStateFlow(RecitationsInputs())

    /** Ce qui a été chargé pour la ligne ouverte, étiqueté par elle. */
    private val details = MutableStateFlow(RecitationsDetails())

    /**
     * Le message du **dernier geste**, ou `null`.
     *
     * Il porte deux choses, et c'est délibéré : l'échec de **préparation** de l'écoute — celui qui
     * dit qu'on n'a même pas pu obtenir l'adresse à ouvrir, faute de réseau ou de compte —, et le
     * résultat du **partage**, réussite comprise. `RecitationPlayback.error` ne dit pas la même
     * chose : lui dit qu'un fichier n'a pas pu s'ouvrir.
     *
     * Un seul canal, parce que l'original n'a qu'un `setMessage` : deux champs se masqueraient
     * l'un l'autre selon l'ordre des recompositions.
     */
    private val message = MutableStateFlow<String?>(null)

    /**
     * La récitation dont la piste est **chargée** dans le lecteur, ou `null`.
     *
     * Tenu ici, et non lu dans le lecteur : une adresse signée change à chaque signature, et
     * comparer deux adresses pour savoir si c'est « la même piste » serait faux tôt ou tard. Ce
     * qu'on veut savoir est **quelle ligne** a été chargée, et c'est le `ViewModel` qui l'a
     * demandée.
     */
    private var loadedId: String? = null

    /** L'état du lecteur, ou un état vide quand aucun lecteur n'a été fourni. */
    private val playback: StateFlow<RecitationPlayback> =
        player?.state ?: MutableStateFlow(RecitationPlayback())

    /**
     * Les destinataires possibles, tenus à jour depuis la couche sociale.
     *
     * La réduction aux amitiés **acceptées** vit dans `Social.shareRecipients`, où elle s'éprouve ;
     * ici on ne fait que la mettre en forme. Sans couche sociale, la liste est **vide** — et c'est
     * exactement ce que l'original lit quand il n'y a pas d'ami (« Aucun ami accepté pour le
     * moment. »).
     */
    private val amis: Flow<List<RecitationFriend>> = social?.state
        ?.map { etat ->
            Social.shareRecipients(etat.links).map { lien ->
                RecitationFriend(
                    linkId = lien.id,
                    name = lien.other?.displayName ?: RecitationText.FRIEND_FALLBACK,
                )
            }
        }
        ?: flowOf(emptyList())

    private val _state = MutableStateFlow(RecitationsUiState())

    /** État affichable de l'écran. */
    val state: StateFlow<RecitationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Deux `combine` imbriqués, et non un seul : les surcharges typées de `combine`
            // s'arrêtent à cinq sources. Les cinq premières sont celles du dépôt et des saisies,
            // la sixième vient de la couche sociale.
            //
            // Un seul `collect` sur l'ensemble : des collectes séparées liraient des valeurs de
            // moments différents, et publieraient une liste calculée sur un filtre déjà remplacé —
            // ou des cartes sans la ligne qui les porte.
            val base = combine(
                repository.state,
                inputs,
                details,
                playback,
                message,
            ) { etat, saisies, chargees, lecture, souci ->
                Sources(etat, saisies, chargees, lecture, souci, emptyList())
            }

            combine(base, amis) { sources, destinataires -> sources.copy(amis = destinataires) }
                .collect { sources ->
                    val pourLaLigne = sources.chargees.forOpen(sources.saisies.openId)
                    _state.value = RecitationsRenderer.render(
                        state = sources.etat,
                        // La lecture n'est pas une saisie : elle vient du lecteur, et le rendu la
                        // reçoit par les deux champs que `RecitationsInputs` réserve à cela.
                        inputs = sources.saisies.copy(
                            playing = sources.lecture.playing,
                            positionMs = sources.lecture.positionMs,
                        ),
                        corrections = pourLaLigne.corrections,
                        feedback = pourLaLigne.feedback,
                        // **La capacité, et non l'intention** : c'est la présence d'un lecteur qui
                        // décide si l'écran offre l'écoute, et non le fait qu'une ligne soit
                        // dépliée.
                        canListen = player != null,
                        // L'erreur du lecteur est distincte du message du dernier geste, et le
                        // rendu les ordonne : le message d'abord — il vient d'un geste, et il est
                        // plus récent —, puis l'erreur du lecteur, puis le dépôt.
                        playbackError = sources.lecture.error,
                        friends = sources.amis,
                        // Même garde que pour l'écoute : sans couche sociale, il n'y a personne à
                        // qui envoyer, et l'écran n'offre pas le geste.
                        canShare = social != null,
                        shareMessage = sources.message,
                    )
                }
        }
    }

    /**
     * Relit à l'ouverture de l'écran.
     *
     * **Sans effet si un chargement est déjà en cours.** L'écran s'ouvre presque toujours après la
     * construction du dépôt, qui charge déjà : sans cette garde, la première ouverture coûterait
     * deux lectures, et deux lectures qui se suivent sur la même donnée ne s'affichent pas, elles
     * se remplacent.
     */
    fun onVisible() {
        if (repository.state.value.loading) return
        viewModelScope.launch { repository.refresh() }
    }

    /** Relit à la demande : c'est le geste « Rafraîchir » de l'écran, et il n'a pas de garde. */
    fun onRefresh() {
        viewModelScope.launch { repository.refresh() }
    }

    /**
     * Retient le filtre touché.
     *
     * La comparaison porte sur le **libellé**, parce que c'est ce que le sélecteur segmenté rend.
     * Un libellé inconnu laisse le filtre en place : le composant ne choisit pas à la place de
     * l'écran, et l'écran ne doit pas inventer un filtre qu'aucun segment n'a demandé.
     */
    fun onFilterSelected(label: String) {
        val filtre = filtreDe(label) ?: return
        inputs.value = inputs.value.copy(filter = filtre)
    }

    /**
     * Déplie la ligne touchée, ou la replie si c'était déjà elle.
     *
     * **Les corrections sont demandées à l'ouverture, et non à la lecture de la liste** : c'est
     * l'original, où la liste ne porte que les lignes et où les cartes viennent d'un second appel.
     * Tout replier **vide** les détails en mémoire, pour qu'une réponse encore en vol ne laisse
     * pas ses cartes derrière elle.
     *
     * **Et l'écoute s'arrête.** La piste chargée appartient à la ligne ouverte : la garder ferait
     * entendre une récitation sous une autre ligne, ou sous aucune.
     *
     * **Et le choix d'ami se ferme, confirmation comprise.** C'est l'original, qui remet `sharing`
     * à `null` à l'ouverture d'une ligne : la liste des destinataires appartient au bouton qui l'a
     * ouverte. La confirmation en cours tombe avec le choix — la garder ferait confirmer l'envoi
     * d'une récitation qui n'est plus celle qu'on regarde.
     */
    fun onOpen(id: String) {
        val ouverte = inputs.value.openId
        val nouvelle = if (ouverte == id) null else id
        inputs.value = inputs.value.copy(
            openId = nouvelle,
            sharingId = null,
            pendingLinkId = null,
        )

        arreterLEcoute()

        if (nouvelle == null) {
            details.value = RecitationsDetails()
            return
        }

        viewModelScope.launch {
            details.value = RecitationsDetails(
                id = nouvelle,
                corrections = repository.corrections(nouvelle),
                feedback = repository.generalFeedback(nouvelle),
            )
        }
    }

    /**
     * Le geste « ▶ Réécouter / Pause ».
     *
     * Ce qu'il fait est décidé par `RecitationsList.playbackAction`, et non ici : voir l'en-tête du
     * fichier. Ce fichier se contente d'exécuter la décision.
     */
    fun onPlayPause() {
        val lecteur = player ?: return
        val ouvert = inputs.value.openId ?: return
        val lecture = lecteur.state.value

        when (
            RecitationsList.playbackAction(
                playing = lecture.playing,
                ended = lecture.ended,
                loadedId = loadedId,
                openId = ouvert,
            )
        ) {
            RecitationsList.PlaybackAction.NOTHING -> Unit
            RecitationsList.PlaybackAction.PAUSE -> lecteur.pause()
            RecitationsList.PlaybackAction.RESUME -> lecteur.resume()
            RecitationsList.PlaybackAction.LOAD -> charger(ouvert)
        }
    }

    /**
     * Recule de dix secondes.
     *
     * **Rien à faire tant que rien n'est chargé**, et c'est l'original (`player.current?.seekTo`).
     * Déplacer la tête d'une piste qui n'existe pas n'a pas de sens, et le lecteur n'a pas à
     * inventer une piste pour obéir.
     */
    fun onSeekBackward() {
        val lecture = player?.state?.value ?: return
        if (!lecture.isLoaded) return
        player.seekTo(RecitationsList.seekBackward(lecture.positionMs))
    }

    /** Avance de dix secondes. Même garde que [onSeekBackward]. */
    fun onSeekForward() {
        val lecture = player?.state?.value ?: return
        if (!lecture.isLoaded) return
        player.seekTo(RecitationsList.seekForward(lecture.positionMs))
    }

    /**
     * Ouvre — ou referme — le **choix d'ami** de la ligne [id].
     *
     * L'original bascule sur la ligne touchée (`setSharing(sharing === item.id ? null : item.id)`),
     * et n'ouvre donc qu'un choix à la fois. Rien n'est décidé ici sur ce qui est partageable : le
     * bouton n'existe déjà que là où le partage a un sens.
     *
     * **Une confirmation en cours tombe avec le choix** : refermer le choix, c'est renoncer, et
     * laisser la phrase de confirmation derrière soi ferait confirmer un envoi que plus rien
     * n'attend.
     */
    fun onShare(id: String) {
        val ouvert = inputs.value.sharingId
        inputs.value = inputs.value.copy(
            sharingId = if (ouvert == id) null else id,
            pendingLinkId = null,
        )
    }

    /**
     * Retient l'ami [linkId] et **demande confirmation**, sans rien envoyer.
     *
     * **Le partage n'est pas immédiat, et c'est l'original.** `Alert.alert` y pose une question —
     * « Seul <ami> pourra écouter <référence> tant que vous restez amis. » —, et n'appelle
     * `shareRecitation` qu'au geste de confirmation. Le dépôt n'a pas d'outillage de dialogue : le
     * second cran est donc tenu ici, et l'écran l'affiche **dans la ligne**.
     *
     * **Rien n'est vérifié ici sur l'ami.** Le rendu a déjà réduit la liste aux amitiés acceptées
     * (`Social.shareRecipients`), et l'écran n'offre que celles-là : retrouver l'ami dans l'état
     * pour le valider une seconde fois ferait deux sources pour la même décision.
     */
    fun onPickFriend(linkId: String) {
        if (inputs.value.sharingId == null) return
        inputs.value = inputs.value.copy(pendingLinkId = linkId)
    }

    /** Renonce au partage en cours de confirmation. Le choix d'ami, lui, **reste ouvert**. */
    fun onCancelShare() {
        inputs.value = inputs.value.copy(pendingLinkId = null)
    }

    /**
     * Envoie la récitation à l'ami dont la confirmation est ouverte.
     *
     * **Le partage part par un lien, et jamais par un cercle.** Un cercle réunit des gens qui ne
     * sont pas tous amis, et le serveur refuse un partage qui y serait déposé — la signature de
     * `shareRecitation` ne prend donc qu'un lien, et le type dit la règle.
     *
     * La réussite **referme le choix et la confirmation**, comme l'original (`setSharing(null)`),
     * et laisse un message. L'échec **referme la seule confirmation** : la personne doit pouvoir
     * réessayer, ou choisir un autre destinataire, sans rouvrir le choix.
     */
    fun onConfirmShare() {
        val ouvert = inputs.value.sharingId ?: return
        val destinataire = inputs.value.pendingLinkId ?: return
        val reseau = social ?: return
        val ligne = _state.value.rows.firstOrNull { it.id == ouvert } ?: return

        viewModelScope.launch {
            message.value = null
            val raison = reseau.shareRecitation(
                linkId = destinataire,
                recitationId = ouvert,
                description = RecitationsList.shareDescription(ligne.shareLabel),
            )
            message.value = raison ?: RecitationText.SHARE_DONE
            inputs.value = if (raison == null) {
                // Réussi : le choix entier se referme, comme l'original.
                inputs.value.copy(sharingId = null, pendingLinkId = null)
            } else {
                // Refusé : seule la confirmation tombe. Le choix reste ouvert, et la personne peut
                // réessayer ou viser un autre ami sans rouvrir la liste.
                inputs.value.copy(pendingLinkId = null)
            }
        }
    }

    /**
     * Retire une récitation.
     *
     * **La copie distante l'emporte quand elle existe** : le dépôt emporte alors la copie locale
     * dans le même geste. Ne retirer que le fichier de l'appareil laisserait la ligne listée, et le
     * prochain dépôt la renverrait au serveur — annulant la suppression demandée.
     *
     * Une ligne qui n'existe dans **aucune** des deux listes ne fait rien : elle vient d'être
     * retirée par une relecture, et redemander sa suppression serait un aller-retour pour rien.
     *
     * **Et l'écoute s'arrête** quand c'est la ligne ouverte qu'on retire : le fichier disparaît du
     * disque, et une piste qui joue encore un fichier effacé est un état que rien ne rattrape.
     */
    fun onDelete(id: String) {
        val distante = repository.state.value.remote.firstOrNull { it.id == id }
        val locale = repository.state.value.items.firstOrNull { it.id == id }

        if (inputs.value.openId == id) {
            inputs.value = inputs.value.copy(
                openId = null,
                sharingId = null,
                pendingLinkId = null,
            )
            details.value = RecitationsDetails()
            arreterLEcoute()
        }

        viewModelScope.launch {
            when {
                distante != null -> repository.deleteRemote(distante)
                locale != null -> repository.delete(locale)
            }
        }
    }

    /**
     * Charge et joue la récitation [id] depuis le début.
     *
     * **Le fichier de l'appareil l'emporte sur le serveur**, comme dans l'original
     * (`file?.uri ?? await signedAudioUrl(...)`) : il est déjà là, il ne coûte pas d'aller-retour,
     * et il fonctionne sans réseau.
     *
     * L'échec de la signature ne remonte pas en exception : il est publié comme message. Laisser
     * filer une exception dans `viewModelScope` ferait tomber l'application pour un fichier
     * introuvable, ce qui est hors de proportion.
     */
    private fun charger(id: String) {
        val lecteur = player ?: return

        viewModelScope.launch {
            val locale = repository.state.value.items.firstOrNull { it.id == id }
            val distante = repository.state.value.remote.firstOrNull { it.id == id }

            val adresse = when {
                locale != null -> locale.uri
                distante != null -> runCatching { repository.signedUrl(distante.storagePath) }
                    .getOrElse {
                        message.value = RecitationText.AUDIO_UNAVAILABLE
                        return@launch
                    }

                else -> return@launch
            }

            // **Le message est effacé avant de jouer**, et ce n'est pas un détail : sans cela, un
            // échec de préparation resterait affiché par-dessus l'erreur du lecteur, et la personne
            // lirait la panne de l'essai précédent.
            message.value = null
            loadedId = id
            lecteur.play(adresse)
        }
    }

    /** Arrête l'écoute et oublie la piste chargée. */
    private fun arreterLEcoute() {
        loadedId = null
        message.value = null
        player?.stop()
    }

    /** Le filtre que porte un libellé, ou `null` si aucun segment ne le réclame. */
    private fun filtreDe(label: String): RecitationsList.Filter? = when (label) {
        RecitationText.FILTER_ALL -> RecitationsList.Filter.ALL
        RecitationText.FILTER_QURAN -> RecitationsList.Filter.QURAN
        RecitationText.FILTER_INVOCATION -> RecitationsList.Filter.INVOCATION
        else -> null
    }

    companion object {
        /** Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return RecitationsViewModel(
                        repository = container.recitations,
                        player = container.recitationPlayer,
                        social = container.social,
                    ) as T
                }
            }
    }
}
