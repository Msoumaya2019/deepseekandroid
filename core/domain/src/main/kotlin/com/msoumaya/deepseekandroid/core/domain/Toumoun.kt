package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.Range
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonNull

/**
 * Toumoun (un huitième de juz’).
 *
 * Porté depuis `src/core/toumoun.ts`. **Le rythme « 1 toumoun » reste indisponible**, et
 * c'est volontaire : les 480 enregistrements de `toumoun.json` portent tous
 * `verificationStatus: "missing_hafs_reference"` et aucune borne de verset. Le client
 * d'origine refuse de proposer un rythme dont les limites ne sont pas vérifiées ; ce
 * client applique exactement la même règle.
 *
 * Trois limites du découpage traditionnel tombent au milieu d'un verset (2:196, 3:7, 18:22).
 * Une décision éditoriale explicite est nécessaire avant de les activer : une approximation
 * ne doit pas être présentée comme authentifiée.
 */
object Toumoun {

    data class Record(
        val number: Int,
        val hizb: Int,
        val rub: Int,
        val startSurah: Int?,
        val startAyah: Int?,
        val endSurah: Int?,
        val endAyah: Int?,
        val source: String?,
        val verificationStatus: String,
    )

    private val records: List<Record> by lazy { loadRecords() }

    val allRecords: List<Record> get() = records

    /**
     * Renvoie les 480 plages si — et seulement si — l'ensemble est complet, ordonné,
     * contigu, aligné sur les rub‘ et marqué vérifié. Sinon `null`.
     */
    fun verifiedToumounRanges(input: List<Record> = records): List<Range>? {
        if (input.size != 480) return null
        val ranges = ArrayList<Range>(480)
        for (index in input.indices) {
            val item = input[index]
            if (item.number != index + 1) return null
            if (item.hizb != index / 8 + 1) return null
            if (item.rub != index / 2 + 1) return null
            if (item.verificationStatus != "verified_hafs") return null
            if (item.source.isNullOrEmpty()) return null
            val startSurah = item.startSurah ?: return null
            val startAyah = item.startAyah ?: return null
            val endSurah = item.endSurah ?: return null
            val endAyah = item.endAyah ?: return null
            val start = Quran.verseId(startSurah, startAyah) ?: return null
            val end = Quran.verseId(endSurah, endAyah) ?: return null
            val quarter = Quran.quarters.getOrNull(index / 2) ?: return null
            if (start > end || start < quarter.start || end > quarter.end) return null
            if (index % 2 == 0 && start != quarter.start) return null
            if (index % 2 == 1 && (end != quarter.end || start != ranges[index - 1].end + 1)) return null
            ranges.add(Range(start, end))
        }
        return ranges
    }

    /** Résultat de la vérification sur les données embarquées. `null` aujourd'hui. */
    val verifiedToumouns: List<Range>? by lazy { verifiedToumounRanges(records) }

    private fun loadRecords(): List<Record> {
        val stream = Toumoun::class.java.classLoader
            ?.getResourceAsStream("${QuranDataLoader.DIRECTORY}/toumoun.json")
            ?: return emptyList()
        val text = stream.use { it.readBytes().decodeToString() }
        return AppJson.parseToJsonElement(text).jsonArray.map { element ->
            val o = element.jsonObject
            Record(
                number = o.getValue("number").jsonPrimitive.int,
                hizb = o.getValue("hizb").jsonPrimitive.int,
                rub = o.getValue("rub").jsonPrimitive.int,
                startSurah = o.intOrNull("startSurah"),
                startAyah = o.intOrNull("startAyah"),
                endSurah = o.intOrNull("endSurah"),
                endAyah = o.intOrNull("endAyah"),
                source = o["source"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content },
                verificationStatus = o.getValue("verificationStatus").jsonPrimitive.content,
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObject.intOrNull(key: String): Int? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.content.toIntOrNull()
    }
}
