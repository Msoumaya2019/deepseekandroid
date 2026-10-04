package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource

/**
 * La forme d'une page, selon la source affichée.
 *
 * Le moushaf de Médine et la source « Coran 1441 » n'ont pas le même rapport : la page
 * embarquée est en 1920×3106, celle du paquet téléchargé en 1440×2320. Centrer une page avec
 * le rapport de l'autre la **déformerait** — et une page de moushaf étirée est illisible, ce
 * qui est précisément le défaut que le cahier des charges interdit.
 *
 * Cette règle est écrite ici, et non dans l'écran, pour une raison précise : l'écran ne peut
 * pas la vérifier sans être dessiné. Ici, elle s'éprouve sur les 604 pages.
 */
object MushafPageShape {

    /** La forme de la page embarquée, utilisée par toutes les sources sauf le paquet. */
    val EMBEDDED: ReaderLayout.Size = ReaderLayout.Size(
        ReaderLayout.PAGE_WIDTH.toDouble(),
        ReaderLayout.PAGE_HEIGHT.toDouble(),
    )

    /**
     * La forme de la page [page] pour la source [source].
     *
     * Le repli sur [EMBEDDED] n'est pas un détail de confort : `ZipQuranSource.pageData` rend
     * `(1.0, 1.0)` pour une page absente du référentiel. Diviser par ce rapport donnerait une
     * page d'un pixel de côté. Une page hors bornes doit donc être écartée, et le seuil
     * `> 1.0` écarte aussi le repli du référentiel lui-même.
     *
     * ## Deux garde-fous dont aucun n'est éprouvable seul — et c'est mesuré
     *
     * La garde de bornes et le seuil se **recouvrent exactement**, sur le référentiel livré :
     * `coran_1441-dimensions.json` porte **604 clés, les pages 1 à 604, et une seule valeur**
     * `(1440, 2320)`. Conséquences, toutes deux vérifiées par falsification :
     *
     *  * retirer la garde de bornes ne fait **tomber aucun test** : une page hors bornes
     *    obtient `(1.0, 1.0)`, que le seuil refuse déjà ;
     *  * neutraliser le seuil ne fait **tomber aucun test** non plus : la garde a déjà écarté
     *    les seules pages pour lesquelles il servirait.
     *
     * Les deux sont donc conservés, mais **aucun test ne les couvre individuellement**, et il ne
     * faut pas écrire le contraire : un test qui prétendrait le faire serait vert pour la
     * mauvaise raison. Ce qui est réellement éprouvé ici est le **choix des dimensions** —
     * échanger `width` et `height` fait tomber deux tests.
     *
     * Ils restent parce que la redondance est le but : le jour où le référentiel changerait de
     * forme — une page absente, des dimensions en chaîne de caractères, un espace différent —
     * les deux ne se recouvriraient plus, et c'est le seuil qui rattraperait la garde.
     */
    fun of(source: MushafSource, page: Int): ReaderLayout.Size {
        if (!QuranSourceReady.isZipSource(source)) return EMBEDDED
        if (page < 1 || page > QuranSourceTransition.TOTAL_PAGES) return EMBEDDED
        val data = ZipQuranSource.pageData(page)
        return if (data.width > 1.0 && data.height > 1.0) {
            ReaderLayout.Size(data.width, data.height)
        } else {
            EMBEDDED
        }
    }

    /**
     * Le nombre de bandes d'une page.
     *
     * La source 1441 livre ses pages **découpées en lignes** — 9 060 fichiers pour 604 pages —
     * parce que les images d'une page entière seraient trop lourdes à télécharger d'un bloc.
     * Les autres sources livrent une image par page.
     */
    fun lines(source: MushafSource): Int =
        if (QuranSourceReady.isZipSource(source)) ZIP_LINES_PER_PAGE else 1
}
