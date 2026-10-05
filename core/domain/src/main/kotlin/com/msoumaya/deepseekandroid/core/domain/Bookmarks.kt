package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.VerseBookmark

/**
 * Signets.
 *
 * Porté depuis `src/core/bookmarks.ts`. Le point délicat est la **suppression logique** :
 * un signet supprimé n'est pas retiré de la carte, il reçoit un `deletedAt`. Sans ce
 * marqueur, un autre appareil qui possède encore le signet le réintroduirait à la
 * synchronisation suivante.
 */
object Bookmarks {

    fun saveBookmark(
        state: AppState,
        id: Int,
        at: String = Dates.nowIso(),
        sourcePage: Pair<String, Int>? = null,
    ): AppState {
        require(id in 1..Quran.verses.size) { "Verset inexistant." }
        val verse = Quran.verseAt(id)
        val old = state.bookmarks?.get(id.toString())
        val existing = state.bookmarks ?: emptyMap()
        val updated = VerseBookmark(
            verseId = id,
            surah = verse.surah,
            ayah = verse.ayah,
            page = Quran.pageOf(id),
            createdAt = old?.createdAt ?: at,
            updatedAt = at,
            sourcePages = sourcePage?.let { (source, page) ->
                (old?.sourcePages ?: emptyMap()) + (source to page)
            } ?: old?.sourcePages,
        )
        return state.copy(
            updatedAt = at,
            lastRead = com.msoumaya.deepseekandroid.core.model.LastRead(
                page = sourcePage?.second ?: Quran.pageOf(id),
                verseId = id,
                readAt = at,
            ),
            bookmarks = existing + (id.toString() to updated),
        )
    }

    fun deleteBookmark(state: AppState, id: Int, at: String = Dates.nowIso()): AppState {
        val item = state.bookmarks?.get(id.toString()) ?: return state
        return state.copy(
            updatedAt = at,
            bookmarks = (state.bookmarks ?: emptyMap()) +
                (id.toString() to item.copy(deletedAt = at, updatedAt = at)),
        )
    }

    fun useBookmark(
        state: AppState,
        id: Int,
        at: String = Dates.nowIso(),
        pageOverride: Int? = null,
    ): AppState {
        val item = state.bookmarks?.get(id.toString()) ?: return state
        if (item.deletedAt != null) return state
        return state.copy(
            updatedAt = at,
            lastRead = com.msoumaya.deepseekandroid.core.model.LastRead(
                page = pageOverride ?: Quran.pageOf(id),
                verseId = id,
                readAt = at,
            ),
            bookmarks = (state.bookmarks ?: emptyMap()) +
                (id.toString() to item.copy(lastUsedAt = at, updatedAt = at)),
        )
    }

    /** Signets visibles, du plus récemment utilisé au plus ancien. */
    fun visibleBookmarks(state: AppState): List<VerseBookmark> =
        (state.bookmarks ?: emptyMap()).values
            .filter { it.deletedAt == null }
            .sortedByDescending { it.lastUsedAt ?: it.updatedAt }

    /**
     * Versets portant un signet, pour le surlignage de la page.
     *
     * Passe par [visibleBookmarks] et non par la carte brute : un signet supprimé garde son
     * marqueur `deletedAt` pour la synchronisation, et ne doit donc pas colorer la page.
     * Lire la carte directement ferait revivre un signet supprimé sur l'écran où il ne
     * devrait plus apparaître — l'inverse exact de ce que la suppression logique protège.
     */
    fun bookmarkedIds(state: AppState): Set<Int> =
        visibleBookmarks(state).map { it.verseId }.toSet()

    // -----------------------------------------------------------------------
    // L'écran des marques-pages
    // -----------------------------------------------------------------------

    /**
     * Une ligne de l'écran des marques-pages, telle qu'elle s'affiche.
     *
     * Elle porte du texte déjà résolu — nom de sourate, texte arabe — plutôt qu'un identifiant
     * à résoudre : la vue n'a ainsi aucun accès au référentiel, et une ligne qui existe est une
     * ligne qui s'affiche.
     */
    data class Row(
        val verseId: Int,
        val surahName: String,
        val ayah: Int,
        val page: Int,
        val text: String,
        val lastUsed: Boolean,
    )

