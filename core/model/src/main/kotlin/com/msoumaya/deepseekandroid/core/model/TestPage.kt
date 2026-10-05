package com.msoumaya.deepseekandroid.core.model

/**
 * Une page de la composition « Coran avec règles de Tajwid ».
 *
 * Porté depuis `src/coranTest/model.ts`. Cette source n'est **pas** une image : c'est une
 * composition typographique. Chaque mot est un glyphe de la police couleur de sa page, et le
 * Tajwid vient des tables `COLR v0` / `CPAL` de cette police — il n'est jamais recoloré après
 * coup. Les 9 046 lignes des 604 pages sont décrites ici, et rien n'est mesuré à l'œil.
 *
 * ## Le mot, tel que le référentiel le donne
 *
 * Un mot est un **sextuplet** dans le JSON d'origine :
 * `[id, sourate, verset, rang, glyphes, arabe]`.
 *
 * - `glyphes` est ce qui est **dessiné** : des points de code des formes de présentation
 *   arabe, que la police de la page sait résoudre ;
 * - `arabe` est le texte lisible, employé comme libellé d'accessibilité et jamais dessiné.
 *
 * Les deux ne sont pas interchangeables : dessiner `arabe` avec la police de la page ne
 * donnerait pas la calligraphie attendue.
 */
data class TestWord(
    /** Identifiant global du verset, celui du reste de l'application. */
    val id: Int,
    val surah: Int,
    val ayah: Int,
    /** Rang du mot dans le verset, tel que la source le donne. */
    val word: Int,
    /** Les glyphes dessinés. */
    val glyphs: String,
    /** Le texte arabe lisible, pour l'accessibilité. */
    val arabic: String,
) {
    /** La clé `sourate:verset` employée par l'index et par les surcouches. */
    val verseKey: String get() = "$surah:$ayah"
}

/** Ce qu'une ligne de la composition porte. */
enum class TestLineType {
    /** Des mots d'un ou plusieurs versets. */
    AYAH,

    /** Le bandeau du nom d'une sourate, avec son ornement. */
    SURAH_NAME,

    /** La basmala, dont les glyphes dépendent de la sourate qui suit. */
    BASMALLAH,
}

/**
 * Une ligne de la composition.
 *
 * `surah` n'est renseigné que pour un bandeau de sourate : une ligne de mots ne porte pas de
 * sourate propre, un verset pouvant s'achever sur une ligne et le suivant commencer sur la
 * même.
 */
data class TestLine(
    val line: Int,
    val type: TestLineType,
    val centered: Boolean,
    val surah: Int?,
    val words: List<TestWord>,
)

/** Une page entière : ses métadonnées, sa taille de police, et ses lignes. */
data class TestPage(
    val page: Int,
    val surah: Int,
    val juz: Int,
    /**
     * Taille de police de la page, en pixels du canevas logique.
     *
     * C'est un **décimal**, et pas un entier : mesuré sur les 604 pages livrées, **602**
     * portent une valeur fractionnaire (de 55,29 à 70), et deux seulement tombent juste.
     * Déclarer un entier ici ne perdait pas une décimale — cela faisait échouer le
     * chargement de 602 pages sur 604, ce qu'un contrôle portant sur la seule page 1
     * n'aurait jamais montré.
     */
    val fontSize: Double,
    val lines: List<TestLine>,
)
