package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.domain.DailyContentMedia
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Le dépôt des médias, éprouvé contre une doublure — **sans réseau, sans compartiment et sans
 * appareil**.
 *
 * ## Ce qui est mesuré, et pourquoi cela ne peut pas l'être ailleurs
 *
 * Le dépôt porte une règle qui n'a **pas d'écran** : une adresse signée est réutilisée pendant
 * cinquante-cinq minutes. Quand elle est fausse, rien ne lève — resigner à chaque affichage
 * produit une application qui marche, et une facture de signatures que personne ne regarde ;
 * servir une adresse expirée produit une vignette qui disparaît **après** un rendu réussi. Les
 * deux se mesurent ici, en comptant les signatures et en avançant une horloge injectée.
 *
 * ## L'horloge est injectée, et c'est la condition du contrôle
 *
 * Le dépôt reçoit `now`. Sans cela, la fenêtre ne se mesurerait qu'en attendant cinquante-cinq
 * minutes, donc jamais : le contrôle se contenterait de vérifier que la signature a lieu, ce qui
 * est vrai dans tous les cas. L'horloge est ici une variable, et le test la fait avancer de la
 * durée exacte qu'il veut éprouver.
 *
 * ## Ce que ce fichier ne dit pas
 *
 * Il ne dit rien de la **forme** de l'adresse signée : c'est le compartiment qui la produit, et la
 * doublure ne fait que préfixer le chemin. Il ne dit rien non plus de la durée demandée au
 * serveur — c'est `DailyContentMedia.SIGNED_URL_SECONDS`, éprouvée par le banc du domaine, et
 * c'est `SupabaseDailyContentMediaSource` qui la passe au SDK.
 */
class DailyContentMediaRepositoryTest {

    /** Le projet partagé, tel que le conteneur le reçoit. */
    private val projet = "https://npbwnvrqmajwqtnncuyv.supabase.co"

    private val racine = DailyContentMedia.publicRoot(projet)

    /** Le chemin d'un objet réel, tel que `chooseAndUploadContentMedia` le compose. */
    private val chemin = "11111111-2222-3333-4444-555555555555/1700000000000-ab12cd.png"

    private val adresse = racine + chemin

    /** L'horloge du banc : le dépôt la lit, et les tests la font avancer. */
    private var instant = 1_000_000L

    private fun nouveauDepot(source: FakeMediaSource?) = DailyContentMediaRepository(
        source = source,
        now = { instant },
    )

    private fun source() = FakeMediaSource(root = racine)

    // ------------------------------------------------------------------ les quatre sorties

    @Test
    fun `une adresse absente reste absente et ne signe rien`() = runTest {
        // La moitié des cas : `image_url` et `audio_url` sont nullables, et un rappel sans
        // vignette ne doit produire aucune requête — pas même une requête refusée.
        val api = source()

        assertNull(nouveauDepot(api).resolve(null))
        assertEquals(0, api.signatures)
    }

    @Test
    fun `une adresse du compartiment est signee`() = runTest {
        val api = source()

        assertEquals("https://signe.test/$chemin", nouveauDepot(api).resolve(adresse))
        assertEquals(listOf(chemin), api.pathsAsked)
    }

    @Test
    fun `une adresse etrangere est rendue telle quelle`() = runTest {
        // L'original ecrit `if(!path)return url`. Le controle mesure la consequence : aucune
        // signature ne part pour une adresse qui n'est pas la notre — un serveur distant n'a rien
        // a signer, et demander une signature pour lui ferait echouer un affichage qui marchait.
        val api = source()
        val externe = "https://exemple.test/vignette.png"

        assertEquals(externe, nouveauDepot(api).resolve(externe))
        assertEquals(0, api.signatures)
    }

    @Test
    fun `une adresse d'un autre compartiment est rendue telle quelle`() = runTest {
        // Le cas reel : la meme table range aussi des adresses vers `recitations`.
        val api = source()
        val ailleurs = "$projet/storage/v1/object/public/recitations/moi-0000/voix.m4a"

        assertEquals(ailleurs, nouveauDepot(api).resolve(ailleurs))
        assertEquals(0, api.signatures)
    }

    @Test
    fun `sans source l'adresse est rendue telle quelle`() = runTest {
        // Le mode hors ligne : aucun projet configure. Il n'y a pas de racine pour reconnaitre le
        // compartiment, donc pas de decision a prendre — et surtout pas un refus, qui retirerait
        // de l'ecran une adresse que l'original aurait gardee.
        assertEquals(adresse, nouveauDepot(source = null).resolve(adresse))
    }

