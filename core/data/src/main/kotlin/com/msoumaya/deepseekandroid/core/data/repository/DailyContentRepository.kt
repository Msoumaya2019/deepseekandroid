package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.DailyContentCacheStore
import com.msoumaya.deepseekandroid.core.data.remote.DailyContentSource
import com.msoumaya.deepseekandroid.core.data.remote.restMessage
import com.msoumaya.deepseekandroid.core.domain.DailyContents
import com.msoumaya.deepseekandroid.core.domain.DailyContentsText
import com.msoumaya.deepseekandroid.core.model.ContentCategory
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import com.msoumaya.deepseekandroid.core.model.DailyContentsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

// ---------------------------------------------------------------------------
// Dépôt « contenus du jour »
// ---------------------------------------------------------------------------
// Portage de la moitié lecture de `src/services/dailyContents.ts` : ce qui est lu, dans quel ordre,
// et ce qu'un échec fait à ce qui est déjà à l'écran.
//
// **Ce fichier ne décide d'aucune règle de contenu.** La taille d'une page, la validité d'un
// document mis en cache et le partage entre « rien à montrer » et « rien n'a pu être lu » sont dans
// `core:domain/DailyContents.kt` ; ici il n'y a que l'ordonnancement et le sort des erreurs.
//
// ## La chronologie du rafraîchissement, et pourquoi elle est dans cet ordre
//
//   1. **lire le document du disque et le publier** — l'accueil doit pouvoir s'afficher sans
//      réseau, et c'est aussi ce qui rend le repli hors ligne honnête : ce qui s'affiche a été lu
//      un jour, et le document porte **sa** date ;
//   2. **lire le contenu du jour au serveur**, puis l'écrire sur le disque ;
//   3. **lire les catégories** — elles ne servent qu'aux pastilles de filtre de l'écran de liste,
//      donc leur échec ne doit pas retirer le contenu du jour, qui est déjà publié à ce moment-là.
//
// ## Le document périmé n'est pas publié, et c'est une décision
//
// Le disque peut porter le contenu d'hier : le serveur choisit une entrée **par type et par
// date**, et rien n'empêche l'appareil d'être resté fermé depuis la veille. [DailyContents.isFor]
// tranche, et un document qui n'est pas du jour est remplacé par un document **vide et du jour** —
// jamais publié tel quel. Sans cette substitution, l'accueil afficherait le rappel de la veille
// sous le titre « Rappel du jour » : une affirmation fausse, sur un contenu religieux, et qui ne
// lève rien.
//
// ## Les deux états d'échec, et ce qu'ils évitent
//
// Comme pour l'espace social, le Quiz et les récitations : un échec qui laisse l'écran **vide**
// publie [DailyContentsState.failure], et un échec qui laisse quelque chose à lire publie
// [DailyContentsState.notice]. La raison est propre aux contenus du jour : un document vide
// s'affiche « Aucun contenu disponible aujourd'hui », ce qui est faux quand la lecture a échoué —
// et faux sur un sujet où personne ne vérifiera.
// ---------------------------------------------------------------------------

/**
 * Ce que les écrans « contenus du jour » ont besoin de savoir, et rien de plus.
 *
 * [snapshot] est `null` tant qu'aucune lecture locale n'a eu lieu, et ne l'est plus ensuite : le
 * dépôt est **hors ligne d'abord**, donc le document du disque est publié avant tout appel réseau,
 * et l'écran n'a jamais à distinguer « pas encore lu » de « lu, et vide ».
 *
 * **Il n'y a pas d'`ownerId`, et ce n'est pas un oubli.** Les contenus du jour sont **publics** :
 * les politiques de `supabase/daily-contents.sql` ouvrent la lecture à `anon`, et le cache n'est
 * donc pas par compte. **Les favoris ne l'ont pas fait entrer non plus**, et cela se mesure : le
 * compte est résolu par `DailyContentFavoritesRepository`, et [favoritePage] ne reçoit que des
 * **identifiants** — ce dépôt ne sait toujours pas pour qui il lit, et c'est précisément ce qui lui
 * permet de servir les favoris d'un **visiteur** comme ceux d'un compte, sans branche.
 */
