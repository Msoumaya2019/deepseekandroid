package com.msoumaya.deepseekandroid.feature.reader

/**
 * Où poser les bandes d'une page.
 *
 * La source « Coran 1441 » livre chaque page en quinze bandes de 1440×232, alors que la page
 * mesure 1440×2320. Les bandes ne sont donc **pas** juxtaposées : quinze bandes de 232 pixels
 * en couvriraient 3 480, soit une page et demie. Elles sont réparties de façon que la première
 * touche le haut de la page et la dernière son bas, ce qui les fait se recouvrir.
 *
 * C'est la disposition du client d'origine, et elle est écrite ici — hors du composable —
 * pour une raison précise : c'est un calcul, et un calcul se vérifie. Dans le composable, il ne
 * se voyait que sur un appareil, et seulement si l'on remarquait un décalage d'une ligne.
 *
 * Les bandes se recouvrant, **l'ordre de dessin compte** : la dernière posée passe devant.
 * C'est pourquoi les bandes sont dessinées de haut en bas, dans l'ordre des lignes du moushaf.
 */
internal object MushafPageGeometry {

    /**
     * La hauteur d'une bande pour une page large de [innerWidth].
     *
     * Elle vient du rapport mesuré sur le paquet, et non de la hauteur de la page divisée par
     * le nombre de bandes : les deux ne coïncident pas, et c'est justement ce qui produit le
     * recouvrement.
     */
    fun bandHeight(innerWidth: Float): Float = innerWidth * LINE_ASPECT

    /**
     * La position verticale de la bande [index], parmi [count] bandes, dans une page haute de
     * [innerHeight].
     *
     * L'espace restant après la première bande est réparti également sur les intervalles qui la
     * séparent des suivantes. Quand le total des bandes dépasse la hauteur — le cas réel — cet
     * espace est négatif, et les bandes se recouvrent au lieu de laisser un blanc.
     *
     * Une seule bande occupe la page entière : elle n'a rien à répartir.
     */
    fun bandTop(index: Int, count: Int, innerHeight: Float, bandHeight: Float): Float {
        if (count <= 1) return 0f
        val step = (innerHeight - bandHeight) / (count - 1)
        return step * index
    }
}
