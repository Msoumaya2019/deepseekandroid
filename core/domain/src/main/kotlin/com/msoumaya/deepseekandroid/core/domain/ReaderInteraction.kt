package com.msoumaya.deepseekandroid.core.domain

/**
 * Décisions d'interaction du lecteur : ce qu'un glissement fait, et quelles pages garder en
 * mémoire.
 *
 * Ces deux règles vivent dans le domaine, et non dans l'écran, parce qu'elles se trompent
 * silencieusement : une page qui tourne alors qu'on voulait regarder son bord, ou cinq pages
 * gardées en mémoire au lieu de trois, ne produisent aucune erreur — seulement une sensation
 * de lecteur cassé, ou une consommation qui ne se voit que sur un appareil modeste.
 */

/**
 * Intention d'un glissement du doigt.
 *
 * `PAN` est le cas qui manquait : une page **agrandie** se déplace sous le doigt, elle ne
 * tourne pas. Sans cette distinction, chercher à lire le bord droit d'une page zoomée ferait
 * sauter à la page suivante — et l'utilisateur perdrait sa page à chaque fois qu'il essaie
 * de la regarder de près.
 */
enum class DragIntent { TURN_PREVIOUS, TURN_NEXT, PAN, NONE }

object ReaderGesture {

    /**
     * Au-delà de ce facteur, la page est considérée comme agrandie.
     *
     * Ce n'est pas exactement `ReaderZoomGeometry.MIN_SCALE` : après un pincement qui revient
     * à 1, l'échelle calculée vaut 1.0000001 et non 1. Sans cette tolérance, la page
     * resterait bloquée en mode « déplacement » alors qu'elle est visuellement entière.
     */
    const val ZOOMED_THRESHOLD = 1.01f

    /**
     * @param dx déplacement horizontal du glissement, en pixels (négatif = vers la gauche)
     * @param dy déplacement vertical, en pixels
     * @param scale échelle de zoom courante
     */
    fun dragIntent(dx: Float, dy: Float, scale: Float): DragIntent {
        if (scale > ZOOMED_THRESHOLD) return DragIntent.PAN

        val horizontal = kotlin.math.abs(dx)
        val vertical = kotlin.math.abs(dy)
        if (horizontal < PageNavigation.MIN_DX) return DragIntent.NONE
        if (horizontal < vertical * PageNavigation.HORIZONTAL_RATIO) return DragIntent.NONE

        // Le sens est celui de `PageNavigation.pageAfterSwipe`, qui est le portage fidèle du
        // client d'origine et qui est déjà éprouvé : **un glissement vers la gauche recule,
        // vers la droite avance.** Le contredire ici serait le pire des cas — l'intention
        // dirait « suivante » pendant que le numéro calculé dirait « précédente », et la page
        // affichée ne serait pas celle que le doigt a demandée. Un test d'accord vérifie les
        // deux ensemble.
        return if (dx < 0) DragIntent.TURN_PREVIOUS else DragIntent.TURN_NEXT
    }
}

/**
 * Fenêtre de préchargement des pages.
 *
 * **Trois pages au maximum**, jamais plus : la page courante et ses deux voisines. Le moushaf
 * complet pèse 114 Mo, et une page environ 191 Ko une fois décodée bien davantage ; garder
 * tout le moushaf en mémoire fait tomber l'application sur un appareil d'entrée de gamme.
 *
 * L'ordre compte : la page **courante** d'abord. C'est elle qu'on attend, les voisines ne
 * servent qu'à rendre le glissement suivant instantané.
 */
object ReaderPreload {

    const val WINDOW = 3

    fun pages(page: Int, totalPages: Int = 604): List<Int> {
        if (totalPages < 1) return emptyList()
        val current = page.coerceIn(1, totalPages)
        val out = LinkedHashSet<Int>()
        out.add(current)
        if (current - 1 >= 1) out.add(current - 1)
        if (current + 1 <= totalPages) out.add(current + 1)
        return out.toList()
    }
}
