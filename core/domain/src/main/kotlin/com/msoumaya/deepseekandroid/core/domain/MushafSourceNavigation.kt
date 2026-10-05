package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource
import com.msoumaya.deepseekandroid.core.model.Range

/**
 * Navigation dans le moushaf, **selon la source affichée**.
 *
 * Porté depuis `src/core/sourceNavigation.ts`. Ce n'est pas une commodité : les sources ne
 * découpent pas le Coran aux mêmes endroits. La page 300 du moushaf de Médine et la page 300
 * du paquet « Coran 1441 » ne portent pas les mêmes versets. Utiliser la table de l'une pour
 * l'autre donnerait une écoute qui commence au mauvais verset — un défaut qu'on n'entend
 * qu'en écoutant, et qu'aucun contrôle de compilation ne signale.
 *
 * ## Trois découpages, et l'ordre qui les sépare
 *
 * L'ordre des cas est celui de `sourceNavigation.ts`, et il compte : `coranTest` est regardée
 * **avant** la question du paquet, parce qu'elle n'en est pas un et n'a pas de table de lignes.
 * Un `else` qui les confondrait ferait lire la composition typographique avec les pages du
 * moushaf de Médine — un défaut qui ne se voit qu'à l'écoute, ou à l'endroit où le lecteur
 * s'ouvre.
 *
 * ## Ce qui a changé ici, et pourquoi c'était nécessaire
 *
 * `coranTest` se lisait jusqu'ici avec le découpage du moushaf de Médine, faute d'écran
 * immersif. C'est désormais `TestPageIndex` qui répond pour elle : **56 des 6 236 versets
 * changent de page** entre les deux découpages (mesuré, et éprouvé là-bas), donc un signet, une
 * reprise ou un suivi d'écoute calculés avec l'une des deux tables ouvriraient à côté sur
 * l'autre. Les deux moitiés — le découpage et l'écran qui le peint — doivent donc arriver
 * ensemble.
 */
object MushafSourceNavigation {

    /**
     * La source est-elle **composée** — peinte par un écran à elle, et non par le lecteur
     * d'images ?
     *
     * Une seule source l'est aujourd'hui. La question est posée à part plutôt que comparée sur
     * place, parce qu'elle ne se confond **pas** avec `QuranSourceReady.FontRendered` : celle-ci
     * dit « il n'y a aucun fichier à préparer », et la lecture simplifiée est rendue par police
     * elle aussi — mais elle passe par le lecteur d'images et sa surimpression, pas par l'écran
     * immersif. Deux questions voisines, deux réponses différentes : les confondre enverrait la
     * lecture simplifiée sur un écran qui n'a pas ses données.
     */
    fun isImmersive(source: MushafSource): Boolean = source == MushafSource.CORAN_TEST

    /** La plage de versets d'une page, dans le découpage de la source. */
    fun pageRange(source: MushafSource, page: Int): Range = when {
        source == MushafSource.CORAN_TEST -> TestPageIndex.pageRange(page)
        QuranSourceReady.isZipSource(source) -> ZipQuranSource.pageRange(page)
        else -> Quran.pageRange(page)
    }

    /**
     * La page d'un verset, dans le découpage de la source.
     *
     * @param current la page affichée. Elle est **conservée** si elle porte déjà le verset :
     *   sans cela, un suivi automatique ramènerait à la première page du verset à chaque
     *   récitation, et sauterait en arrière au milieu d'une lecture.
     */
    fun versePage(source: MushafSource, id: Int, current: Int? = null): Int = when {
        source == MushafSource.CORAN_TEST -> TestPageIndex.versePage(id, current)
        QuranSourceReady.isZipSource(source) -> ZipQuranSource.versePage(id, current)
        else -> Quran.pageOf(id)
    }
}
