package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les règles des médias d'un contenu du jour, mesurées **sans réseau et sans appareil**.
 *
 * ## Ce que ce fichier éprouve, et pourquoi cela vaut un fichier
 *
 * [DailyContentMedia.pathOf] décide si une adresse rangée en base est **signable**. Quand elle se
 * trompe, rien ne lève : l'adresse est rendue telle quelle, l'application demande un objet privé
 * sans signature, et la vignette reste vide — ou l'écoute ne démarre pas. Le défaut est donc
 * **muet à l'exécution** et ne se voit que sur un appareil, avec un contenu dont l'image est
 * réellement hébergée dans le compartiment.
 *
 * C'est exactement ce que ce fichier remplace : quatre formes d'adresse qui doivent être refusées
 * ou reconnues, mesurées en quelques microsecondes.
 *
 * ## Le projet utilisé est celui du partage, et c'est délibéré
 *
 * Les adresses de ces tests sont composées sur `https://npbwnvrqmajwqtnncuyv.supabase.co` — le
 * projet partagé avec le client React Native —, plutôt que sur un nom inventé. La raison est que
 * [DailyContentMedia.publicRoot] **compose** une adresse au lieu de la demander au client : ce
 * fichier est donc le seul endroit où la forme obtenue est comparée à la forme réelle du projet,
 * et un nom inventé ne prouverait rien de plus.
 */
class DailyContentMediaTest {

    /** Le projet partagé, tel que `SupabaseConfig.PROJECT_URL` et `BuildConfig.SUPABASE_URL` le portent. */
    private val projet = "https://npbwnvrqmajwqtnncuyv.supabase.co"

    private val racine = DailyContentMedia.publicRoot(projet)

    // ------------------------------------------------------------------ la racine

    @Test
    fun `la racine est celle que getPublicUrl rend pour un chemin vide`() {
        // L'original lit la racine du client : `getPublicUrl('')`. Cette valeur est donc un
        // contrat avec le serveur, et non une commodité : c'est elle qui doit correspondre au
        // debut des adresses rangees en base. La barre oblique finale en fait partie.
        assertEquals(
            "https://npbwnvrqmajwqtnncuyv.supabase.co/storage/v1/object/public/daily-content-media/",
            racine,
        )
    }

    @Test
    fun `la racine porte le nom du compartiment declare par la migration`() {
        // Le nom est ecrit deux fois dans le depot — ici et dans `daily-content-media.sql`. Ce
        // controle les relie : renommer l'un sans l'autre ferait echouer la reconnaissance de
        // toutes les adresses, et l'application demanderait des objets prives sans signature.
        assertTrue(
            racine.endsWith("/storage/v1/object/public/${DailyContentMedia.BUCKET}/"),
            "La racine doit se terminer par le chemin public du compartiment " +
                "`${DailyContentMedia.BUCKET}`, barre oblique finale comprise.",
        )
    }

    // ------------------------------------------------------------------ le chemin reconnu

    @Test
    fun `une adresse du compartiment rend son chemin`() {
        val adresse = racine + "11111111-2222-3333-4444-555555555555/1700000000000-ab12cd.png"

        assertEquals(
            "11111111-2222-3333-4444-555555555555/1700000000000-ab12cd.png",
            DailyContentMedia.pathOf(adresse, racine),
        )
    }

    @Test
    fun `une adresse d'un autre compartiment est refusee`() {
        // Le cas reel : la meme table range aussi des adresses vers `recitations`. Les confondre
        // demanderait au serveur de signer un objet qui n'existe pas dans ce compartiment.
        val adresse = "$projet/storage/v1/object/public/recitations/moi-0000/voix.m4a"

        assertNull(DailyContentMedia.pathOf(adresse, racine))
    }

    @Test
    fun `une adresse d'un autre projet est refusee`() {
        // C'est le defaut que la racine construite sur `SupabaseConfig.PROJECT_URL` plutot que sur
        // l'adresse reellement configuree produirait **dans l'autre sens** : ici, un contenu du
        // jour dont la vignette aurait ete deposee dans un autre projet doit etre rendu tel quel,
        // et non signe contre le mauvais projet.
        val adresse = "https://un-autre-projet.supabase.co" +
            "/storage/v1/object/public/daily-content-media/x/y.png"

        assertNull(DailyContentMedia.pathOf(adresse, racine))
    }

