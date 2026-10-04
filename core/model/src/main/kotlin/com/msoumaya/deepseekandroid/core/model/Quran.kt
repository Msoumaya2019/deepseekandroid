package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.Serializable

/**
 * Référentiel coranique.
 *
 * Porté depuis `src/core/quran.ts` du dépôt `Msoumaya2019/coran-memoire`.
 * Le référentiel est bâti à partir de trois sources :
 *  - `verses.json` : les 6 236 versets Hafs ‘an ‘Âsim (Tanzil, version Uthmani) ;
 *  - `meta.json`   : sourates, juz’, rub‘ (quarters), hizb et nisf ;
 *  - `pages.json`  : les 604 pages et leurs premiers/derniers versets.
 *
 * Les identifiants de verset sont **globaux et contigus** de 1 à 6236 : c'est la clé
 * partagée par tous les clients (React Native, Android, iOS) et par le backend Supabase.
 */

/**
 * Plage de versets, bornes incluses.
 *
 * Aucune validation dans le constructeur : cette classe est désérialisée depuis l'état
 * distant, et un état malformé doit pouvoir être lu puis réparé plutôt que faire échouer
 * la désérialisation. Les fonctions du domaine valident leurs entrées explicitement.
 */
@Serializable
data class Range(val start: Int, val end: Int) {
    val size: Int get() = end - start + 1

    fun contains(id: Int): Boolean = id in start..end

    fun intersects(other: Range): Boolean = start <= other.end && other.start <= end
}

/** Un verset et son texte arabe vocalisé. */
@Serializable
data class Verse(val surah: Int, val ayah: Int, val text: String)

/** Une division numérotée (juz’, hizb, nisf, rub‘). */
@Serializable
data class Division(val number: Int, val start: Int, val end: Int) {
    val range: Range get() = Range(start, end)
}

/** Une sourate. */
@Serializable
data class Surah(
    val number: Int,
    val name: String,
    val meaning: String,
    val arabic: String,
    val start: Int,
    val end: Int,
    val count: Int,
    val isMeccan: Boolean? = null,
) {
    val range: Range get() = Range(start, end)
}

/** Première et dernière référence (sourate, verset) d'une page du moushaf. */
@Serializable
data class PageRef(val page: Int, val first: List<Int>, val last: List<Int>)

/**
 * Référentiel complet, immuable une fois construit.
 *
 * [weights] contient, pour chaque verset, son nombre de lettres arabes (minimum 1).
 * Ce poids sert à toutes les mesures de progression : une même plage comptée deux fois
 * ne compte qu'une seule fois, et un pourcentage est toujours un rapport de volume.
 */
class QuranData(
    val verses: List<Verse>,
    val surahs: List<Surah>,
    val juzs: List<Division>,
    val quarters: List<Division>,
    val hizbs: List<Division>,
    val halves: List<Division>,
    val pages: List<PageRef>,
    val weights: IntArray,
    val totalVolume: Int,
) {
    init {
        require(verses.size == 6236) { "Le référentiel doit contenir 6 236 versets, reçu ${verses.size}" }
        require(surahs.size == 114) { "Le référentiel doit contenir 114 sourates, reçu ${surahs.size}" }
        require(weights.size == verses.size) { "weights et verses doivent avoir la même taille" }
    }

    companion object {
        /** Référentiel vide, utilisé tant que les données ne sont pas chargées. */
        val EMPTY: QuranData = run {
            val surahs = (1..114).map {
                Surah(it, "Sourate $it", "", "", 1, 1, 1)
            }
            QuranData(
                verses = List(6236) { Verse(1, 1, "") },
                surahs = surahs,
                juzs = emptyList(),
                quarters = emptyList(),
                hizbs = emptyList(),
                halves = emptyList(),
                pages = emptyList(),
                weights = IntArray(6236) { 1 },
                totalVolume = 6236,
            )
        }

        /**
         * Construit le référentiel à partir des données brutes.
         *
         * [meta] reprend la structure de `meta.json` : listes `juzs`, `quarters`, `hizbs`.
         * Les hizb et nisf sont recalculés depuis les rub‘ exactement comme dans le client
         * d'origine : un hizb = 4 rub‘ consécutifs, un nisf = 2 rub‘ consécutifs.
         */
        fun build(
            verses: List<Verse>,
            surahs: List<Surah>,
            juzs: List<Division>,
            quarters: List<Division>,
            pages: List<PageRef>,
        ): QuranData {
            val hizbs = (0 until 60).map { i ->
                Division(i + 1, quarters[i * 4].start, quarters[i * 4 + 3].end)
            }
            val halves = (0 until 120).map { i ->
                Division(i + 1, quarters[i * 2].start, quarters[i * 2 + 1].end)
            }
            val weights = IntArray(verses.size) { i ->
                arabicLetterCount(verses[i].text).coerceAtLeast(1)
            }
            return QuranData(
                verses = verses,
                surahs = surahs,
                juzs = juzs,
                quarters = quarters,
                hizbs = hizbs,
                halves = halves,
                pages = pages,
                weights = weights,
                totalVolume = weights.sum(),
            )
        }

        private val ARABIC_LETTER = Regex("[\\u0621-\\u064A]")

        /** Nombre de lettres arabes d'un texte. Reproduit `weights[id-1]` du client d'origine. */
        fun arabicLetterCount(text: String): Int = ARABIC_LETTER.findAll(text).count()
    }
}
