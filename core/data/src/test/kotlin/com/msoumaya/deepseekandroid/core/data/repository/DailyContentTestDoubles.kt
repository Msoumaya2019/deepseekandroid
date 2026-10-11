package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.remote.DailyContentSource
import com.msoumaya.deepseekandroid.core.model.ContentCategory
import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import kotlinx.coroutines.CompletableDeferred

// ---------------------------------------------------------------------------
// Doublure des tests du dépôt des contenus du jour
// ---------------------------------------------------------------------------
// **Ce qu'elle mesure, et pourquoi elle le mesure.** Elle retient l'**ordre** de ses appels, et ce
// n'est pas du confort : l'ordre est une des deux choses que `DailyContentRepository` décide
// vraiment — le contenu du jour se lit **avant** les catégories, pour qu'un échec des pastilles de
// filtre ne retire pas de l'écran le contenu qui vient d'être publié. Une doublure qui se
// contenterait de rendre des valeurs ne pourrait pas l'apercevoir.
//
// **Chaque refus est séparé.** Le contenu du jour et les catégories : un échec de l'un n'a pas les
// mêmes conséquences que l'échec de l'autre, et une doublure qui refuserait tout d'un bloc ne les
// distinguerait pas. Les deux lectures d'écran — la page et la fiche — ont chacune la leur, parce
// qu'elles ne passent pas par l'état publié.
//
// **Deux crochets, et une raison chacun.** [probe] photographie l'état publié au moment précis où
// le réseau est appelé — c'est la seule façon de mesurer que le **disque** est publié avant le
// premier appel, donc que l'accueil affiche le contenu du jour sans connexion. [gate] suspend les
// appels, ce qui permet de tenir un rafraîchissement en cours et d'éprouver ce qui ne se mesure
// pas avec une doublure qui répond instantanément.
// ---------------------------------------------------------------------------

/** Doublure en mémoire du service des contenus du jour. */
internal class FakeDailyContentSource : DailyContentSource {

    /** Ce que le serveur rendra pour la date demandée. */
    var dayEntries: List<DailyContent> = emptyList()

    /** Ce que le serveur rendra pour les catégories. */
    var categoryList: List<ContentCategory> = emptyList()

    /** Ce que le serveur rendra pour une page. */
    var page: List<DailyContent> = emptyList()

    /** Ce que le serveur rendra pour une fiche. */
    var single: DailyContent? = null

    var failDay: Throwable? = null
    var failCategories: Throwable? = null
    var failPage: Throwable? = null
    var failContent: Throwable? = null

    /** Les appels, dans leur ordre, sous une forme lisible par un test. */
    val calls = mutableListOf<String>()

    /** Appelé au début de la lecture du jour : permet de photographier l'état publié. */
    var probe: (() -> Unit)? = null

    /** Quand elle est posée, chaque appel attend qu'elle soit franchie. */
    var gate: CompletableDeferred<Unit>? = null

    /** Les dates demandées, dans l'ordre. */
    val daysAsked = mutableListOf<String>()

    /** Les pages demandées : type, catégorie, décalage. */
    val pagesAsked = mutableListOf<Triple<DailyContentType, String?, Int>>()

    /** Les identifiants demandés à la fiche. */
    val idsAsked = mutableListOf<String>()

    override suspend fun dayContents(day: String): List<DailyContent> {
        calls += "contenu du jour"
        daysAsked += day
        probe?.invoke()
        gate?.await()
        failDay?.let { throw it }
        return dayEntries
    }

    override suspend fun categories(): List<ContentCategory> {
        calls += "categories"
        gate?.await()
        failCategories?.let { throw it }
        return categoryList
    }

    override suspend fun contents(
        type: DailyContentType,
        categoryId: String?,
        offset: Int,
    ): List<DailyContent> {
        calls += "page"
        pagesAsked += Triple(type, categoryId, offset)
        gate?.await()
        failPage?.let { throw it }
        return page
    }

    override suspend fun content(id: String): DailyContent? {
        calls += "fiche"
        idsAsked += id
        gate?.await()
        failContent?.let { throw it }
        return single
    }
}
