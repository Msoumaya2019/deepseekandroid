package com.msoumaya.deepseekandroid.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * La valeur écrite dans la colonne `type` de `daily_contents` et `content_categories`.
 *
 * `DailyContentType.key` sert de **filtre PostgREST** (`eq("type", …)`), et une chaîne fausse ne
 * lève rien : elle demanderait simplement des lignes qui n'existent pas — donc une liste vide, donc
 * l'affichage « Aucun rappel disponible » sur une base qui en contient. C'est un défaut muet, et
 * c'est ce que ce fichier empêche.
 *
 * **Ce qui est mesuré.** La valeur est lue par la **sérialisation réelle** du descripteur, comme
 * pour `MushafSource.persistedKey`, et non recopiée depuis une seconde table qui pourrait diverger.
 * Le compte des valeurs est figé : ajouter une valeur d'énumération sans l'inscrire ici fait échouer
 * le dernier test, au lieu de laisser une valeur non éprouvée passer en production.
 *
 * **Ce que ce fichier ne mesure pas.** La correspondance des **colonnes** — `arabic_text`,
 * `french_text`… — n'est pas ici : elle vit dans `DailyContentRow`, côté `core:data`, et c'est là
 * qu'elle est éprouvée, sur un vrai document JSON.
 */
class DailyContentTypeKeyTest {

    @Test
    fun `les valeurs de type sont celles de la migration`() {
        assertEquals("reminder", DailyContentType.REMINDER.key)
        assertEquals("invocation", DailyContentType.INVOCATION.key)
    }

    @Test
    fun `aucune valeur ne se repete`() {
        // Deux valeurs identiques rendraient un filtre ambigu : le serveur rendrait les lignes de
        // l'une et de l'autre, et l'ecran melangerait rappels et invocations.
        val valeurs = DailyContentType.entries.map { it.key }
        assertEquals(valeurs.size, valeurs.toSet().size, "valeurs en double : $valeurs")
    }

    @Test
    fun `chaque valeur est couverte`() {
        // Le compte est fige : ajouter une valeur d'enumeration sans l'inscrire ci-dessus echoue
        // ici, au lieu de laisser une valeur non eprouvee.
        assertEquals(2, DailyContentType.entries.size)
    }
}
