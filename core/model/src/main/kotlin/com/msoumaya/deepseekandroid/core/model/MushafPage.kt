package com.msoumaya.deepseekandroid.core.model

/**
 * Ce qu'il faut pour dessiner une page : ses images, les rectangles de ses versets, et
 * l'espace dans lequel ces rectangles sont exprimés.
 *
 * ## Pourquoi cette couture existe
 *
 * Le lecteur ne doit **pas** savoir où sont les images. Le moushaf de Médine est embarqué dans
 * l'application ; la source « Coran 1441 » est téléchargée dans le stockage privé. Si le
 * lecteur allait les chercher lui-même, il lui faudrait connaître le stockage et le réseau — et
 * le lecteur doit s'ouvrir en avion. Il reçoit donc des chemins déjà résolus, et rien d'autre.
 *
 * ## Deux formes, un seul type
 *
 * La page embarquée est **une** image de 1920×3106. Une page du paquet est **quinze** bandes
 * de 1440×232, exprimées dans un espace de 1440×2320. Les deux se décrivent avec les mêmes
 * champs : ce qui change est le nombre de bandes, et l'espace des coordonnées. Un type par
 * forme aurait obligé le rendu à choisir entre deux chemins, et à porter deux fois la même
 * géométrie de centrage.
 *
 * @param lines les images de la page, **de haut en bas**. Une seule pour la page embarquée,
 *   quinze pour une page du paquet.
 * @param rows les rectangles des versets, dans l'espace `sourceWidth × sourceHeight`.
 * @param sourceWidth largeur de l'espace des coordonnées. Ce n'est pas la largeur d'affichage.
 * @param sourceHeight hauteur de l'espace des coordonnées.
 */
data class MushafPage(
    val lines: List<String>,
    val rows: List<VerseBoundsRow>,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    /** Vrai quand la page tient en une seule image — le cas de toutes les sources embarquées. */
    val isSingleImage: Boolean get() = lines.size <= 1
}

/**
 * Fournit la page à dessiner.
 *
 * C'est le seul point par lequel le lecteur reçoit quelque chose du dehors : l'appelant sait
 * quelle source est choisie et où ses images se trouvent, le lecteur sait les dessiner.
 */
fun interface MushafPageSource {
    fun page(page: Int): MushafPage
}
