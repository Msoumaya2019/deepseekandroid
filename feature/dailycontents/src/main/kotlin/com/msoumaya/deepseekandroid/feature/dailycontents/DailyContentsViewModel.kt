package com.msoumaya.deepseekandroid.feature.dailycontents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msoumaya.deepseekandroid.core.data.AppContainer
import com.msoumaya.deepseekandroid.core.data.repository.DailyContentFavoritesRepository
import com.msoumaya.deepseekandroid.core.data.repository.DailyContentMediaRepository
import com.msoumaya.deepseekandroid.core.data.repository.DailyContentRepository
import com.msoumaya.deepseekandroid.core.domain.DailyContents
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// L'écran de liste des contenus du jour
// ---------------------------------------------------------------------------
// Portage du corps de `DailyContentsScreen` (`src/DailyContentsScreen.tsx:53`). Ce fichier ne fait
// que **câbler** : il relie l'état des dépôts aux saisies de la personne, puis délègue la mise en
// forme à `DailyContentsListRenderer`, qui est pur et donc éprouvé sans coroutine.
//
// **Les saisies sont tenues ici, et non dans l'écran.** L'onglet, la catégorie, la bascule des
// favoris et la fiche ouverte sont des **entrées** du rendu : la liste affichée en dépend. Les
// garder dans les composables ferait deux sources pour une même décision — la pastille allumée à
// l'écran, et la page demandée ailleurs —, et un changement d'onglet publierait un état où les
// deux ne seraient pas d'accord.
//
// **Les catégories ne sont pas relues ici.** L'original appelle `service.categories()` à
// l'ouverture de l'écran ; le portage lit celles que le dépôt a **déjà** chargées, et qu'il
// rafraîchit au démarrage. Une seule lecture pour une seule vérité : deux requêtes pour la même
// rangée de pastilles finiraient par ne pas s'accorder, et rien à l'écran ne le dirait.
//
// **Le message d'erreur a une seule source, comme dans l'original.** Celui-ci écrit son
// `setError` depuis quatre endroits — les catégories, la page, les favoris, la fiche — et n'a donc
// qu'un canal. Le portage garde le sien pour ses propres lectures, et laisse passer celui du
// dépôt quand il n'a rien à dire : c'est la seule façon qu'un échec de lecture des catégories —
// le seul que l'écran ne peut pas observer directement — reste visible au lieu de se lire comme
// une rangée de pastilles vide.
//
// **Ce qui n'est pas ici.** L'observation du premier plan et le changement de date : l'original ne
// les a **pas** dans cet écran, il les a dans le carrousel de l'accueil. Les ajouter ici ferait
// deux endroits qui relisent le contenu du jour, et deux horloges qui peuvent diverger.
// ---------------------------------------------------------------------------

/**
 * Prépare l'affichage de la liste, et exécute ses gestes.
 *
 * @param contents le dépôt des contenus du jour : la lecture d'une page, celle d'une fiche, et
 *   l'état publié dont viennent les catégories.
 * @param favorites les favoris **par compte**, qui décident de la pastille et de l'onglet
 *   « Mes favoris ». Le dépôt des contenus ne les connaît pas : il ne reçoit que des identifiants.
 * @param media la résolution des adresses signées, que la fiche appelle pour son illustration.
 *   Exposée parce que la fiche est un composable, et qu'un composable ne lit pas un dépôt sans le
 *   recevoir.
 * @param initialId le contenu **à ouvrir** en arrivant, ou `null`. C'est l'`initialId` de
 *   l'original : le carrousel de l'accueil le porte, et c'est ce qui décide aussi de ce que fait
 *   le bouton « Retour » de la fiche — voir `DailyContentsListRenderer.detailBackExits`.
 */
