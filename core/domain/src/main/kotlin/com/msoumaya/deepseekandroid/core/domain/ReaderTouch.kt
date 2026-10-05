package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.ReaderZoom
import com.msoumaya.deepseekandroid.core.model.VerseBoundsRow

/**
 * Ce que le doigt désigne sur la page.
 *
 * ## Pourquoi cette règle existe séparément
 *
 * La conversion d'une position d'écran en verset était écrite **en clair** dans l'appui long du
 * lecteur. Le mode de pose d'un signet a besoin d'exactement la même conversion : c'est le même
 * doigt, sur la même page, avec la même géométrie. Deux copies divergeraient au premier
 * ajustement — et l'écart ne se lirait pas comme une erreur de calcul, mais comme un **verset
 * faux** : la fiche décrirait un verset, le signet en poserait un autre, et les deux seraient
 * plausibles.
 *
 * ## Trois repères, et deux corrections
 *
 * Le doigt est dans le repère de l'écran. Les rectangles des versets sont dans celui de la
 * **source** (`sourceWidth × sourceHeight`), qui n'est pas la taille d'affichage. Il y a donc
 * deux corrections à faire, dans cet ordre :
 *
 *  1. **défaire le zoom** — la page agrandie se déplace sous le doigt, donc la position se divise
 *     par l'échelle, après retrait de la translation. Oublier cette étape fait désigner un verset
 *     d'autant plus éloigné que l'agrandissement est fort ;
 *  2. **retirer le centrage** — la page est centrée dans l'espace disponible, et l'écart
 *     `(disponible − page) / 2` n'est pas compté dans les rectangles.
 *
 * [ReaderData.verseAtImagePoint] fait ensuite le passage à l'espace de la source, puis retient le
 * **plus petit** rectangle contenant le point.
 *
 * ## Ce que cette règle ne décide pas
 *
 * Elle ne décide pas si le point désigne quelque chose : elle rend `null` quand il tombe hors de
 * la page ou entre deux versets. C'est à l'appelant d'en tirer conséquence, et les deux appelants
 * en tirent des conséquences **différentes** : l'appui long n'ouvre pas de fiche, tandis que la
 * pose d'un signet **reste en attente** au lieu d'enregistrer autre chose. La règle ne tranche
 * donc pas à leur place.
 *
 * Le cas `scale == 0` n'est pas traité à part, et c'est mesuré : `ReaderZoomGeometry.constrain`
 * borne déjà l'échelle, et une échelle nulle donnerait `Infinity` ou `NaN`, que
 * `verseAtImagePoint` refuse par ses bornes (`x > width` est vrai pour l'infini, et `NaN` ne
 * satisfait aucun intervalle). Une garde supplémentaire serait une branche qu'aucun test ne
 * sépare.
 */
object ReaderTouch {

    /**
     * Le verset sous un point de l'écran, ou `null` si ce point n'en désigne aucun.
     *
     * @param x abscisse du doigt, dans le repère de l'écran.
     * @param y ordonnée du doigt, dans le repère de l'écran.
     * @param zoom zoom courant : la page est agrandie, puis translatée.
     * @param availableWidth largeur de l'espace où la page est centrée.
     * @param availableHeight hauteur de cet espace.
     * @param pageWidth largeur de la page telle qu'elle est dessinée.
     * @param pageHeight hauteur de la page telle qu'elle est dessinée.
     * @param rows les rectangles des versets, dans l'espace de la source.
     * @param sourceWidth largeur de cet espace. Elle est **exigée** plutôt que par défaut : un
     *   appelant qui l'oublierait lirait les rectangles d'une autre source, et désignerait un
     *   verset voisin sur une page pourtant correcte.
     */
    fun verseAt(
        x: Double,
        y: Double,
        zoom: ReaderZoom,
        availableWidth: Double,
        availableHeight: Double,
        pageWidth: Double,
        pageHeight: Double,
        rows: List<VerseBoundsRow>,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Int? {
        val scale = zoom.scale.toDouble()
        val unzoomedX = (x - zoom.x) / scale
        val unzoomedY = (y - zoom.y) / scale
        return ReaderData.verseAtImagePoint(
            rows = rows,
            x = unzoomedX - (availableWidth - pageWidth) / 2.0,
            y = unzoomedY - (availableHeight - pageHeight) / 2.0,
            width = pageWidth,
            height = pageHeight,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )
    }
}
