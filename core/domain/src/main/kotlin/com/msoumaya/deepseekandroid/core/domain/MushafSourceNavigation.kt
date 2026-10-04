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
 * ## Ce qui manque encore, et qui est dit
 *
 * La source `coranTest` a, dans le client d'origine, **son propre découpage** en pages de
 * test (`testPageRange` / `testVersePage`), parce que son écran affiche un verset par page
 * plutôt qu'une page de moushaf. Cet écran n'est pas encore porté : en attendant, `coranTest`
 * se lit avec le découpage du moushaf de Médine. C'est un écart connu, pas un oubli — il
 * disparaîtra avec l'écran immersif, et le noter ici évite de le chercher ailleurs.
 */
object MushafSourceNavigation {

    /** La plage de versets d'une page, dans le découpage de la source. */
    fun pageRange(source: MushafSource, page: Int): Range =
        if (QuranSourceReady.isZipSource(source)) ZipQuranSource.pageRange(page) else Quran.pageRange(page)

    /**
     * La page d'un verset, dans le découpage de la source.
     *
     * @param current la page affichée. Elle est **conservée** si elle porte déjà le verset :
     *   sans cela, un suivi automatique ramènerait à la première page du verset à chaque
     *   récitation, et sauterait en arrière au milieu d'une lecture.
     */
    fun versePage(source: MushafSource, id: Int, current: Int? = null): Int =
        if (QuranSourceReady.isZipSource(source)) {
            ZipQuranSource.versePage(id, current)
        } else {
            Quran.pageOf(id)
        }
}
