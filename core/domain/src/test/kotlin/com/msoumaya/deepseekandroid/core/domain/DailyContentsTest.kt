package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.DailyContent
import com.msoumaya.deepseekandroid.core.model.DailyContentType
import com.msoumaya.deepseekandroid.core.model.DailyContentsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve les règles des contenus du jour.
 *
 * Trois règles, et aucune ne lève d'exception quand elle est fausse — c'est pourquoi elles sont
 * figées ici :
 *
 *  - **la taille d'une page.** Elle décide de **deux** choses : la fenêtre demandée au serveur et
 *    l'existence d'une suite à l'écran. Deux copies divergeraient, et la divergence se lirait comme
 *    une liste qui s'arrête au milieu sans rien dire ;
 *  - **la validité d'un document mis en cache.** Un document d'hier publié comme celui du jour
 *    afficherait un rappel périmé sous le titre « Rappel du jour » ;
 *  - **le partage entre « rien à montrer » et « rien n'a pu être lu ».** Le confondre ferait dire à
 *    l'écran « Aucun contenu disponible aujourd'hui » après une lecture ratée — une affirmation
 *    fausse, sur un sujet où personne ne vérifiera.
 *
 * **Ce que ce fichier ne couvre pas.** Les libellés — les quatre phrases de l'accueil, les deux
 * onglets, « Afficher la suite » — et la composition du texte partagé arrivent avec l'écran, et
 * seront éprouvés là. Une constante que personne n'affiche est une surface à relire sans preuve.
 */
class DailyContentsTest {

    private val jour = "2026-10-11"

    private fun contenu(id: String = "c1") = DailyContent(
        id = id,
        type = DailyContentType.REMINDER,
        frenchText = "Un texte.",
        source = "Une source",
        categoryId = "cat-1",
    )

    @Test
    fun `la taille d'une page est celle de l'original`() {
        // `range(offset, offset+29)` de `dailyContents.ts` : trente entrees, bornes comprises.
        assertEquals(30, DailyContents.PAGE_SIZE)
    }

    @Test
    fun `le jour du jour est une date AAAA-MM-JJ`() {
        // C'est `localDate()` de l'original — `getFullYear`, `getMonth`, `getDate` —, et le format
        // n'est pas cosmetique : c'est celui que le serveur attend (`p_date date`) et celui que
        // `isFor` compare, caractere a caractere.
        assertTrue(
            Regex("""\d{4}-\d{2}-\d{2}""").matches(DailyContents.today()),
            "Le jour doit etre une date AAAA-MM-JJ, mesuree : ${DailyContents.today()}",
        )
    }

    @Test
    fun `le document vide porte le jour du jour`() {
        assertEquals(DailyContents.today(), DailyContents.emptySnapshot().day)
        assertTrue(DailyContents.emptySnapshot().entries.isEmpty())
    }

    @Test
    fun `le document vide peut porter un autre jour`() {
        // Le magasin s'en sert pour rendre un document **vide et du jour** : un document vide dont
        // le jour serait `""` serait traite comme perime a chaque lecture, et le cache ne servirait
        // plus a rien.
        assertEquals(jour, DailyContents.emptySnapshot(jour).day)
    }

    @Test
    fun `le document vide par defaut est reconnu comme celui du jour`() {
        // C'est le couplage qui rend le cache utilisable : le document rendu par un appareil qui
        // n'a jamais rien lu doit passer `isFor`, sans quoi il serait remplace par... lui-meme.
        assertTrue(DailyContents.isFor(DailyContents.emptySnapshot(), DailyContents.today()))
    }

    @Test
    fun `un document d'une autre date n'est pas reconnu`() {
        assertTrue(DailyContents.isFor(DailyContents.emptySnapshot(jour), jour))
        assertFalse(DailyContents.isFor(DailyContents.emptySnapshot("2026-10-10"), jour))
    }

    @Test
    fun `un document sans entree est vide`() {
        assertTrue(DailyContents.isEmpty(DailyContents.emptySnapshot(jour)))
    }

    @Test
    fun `un document avec une entree n'est pas vide`() {
        val snapshot = DailyContentsSnapshot(day = jour, entries = listOf(contenu()))
        assertFalse(
            DailyContents.isEmpty(snapshot),
            "Un document qui porte une entree n'est pas vide : le declarer vide publierait une " +
                "panne alors qu'il y a quelque chose a lire.",
        )
    }
}