class DailyContentsViewModel(
    private val contents: DailyContentRepository,
    private val favorites: DailyContentFavoritesRepository,
    val media: DailyContentMediaRepository,
    private val initialId: String? = null,
) : ViewModel() {

    /**
     * Ce que l'écran décide lui-même.
     *
     * Les catégories et le message du dépôt n'y sont **pas** : ils appartiennent à l'état du
     * dépôt, et les recopier ici créerait deux copies qui pourraient ne pas s'accorder. Ils sont
     * joints à la publication, plus bas.
     */
    private val local = MutableStateFlow(DailyListState())

    /**
     * L'état affichable : les saisies, et ce que le dépôt publie à côté.
     *
     * `Eagerly` parce que l'écran observe cet état dès sa première composition, et qu'un
     * `WhileSubscribed` laisserait la première image sans catégories — donc une rangée de pastilles
     * qui apparaîtrait après coup, sous le doigt.
     */
    val state: StateFlow<DailyListState> = combine(local, contents.state) { mine, remote ->
        mine.copy(
            categories = remote.categories,
            error = mine.error ?: remote.notice ?: remote.failure,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DailyListState())

    /**
     * Numéro de la lecture courante.
     *
     * Chaque changement de filtre l'incrémente, et une page qui revient après coup est **ignorée**
     * si le numéro a bougé : sans lui, une réponse lente à une requête sur « invocation »
     * s'afficherait sous l'onglet « rappel », et rien à l'écran ne dirait que la liste ne
     * correspond plus à ce qui est allumé. C'est le `generation.current` de l'original.
     */
    private var generation = 0

    /**
     * Vrai tant qu'une page est en vol.
     *
     * Une seconde demande pendant ce temps est **ignorée**, comme le `loading.current` de
     * l'original : deux pages du même filtre s'empileraient, et la liste porterait deux fois les
     * mêmes contenus. Il est remis à `false` par un changement de filtre, qui relance une lecture.
     */
    private var loading = false

    init {
        viewModelScope.launch { refreshFavorites() }
        viewModelScope.launch { load(offset = 0) }
        if (initialId != null) {
            viewModelScope.launch { openInitial(initialId) }
        }
    }

    /** Choisit l'onglet de type. **La catégorie est retirée**, comme l'original. */
    fun selectType(type: DailyContentType) {
        if (type == local.value.type) return
        local.value = local.value.copy(type = type, selected = null)
        reload()
    }

    /** Pose ou retire le filtre de catégorie. `null` demande « Tout ». */
    fun selectCategory(categoryId: String?) {
        if (categoryId == local.value.selected) return
        local.value = local.value.copy(selected = categoryId)
        reload()
    }

    /** Bascule l'onglet des favoris. */
    fun toggleFavorites() {
        local.value = local.value.copy(favorites = !local.value.favorites)
        reload()
    }

    /** Demande la page suivante. Le décalage est le nombre de lignes déjà affichées. */
    fun loadMore() {
        val offset = local.value.items.size
        if (offset == 0) return
        viewModelScope.launch { load(offset) }
    }

    /**
     * Ouvre la fiche d'un contenu **déjà chargé** — le cas de la liste.
     *
     * L'identifiant, et non le contenu : c'est le seul jeton que le rendu publie, et c'est ici que
     * la ligne se retrouve. Un identifiant absent ne fait **rien** — la liste a pu être vidée
     * entre l'appui et la lecture, et ouvrir une fiche sans contenu vaudrait moins que ne rien
     * ouvrir.
     */
    fun openDetail(id: String) {
        val content = local.value.items.firstOrNull { it.id == id } ?: return
        local.value = local.value.copy(detail = content)
    }

    /** Referme la fiche, sans quitter l'écran. */
    fun closeDetail() {
        local.value = local.value.copy(detail = null)
    }

    /**
     * Pose ou retire un favori.
     *
     * **L'identifiant est écrit dans la liste locale avant l'appel**, parce que c'est ce que fait
     * l'original — sa carte bascule son état avant que le serveur n'ait répondu —, et parce que
     * l'onglet « Mes favoris » lit cette liste. Un échec ne laisse pas la pastille mentir : le
     * message est publié, et la liste est relue.
     */
    fun setFavorite(id: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                favorites.setFavorite(id, enabled)
                local.value = local.value.copy(favoriteIds = favorites.ids())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                local.value = local.value.copy(error = contents.readable(error))
            }
        }
    }

    /** Relit la liste des identifiants favoris, et publie l'échec s'il y en a un. */
    private suspend fun refreshFavorites() {
        try {
            local.value = local.value.copy(favoriteIds = favorites.ids())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            local.value = local.value.copy(error = contents.readable(error))
        }
    }

    /** Ouvre la fiche désignée par la route, quand le contenu n'est pas dans la page chargée. */
    private suspend fun openInitial(id: String) {
        try {
            contents.content(id)?.let { local.value = local.value.copy(detail = it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            local.value = local.value.copy(error = contents.readable(error))
        }
    }

    /** Change de filtre : la liste est **vidée**, et une lecture repart de zéro. */
    private fun reload() {
        generation++
        loading = false
        local.value = local.value.copy(items = emptyList())
        viewModelScope.launch { load(offset = 0) }
    }

    /**
     * Lit une page et la publie, si le filtre n'a pas changé entre-temps.
     *
     * La lecture est celle de l'onglet : `favoritePage` quand la bascule des favoris est allumée —
     * les identifiants viennent du dépôt des favoris, et le dépôt des contenus ne reçoit que
     * ceux-là —, `page` sinon. Le drapeau `more` vient de `DailyContents.pageComplete`, et non d'un
     * `== 30` écrit ici : le nombre de la fenêtre est celui de la requête, et il vit dans le
     * domaine.
     */
    private suspend fun load(offset: Int) {
        if (loading) return
        loading = true
        local.value = local.value.copy(busy = true)

        val mine = generation
        val snapshot = local.value
        try {
            val rows = if (snapshot.favorites) {
                contents.favoritePage(snapshot.favoriteIds, snapshot.type, snapshot.selected, offset)
            } else {
                contents.page(snapshot.type, snapshot.selected, offset)
            }
            if (mine == generation) {
                local.value = local.value.copy(
                    items = if (offset > 0) local.value.items + rows else rows,
                    more = DailyContents.pageComplete(rows),
                    error = null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (mine == generation) {
                local.value = local.value.copy(error = contents.readable(error))
            }
        } finally {
            if (mine == generation) {
                loading = false
                local.value = local.value.copy(busy = false)
            }
        }
    }

    companion object {
        /**
         * Fabrique rattachée au conteneur, pour que l'écran n'ait pas à le connaître.
         *
         * [initialId] est passé **à la construction**, comme `initialSharingId` des récitations, et
         * pour la même raison : la navigation met le `ViewModel` en cache, donc une valeur posée
         * après coup ne serait jamais relue.
         */
        fun factory(
            container: AppContainer,
            initialId: String? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return DailyContentsViewModel(
                        contents = container.dailyContents,
                        favorites = container.dailyContentFavorites,
                        media = container.dailyContentMedia,
                        initialId = initialId,
                    ) as T
                }
            }
    }
}
