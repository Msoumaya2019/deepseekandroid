package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafPage
import com.msoumaya.deepseekandroid.core.model.MushafPageSource
import com.msoumaya.deepseekandroid.core.model.MushafSource

/**
 * Les deux façons de décrire une page, selon la source.
 *
 * Elles sont écrites ici — et non dans les écrans — parce que ce sont des règles : quelles
 * images, quels rectangles, et dans quel espace. Ici elles s'éprouvent sur les 604 pages sans
 * dessiner quoi que ce soit.
 *
 * Les deux fonctions reçoivent la façon de nommer une image plutôt que de la calculer. C'est
 * ce qui leur permet de décrire aussi bien les ressources embarquées que le paquet installé,
 * sans connaître ni `android_asset` ni le stockage privé.
 */

/**
 * La page du moushaf de Médine, embarquée dans l'application.
 *
 * Les 604 images vivent dans `app/src/main/assets/quran/pages/` : la lecture ne dépend d'aucun
 * réseau, c'est la condition du fonctionnement hors ligne.
 */
fun embeddedMushafPages(uri: (Int) -> String): MushafPageSource = MushafPageSource { page ->
    MushafPage(
        lines = listOf(uri(page)),
        rows = ReaderData.pageRows(page),
        sourceWidth = ReaderLayout.PAGE_WIDTH,
        sourceHeight = ReaderLayout.PAGE_HEIGHT,
    )
}

/**
 * Une page du paquet « Coran 1441 » : quinze bandes, et les rectangles de sa propre source.
 *
 * Le rapport de la page est celui de `MushafPageShape` et **non** celui de la page embarquée :
 * ajuster cette page avec le rapport de l'autre l'étirerait. Les rectangles viennent de
 * `ZipQuranSource`, exprimés dans le même espace de 1440×2320.
 *
 * Une page hors bornes rend une page vide : le refus de lecture appartient à
 * `QuranSourceReady`, et un fournisseur d'images qui lèverait ferait tomber le lecteur au lieu
 * de laisser l'écran dire ce qui manque.
 */
fun archiveMushafPages(uri: (Int, Int) -> String): MushafPageSource = MushafPageSource { page ->
    val shape = MushafPageShape.of(MushafSource.CORAN_1441, page)
    MushafPage(
        lines = (1..ZIP_LINES_PER_PAGE).map { line -> uri(page, line) },
        rows = ZipQuranSource.verseRows(page),
        sourceWidth = shape.width.toInt(),
        sourceHeight = shape.height.toInt(),
    )
}
