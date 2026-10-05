package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.TestLine
import com.msoumaya.deepseekandroid.core.model.TestLineType
import com.msoumaya.deepseekandroid.core.model.TestPage
import com.msoumaya.deepseekandroid.core.model.TestWord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Chargement d'une page de la composition « Coran avec règles de Tajwid ».
 *
 * Les 604 fichiers vivent dans `core/domain/src/main/resources/coranTest/`, à côté de
 * `verse-index.json`. Ils sont donc lisibles **aussi bien par les tests JVM que par
 * l'application**, sans dupliquer 4,25 Mo de données.
 *
 * ## Ce que le chargement refuse
 *
 * Une page absente lève, et ne rend pas une page vide : un écran qui afficherait une page
 * blanche en silence serait plus difficile à diagnostiquer qu'une erreur nommée. Le refus
 * porte aussi sur le numéro : demander la page 605 est une faute d'appel, pas une donnée
 * manquante, et les deux ne se réparent pas au même endroit.
 *
 * Le parsing suit l'arbre JSON plutôt qu'une désérialisation typée, comme `QuranDataLoader` :
 * les fichiers d'origine portent des champs que ce client n'utilise pas, et un champ
 * supplémentaire ne doit jamais faire échouer l'affichage d'une page.
 */
object TestPageLoader {

    fun load(page: Int): TestPage {
        require(TestPageIndex.validPage(page)) { "Page invalide : $page" }
        val stream = TestPageLoader::class.java.classLoader
            ?.getResourceAsStream("${TestPageIndex.DIRECTORY}/$page.json")
            ?: error("Ressource absente : ${TestPageIndex.DIRECTORY}/$page.json")
        return parse(stream.use { it.readBytes().decodeToString() })
    }

    /** Analyse le document d'une page. Exposé pour être éprouvé sur un document écrit à la main. */
    fun parse(text: String): TestPage {
        val root = AppJson.parseToJsonElement(text).jsonObject
        val lines = (root["lines"] as? JsonArray ?: JsonArray(emptyList())).map { element ->
            parseLine(element.jsonObject)
        }
        return TestPage(
            page = root.int("page"),
            surah = root.int("surah"),
            juz = root.int("juz"),
            fontSize = root["fontSize"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: error("Champ absent : fontSize"),
            lines = lines,
        )
    }

    private fun parseLine(fields: JsonObject): TestLine = TestLine(
        line = fields.int("line"),
        type = typeOf(fields["type"]?.jsonPrimitive?.content),
        centered = fields["centered"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
        surah = fields["surah"]?.jsonPrimitive?.intOrNull,
        words = (fields["words"] as? JsonArray ?: JsonArray(emptyList())).map { parseWord(it.jsonArray) },
    )

    /**
     * Le type d'une ligne, et le refus d'un type inconnu.
     *
     * Un type non reconnu ferait tomber la ligne dans la branche « basmala » du générateur — un
     * défaut visible mais trompeur, qui accuserait la basmala. On refuse donc en nommant le
     * type reçu.
     */
    private fun typeOf(name: String?): TestLineType = when (name) {
        "ayah" -> TestLineType.AYAH
        "surah_name" -> TestLineType.SURAH_NAME
        "basmallah" -> TestLineType.BASMALLAH
        else -> error("Type de ligne inconnu : $name")
    }

    private fun parseWord(row: JsonArray): TestWord = TestWord(
        id = row[0].jsonPrimitive.intOrNull ?: 0,
        surah = row[1].jsonPrimitive.intOrNull ?: 0,
        ayah = row[2].jsonPrimitive.intOrNull ?: 0,
        word = row[3].jsonPrimitive.intOrNull ?: 0,
        glyphs = row[4].jsonPrimitive.content,
        arabic = row[5].jsonPrimitive.content,
    )

    private fun JsonObject.int(name: String): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: error("Champ absent : $name")
}