data class DailyContentsState(

    /** Vrai tant que le document du disque n'a pas été publié. Le défaut est donc « en attente ». */
    val loading: Boolean = true,

    /** Dernier message d'information, ou `null`. Voir le KDoc du type. */
    val notice: String? = null,

    /** Panne quand il n'y a **rien à montrer** à la place. Voir [DailyContents.isEmpty]. */
    val failure: String? = null,

    /** Le document affichable, ou `null` tant que le disque n'a pas été lu. */
    val snapshot: DailyContentsSnapshot? = null,

    /** Les catégories actives, ou la liste vide tant qu'aucune lecture n'a abouti. */
    val categories: List<ContentCategory> = emptyList(),
)

/**
 * Charge et publie les contenus du jour, en gardant l'écran honnête sur ce qu'il sait.
 *
 * @param source accès aux données, ou `null` si aucun projet Supabase n'est configuré. Le `null`
 *   n'est pas une panne : c'est le mode hors ligne, et il doit se lire comme « rien à demander »,
 *   jamais comme une erreur réseau.
 * @param cache le document persisté. **Un seul fichier**, non par compte — voir
 *   [DailyContentCacheStore].
 * @param scope portée des travaux qui vivent aussi longtemps que l'application.
 * @param today jour courant, injectable pour que les tests ne dépendent pas de l'horloge.
 */
