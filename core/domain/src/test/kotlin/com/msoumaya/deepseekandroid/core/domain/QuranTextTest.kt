package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Les mots de l'écran du Coran
// ---------------------------------------------------------------------------
// Ces contrôles ne sont pas des redites du fichier : ils portent sur ce qu'on **ne voit pas** en
// le relisant. Un libellé recopié à la main peut perdre son apostrophe typographique — `Juz'` au
// lieu de `Juz’` — ou son tiret demi-cadratin, et l'écran s'afficherait avec un caractère de
// remplacement ou un trait d'union sans qu'aucune compilation ne s'en plaigne. Ce sont des textes
// d'interface validés par le propriétaire du projet : un caractère changé est un défaut, pas une
// variante.
//
// Ils portent aussi sur les **associations** : la présence d'une phrase ne dit pas qu'elle est
// rattachée à la bonne vue. Un sous-titre échangé entre « Juz’ » et « Hizb » se relit sans
// sursaut et afficherait « 30 parties » devant 60 hizb.
// ---------------------------------------------------------------------------

class QuranTextTest {

    /** L'apostrophe typographique de « Juz’ » et de « jusqu’à ». */
    private val apostrophe = '\u2019'

    /** Le guillemet simple ouvrant de « Hafs ‘an ‘Âsim ». */
    private val ouvrant = '\u2018'

    /** La puce des sous-titres. */
    private val puce = '\u2022'

    /** Le tiret demi-cadratin des plages de pages. */
    private val demiCadratin = '\u2013'

    // -----------------------------------------------------------------------
    // Les signes typographiques
    // -----------------------------------------------------------------------

    @Test
    fun `l'apostrophe de Juz’ est typographique`() {
        assertTrue(
            QuranText.View.JUZ.label.contains(apostrophe),
            "« Juz’ » doit porter U+2019 : une apostrophe droite se verrait à l'écran.",
        )
        assertFalse(
            QuranText.View.JUZ.label.contains('\''),
            "« Juz’ » ne doit pas porter d'apostrophe droite.",
        )
    }

    @Test
    fun `le libelle de connaissance porte deux apostrophes typographiques`() {
        assertEquals(2, QuranText.KNOWN_LABEL.count { it == apostrophe })
        assertFalse(QuranText.KNOWN_LABEL.contains('\''))
        assertEquals("J’ai appris jusqu’à :", QuranText.KNOWN_LABEL)
    }

    @Test
    fun `les deux guillemets de Hafs an Asim sont ouvrants`() {
        // La source écrit « Hafs ‘an ‘Âsim » avec deux U+2018, et non avec des apostrophes
        // typographiques U+2019 : dans une translittération, ces signes ouvrent.
        assertEquals(2, QuranText.SUBTITLE_LIST.count { it == ouvrant })
        assertFalse(QuranText.SUBTITLE_LIST.contains(apostrophe))
    }

    @Test
    fun `la plage de pages porte un demi-cadratin, pas un trait d'union`() {
        val plage = QuranText.divisionPages(12, 34)
        assertEquals("Pages 12 – 34", plage)
        assertTrue(plage.contains(demiCadratin))
        assertFalse(plage.contains('-'), "un trait d'union serait un défaut de portage")
    }

    @Test
    fun `le sous-titre de la liste porte deux puces`() {
        assertEquals(2, QuranText.SUBTITLE_LIST.count { it == puce })
    }

    // -----------------------------------------------------------------------
    // L'ordre des deux choix
    // -----------------------------------------------------------------------

    @Test
    fun `les trois vues sont dans l'ordre du selecteur`() {
        // L'ordre est celui de la déclaration, et le sélecteur l'affiche tel quel. Réordonner
        // l'énumération réordonnerait les trois segments : ce contrôle le refuse.
        assertEquals(
            listOf("Liste", "Juz’", "Hizb"),
            QuranText.View.entries.map { it.label },
        )
    }

    @Test
    fun `les trois filtres sont dans l'ordre du message de choix`() {
        assertEquals(
            listOf("Toutes", "Mecquoises", "Médinoises"),
            QuranText.Filter.entries.map { it.label },
        )
    }

