package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.Division
import com.msoumaya.deepseekandroid.core.model.PageRef
import com.msoumaya.deepseekandroid.core.model.QuranData
import com.msoumaya.deepseekandroid.core.model.Surah
import com.msoumaya.deepseekandroid.core.model.Verse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream

/**
 * Chargement du référentiel coranique.
 *
 * Les fichiers proviennent de `src/data/` de `coran-memoire`, copiés par
 * `tools/import-quran-assets.mjs`. Ils sont placés dans `src/main/resources/quran/` afin
 * d'être accessibles aussi bien depuis les tests JVM que depuis l'application Android,
 * sans dupliquer 8 Mo de données.
 *
 * Le parsing est fait sur l'arbre JSON plutôt que par désérialisation typée : les fichiers
 * d'origine portent des champs que ce client n'utilise pas, et un champ supplémentaire ne
 * doit jamais faire échouer le chargement du référentiel.
 */
object QuranDataLoader {

    const val DIRECTORY = "quran"

    fun loadFromClasspath(): QuranData = load { name ->
        QuranDataLoader::class.java.classLoader?.getResourceAsStream("$DIRECTORY/$name")
    }

    fun load(open: (String) -> InputStream?): QuranData {
        val verses = parseVerses(readArray(open, "verses.json"))
        val meta = parseJson(readText(open, "meta.json")).jsonObject
        val surahs = parseSurahs(meta["surahs"]?.jsonArray ?: JsonArray(emptyList()))
        val juzs = parseDivisions(meta["juzs"]?.jsonArray ?: JsonArray(emptyList()))
        val quarters = parseDivisions(meta["quarters"]?.jsonArray ?: JsonArray(emptyList()))
        val pages = parsePages(readArray(open, "pages.json"))
        return QuranData.build(verses, surahs, juzs, quarters, pages)
    }

    private fun readText(open: (String) -> InputStream?, name: String): String {
        val stream = open(name) ?: error("Ressource coranique absente : $DIRECTORY/$name")
        return stream.use { it.readBytes().decodeToString() }
    }

    private fun readArray(open: (String) -> InputStream?, name: String): JsonArray =
        parseJson(readText(open, name)).jsonArray

    private fun parseJson(text: String) = AppJson.parseToJsonElement(text)

    private fun parseVerses(array: JsonArray): List<Verse> = array.map { element ->
        val o = element.jsonObject
        Verse(
            surah = o.getValue("surah").jsonPrimitive.int,
            ayah = o.getValue("ayah").jsonPrimitive.int,
            text = o.getValue("text").jsonPrimitive.content,
        )
    }

    private fun parseSurahs(array: JsonArray): List<Surah> = array.map { element ->
        val o = element.jsonObject
        Surah(
            number = o.getValue("number").jsonPrimitive.int,
            name = o.getValue("name").jsonPrimitive.content,
            meaning = o["meaning"]?.jsonPrimitive?.content ?: "",
            arabic = o["arabic"]?.jsonPrimitive?.content ?: "",
            start = o.getValue("start").jsonPrimitive.int,
            end = o.getValue("end").jsonPrimitive.int,
            count = o.getValue("count").jsonPrimitive.int,
            isMeccan = o["isMeccan"]?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
        )
    }

    private fun parseDivisions(array: JsonArray): List<Division> = array.map { element ->
        val o = element.jsonObject
        Division(
            number = o.getValue("number").jsonPrimitive.int,
            start = o.getValue("start").jsonPrimitive.int,
            end = o.getValue("end").jsonPrimitive.int,
        )
    }

    private fun parsePages(array: JsonArray): List<PageRef> = array.map { element ->
        val o = element.jsonObject
        PageRef(
            page = o.getValue("page").jsonPrimitive.int,
            first = o.getValue("first").jsonArray.map { it.jsonPrimitive.int },
            last = o.getValue("last").jsonArray.map { it.jsonPrimitive.int },
        )
    }

    private fun JsonObject.getValue(key: String) = this[key] ?: error("Champ JSON absent : $key")

    private fun kotlinx.serialization.json.JsonPrimitive.intOrZero(): Int = intOrNull ?: 0
}