    // ------------------------------------------------------------------ la fenetre de reutilisation

    @Test
    fun `une signature fraiche est reutilisee`() = runTest {
        // Deux affichages rapproches de la meme fiche ne font qu'une signature. Sans cette regle,
        // une liste de trente vignettes produirait trente signatures identiques.
        val api = source()
        val depot = nouveauDepot(api)

        val premiere = depot.resolve(adresse)
        instant += DailyContentMedia.CACHE_MILLIS - 1
        val seconde = depot.resolve(adresse)

        assertEquals(1, api.signatures)
        assertEquals(premiere, seconde)
    }

    @Test
    fun `une signature perimee est refaite`() = runTest {
        // La borne est **exclusive**, comme `isFresh` : a exactement la duree de la fenetre, la
        // signature est refaite. Le controle epingle la borne, et non un point au hasard.
        val api = source()
        val depot = nouveauDepot(api)

        depot.resolve(adresse)
        instant += DailyContentMedia.CACHE_MILLIS
        depot.resolve(adresse)

        assertEquals(2, api.signatures)
    }

    @Test
    fun `deux adresses distinctes ont chacune leur signature`() = runTest {
        // La memoire est indexee par **adresse**, et non par un seul emplacement : une seconde
        // adresse ne doit pas prendre la place de la premiere, sinon revenir sur la premiere
        // fiche resignerait — et c'est exactement ce que fait un carrousel qu'on parcourt.
        val api = source()
        val depot = nouveauDepot(api)
        val autre = racine + "22222222-3333-4444-5555-666666666666/1700000000001-ef34ab.mp3"

        depot.resolve(adresse)
        depot.resolve(autre)
        depot.resolve(adresse)

        assertEquals(2, api.signatures)
        assertEquals(listOf(chemin, "22222222-3333-4444-5555-666666666666/1700000000001-ef34ab.mp3"), api.pathsAsked)
    }

    // ------------------------------------------------------------------ l'echec

    @Test
    fun `l'echec d'une signature remonte`() = runTest {
        // L'original ecrit `if(error)throw error`, et la consequence compte : la fiche d'un
        // contenu affiche alors un avis, au lieu de poser dans un lecteur une adresse qui ne
        // mene nulle part.
        val api = source()
        api.failSignedUrl = IllegalStateException("compartiment injoignable")

        assertFailsWith<IllegalStateException> { nouveauDepot(api).resolve(adresse) }
    }

    @Test
    fun `un echec n'est pas retenu`() = runTest {
        // Le controle qui distingue « le cache n'est pas ecrit » de « le cache est ecrit avec une
        // valeur vide ». Si l'echec laissait une entree, la resolution suivante rendrait un
        // resultat sans jamais reessayer — et la vignette resterait absente jusqu'a l'expiration
        // d'une signature qui n'existe pas.
        val api = source()
        api.failSignedUrl = IllegalStateException("compartiment injoignable")
        val depot = nouveauDepot(api)

        assertFailsWith<IllegalStateException> { depot.resolve(adresse) }

        api.failSignedUrl = null
        assertEquals("https://signe.test/$chemin", depot.resolve(adresse))
        assertEquals(2, api.signatures)
    }

    @Test
    fun `la fenetre est comptee depuis la signature et non depuis la lecture`() = runTest {
        // L'echeance est posee a `now() + CACHE_MILLIS` au moment ou la signature **aboutit**, et
        // non a celui ou la resolution commence. La doublure fait donc avancer l'horloge
        // **pendant** la signature, puis le test place l'instant juste apres l'echeance qu'aurait
        // posee un calcul fait trop tot — et juste avant celle qui a reellement ete posee. Si
        // l'echeance avait ete calculee avant l'appel, la seconde resolution resignerait, et le
        // compte tomberait a deux.
        val api = source()
        val depot = nouveauDepot(api)
        api.probe = { instant += RETARD_SIGNATURE }

        depot.resolve(adresse)
        instant += DailyContentMedia.CACHE_MILLIS - RETARD_SIGNATURE / 2
        depot.resolve(adresse)

        assertEquals(1, api.signatures)
    }

    private companion object {
        /**
         * Le retard que la doublure introduit **pendant** la signature, en millisecondes.
         *
         * Il n'est pas decoratif : c'est lui qui separe les deux echeances possibles — celle
         * posee avant l'appel et celle posee apres —, et donc ce qui rend le controle capable de
         * les distinguer. Un retard nul rendrait le test vert dans les deux cas.
         */
        const val RETARD_SIGNATURE = 10_000L
    }
}
