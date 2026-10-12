package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.DailyContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Les mots des contenus du jour, et les règles qui les assemblent.
 *
 * ## Ce que ce fichier défend
 *
 * Deux choses, et aucune ne lève quand elle est fausse :
 *
 *  - **deux textes pour deux contextes.** Le carrousel de l'accueil annonce « Chargement du contenu
 *    du jour… » et le pied de l'écran de liste « Chargement… ». Les confondre ferait dire au pied
 *    de liste qu'il charge « le contenu du jour » alors qu'il charge une page **filtrée**, et à la
 *    première page qu'elle charge une suite qui n'existe pas encore ;
 *  - **un repli qui nomme le bon objet.** Un contenu sans titre s'annonce « Ouvrir ce contenu », et
 *    non « Ouvrir Rappel » : le second nommerait un **type** là où la personne entend un contenu.
 *    Et le titre d'une carte se replie sur le nom du **type** — c'est l'inverse, et les deux
 *    chemins se croisent.
 *
 * ## Pourquoi il passe par les fonctions, et non par les constantes
 *
 * Les constantes sont lues **à travers** les fonctions qui les choisissent — [DailyContentsText.tabLabel],
 * `cardTitle`, `openLabel`… —, parce que c'est le chemin que les écrans empruntent. Les quelques
 * contrôles qui portent directement sur une constante visent des **relations** que la constante
 * seule peut casser : deux textes qui doivent différer, une espace qui doit être là, une flèche
 * qui doit aller dans le bon sens.
 *
 * ## Ce qu'il ne mesure pas
 *
 * Le rendu : les écrans sont des `@Composable`, et le projet n'a pas d'outillage de test
 * d'interface. Les contrôles de forme de `feature:home` et `feature:dailycontents` tiennent le
 * branchement, et le banc du renderer tient la **composition** du texte partagé.
 */
class DailyContentsTextTest {

    // ------------------------------------------------------------------
    // Le titre d'une carte, et son repli
    // ------------------------------------------------------------------

    @Test
    fun `le titre d'une carte est celui du contenu quand il en porte un`() {
        assertEquals(
            "Le pardon",
            DailyContentsText.cardTitle("Le pardon", DailyContentType.INVOCATION),
        )
    }

    @Test
    fun `un contenu sans titre se replie sur le nom de son type`() {
        assertEquals(DailyContentsText.CARD_INVOCATION, DailyContentsText.cardTitle(null, DailyContentType.INVOCATION))
        assertEquals(DailyContentsText.CARD_REMINDER, DailyContentsText.cardTitle(null, DailyContentType.REMINDER))
    }

    @Test
    fun `un titre vide se replie comme un titre absent`() {
        // `title?.takeIf { it.isNotEmpty() }` de l'original. Un titre vide n'est pas un titre : le
        // garder afficherait un en-tete de carte sans rien dedans, et rien ne le signalerait.
        assertEquals(
            DailyContentsText.CARD_REMINDER,
            DailyContentsText.cardTitle("", DailyContentType.REMINDER),
            "Un titre vide doit se replier sur le nom du type, comme un titre absent.",
        )
    }

    @Test
    fun `le nom du type suit le contenu, et les deux sont distincts`() {
        assertEquals(DailyContentsText.CARD_REMINDER, DailyContentsText.cardTypeName(DailyContentType.REMINDER))
        assertEquals(DailyContentsText.CARD_INVOCATION, DailyContentsText.cardTypeName(DailyContentType.INVOCATION))
        assertNotEquals(
            DailyContentsText.CARD_REMINDER,
            DailyContentsText.CARD_INVOCATION,
            "Les deux replis ne peuvent pas porter le meme mot : la fiche nommerait un rappel la " +
                "ou elle montre une invocation.",
        )
    }

    // ------------------------------------------------------------------
    // Les deux onglets
    // ------------------------------------------------------------------

    @Test
    fun `les deux onglets portent leur glyphe, et different`() {
        // Les glyphes sont ceux de l'original — `☀` et `☾` —, et ils ne sont pas decoratifs : ce
        // sont eux qui distinguent les deux onglets d'un coup d'oeil. Deux libelles sans glyphe
        // seraient lisibles, mais l'ecran perdrait ce que la source a choisi.
        assertTrue(DailyContentsText.TAB_REMINDER.startsWith("☀"), "L'onglet rappel a perdu son glyphe.")
        assertTrue(DailyContentsText.TAB_INVOCATION.startsWith("☾"), "L'onglet invocation a perdu son glyphe.")
        assertNotEquals(DailyContentsText.TAB_REMINDER, DailyContentsText.TAB_INVOCATION)
    }

