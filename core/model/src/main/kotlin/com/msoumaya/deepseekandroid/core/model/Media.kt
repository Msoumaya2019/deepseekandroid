package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.Serializable

/**
 * Modèle audio et lecteur.
 *
 * Porté depuis `src/core/audio.ts`, `src/core/readerZoom.ts`, `src/core/readerAppearance.ts`,
 * `src/core/marginAnnotations.ts` et `src/core/readerData.ts`.
 */

/**
 * Un récitateur.
 *
 * Deux familles d'URL coexistent, exactement comme dans le client d'origine :
 *  - `verseFolder` absent : fichier nommé par l'identifiant global du verset (1..6236),
 *    servi par `cdn.islamic.network` ;
 *  - `verseFolder` présent : fichier nommé `SSSAAA.mp3` (sourate et verset sur 3 chiffres),
 *    servi par `everyayah.com`.
 */
@Serializable
data class Reciter(
    val id: String,
    val name: String,
    val reading: String,
    val bitrate: Int,
    val verseFolder: String? = null,
    val arabicName: String? = null,
)

/** Position de lecture audio : un verset et le numéro de répétition en cours. */
@Serializable
data class AudioPosition(val verseId: Int, val repetition: Int)

/** Bornes d'un verset dans un fichier audio de sourate entière. */
@Serializable
data class ChapterVerseSpan(val start: Double, val end: Double)

/** Piste audio d'une sourate entière, découpée par verset. */
@Serializable
data class ChapterAudio(
    val url: String,
    val verses: Map<Int, ChapterVerseSpan>,
)

/** Segment audio à jouer, avec bornes optionnelles en secondes. */
@Serializable
data class AudioSegment(
    val url: String,
    val startSeconds: Double? = null,
    val endSeconds: Double? = null,
)

/** Un fragment de texte et la règle de Tajweed qui s'y applique (`null` = aucune). */
@Serializable
data class TajweedSpan(val text: String, val rule: String? = null)

/** État de zoom du lecteur : échelle et décalage. Échelle bornée à 1..3. */
@Serializable
data class ReaderZoom(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)

/**
 * Rectangle d'un verset sur une page, en coordonnées **normalisées** (0..1).
 * Utilisé pour les marqueurs de marge et la détection d'appui long.
 */
@Serializable
data class MarginRegion(
    val id: Int,
    val ayah: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val line: Int,
)

/**
 * Rectangle d'un verset dans l'espace de la source (pixels).
 * Format des lignes de `bounds.json` : `[sourate, verset, ligne, x1, x2, y1, y2]`.
 */
@Serializable
data class VerseBoundsRow(
    val surah: Int,
    val ayah: Int,
    val line: Int,
    val x1: Int,
    val x2: Int,
    val y1: Int,
    val y2: Int,
) {
    val left: Int get() = minOf(x1, x2)
    val right: Int get() = maxOf(x1, x2)
    val top: Int get() = minOf(y1, y2)
    val bottom: Int get() = maxOf(y1, y2)

    val area: Int get() = (right - left) * (bottom - top)
}

/** Clé textuelle d'un verset, format `"sourate:verset"` utilisé par le rendu Tajwid. */
fun verseKey(surah: Int, ayah: Int): String = "$surah:$ayah"
