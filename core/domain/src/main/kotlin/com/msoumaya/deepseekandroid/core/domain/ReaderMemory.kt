package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState
import com.msoumaya.deepseekandroid.core.model.LastRead
import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.effectiveReadPages

/**
 * La mémoire du lecteur : où l'on s'est arrêté, et ce que l'on a parcouru.
 *
 * Porté depuis la fermeture du lecteur du client d'origine (`closeReader`, `src/App.tsx`), qui
 * écrivait trois choses d'un seul geste :
 *
 *  - `readPages`, réuni avec la page affichée ;
 *  - `lastRead`, avec la page, un verset **choisi**, et l'instant ;
 *  - `reader.testPage`, mais **seulement** pour la source composée.
 *
 * ## Pourquoi cela mérite une règle, et non trois lignes dans la route
 *
 * Parce que le choix du verset est le seul endroit du portage où l'on décide **quel verset
 * représente une page**, et que cette décision se prend en trois temps, dans un ordre qui
 * compte :
 *
 *  1. le verset déjà mémorisé, **s'il tombe encore sur cette page** — c'est le cas du simple
 *     feuilletage : on ne veut pas que la position se décale d'un verset à chaque page tournée ;
 *  2. le verset sur lequel la lecture avait été **ouverte**, à la même condition — c'est le cas
 *     d'une séance : on ouvre sur le verset 746, on tourne jusqu'à la page 120, et l'on veut
 *     retrouver 746, pas le premier verset de la page 120 ;
 *  3. à défaut, le premier verset de la page — le repli honnête, puisqu'aucun des deux candidats
 *     n'est visible.
 *
 * ## La source, et pourquoi elle est lue **après** migration
 *
 * La source retenue est celle de l'état **migré**. La lire avant laisserait passer un état
 * portant encore `tajweedPages` : la règle ne reconnaîtrait pas la source composée, n'écrirait
 * pas `testPage`, et la migration changerait ensuite la source en `coranTest` — donc une page
 * mémorisée pour la source qui s'affiche, jamais écrite. L'écart ne se verrait qu'à la deuxième
 * ouverture du lecteur, sous la forme d'un retour à la page 1.
 *
 * ## Ce qui n'est écrit que pour la source composée, et pourquoi
 *
 * `testPage` est la page de la **composition typographique**, et d'elle seule : c'est le seul
 * découpage dont ce client garde la position page à page. Les sources en images n'ont pas de
 * champ équivalent, et en inventer un second (une page par source, dans une carte) serait un
 * ajout de format que le client d'origine ne connaît pas — donc un champ que lui ne relirait
 * pas. `lastRead.page`, lui, est écrit pour toutes les sources : c'est la page **de la source
 * affichée**, et c'est ce que la carte « Continuer » de l'accueil annonce.
 *
 * La réécriture de `mushaf` et de `followAudio` n'est pas une redondance : c'est le geste par
 * lequel le client d'origine **adopte** la source composée en refermant le lecteur, et il la
 * rejoue à chaque fermeture. La retirer ici ferait diverger l'état écrit de celui du client
 * d'origine pour la même suite d'actions.
 */
object ReaderMemory {

    /** La première page de tout moushaf. */
    const val FIRST_PAGE = 1

    /**
     * La page à ouvrir pour une source donnée.
     *
     * La source composée a **sa** page mémorisée, et c'est elle qui gagne : c'est le seul
     * découpage dont la position soit conservée à part. Les autres sources n'ont pas de mémoire
     * propre ; la position est alors **projetée** depuis le dernier verset lu, et non reprise
     * telle quelle — `lastRead.page` a été écrite dans le découpage de la source qui était
     * affichée, et la relire pour une autre source ouvrirait à côté. La projection est le même
     * calcul que celui qui a servi à choisir la page, donc les deux ne peuvent pas diverger.
     *
     * La page rendue n'est pas bornée : c'est le lecteur qui borne, et lui seul connaît le
     * nombre de pages de ce qu'il affiche.
     */
    fun openingPage(state: AppState, source: MushafSource): Int = when {
        source == MushafSource.CORAN_TEST -> state.reader?.testPage ?: FIRST_PAGE
        else -> state.lastRead
            ?.let { runCatching { MushafSourceNavigation.versePage(source, it.verseId) }.getOrNull() }
            ?: FIRST_PAGE
    }

    /**
     * L'état après fermeture du lecteur, la page affichée étant [page].
     *
     * @param page la page affichée au moment de fermer. Elle vient du lecteur, qui la borne au
     *   moushaf qu'il peint — d'où le `require`, qui dénonce un appel fautif plutôt que
     *   d'écrire une page hors table.
     * @param start le premier verset de ce sur quoi la lecture avait été **ouverte**, ou `null`
     *   quand elle l'a été librement. Le client d'origine le tenait de sa demande d'ouverture
     *   (`reader.range.start`) ; ici, l'appelant le tient de la route, qui l'a reçu en argument.
     * @param at l'instant à écrire, pour `readAt` **et** `updatedAt`. Le client d'origine
     *   appelle `new Date()` deux fois et écrit donc deux instants séparés de quelques
     *   microsecondes ; un seul suffit, et il rend la règle éprouvable sans horloge.
     */
    fun close(
        state: AppState,
        page: Int,
        start: Int? = null,
        at: String = Dates.nowIso(),
    ): AppState {
        require(page >= FIRST_PAGE) { "Page invalide : $page" }
        val base = Program.migrateReaderState(state)
        val source = base.reader?.mushaf ?: MushafSource.CORAN_TEST
        val range = MushafSourceNavigation.pageRange(source, page)
        val verseId = versetRetenu(source, base.lastRead?.verseId, page)
            ?: versetRetenu(source, start, page)
            ?: range.start
        // L'adoption de la source composée est appliquée **avant** le reste, et non au milieu du
        // `copy` final : la règle vit dans `Program`, partagée avec la carte « Coran avec règles
        // de Tajwid » de l'écran Coran. Fermer le lecteur est le seul appelant qui lui passe une
        // page — c'est le geste qui mémorise où l'on s'est arrêté —, et l'adoption n'a lieu que
        // si la source affichée **est** la source composée.
        val retenu = if (source == MushafSource.CORAN_TEST) {
            Program.adoptComposedSource(base, page)
        } else {
            base
        }
        return retenu.copy(
            updatedAt = at,
            // La page est **ajoutée** à la suite, comme le `Array.from(new Set([...]))` du
            // client d'origine : l'ordre d'insertion est celui des lectures, et un tri ici
            // écrirait un état différent du sien pour la même suite d'actions. La réunion qui
            // sert à la synchronisation trie de son côté, et c'est elle qui fait foi.
            readPages = (retenu.effectiveReadPages + page).distinct(),
            lastRead = LastRead(page = page, verseId = verseId, readAt = at),
        )
    }

    /**
     * Le verset [id], s'il tombe encore sur [page] dans le découpage de [source].
     *
     * Le calcul est **enveloppé** : `MushafSourceNavigation.versePage` refuse un verset hors
     * corpus, et un état synchronisé depuis un autre client peut en porter un. Fermer le lecteur
     * ne doit pas pouvoir échouer — un verset illisible n'est pas un candidat, il est écarté, et
     * le repli suivant prend la main.
     */
    private fun versetRetenu(source: MushafSource, id: Int?, page: Int): Int? = id?.takeIf {
        runCatching { MushafSourceNavigation.versePage(source, it, page) }.getOrNull() == page
    }
}