    @Test
    fun `le choix d'annulation n'est pas un filtre`() {
        // Le message de choix a quatre boutons mais trois filtres : « Annuler » referme sans
        // rien changer. Le ranger dans l'énumération donnerait un filtre qui ne filtre rien.
        assertTrue(QuranText.Filter.entries.none { it.label == QuranText.FILTER_CANCEL })
        assertEquals("Annuler", QuranText.FILTER_CANCEL)
    }

    // -----------------------------------------------------------------------
    // Les libellés composés, et l'association à la bonne vue
    // -----------------------------------------------------------------------

    @Test
    fun `chaque vue a son sous-titre et son compte`() {
        // Les trois phrases annoncent la taille de ce qu'elles listent. Les échanger afficherait
        // « 30 parties » devant 60 hizb sans qu'aucune compilation ne s'en plaigne.
        assertEquals("Mushaf de Médine • Hafs ‘an ‘Âsim • 604 pages", QuranText.subtitle(QuranText.View.LIST))
        assertEquals("Liste des Juz’ • 30 parties", QuranText.subtitle(QuranText.View.JUZ))
        assertEquals("Liste des Hizb • 60 parties", QuranText.subtitle(QuranText.View.HIZB))
    }

    @Test
    fun `chaque vue a son invite de recherche`() {
        // La liste a sa propre phrase — « une sourate » —, les deux divisions reprennent le mot
        // de la vue. La source écrit « Rechercher un Juz’ », jamais « Rechercher un divisions ».
        assertEquals("Rechercher une sourate", QuranText.search(QuranText.View.LIST))
        assertEquals("Rechercher un Juz’", QuranText.search(QuranText.View.JUZ))
        assertEquals("Rechercher un Hizb", QuranText.search(QuranText.View.HIZB))
    }

    @Test
    fun `le nom d'une division reprend le mot de la vue`() {
        assertEquals("Juz’ 30", QuranText.divisionName(QuranText.View.JUZ, 30))
        assertEquals("Hizb 12", QuranText.divisionName(QuranText.View.HIZB, 12))
    }

    @Test
    fun `la reference du verset connu porte une puce`() {
        assertEquals("Al-Fâtiha • verset 3", QuranText.knownVerse("Al-Fâtiha", 3))
        assertTrue(QuranText.knownVerse("Al-Fâtiha", 3).contains(puce))
    }

    @Test
    fun `le lieu de revelation se lit dans les deux sens`() {
        assertEquals("Mecquoise", QuranText.place(isMeccan = true))
        assertEquals("Médinoise", QuranText.place(isMeccan = false))
        // Deux mots distincts, et non le même mot avec un préfixe : la pastille change aussi de
        // couleur, et deux libellés égaux rendraient ce changement invisible.
        assertFalse(QuranText.MECCAN == QuranText.MEDINAN)
    }

    @Test
    fun `le compte de versets est toujours au pluriel`() {
        // La source n'écrit jamais « 1 verset » : la plus courte sourate du Coran en compte
        // trois, donc le singulier ne se présente pas.
        assertEquals("3 versets", QuranText.verseCount(3))
        assertEquals("286 versets", QuranText.verseCount(286))
    }

    @Test
    fun `le libelle d'ouverture nomme la ligne`() {
        assertEquals("Ouvrir Al-Baqara", QuranText.open("Al-Baqara"))
        assertEquals("Ouvrir Juz’ 30", QuranText.open("Juz’ 30"))
    }

    @Test
    fun `les mots de la liste vide et du pied sont ceux de la source`() {
        assertEquals("Aucun résultat.", QuranText.EMPTY)
        assertEquals("Coran avec règles de Tajwid", QuranText.TAJWID_TITLE)
        assertEquals(
            "Organisation par couleurs pour faciliter votre lecture et votre apprentissage.",
            QuranText.TAJWID_BODY,
        )
        assertEquals("Dernière lecture", QuranText.LAST_READ)
        assertEquals("Le Coran", QuranText.TITLE)
    }

    @Test
    fun `le titre de l'ecran et celui de la carte du pied different`() {
        // « Le Coran » est le titre de la bande d'en-tête ; « Coran avec règles de Tajwid » celui
        // de la carte du pied. Les confondre ferait porter deux fois le même titre au même écran.
        assertFalse(QuranText.TITLE == QuranText.TAJWID_TITLE)
    }
}
