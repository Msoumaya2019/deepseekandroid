package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Les règles des favoris d'un contenu du jour, mesurées **sans réseau et sans appareil**.
 *
 * ## Ce que ce fichier éprouve, et pourquoi cela vaut un fichier
 *
 * Les deux règles de [DailyContentFavorites] sont courtes, et c'est précisément pour cela qu'elles
 * s'écrivent ici plutôt qu'ailleurs : elles se **paient en silence** quand elles sont fausses.
 * Une fenêtre plus longue que prévu sert une liste périmée ; une fenêtre plus courte que prévu
 * relit sans arrêt. Un ajout qui dupliquerait ferait afficher deux fois la même fiche dans la
 * liste des favoris — et une fiche dupliquée ne lève rien.
 *
 * ## Ce que ce fichier ne dit pas
 *
 * Il ne dit **rien** de ce qui se passe quand la fenêtre est franchie : la relecture, le
 * dédoublonnage et la voie du visiteur vivent dans `DailyContentFavoritesRepository`, et c'est
 * `DailyContentFavoritesRepositoryTest` qui les mesure. Ici, il n'y a que le calcul.
 */
class DailyContentFavoritesTest {

    // ------------------------------------------------------------------ la fenetre

    @Test
    fun `une liste lue a l'instant meme est fraiche`() {
        assertTrue(DailyContentFavorites.isFresh(at = 1_000L, now = 1_000L))
    }

    @Test
    fun `une liste reste fraiche juste avant la fin de la fenetre`() {
        val at = 1_000L

        assertTrue(
            DailyContentFavorites.isFresh(at = at, now = at + DailyContentFavorites.CACHE_MILLIS - 1),
        )
    }

    @Test
    fun `une liste n'est plus fraiche a la fin de la fenetre`() {
        // La borne est **exclusive** : l'original ecrit `Date.now()-cached.at<30000`, donc a
        // exactement trente secondes la liste est relue. Le controle epingle la borne elle-meme,
        // et non un point au hasard dans la fenetre.
        val at = 1_000L

        assertFalse(
            DailyContentFavorites.isFresh(at = at, now = at + DailyContentFavorites.CACHE_MILLIS),
        )
    }

    @Test
    fun `une horloge qui recule ne perime pas la liste`() {
        // **Le cas que la difference traite autrement qu'une echeance.** L'original soustrait les
        // deux instants ; une correction NTP ou un changement de fuseau peut faire passer `now`
        // sous `at`, et la difference devient negative — donc inferieure a la fenetre, donc
        // fraiche. C'est le sens sur : la liste lue juste avant est la bonne, et une relecture
        // n'apprendrait rien. Ecrire `at + CACHE_MILLIS > now` a la place donnerait l'inverse, et
        // ce controle tomberait.
        assertTrue(DailyContentFavorites.isFresh(at = 10_000L, now = 1_000L))
    }

    // ------------------------------------------------------------------ le geste

    @Test
    fun `ajouter un favori absent l'ajoute`() {
        assertEquals(
            setOf("a", "b"),
            DailyContentFavorites.toggled(setOf("a"), id = "b", enabled = true),
        )
    }

    @Test
    fun `ajouter un favori deja present ne le duplique pas`() {
        // L'original ecrit `[...new Set([...ids, id])]`, et le `Set` du portage rend la regle
        // **structurelle** : elle n'est plus une ligne qu'on pourrait retirer. Le controle existe
        // pour que la propriete soit nommee, pas parce qu'elle pourrait etre perdue par
        // inadvertance.
        assertEquals(
            setOf("a", "b"),
            DailyContentFavorites.toggled(setOf("a", "b"), id = "b", enabled = true),
        )
    }

    @Test
    fun `retirer un favori present le retire`() {
        assertEquals(
            setOf("a"),
            DailyContentFavorites.toggled(setOf("a", "b"), id = "b", enabled = false),
        )
    }

    @Test
    fun `retirer un favori absent ne retire rien d'autre`() {
        // C'est ce qui permet au depot de calculer le suivant a partir d'une liste qui peut avoir
        // jusqu'a trente secondes de retard : le geste est sur meme quand la liste ne l'est pas.
        assertEquals(
            setOf("a", "b"),
            DailyContentFavorites.toggled(setOf("a", "b"), id = "z", enabled = false),
        )
    }

    @Test
    fun `le geste ne modifie pas la liste recue`() {
        // Le `Set` recu est celui du cache. Le modifier sur place ferait changer le cache **avant**
        // que l'ecriture serveur n'ait eu lieu — exactement ce que l'ordre des trois temps du
        // depot interdit.
        val initial = setOf("a")

        DailyContentFavorites.toggled(initial, id = "b", enabled = true)

        assertEquals(setOf("a"), initial)
    }
}
