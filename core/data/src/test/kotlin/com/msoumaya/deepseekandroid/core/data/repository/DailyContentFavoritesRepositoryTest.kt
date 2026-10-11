package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.GuestFavoritesStore
import com.msoumaya.deepseekandroid.core.domain.DailyContentFavorites
import com.msoumaya.deepseekandroid.core.domain.DailyContentFavoritesText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Le dépôt des favoris, éprouvé contre une doublure — **sans réseau et sans appareil**.
 *
 * ## Ce qui est mesuré, et pourquoi cela ne peut pas l'être ailleurs
 *
 * Trois règles du dépôt n'ont **pas d'écran**, et aucune ne lève quand elle est fausse :
 *
 *  - **la fenêtre de trente secondes** — une fenêtre plus longue sert une liste périmée, plus
 *    courte relit sans arrêt ;
 *  - **le dédoublonnage des lectures concurrentes** — sans lui, la fiche et l'écran de liste
 *    demandent la liste au même instant et deux requêtes identiques partent ;
 *  - **la voie du visiteur** — `anon` n'a aucun `grant` sur `content_favorites`. Un visiteur dont
 *    le geste partirait au serveur recevrait un refus, et son cœur ne se remplirait **jamais** :
 *    le défaut le plus silencieux de cette tranche, puisqu'il ne se produit que sans compte.
 *
 * ## Ce qui est mesuré en plus, et qui n'est pas dans la liste de l'original
 *
 * Le **partage par compte** n'est pas une règle que l'original écrit : elle vient de la clé de son
 * cache (`userId ?? 'guest'`). Le contrôle existe parce que la faute inverse — un cache à clé
 * unique — serait invisible à l'écran tant qu'un seul compte est ouvert, et afficherait les
 * favoris d'un compte sous un autre le jour où deux personnes se relaient sur le même appareil.
 *
 * ## Les doublures d'attente
 *
 * Deux contrôles tiennent une lecture **en vol** pour en lancer une seconde : sans cela, le
 * dédoublonnage ne se mesurerait qu'en le relisant dans le source. [FakeFavoriteSource.probe] et
 * [FakeFavoriteSource.gate] sont là pour cela, et ils sont employés par les deux seuls contrôles
 * qui en ont besoin.
 */
class DailyContentFavoritesRepositoryTest {

    /** L'horloge du banc : le dépôt la lit, et les tests la font avancer. */
    private var instant = 1_000_000L

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("daily-content-favorites-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun depot(
        source: FakeFavoriteSource?,
        owner: String?,
        guest: GuestFavoritesStore = GuestFavoritesStore(root),
    ) = DailyContentFavoritesRepository(
        source = source,
        guest = guest,
        session = FakeOwners(owner),
        now = { instant },
    )

    // ------------------------------------------------------------------ la voie du visiteur

    @Test
    fun `un visiteur lit sa liste locale`() = runTest {
        // Le visiteur n'a pas de compte, donc pas de ligne : sa liste est sur l'appareil. Le
        // serveur est pourtant configure ici, et le controle mesure qu'il n'est **pas** appele.
        val api = FakeFavoriteSource()
        GuestFavoritesStore(root).replace(setOf("x", "y"))

        val ids = depot(api, owner = null).ids()

        assertEquals(setOf("x", "y"), ids)
        assertEquals(0, api.listCalls)
    }

    @Test
    fun `un visiteur travaille sans projet configure`() = runTest {
        // La voie du visiteur ne touche pas au reseau, donc elle doit fonctionner alors qu'aucune
        // source n'existe. C'est la difference de fond avec la voie du compte, ou l'absence de
        // projet est un echec nomme.
        GuestFavoritesStore(root).replace(setOf("x"))

        assertEquals(setOf("x"), depot(source = null, owner = null).ids())
    }

    @Test
    fun `un visiteur ecrit dans son document local`() = runTest {
        // Le controle lit le fichier par une **seconde** instance : si l'ecriture n'avait touche
        // que la memoire du magasin, la relecture ne la verrait pas, et un favori pose par un
        // visiteur disparaitrait au redemarrage sans que rien ne le signale.
        val api = FakeFavoriteSource()

        depot(api, owner = null).setFavorite(id = "x", enabled = true)

        assertEquals(setOf("x"), GuestFavoritesStore(root).current())
        assertEquals(0, api.listCalls)
        assertEquals(emptyList(), api.added)
    }

    // ------------------------------------------------------------------ la voie du compte