    /**
     * La page d'un signet **dans la source affichée**.
     *
     * Les sources ne découpent pas le Coran aux mêmes endroits, et c'est mesuré : entre le
     * moushaf de Médine et le paquet « Coran 1441 », **56 versets sur 6 236 changent de page**
     * (par exemple Al Mâ'idah 77, page 121 d'un côté et 120 de l'autre). Utiliser la table de
     * l'une pour l'autre ferait donc ouvrir le signet à la page voisine — un défaut qu'on ne
     * voit qu'en regardant la page, et qu'aucun contrôle de compilation ne signale.
     *
     * La clé de lecture est [MushafSource.persistedKey] — le `@SerialName` de la source, donc
     * exactement la clé que le client d'origine écrit dans `sourcePages`.
     *
     * ## Ce que cette fonction change aujourd'hui, et ce qu'elle ne change pas encore
     *
     * Deux choses distinctes, et il serait malhonnête de les confondre :
     *
     *  * la **source** est observable dès maintenant : [pageFor] avec `CORAN_1441` et avec
     *    `MEDINA` ne rend pas la même page pour les 56 versets ci-dessus ;
     *  * la **page notée** ne l'est pas : dans les bornes livrées, un verset n'occupe qu'une
     *    seule page par découpage, donc `MushafSourceNavigation.versePage` rend toujours la page
     *    du verset, que `current` soit fourni ou non. La branche « conserver la page notée si
     *    elle porte le verset » est donc **inatteignable avec les données actuelles**. Elle est
     *    conservée parce qu'elle est celle du client d'origine, et parce qu'un découpage où un
     *    verset s'étale sur deux pages la rendrait nécessaire ; `BookmarksScreenRulesTest`
     *    mesure l'hypothèse pour qu'on la revoie si les données changent.
     *
     * Le choix de [MushafSource.persistedKey] plutôt que du repli
     * `StudyProgressCalculator.sourceKey` est de la même nature : aujourd'hui les deux rendent
     * le même résultat pour toute source atteignable — la seule source repliée qui utilise le
     * découpage 1441 est `CORAN_1441`, dont la clé n'est justement pas repliée. L'écart
     * apparaîtra le jour où `coranTest` aura son propre découpage, ce que l'écran immersif
     * apportera. On écrit donc la clé **réellement écrite**, pas une clé qui se trouve coïncider.
     *
     * @param id un identifiant **dans le corpus**. Hors corpus, `Quran.pageOf` lève : c'est
     *   [rows] qui écarte ces signets, et elle est la porte d'entrée de l'écran.
     */
    fun pageFor(state: AppState, source: MushafSource, id: Int): Int =
        MushafSourceNavigation.versePage(
            source = source,
            id = id,
            current = state.bookmarks?.get(id.toString())?.sourcePages?.get(source.persistedKey),
        )

    /**
     * Le verset de la reprise la plus récente, ou `null` si aucun signet n'a encore été repris.
     *
     * Le tri est **refait ici** au lieu de lire la tête de [visibleBookmarks] : celle-ci classe
     * aussi les signets jamais repris, par leur date de modification, si bien qu'un signet
     * fraîchement posé passerait devant. « Dernière reprise » ne se dit que d'un signet qu'on a
     * effectivement repris.
     *
     * La comparaison porte sur des dates ISO 8601 en UTC, donc l'ordre lexicographique est
     * l'ordre chronologique — c'est la comparaison du client d'origine, `localeCompare` sur les
     * mêmes chaînes.
     */
    fun lastUsedId(state: AppState): Int? =
        visibleBookmarks(state)
            .mapNotNull { item -> item.lastUsedAt?.let { item.verseId to it } }
            .maxByOrNull { it.second }
            ?.first

    /**
     * Les lignes de l'écran des marques-pages, dans l'ordre d'affichage.
     *
     * ## Un état synchronisé n'est pas une promesse de validité
     *
     * Un signet venu d'un autre appareil — ou d'un état écrit à la main — peut désigner un
     * verset que ce référentiel ne connaît pas. `Quran.verseAt` et `Quran.surahAt` **lèvent**
     * alors, et faire tomber l'écran entier pour une ligne serait le pire des échanges. La
     * ligne hors corpus est donc **omise**, comme la ligne d'une destination non branchée l'est
     * dans `ReaderOptionsText` : on retire, on ne grise pas.
     *
     * ## Le verset est la vérité, les champs enregistrés ne sont qu'un cache
     *
     * Le nom de sourate et le numéro de verset sont lus sur le **verset**, et non sur les champs
     * `surah` / `ayah` du signet — que le client d'origine affiche, lui. `saveBookmark` les
     * écrit depuis ce même verset : les deux coïncident donc pour tout état que l'application a
     * produit. Ils ne peuvent diverger que pour un état venu d'ailleurs, et dans ce cas c'est le
     * référentiel qui a raison : une étiquette fausse à côté du bon texte serait le pire des
     * deux.
     */
    fun rows(state: AppState, source: MushafSource): List<Row> {
        val lastUsed = lastUsedId(state)
        return visibleBookmarks(state).mapNotNull { item ->
            val id = item.verseId
            if (id !in 1..Quran.verses.size) return@mapNotNull null
            val verse = Quran.verseAt(id)
            Row(
                verseId = id,
                surahName = Quran.surahAt(id).name,
                ayah = verse.ayah,
                page = pageFor(state, source, id),
                text = verse.text,
                lastUsed = id == lastUsed,
            )
        }
    }

    /** Fusion par date de modification, marqueurs de suppression inclus. */
    fun mergeBookmarks(
        a: Map<String, VerseBookmark>?,
        b: Map<String, VerseBookmark>?,
    ): Map<String, VerseBookmark>? {
        if (a == null) return b
        if (b == null) return a
        val merged = a.toMutableMap()
        for ((id, item) in b) {
            val current = merged[id]
            if (current == null || item.updatedAt > current.updatedAt) merged[id] = item
        }
        return merged
    }
}
