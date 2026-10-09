package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Les cinq valeurs de `ProblemReportType`, telles qu'elles sont écrites en base.
 *
 * `app_problem_reports.type` est une colonne **partagée** avec le client React Native, qui y écrit
 * ces cinq littéraux, et le serveur la contraint par un `check(type in('Bug','Affichage','Audio',
 * 'Notification','Autre'))`. Les changer ne casse rien ici : la ligne partirait simplement avec une
 * valeur que le serveur refuse — et le refus ne se verrait qu'au dépôt, longtemps après le geste.
 *
 * **Ce que ce test mesure.** Les valeurs sont lues **par la sérialisation réelle**, et non
 * recopiées depuis une seconde table qui pourrait diverger. C'est le pendant de
 * `SocialEnumKeyTest`, et la même règle : ajouter une valeur d'énumération sans l'inscrire ici fait
 * échouer le dernier test, au lieu de laisser une valeur non éprouvée passer en production.
 *
 * **Ce qu'il ne mesure pas.** Le `check` du serveur lui-même : il vit dans
 * `supabase/problem-reports.sql`, et `ProblemReportsTest` (côté `core:domain`) est le seul endroit
 * où cette contrainte est citée pour être comparée. Ici, on ne fige que ce que le client écrit.
 */
class ProblemReportTypeKeyTest {

    /** Valeur telle qu'elle part sur le réseau : la chaîne JSON sans ses guillemets. */
    private inline fun <reified T> fil(value: T): String = AppJson.encodeToString(value).trim('"')

    @Test
    fun `le type d'un signalement est celui des colonnes de app_problem_reports`() {
        assertEquals("Bug", fil(ProblemReportType.BUG))
        assertEquals("Affichage", fil(ProblemReportType.AFFICHAGE))
        assertEquals("Audio", fil(ProblemReportType.AUDIO))
        assertEquals("Notification", fil(ProblemReportType.NOTIFICATION))
        assertEquals("Autre", fil(ProblemReportType.AUTRE))
    }

    @Test
    fun `la lecture sur le descripteur rend la meme chaine que la serialisation`() {
        // `wire` est lue sur le descripteur, et `fil` passe par le serialiseur : les deux chemins
        // doivent dire la meme chose. S'ils divergeaient, la colonne recevrait une valeur que la
        // table ci-dessus croit avoir verifiee.
        for (valeur in ProblemReportType.entries) {
            assertEquals(fil(valeur), valeur.wire, "desaccord sur $valeur")
        }
    }

    @Test
    fun `aucune valeur ne se repete dans l'enumeration`() {
        // Deux valeurs identiques rendraient une ligne ambigue : la lecture choisirait la
        // premiere, et l'ecriture en produirait une que l'autre client ne saurait pas relire.
        val valeurs = ProblemReportType.entries.map { fil(it) }
        assertEquals(valeurs.size, valeurs.toSet().size, "valeurs en double : $valeurs")
    }

    @Test
    fun `l'ordre des valeurs est celui de l'ecran`() {
        // `ProblemReportSheet` parcourt `ProblemReportType.all` pour dessiner les cinq boutons
        // radio : cet ordre est donc ce que la personne voit, et le figer ici empeche qu'un
        // remaniement de l'enumeration le change en silence.
        assertEquals(
            listOf("Bug", "Affichage", "Audio", "Notification", "Autre"),
            ProblemReportType.all.map { it.wire },
        )
    }

    @Test
    fun `l'enumeration est couverte en entier`() {
        // Le compte est fige : ajouter une valeur d'enumeration sans l'inscrire dans les tests
        // ci-dessus echoue ici, au lieu de laisser une valeur non eprouvee.
        assertEquals(5, ProblemReportType.entries.size)
    }
}