    @Test
    fun `une adresse externe est refusee`() {
        assertNull(DailyContentMedia.pathOf("https://exemple.test/vignette.png", racine))
    }

    @Test
    fun `une adresse en clair est refusee`() {
        // La migration impose `like 'https://%'` sur les deux colonnes : une adresse `http` ne
        // peut pas y entrer. Le refus est ecrit quand meme, parce que c'est le meme `startsWith`
        // qui le produit, et qu'un jour ou la contrainte changerait, la regle ne changerait pas
        // avec elle sans qu'on le dise.
        val adresse = "http://npbwnvrqmajwqtnncuyv.supabase.co" +
            "/storage/v1/object/public/daily-content-media/x/y.png"

        assertNull(DailyContentMedia.pathOf(adresse, racine))
    }

    // ------------------------------------------------------------------ les deux refus limites

    @Test
    fun `la racine seule ne designe aucun objet`() {
        // L'original l'ecrit en deux temps : `startsWith` la reconnait, `split` ne trouve rien
        // apres le marqueur et rend `undefined`, et le `?? null` la replie sur `null` — que
        // l'appelant confond ensuite avec « hors du compartiment ». C'est l'adresse qu'un
        // enregistrement fautif, une vignette posee sans chemin, laisserait en base.
        assertNull(DailyContentMedia.pathOf(racine, racine))
    }

    @Test
    fun `un chemin qui contient le marqueur n'est pas tronque`() {
        // **L'ecart volontaire avec l'original, et il est mesure ici.** L'original ecrit
        // `url.split(marker)[1]`, qui rend le texte entre la **premiere** et la **seconde**
        // occurrence du marqueur : un chemin qui contiendrait le marqueur serait donc tronque a
        // cet endroit. `removePrefix` rend le chemin entier.
        //
        // Aucun depot ne peut produire un tel chemin — `chooseAndUploadContentMedia` compose
        // `userId/horodatage-alea.extension` —, donc les deux formes donnent le meme resultat sur
        // toutes les adresses reelles. Le controle existe pour que la difference soit **ecrite**
        // plutot que decouverte, et pour qu'un futur retour au `split` echoue ici en nommant la
        // raison.
        val chemin = "a/storage/v1/object/public/daily-content-media/b.png"

        assertEquals(chemin, DailyContentMedia.pathOf(racine + chemin, racine))
    }

    // ------------------------------------------------------------------ la fenetre de reutilisation

    @Test
    fun `une signature est fraiche tant que son echeance est devant`() {
        assertTrue(DailyContentMedia.isFresh(expiresAt = 1_000L, now = 999L))
        assertFalse(DailyContentMedia.isFresh(expiresAt = 1_000L, now = 1_000L))
        assertFalse(DailyContentMedia.isFresh(expiresAt = 1_000L, now = 1_001L))
    }

    @Test
    fun `la reutilisation s'arrete avant l'expiration`() {
        // C'est **la** regle des deux constantes, et elle ne se voit pas a l'oeil : 3 300 000 ms
        // contre 3 600 s. Les ecrire dans le mauvais ordre — ou changer l'une sans l'autre —
        // servirait une adresse expiree, et une vignette disparaitrait **apres** un rendu reussi.
        // L'assertion porte sur les deux constantes du domaine, et non sur des nombres recopies.
        val validiteMillis = DailyContentMedia.SIGNED_URL_SECONDS * 1000L

        assertTrue(
            DailyContentMedia.CACHE_MILLIS < validiteMillis,
            "La duree de reutilisation (${DailyContentMedia.CACHE_MILLIS} ms) doit rester " +
                "inferieure a la validite de la signature ($validiteMillis ms) : sinon une " +
                "adresse expiree serait servie depuis le cache.",
        )
    }
}