    @Test
    fun `un compte lit sa liste au serveur`() = runTest {
        val api = FakeFavoriteSource()
        api.lists["aaaa"] = setOf("x")

        assertEquals(setOf("x"), depot(api, owner = "aaaa").ids())
        assertEquals(1, api.listCalls)
    }

    @Test
    fun `un compte sans favori rend une liste vide et non une erreur`() = runTest {
        // Une liste vide est une **information** : ce compte n'a rien mis en favori. C'est
        // exactement ce que la voie du compte peut affirmer, parce que la lecture a abouti.
        assertEquals(emptySet(), depot(FakeFavoriteSource(), owner = "aaaa").ids())
    }

    @Test
    fun `une liste fraiche est reutilisee`() = runTest {
        val api = FakeFavoriteSource()
        val sujet = depot(api, owner = "aaaa")

        sujet.ids()
        instant += DailyContentFavorites.CACHE_MILLIS - 1
        sujet.ids()

        assertEquals(1, api.listCalls)
    }

    @Test
    fun `une liste perimee est relue`() = runTest {
        val api = FakeFavoriteSource()
        val sujet = depot(api, owner = "aaaa")

        sujet.ids()
        instant += DailyContentFavorites.CACHE_MILLIS
        sujet.ids()

        assertEquals(2, api.listCalls)
    }

    @Test
    fun `sans projet un compte ne peut pas lire ses favoris`() = runTest {
        // La branche est inatteignable aujourd'hui — un proprietaire n'existe que si une session a
        // ete ouverte, donc si un projet est configure —, et elle est ecrite quand meme. Rendre
        // une liste vide ferait dire a l'ecran « ce compte n'a aucun favori », ce qui est une
        // affirmation, et une affirmation fausse le jour ou la configuration changerait.
        val erreur = assertFailsWith<IllegalStateException> {
            depot(source = null, owner = "aaaa").ids()
        }

        assertEquals(DailyContentFavoritesText.NO_PROJECT, erreur.message)
    }

    // ------------------------------------------------------------------ le dedoublonnage

    @Test
    fun `deux lectures concurrentes ne font qu'une requete`() = runTest {
        // La fiche et l'ecran de liste demandent la liste au meme instant. L'original ne fait
        // partir qu'une requete (`favoriteRequests`), et le controle tient la premiere **dans** la
        // source avant de lancer la seconde : sans cette tenue, la seconde partirait apres que la
        // premiere ait rempli le cache, et le controle serait vert sans rien prouver.
        val api = FakeFavoriteSource()
        api.lists["aaaa"] = setOf("x")
        val entre = CompletableDeferred<Unit>()
        val porte = CompletableDeferred<Unit>()
        api.probe = { entre.complete(Unit) }
        api.gate = porte
        val sujet = depot(api, owner = "aaaa")

        val premier = backgroundScope.async { sujet.ids() }
        entre.await()
        val second = backgroundScope.async { sujet.ids() }
        porte.complete(Unit)

        assertEquals(setOf("x"), premier.await())
        assertEquals(setOf("x"), second.await())
        assertEquals(1, api.listCalls)
    }

    @Test
    fun `un echec est partage avec les lecteurs concurrents`() = runTest {
        // **Le controle qui distingue le dedoublonnage d'une simple serialisation.** Une lecture
        // qui se contenterait de faire la queue derriere un verrou reessaierait apres l'echec du
        // premier, et compterait deux lectures. L'original, lui, fait partager la **meme**
        // reponse : les deux appelants recoivent l'echec, et une seule requete est partie.
        val api = FakeFavoriteSource()
        val entre = CompletableDeferred<Unit>()
        val porte = CompletableDeferred<Unit>()
        api.probe = { entre.complete(Unit) }
        api.gate = porte
        api.failList = IllegalStateException("table injoignable")
        val sujet = depot(api, owner = "aaaa")

        val premier = backgroundScope.async { runCatching { sujet.ids() } }
        entre.await()
        val second = backgroundScope.async { runCatching { sujet.ids() } }
        porte.complete(Unit)

        assertTrue(premier.await().isFailure, "Le premier lecteur doit recevoir l'echec.")
        assertTrue(second.await().isFailure, "Le second lecteur doit recevoir le meme echec.")
        assertEquals(1, api.listCalls)
    }

