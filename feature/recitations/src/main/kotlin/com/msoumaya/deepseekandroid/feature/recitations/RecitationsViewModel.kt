package com.msoumaya.deepseekandroid.feature.recitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.audio.RecitationPlayback
import com.msoumaya.deepseekandroid.core.audio.RecitationPlayer
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.repository.RecitationRepository
import com.msoumaya.deepseekandroid.core.data.repository.RecitationState
import com.msoumaya.deepseekandroid.core.domain.RecitationText
import com.msoumaya.deepseekandroid.core.domain.RecitationsList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
// **Les saisies sont tenues ici, et non dans l'écran.** Le filtre choisi et la ligne dépliée sont
// des **entrées** du rendu : la liste affichée et les cartes en dépendent. Les garder dans les
// composables ferait deux sources pour la même décision — la puce allumée à l'écran, et la liste
// calculée ailleurs —, et un changement de filtre publierait un état où les deux ne seraient pas
// d'accord.
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
// terminée — un défaut qu'aucun test d'écran n'attraperait.
//
// **La piste chargée appartient à la ligne ouverte.** Déplier une autre ligne, ou tout replier,
// arrête la lecture. Sans cela, le son d'une récitation continuerait sous une autre, et rien à
// l'écran ne dirait laquelle on entend.
// ---------------------------------------------------------------------------

/**
 * Les sources du rendu, réunies pour être lues d'un seul geste.
 *
 * Un `combine` à cinq branches rendrait un `List<Any>` ou cinq paramètres de types différents à
 * re-croiser dans le corps ; nommer les morceaux dit ce qu'on assemble, et l'ordre des champs suit
 * celui des arguments du `combine`.
 */
private data class Sources(
    val etat: RecitationState,
    val saisies: RecitationsInputs,
    val chargees: RecitationsDetails,
    val lecture: RecitationPlayback,
    val echec: String?,
)

/**
 * Prépare l'affichage de la liste des récitations, et conduit son écoute.
 *
 * @param repository source de l'état des récitations, et destination des gestes.
 * @param player le lecteur d'une récitation enregistrée, ou `null` si aucun n'a été fourni au
 *   conteneur. Nul, l'écran n'offre **pas** de bouton de lecture : un bouton qui ne joue rien est
 *   le geste mort que ce dépôt s'interdit.
 */
class RecitationsViewModel(
    private val repository: RecitationRepository,
    private val player: RecitationPlayer? = null,
) : ViewModel() {

    /** Les saisies, qui entrent dans le calcul. */
    private val inputs = MutableStateFlow(RecitationsInputs())

    /** Ce qui a été chargé pour la ligne ouverte, étiqueté par elle. */
    private val details = MutableStateFlow(RecitationsDetails())

    /**
     * Le message d'un échec de **préparation** de l'écoute.
     *
     * Il ne double pas `RecitationPlayback.error`, qui dit qu'un fichier n'a pas pu s'ouvrir :
     * celui-ci dit qu'on n'a même pas pu obtenir **l'adresse** à ouvrir — la signature d'un fichier
     * distant demande un aller-retour, et il échoue quand il n'y a pas de réseau ou pas de compte.
     * Sans ce canal, appuyer sur lecture hors ligne ne dirait rien du tout.
     */
    private val echec = MutableStateFlow<String?>(null)

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

    private val _state = MutableStateFlow(RecitationsUiState())

    /** État affichable de l'écran. */
    val state: StateFlow<RecitationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Un seul `collect` sur les cinq sources : des collectes séparées liraient des valeurs
            // de moments différents, et publieraient une liste calculée sur un filtre déjà
            // remplacé — ou des cartes sans la ligne qui les porte.
            combine(
                repository.state,
                inputs,
                details,
                playback,
                echec,
            ) { etat, saisies, chargees, lecture, souci ->
                Sources(etat, saisies, chargees, lecture, souci)
            }.collect { sources ->
                val pourLaLigne = sources.chargees.forOpen(sources.saisies.openId)
                _state.value = RecitationsRenderer.render(
                    state = sources.etat,
                    // La lecture n'est pas une saisie : elle vient du lecteur, et le rendu la reçoit
                    // par les deux champs que `RecitationsInputs` réserve à cela.
                    inputs = sources.saisies.copy(
                        playing = sources.lecture.playing,
                        positionMs = sources.lecture.positionMs,
                    ),
                    corrections = pourLaLigne.corrections,
                    feedback = pourLaLigne.feedback,
                    // **La capacité, et non l'intention** : c'est la présence d'un lecteur qui
                    // décide si l'écran offre l'écoute, et non le fait qu'une ligne soit dépliée.
                    canListen = player != null,
                    // L'échec du lecteur prime sur celui de la préparation : il est plus récent, et
                    // il décrit ce qui vient de se passer sous les yeux de la personne.
                    playbackError = sources.lecture.error ?: sources.echec,
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
     */
    fun onOpen(id: String) {
        val ouverte = inputs.value.openId
        val nouvelle = if (ouverte == id) null else id
        inputs.value = inputs.value.copy(openId = nouvelle)

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
            inputs.value = inputs.value.copy(openId = null)
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
                        echec.value = RecitationText.AUDIO_UNAVAILABLE
                        return@launch
                    }

                else -> return@launch
            }

            echec.value = null
            loadedId = id
            lecteur.play(adresse)
        }
    }

    /** Arrête l'écoute et oublie la piste chargée. */
    private fun arreterLEcoute() {
        loadedId = null
        echec.value = null
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
                    ) as T
                }
            }
    }
}
