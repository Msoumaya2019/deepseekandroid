package com.msoumaya.deepseekandroid.core.data.local

import java.io.File

/**
 * Ressources lourdes conservées sur le disque.
 *
 * Les pages de moushaf et les fichiers audio ne passent **pas** par DataStore : ce sont des
 * fichiers de plusieurs centaines de kilo-octets, lus par le lecteur d'images et par le
 * décodeur audio. Les charger en mémoire ou les sérialiser dans un fichier d'état serait un
 * gaspillage et un risque de corruption.
 *
 * Arborescence :
 * ```
 * <racine>/quran/pages/<source>/page001.png
 * <racine>/quran/audio/<recitateur>/<idVerset>.mp3
 * <racine>/quran/audio/chapter/<recitateur>/<sourate>.mp3
 * ```
 *
 * Toutes les validations sont faites ici plutôt que chez l'appelant : un numéro de page ou
 * de verset hors bornes produirait un chemin de fichier qui n'existe pas et une erreur de
 * lecture difficile à relier à sa cause.
 */
class ResourceFiles(private val root: File) {

    private val pagesRoot: File get() = File(root, "quran/pages")
    private val audioRoot: File get() = File(root, "quran/audio")

    fun pageFile(source: String, page: Int): File {
        require(page in 1..TOTAL_PAGES) { "Page du Coran invalide : $page" }
        return File(File(pagesRoot, source.sanitized()), fileName(page))
    }

    fun hasPage(source: String, page: Int): Boolean = pageFile(source, page).let { it.isFile && it.length() > 0 }

    /** Pages déjà présentes sur le disque, dans l'ordre croissant. */
    fun cachedPages(source: String): Set<Int> {
        val dir = File(pagesRoot, source.sanitized())
        val entries = dir.listFiles() ?: return emptySet()
        return entries.asSequence()
            .filter { it.isFile }
            .mapNotNull { it.name.toPageNumber() }
            .toSortedSet()
    }

    fun audioFile(reciterId: String, verseId: Int): File {
        require(verseId in 1..TOTAL_VERSES) { "Verset audio invalide : $verseId" }
        return File(File(audioRoot, reciterId.sanitized()), "$verseId.mp3")
    }

    fun chapterAudioFile(reciterId: String, surah: Int): File {
        require(surah in 1..TOTAL_SURAH) { "Sourate invalide : $surah" }
        return File(File(audioRoot, "chapter/${reciterId.sanitized()}"), "$surah.mp3")
    }

    fun hasAudio(reciterId: String, verseId: Int): Boolean =
        audioFile(reciterId, verseId).let { it.isFile && it.length() > 0 }

    /** Taille occupée par un sous-ensemble, en octets. */
    fun sizeOf(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { sizeOf(it) } ?: 0
    }

    fun pagesSizeBytes(): Long = sizeOf(pagesRoot)

    fun audioSizeBytes(): Long = sizeOf(audioRoot)

    /**
     * Supprime les fichiers audio téléchargés.
     *
     * Les pages de moushaf ne sont pas touchées : elles sont l'essentiel du fonctionnement
     * hors connexion et leur retrait rendrait le lecteur inutilisable sans réseau.
     */
    fun clearAudio(): Boolean = audioRoot.deleteRecursively()

    /** Supprime toutes les ressources téléchargées, pages comprises. */
    fun clearAll(): Boolean = File(root, "quran").deleteRecursively()

    private fun fileName(page: Int): String = "page" + page.toString().padStart(3, '0') + ".png"

    private fun String.toPageNumber(): Int? {
        if (!startsWith("page") || !endsWith(".png")) return null
        val digits = removePrefix("page").removeSuffix(".png")
        val page = digits.toIntOrNull() ?: return null
        return if (page in 1..TOTAL_PAGES) page else null
    }

    /** Un identifiant de source ou de récitateur ne doit jamais sortir de son dossier. */
    private fun String.sanitized(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val TOTAL_PAGES = 604
        const val TOTAL_VERSES = 6236
        const val TOTAL_SURAH = 114
    }
}