    @Test
    fun `un echec de lecture n'est pas retenu`() = runTest {
        // Le pendant du controle precedent : l'echec ne remplit pas le cache, donc la lecture
        // suivante **reessaie**. Sans cela, un premier echec reseau condamnerait la fiche a
        // afficher un cœur vide jusqu'a la fin de la fenetre.
        val api = FakeFavoriteSource()
        api.failList = IllegalStateException("table injoignable")
        val sujet = depot(api, owner = "aaaa")

        assertFailsWith<IllegalStateException> { sujet.ids() }

        api.failList = null
        api.lists["aaaa"] = setOf("x")
        assertEquals(setOf("x"), sujet.ids())
        assertEquals(2, api.listCalls)
    }

    // ------------------------------------------------------------------ le geste

    @Test
    fun `poser un favori l'ecrit au serveur et remplit le cache`() = runTest {
        // L'ordre des trois temps du depot se lit ici : la liste est lue, l'ecriture a lieu, le
        // cache est remplace. La lecture qui suit ne fait donc **aucune** requete, et rend la
        // liste neuve — c'est ce qui permet a l'ecran de se mettre a jour sans relire.
        val api = FakeFavoriteSource()
        val sujet = depot(api, owner = "aaaa")

        sujet.setFavorite(id = "x", enabled = true)

        assertEquals(listOf("aaaa" to "x"), api.added)
        assertEquals(setOf("x"), sujet.ids())
        assertEquals(1, api.listCalls)
    }

    @Test
    fun `retirer un favori l'efface au serveur`() = runTest {
        val api = FakeFavoriteSource()
        api.lists["aaaa"] = setOf("x", "y")
        val sujet = depot(api, owner = "aaaa")

        sujet.setFavorite(id = "x", enabled = false)

        assertEquals(listOf("aaaa" to "x"), api.removed)
        assertEquals(setOf("y"), sujet.ids())
    }

    @Test
    fun `un echec d'ecriture laisse le cache inchange`() = runTest {
        // **Le controle qui tient l'ordre des trois temps.** Si le cache etait remplace avant
        // l'ecriture, le cœur s'afficherait rempli alors que le serveur n'a rien recu — et il se
        // viderait au prochain affichage, sans que l'echec ait ete signale nulle part. Le compte
        // des lectures est ce qui distingue les deux implementations : le cache doit porter
        // l'ancienne liste, donc la lecture qui suit ne doit faire aucune requete.
        val api = FakeFavoriteSource()
        api.failWrite = IllegalStateException("table injoignable")
        val sujet = depot(api, owner = "aaaa")

        assertFailsWith<IllegalStateException> { sujet.setFavorite(id = "x", enabled = true) }

        api.failWrite = null
        assertEquals(emptySet(), sujet.ids())
        assertEquals(1, api.listCalls)
    }

    // ------------------------------------------------------------------ le partage par compte

    @Test
    fun `deux comptes ne partagent pas leurs favoris`() = runTest {
        // Le defaut que ce controle tient : un cache a cle unique afficherait la liste du premier
        // compte sous le second. Il serait invisible a l'ecran tant qu'une seule personne utilise
        // l'appareil, et se produirait exactement quand deux personnes se le relaient.
        val api = FakeFavoriteSource()
        api.lists["aaaa"] = setOf("x")
        api.lists["bbbb"] = setOf("y")
        val session = FakeOwners("aaaa")
        val sujet = DailyContentFavoritesRepository(
            source = api,
            guest = GuestFavoritesStore(root),
            session = session,
            now = { instant },
        )

        assertEquals(setOf("x"), sujet.ids())

        session.setOwner("bbbb")
        assertEquals(setOf("y"), sujet.ids())
        assertEquals(2, api.listCalls)

        // Revenir sur le premier compte dans la fenetre rend sa liste retenue, sans requete : la
        // carte n'est pas videe au changement de compte, comme celle de l'original.
        session.setOwner("aaaa")
        assertEquals(setOf("x"), sujet.ids())
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `le geste d'un compte ne touche pas la liste du visiteur`() = runTest {
        // Le couple (compte, visiteur) est celui qui se ressemble le plus et se confond le plus
        // facilement : les deux passent par le meme cache, et seule la cle les separe. Le controle
        // pose un favori sous un compte, puis lit sous le visiteur.
        val api = FakeFavoriteSource()
        api.lists["aaaa"] = setOf("x")
        val session = FakeOwners("aaaa")
        val sujet = DailyContentFavoritesRepository(
            source = api,
            guest = GuestFavoritesStore(root),
            session = session,
            now = { instant },
        )

        sujet.setFavorite(id = "z", enabled = true)

        session.setOwner(null)
        assertEquals(emptySet(), sujet.ids())
    }
}
