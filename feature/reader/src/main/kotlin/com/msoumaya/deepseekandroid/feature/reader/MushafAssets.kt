package com.msoumaya.deepseekandroid.feature.reader

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.msoumaya.deepseekandroid.core.domain.ReaderPreload

/**
 * Les pages du moushaf de Médine, embarquées dans l'application.
 *
 * Les 604 images vivent dans `app/src/main/assets/quran/pages/`, en `page001.png` …
 * `page604.png`. Elles sont **dans l'application**, donc la lecture ne dépend d'aucun réseau :
 * c'est la condition du fonctionnement hors ligne, qui est le cœur du cahier des charges.
 *
 * Le chemin est un URI `file:///android_asset/…`, que Coil sait lire directement — inutile
 * d'ouvrir le flux à la main et de le décoder soi-même.
 */
internal object MushafAssets {

    const val DIRECTORY = "quran/pages"

    /** Nom du fichier d'une page, sur trois chiffres comme dans le dépôt d'origine. */
    fun fileName(page: Int): String = "page" + page.toString().padStart(3, '0') + ".png"

    fun uri(page: Int): String = "file:///android_asset/$DIRECTORY/${fileName(page)}"
}

/**
 * Prépare en mémoire la page courante et ses deux voisines.
 *
 * **Trois pages, jamais plus.** La règle vient de `ReaderPreload`, qui la borne et l'éprouve :
 * le moushaf pèse 114 Mo, et une page décodée bien davantage que ses 191 Ko sur disque. Un
 * préchargement généreux rendrait le lecteur fluide sur un appareil récent et le ferait
 * tomber sur un appareil d'entrée de gamme — c'est-à-dire chez ceux qui en ont le plus besoin.
 *
 * Les images sont demandées **à la taille d'affichage** et non à leur taille d'origine :
 * Coil les réduit au décodage, ce qui divise la mémoire occupée par plusieurs.
 *
 * Rien n'est fait si la page demandée est déjà affichée : `AsyncImage` l'a déjà chargée.
 */
@Composable
internal fun PreloadMushafPages(page: Int, widthPx: Int, heightPx: Int) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    LaunchedEffect(page, widthPx, heightPx) {
        if (widthPx <= 0 || heightPx <= 0) return@LaunchedEffect
        // `ReaderPreload.pages` met la page courante en tête ; elle est déjà à l'écran, donc
        // on l'ignore ici et on ne prépare que les voisines.
        for (neighbour in ReaderPreload.pages(page).drop(1)) {
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(MushafAssets.uri(neighbour))
                    .size(widthPx, heightPx)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
            )
        }
    }
}

/** Accès au chargeur d'images de l'application. */
internal val Context.imageLoader: ImageLoader
    get() = coil.Coil.imageLoader(this)