class DailyContentRepository(
    private val source: DailyContentSource?,
    private val cache: DailyContentCacheStore,
    private val scope: CoroutineScope,
    private val today: () -> String = { DailyContents.today() },
) {

    /**
     * Sérialise les rafraîchissements.
     *
     * Sans lui, deux rafraîchissements concurrents — le démarrage et le retour au premier plan —
     * écriraient le disque en même temps, et le dernier écrit ne serait pas forcément celui dont
     * les entrées ont été lues. Le même verrou que le Quiz, pour la même raison.
     */
    private val lock = Mutex()

    /**
     * Nombre de rafraîchissements **en vol**.
     *
     * Ce compteur existe à cause d'un échec d'intégration continue qu'il a fallu expliquer, et non
     * contourner — le même que `QuizRepository.enTravail`, pour la même raison. **L'état publié ne
     * dit pas qu'un travail est en cours** : le rafraîchissement publie le document du **disque**
     * avant d'appeler le serveur, et c'est délibéré — un appareil sans connexion doit afficher le
     * contenu du jour quand même. Mais entre cette publication et celle du serveur, rien ne change
     * plus dans l'état, donc « l'état ne bouge plus » se produit aussi **au milieu** du travail. Une
     * attente fondée là-dessus rend la main avant la lecture réseau, et le test qui suit lit le
     * document local seul.
     *
     * Le compte est incrémenté **avant la première suspension** et décrémenté après la dernière,
     * dans la coroutine appelante : il ne peut donc pas valoir zéro pendant qu'un travail vit
     * encore. Il enveloppe aussi l'attente du verrou.
     */
    private val enVol = AtomicInteger()

    /**
     * Vrai si un rafraîchissement est en cours.
     *
     * Publique parce que l'attente d'un test ne peut pas se fonder sur l'état publié. Aucun écran
     * ne s'en sert : l'écran affiche `loading`, qui est une autre question — « le document est-il
     * arrivé ? » —, et non « un travail tourne-t-il ? ».
     */
    val enTravail: Boolean get() = enVol.get() > 0

    /** Compte une opération, de sa première à sa dernière suspension. */
    private suspend fun <T> compte(bloc: suspend () -> T): T {
        enVol.incrementAndGet()
        try {
            return bloc()
        } finally {
            enVol.decrementAndGet()
        }
    }

    private val _state = MutableStateFlow(DailyContentsState())

    /** État affichable. */
    val state: StateFlow<DailyContentsState> = _state.asStateFlow()

    init {
        // Le dépôt s'amorce lui-même, et il n'observe aucun compte : contrairement au Quiz, rien
        // de ce qu'il lit ne dépend de qui est connecté. C'est la même forme que `prime()` des
        // réglages d'écoute, et la même raison — l'accueil doit trouver un document prêt, et non
        // attendre qu'un écran le demande.
        scope.launch { refresh() }
    }

    /**
     * Relit le contenu du jour : disque d'abord, serveur ensuite.
     *
     * @return vrai si la lecture réseau a abouti.
     */
    suspend fun refresh(): Boolean = compte { refreshVerrouille() }

    /**
     * Le rafraîchissement lui-même, sérialisé par [lock].
     *
     * @return vrai si la lecture réseau a abouti.
     */
    private suspend fun refreshVerrouille(): Boolean = lock.withLock {
        val api = source
        val day = today()

        // 1. Hors ligne d'abord. Un document qui n'est pas du jour n'est **pas** publié : il est
        //    remplacé par un document vide et du jour, sans quoi l'accueil annoncerait le rappel
        //    d'hier comme « Rappel du jour ».
        val cached = cache.current()
        val snapshot = if (DailyContents.isFor(cached, day)) cached else DailyContents.emptySnapshot(day)
        _state.value = _state.value.copy(loading = false, snapshot = snapshot, failure = null)

        if (api == null) return@withLock false

        // 2. Le contenu du jour. La date envoyée est celle capturée plus haut, et le document
        //    écrit porte **la même** : si minuit passe pendant l'aller-retour, les entrées reçues
        //    sont celles de `day`, et les écrire sous une autre date serait affirmer qu'elles sont
        //    celles du jour. Le document est alors refusé par `isFor` au prochain affichage, ce
        //    qui est exactement son rôle.
        try {
            val entries = api.dayContents(day)
            _state.value = _state.value.copy(
                loading = false,
                failure = null,
                snapshot = cache.update { DailyContentsSnapshot(day = day, entries = entries) },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            fail(error)
            return@withLock false
        }

        // 3. Les catégories, **après** le contenu et sous leur propre garde. Elles ne servent
        //    qu'aux pastilles de filtre de l'écran de liste : leur échec ne doit pas retirer de
        //    l'écran le contenu du jour, qui vient d'être publié. Les précédentes restent en
        //    place, et l'avis dit qu'elles n'ont pas été relues.
        try {
            _state.value = _state.value.copy(categories = api.categories())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = _state.value.copy(notice = describe(error))
        }

        true
    }

    /**
     * Une page de contenus, telle que le serveur la rend.
     *
     * **Cette lecture ne touche pas l'état publié, et c'est délibéré.** Une page est propre à un
     * filtre — un type, une catégorie — et à un décalage ; la publier écraserait le contenu du
     * jour pour tout le monde alors qu'un seul écran l'a demandée. L'appelant garde donc ses
     * pages, et cette méthode n'est qu'une porte vers la source.
     *
     * Elle **lève** quand la lecture échoue, comme l'original : c'est l'écran de liste qui porte
     * son propre message d'erreur, parce que lui seul sait ce qu'il avait à l'écran.
     *
     * Sans projet configuré, elle rend une liste vide sans rien demander : c'est le mode hors
     * ligne, et l'écran de liste affiche alors son état vide.
     */
    suspend fun page(
        type: DailyContentType,
        categoryId: String? = null,
        offset: Int = 0,
    ): List<DailyContent> = source?.contents(type, categoryId, offset).orEmpty()

    /**
     * Une page de favoris, telle que le serveur la rend.
     *
     * **Une liste d'identifiants vide rend une liste vide, et ne demande rien.** La règle est
     * celle de l'original, et elle compte plus qu'elle n'en a l'air : `favoriteContents` rend `[]`
     * **avant** d'appeler `db()`, donc l'onglet « Mes favoris » d'un appareil sans projet
     * configuré — ou d'une personne qui n'a rien mis en favori — affiche son état vide au lieu
     * d'une erreur. Sans cette garde, la requête partirait avec un `in.()` vide que PostgREST
     * refuse, et l'écran annoncerait une panne là où il n'y a rien à montrer. C'est aussi la
     * raison pour laquelle elle ne peut pas vivre dans la source : c'est ici qu'elle se mesure,
     * contre une source factice qui compte ses appels.
     *
     * Elle **lève** dans les mêmes conditions que [page], et pour la même raison : l'écran de
     * liste porte son propre message d'erreur, parce que lui seul sait ce qu'il avait à l'écran.
     *
     * @param ids les identifiants retenus, **déjà** résolus par
     *   [DailyContentFavoritesRepository] — ce dépôt ne connaît ni le compte ni sa liste.
     */
    suspend fun favoritePage(
        ids: Set<String>,
        type: DailyContentType,
        categoryId: String? = null,
        offset: Int = 0,
    ): List<DailyContent> {
        if (ids.isEmpty()) return emptyList()
        return source?.favoriteContents(ids.toList(), type, categoryId, offset).orEmpty()
    }

    /**
     * Un contenu par son identifiant, ou `null` s'il n'existe pas — ou si aucun projet n'est
     * configuré.
     *
     * Elle lève dans les mêmes conditions que [page], et pour la même raison : la fiche ouverte
     * sur un contenu précis est un écran, et c'est lui qui décide de ce qu'il montre quand la
     * lecture échoue.
     */
    suspend fun content(id: String): DailyContent? = source?.content(id)

    /**
     * Publie l'échec d'une lecture du contenu du jour, en choisissant entre panne et avis.
     *
     * Le partage se fait sur ce qui **reste à lire**, et non sur la gravité de l'erreur : quand le
     * document est vide, il n'y a rien à l'écran et l'échec est donc une panne ; quand il porte
     * quelque chose, ce quelque chose n'est pas devenu faux, et l'échec n'est qu'un avis.
     */
    private fun fail(error: Throwable) {
        val text = describe(error)
        val empty = _state.value.snapshot?.let { DailyContents.isEmpty(it) } ?: true
        _state.value = _state.value.copy(
            loading = false,
            failure = if (empty) text else null,
            notice = if (empty) null else text,
        )
    }

    /**
     * Le texte lisible d'une erreur de lecture, pour l'écran qui l'a reçue.
     *
     * **Publique parce que [page], [favoritePage] et [content] lèvent.** Elles le font
     * délibérément — l'écran de liste porte son propre message, parce que lui seul sait ce qu'il
     * avait à l'écran —, mais elles laissent alors l'appelant convertir une erreur du serveur en
     * texte affichable. Sans cette porte, chaque écran réinventerait la conversion, et deux écrans
     * pourraient montrer deux textes différents pour la **même** panne — l'un le message du
     * serveur, l'autre un `toString()` d'exception qui accole un bloc de débogage et l'adresse du
     * projet.
     *
     * Elle ne dit rien de plus que ce que le dépôt publierait lui-même dans `failure` ou `notice` :
     * c'est la même fonction qui répond, et c'est ce qui garantit l'accord.
     */
    fun readable(error: Throwable): String = describe(error)

    /**
     * Message lisible d'une erreur.
     *
     * Le texte vient de [restMessage], qui lit le champ que le serveur a écrit — et non `message`,
     * qui y accole un bloc de débogage et l'adresse du projet. Le repli est
     * [DailyContentsText.GENERIC_FAILURE] : `restMessage()` peut rendre `null` — une exception
     * dont le serveur n'a rien rempli —, et un message nul serait un **échec muet**.
     */
    private fun describe(error: Throwable): String =
        error.restMessage()?.takeIf { it.isNotBlank() } ?: DailyContentsText.GENERIC_FAILURE
}
