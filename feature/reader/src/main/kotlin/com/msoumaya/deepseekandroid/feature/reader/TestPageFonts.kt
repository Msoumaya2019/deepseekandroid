package com.msoumaya.deepseekandroid.feature.reader

import android.content.Context
import android.util.Base64
import com.msoumaya.deepseekandroid.core.domain.TestPageHtml

/**
 * Les polices de la composition « Coran avec règles de Tajwid ».
 *
 * ## Pourquoi les polices sont le corpus, et non un ornement
 *
 * Cette source n'affiche pas d'images : elle compose une page avec les **vrais glyphes** d'une
 * police QCF v4 par page, et c'est le moteur de rendu qui les place. Sans le fichier de la page,
 * les points de code du document ne désignent rien et la page s'affiche en carrés vides — ce
 * n'est pas une page dégradée, c'est une page illisible.
 *
 * ## Trois fichiers par page, et trois seulement
 *
 * - `<page>.woff2` : la police de composition, **une par page** (604 fichiers) ;
 * - `surah-name-v4.woff2` : le titre des sourates ;
 * - `vertopal.com_QCF_Bismillah-Regular.woff2` : la basmala.
 *
 * Le catalogue généré de l'original en déclare une quatrième, `UthmanicHafs_V20.woff2`
 * (105 520 octets), et **rien ne la consomme** — mesuré : aucun module n'importe cet export, et
 * la police arabe réellement employée ailleurs vient de `theme/fonts.ts`, où c'est Amiri. Elle
 * n'est donc pas importée : un fichier que personne ne lit ne se justifie pas dans un paquet.
 *
 * ## Le nom du fichier d'une page n'est pas complété
 *
 * `1.woff2`, et non `001.woff2` : c'est le nom produit par le script d'import de l'original, et
 * celui des fichiers livrés. Le nommage des **images** du moushaf de Médine, lui, est complété
 * sur trois chiffres — deux conventions voisines qui ne se recopient pas.
 */
internal object TestPageFonts {

    /** Dossier des polices dans les actifs de l'application. */
    const val DIRECTORY = "coran-test"

    /** La police des titres de sourate. */
    const val TITLE_FILE = "surah-name-v4.woff2"

    /** La police de la basmala. */
    const val BASMALA_FILE = "vertopal.com_QCF_Bismillah-Regular.woff2"

    /** Le fichier de composition d'une page. */
    fun pageFile(page: Int): String = "$page.woff2"

    /**
     * Les trois fichiers d'une page, **dans l'ordre où le document les attend** : composition,
     * titre, basmala.
     *
     * Rendus ensemble plutôt que construits sur place : c'est ce qui permet de voir que les
     * trois noms sont distincts, et qu'une page ne prend pas la police d'une autre.
     */
    fun filesOf(page: Int): List<String> = listOf(pageFile(page), TITLE_FILE, BASMALA_FILE)

    /**
     * Les trois polices d'une page, encodées en URI `data:`.
     *
     * Le document est chargé **sans origine** : une police désignée par un chemin de fichier
     * serait refusée par sa politique de sécurité, et un fichier lu sur le disque ne serait de
     * toute façon pas joignable depuis une page sans base. L'encodage en base64 est ce qui rend
     * le document autonome — et c'est ce que fait l'original, dont le CSP accepte précisément
     * `data:`.
     */
    fun fontsOf(context: Context, page: Int): TestPageHtml.Fonts {
        val files = filesOf(page)
        return TestPageHtml.Fonts(
            page = dataUri(context, files[0]),
            title = dataUri(context, files[1]),
            basmala = dataUri(context, files[2]),
        )
    }

    private fun dataUri(context: Context, fileName: String): String {
        val bytes = context.assets.open("$DIRECTORY/$fileName").use { it.readBytes() }
        return "data:font/woff2;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