    @Test
    fun `le libelle d'un onglet suit le type`() {
        assertEquals(DailyContentsText.TAB_REMINDER, DailyContentsText.tabLabel(DailyContentType.REMINDER))
        assertEquals(DailyContentsText.TAB_INVOCATION, DailyContentsText.tabLabel(DailyContentType.INVOCATION))
    }

    // ------------------------------------------------------------------
    // L'ouverture d'une fiche
    // ------------------------------------------------------------------

    @Test
    fun `le libelle d'ouverture nomme le contenu, et jamais son type`() {
        // C'est `` `Ouvrir ${item.title ?? 'ce contenu'}` `` de l'original. Le repli est le **mot**
        // de l'ecran, et non le nom du type : « Ouvrir Rappel » nommerait un type la ou la
        // personne entend un contenu — et c'est le piege, puisque `cardTitle` fait exactement
        // l'inverse.
        assertEquals("Ouvrir Le pardon", DailyContentsText.openLabel("Le pardon"))
        assertTrue(
            DailyContentsText.openLabel(null).endsWith(DailyContentsText.CARD_FALLBACK),
            "Un contenu sans titre doit s'annoncer « Ouvrir ce contenu ».",
        )
        assertFalse(
            DailyContentsText.openLabel(null).endsWith(DailyContentsText.CARD_REMINDER),
            "Le libelle d'ouverture ne doit pas nommer un type : « Ouvrir Rappel » designerait " +
                "une categorie la ou la personne entend un contenu.",
        )
    }

    @Test
    fun `un titre vide donne le meme libelle d'ouverture qu'un titre absent`() {
        assertEquals(DailyContentsText.openLabel(null), DailyContentsText.openLabel(""))
    }

    // ------------------------------------------------------------------
    // La bascule des favoris
    // ------------------------------------------------------------------

    @Test
    fun `la bascule des favoris dit ce que l'appui fera`() {
        // Le libelle annonce le **geste**, et non l'etat : un contenu deja en favori propose de le
        // retirer. L'inverse ferait annoncer « Ajouter aux favoris » sur un contenu deja garde, et
        // un lecteur d'ecran enverrait la personne au mauvais geste.
        assertEquals(DailyContentsText.FAVORITE_REMOVE, DailyContentsText.favoriteLabel(enabled = true))
        assertEquals(DailyContentsText.FAVORITE_ADD, DailyContentsText.favoriteLabel(enabled = false))
    }

    @Test
    fun `le glyphe des favoris est plein quand il est garde, vide sinon`() {
        assertEquals(DailyContentsText.FAVORITE_ON, DailyContentsText.favoriteGlyph(enabled = true))
        assertEquals(DailyContentsText.FAVORITE_OFF, DailyContentsText.favoriteGlyph(enabled = false))
        assertNotEquals(
            DailyContentsText.FAVORITE_ON,
            DailyContentsText.FAVORITE_OFF,
            "Les deux glyphes doivent differer : un favori garde et un favori propose ne peuvent " +
                "pas se lire pareil.",
        )
    }

    // ------------------------------------------------------------------
    // Les etats vides
    // ------------------------------------------------------------------

    @Test
    fun `les deux phrases de liste vide nomment le type filtre`() {
        // L'ecran a deux onglets : une phrase qui ne les distingue pas ferait croire que la base
        // est vide alors que l'autre onglet est plein.
        assertEquals(DailyContentsText.EMPTY_REMINDER, DailyContentsText.emptyListText(DailyContentType.REMINDER))
        assertEquals(DailyContentsText.EMPTY_INVOCATION, DailyContentsText.emptyListText(DailyContentType.INVOCATION))
        assertNotEquals(DailyContentsText.EMPTY_REMINDER, DailyContentsText.EMPTY_INVOCATION)
    }

    @Test
    fun `aucune phrase d'ecran vide n'est celle du carrousel`() {
        // L'accueil dit « Aucun contenu disponible aujourd'hui. » ; la liste dit le **type**
        // filtre. Confondre les deux ferait dire a un onglet plein que rien n'est publie.
        assertNotEquals(DailyContentsText.HOME_EMPTY, DailyContentsText.EMPTY_REMINDER)
        assertNotEquals(DailyContentsText.HOME_EMPTY, DailyContentsText.EMPTY_INVOCATION)
    }

    // ------------------------------------------------------------------
    // La ligne de provenance
    // ------------------------------------------------------------------

    @Test
    fun `la ligne de provenance joint la reference quand elle existe`() {
        // C'est `[source, reference].filter(Boolean).join(' · ')` de l'original, ecrit une seule
        // fois pour les trois endroits qui l'affichent.
        assertEquals("Sahih · 1:1", DailyContentsText.sourceLine("Sahih", "1:1"))
    }

    @Test
    fun `la ligne de provenance ecarte une partie absente ou vide`() {
        // Le filtre porte sur **les deux** parties : une reference absente laisserait « Sahih · »
        // se lire comme une donnee manquante, et une source vide produirait un separateur orphelin.
        assertEquals("Sahih", DailyContentsText.sourceLine("Sahih", null))
        assertEquals("Sahih", DailyContentsText.sourceLine("Sahih", ""))
        assertEquals("1:1", DailyContentsText.sourceLine("", "1:1"))
        assertEquals("", DailyContentsText.sourceLine("", null))
    }

    // ------------------------------------------------------------------
    // Les relations entre constantes
    // ------------------------------------------------------------------

    @Test
    fun `le chargement de la liste n'est pas celui du carrousel`() {
        // Deux textes pour deux contextes, et c'est le point : le pied de liste charge une page
        // **filtree**, le carrousel charge le contenu du jour. Les confondre ferait annoncer a
        // l'un ce que l'autre fait.
        assertNotEquals(
            DailyContentsText.HOME_LOADING,
            DailyContentsText.LIST_LOADING,
            "Le chargement de la liste et celui du carrousel ne peuvent pas etre le meme texte.",
        )
        assertTrue(DailyContentsText.LIST_LOADING.endsWith("…"), "Le point de suspension a disparu.")
        assertTrue(DailyContentsText.HOME_LOADING.endsWith("…"), "Le point de suspension a disparu.")
    }

    @Test
    fun `les deux commandes de navigation vont dans le bon sens`() {
        // « ‹ Retour » remonte, « Ouvrir la fiche › » descend. Inverser les deux chevrons ferait
        // annoncer l'inverse du geste, et rien a l'ecran ne le dirait.
        assertTrue(DailyContentsText.BACK.startsWith("‹"), "Le chevron de retour a change de sens.")
        assertTrue(DailyContentsText.OPEN_SHEET.endsWith("›"), "Le chevron d'ouverture a change de sens.")
    }

    @Test
    fun `le prefixe de provenance porte son espace`() {
        // La fiche ecrit `SOURCE_PREFIX + sourceLine(...)`, sans separateur intermediaire : une
        // espace oubliee collerait le mot au nom de la source, et « Source :Sahih » se lirait
        // comme une faute de frappe plutot que comme un prefixe.
        assertTrue(
            DailyContentsText.SOURCE_PREFIX.endsWith(" "),
            "Le prefixe de provenance doit porter son espace : la fiche ne l'ajoute pas.",
        )
    }

    @Test
    fun `la phrase de panne du depot ne nomme pas le reseau, celle du carrousel si`() {
        // Deux phrases, deux origines. `GENERIC_FAILURE` est publiee par le **depot**, qui ne sait
        // pas si la requete a echoue faute de reseau ou sur un refus du serveur — nommer une cause
        // qu'on n'a pas mesuree enverrait chercher au mauvais endroit. `HOME_OFFLINE` est la phrase
        // **fixe** de l'original, qui nomme « hors connexion » une cause qu'il ne connait pas non
        // plus : la reproduire est une fidelite assumee, et confondre les deux ne l'est pas.
        assertFalse(
            DailyContentsText.GENERIC_FAILURE.contains("connexion"),
            "La phrase publiee par le depot ne doit pas nommer le reseau : elle ne l'a pas mesure.",
        )
        assertTrue(
            DailyContentsText.HOME_OFFLINE.contains("connexion"),
            "La phrase fixe du carrousel est celle de l'original, et elle nomme la connexion.",
        )
    }

    @Test
    fun `le titre du carrousel suit le type, et un carrousel vide coiffe un rappel`() {
        // `entries[index]?.type === 'invocation' ? 'Invocation du jour' : 'Rappel du jour'` : le
        // `else` couvre l'absence d'entree comme le type rappel. Un carrousel vide est donc coiffe
        // de « Rappel du jour », ce qui vaut mieux qu'un titre vide — la place du bloc est
        // annoncee.
        assertEquals(DailyContentsText.HOME_INVOCATION, DailyContentsText.homeTitle(DailyContentType.INVOCATION))
        assertEquals(DailyContentsText.HOME_REMINDER, DailyContentsText.homeTitle(DailyContentType.REMINDER))
        assertEquals(DailyContentsText.HOME_REMINDER, DailyContentsText.homeTitle(null))
    }

    @Test
    fun `les deux libelles d'ouverture du carrousel nomment le type`() {
        assertNotEquals(DailyContentsText.OPEN_REMINDER, DailyContentsText.OPEN_INVOCATION)
        assertTrue(DailyContentsText.OPEN_REMINDER.contains("rappel"))
        assertTrue(DailyContentsText.OPEN_INVOCATION.contains("invocation"))
    }
}
