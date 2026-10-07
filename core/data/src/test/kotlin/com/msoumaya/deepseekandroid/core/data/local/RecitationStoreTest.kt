package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.Dates
import com.msoumaya.deepseekandroid.core.domain.Recitations
import com.msoumaya.deepseekandroid.core.model.RecitationKind
import com.msoumaya.deepseekandroid.core.model.RecitationSyncStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve le registre local des récitations et les fichiers qui vont avec.
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * Le registre ne porte que des règles qui n'ont **pas d'écran** : la copie du fichier au moment
 * de l'enregistrement, la nature déduite de la présence d'une invocation, l'ordre de la liste,
 * le partage par compte, et ce qu'un retrait efface. Aucune ne lève d'exception quand elle est
 * fausse — et c'est pourquoi elles sont figées :
 *
 *  - sans copie, une récitation enregistrée mais pas encore déposée disparaîtrait au redémarrage,
 *    le fichier de l'enregistreur étant temporaire ;
 *  - une nature déclarée au lieu d'être déduite permettrait une invocation marquée « Coran »,
 *    que le serveur refuserait sans que l'appareil ait pu le prévoir ;
 *  - une liste rendue dans l'ordre d'insertion s'ouvrirait sur la plus vieille récitation ;
 *  - un registre non filtré montrerait les récitations d'un autre compte sous le sien ;
 *  - un retrait qui ne touche pas le fichier laisserait des mégaoctets invisibles.
 *
 * ## Ce qui n'est pas mesuré ici
 *
 * La **mise de côté** d'un document illisible est le comportement de [JsonFileStore], et elle est
 * éprouvée avec lui. La reprise après un redémarrage, elle, est mesurée : c'est la seule façon de
 * savoir que le registre est bien sur le disque et non en mémoire.
 */
class RecitationStoreTest {

    private val moi = "moi-0000"
    private val autre = "autre-0000"

    private lateinit var root: File
    private lateinit var source: File
    private var ticks = 0
    private var ids = 0

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("recitation-store-test").toFile()
        source = File(root, "source.m4a").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    /** Une horloge qui avance d'une seconde par appel, pour que l'ordre soit sans ambiguïté. */
    private fun clock(): String =
        Instant.ofEpochMilli(1_700_000_000_000L + (ticks++).toLong() * 1_000L).toString()

    private fun nouveauRegistre(): RecitationStore =
        RecitationStore(root, nowIso = { clock() }, newId = { "rec-${++ids}" })

    // ------------------------------------------------------------------
    // L'enregistrement : la copie du fichier
    // ------------------------------------------------------------------

    @Test
    fun `l'enregistrement copie le fichier dans le dossier de l'application`() = runTest {
        val registre = nouveauRegistre()
        val item = registre.add(source.absolutePath, 1, 7, 5_000L, moi)

        val fichier = registre.fileOf(item)
        assertTrue(fichier.isFile, "le fichier audio doit exister dans le dossier de l'application")
        assertEquals(
            registre.folder.canonicalFile,
            fichier.canonicalFile.parentFile,
            "la copie doit vivre dans le dossier des recitations",
        )
        assertContentEquals(source.readBytes(), fichier.readBytes(), "la copie doit etre identique")
    }

    @Test
    fun `l'identifiant sert de nom de fichier`() = runTest {
        val registre = nouveauRegistre()
        val item = registre.add(source.absolutePath, 1, 7, 5_000L, moi)

        assertEquals("${item.id}.m4a", registre.fileOf(item).name)
    }

    @Test
    fun `l'extension trois gp est conservee`() = runTest {
        // Le moteur de l'appareil produit tantot l'un, tantot l'autre : l'extension du fichier
        // copie decide du type MIME envoye, et se tromper ferait refuser le depot.
        val troisGp = File(root, "prise.3gp").also { it.writeBytes(byteArrayOf(9)) }
        val item = nouveauRegistre().add(troisGp.absolutePath, 1, 7, 5_000L, moi)

        assertEquals(".3gp", Recitations.extensionFor(item.uri))
        assertTrue(item.uri.endsWith(".3gp"))
    }

    @Test
    fun `l'extension en majuscules est reconnue`() = runTest {
        val troisGp = File(root, "prise.3GP").also { it.writeBytes(byteArrayOf(9)) }
        val item = nouveauRegistre().add(troisGp.absolutePath, 1, 7, 5_000L, moi)

        assertEquals(".3gp", Recitations.extensionFor(item.uri))
    }

    // ------------------------------------------------------------------
    // Les refus
    // ------------------------------------------------------------------

    @Test
    fun `un passage invalide est refuse et rien n'est copie`() = runTest {
        val registre = nouveauRegistre()

        assertFailsWith<IllegalArgumentException> {
            registre.add(source.absolutePath, 10, 5, 5_000L, moi)
        }

        assertTrue(registre.all().isEmpty(), "aucune entree ne doit etre ecrite")
        assertFalse(
            registre.folder.exists() && registre.folder.listFiles()?.isNotEmpty() == true,
            "aucun fichier ne doit rester dans le dossier",
        )
    }

    @Test
    fun `un verset hors du Coran est refuse`() = runTest {
        val registre = nouveauRegistre()

        assertFailsWith<IllegalArgumentException> {
            registre.add(source.absolutePath, 1, 7_000, 5_000L, moi)
        }
        assertTrue(registre.all().isEmpty())
    }

    @Test
    fun `une duree nulle est refusee`() = runTest {
        val registre = nouveauRegistre()

        assertFailsWith<IllegalArgumentException> {
            registre.add(source.absolutePath, 1, 7, 0L, moi)
        }
        assertTrue(registre.all().isEmpty())
    }

    // ------------------------------------------------------------------
    // La nature, deduite et non declaree
    // ------------------------------------------------------------------

    @Test
    fun `une invocation se passe de bornes de versets`() = runTest {
        // Le client d'origine ne verifie les bornes que pour un passage du Coran : une invocation
        // n'en a pas, et exiger un intervalle valide l'empecherait d'etre enregistree.
        val item = nouveauRegistre()
            .add(source.absolutePath, 0, 0, 5_000L, moi, invocationId = "inv-1")

        assertEquals(RecitationKind.INVOCATION, item.kind)
        assertEquals("inv-1", item.invocationId)
    }

    @Test
    fun `les bornes d'une invocation sont ramenees a zero, quoi qu'on donne`() = runTest {
        // L'original ecrit `draft.invocation ? 0 : range.start` : une invocation se range avec des
        // bornes a zero, quel que soit l'intervalle que l'ecran avait sous la main. Sans cette
        // regle, la ligne locale porterait les versets d'une invocation, et le modele local — deux
        // `Int` que le reste du code additionne — ferait passer une invocation pour un passage.
        val item = nouveauRegistre()
            .add(source.absolutePath, 5, 9, 5_000L, moi, invocationId = "inv-1")

        assertEquals(0, item.start)
        assertEquals(0, item.end)
        assertEquals(RecitationKind.INVOCATION, item.kind)
    }

    @Test
    fun `un passage du Coran se declare par l'absence d'invocation`() = runTest {
        val item = nouveauRegistre().add(source.absolutePath, 1, 7, 5_000L, moi)

        assertEquals(RecitationKind.QURAN, item.kind)
        assertNull(item.invocationId)
    }

    // ------------------------------------------------------------------
    // La liste
    // ------------------------------------------------------------------

    @Test
    fun `la liste est rendue du plus recent au plus ancien`() = runTest {
        val registre = nouveauRegistre()
        val premier = registre.add(source.absolutePath, 1, 7, 5_000L, moi)
        val deuxieme = registre.add(source.absolutePath, 8, 9, 5_000L, moi)
        val troisieme = registre.add(source.absolutePath, 10, 12, 5_000L, moi)

        assertEquals(
            listOf(troisieme.id, deuxieme.id, premier.id),
            registre.list(moi).map { it.id },
            "la liste doit s'ouvrir sur ce qui vient d'etre enregistre",
        )
    }

    @Test
    fun `la liste ne rend que les recitations du compte demande`() = runTest {
        val registre = nouveauRegistre()
        val mienne = registre.add(source.absolutePath, 1, 7, 5_000L, moi)
        registre.add(source.absolutePath, 1, 7, 5_000L, autre)

        assertEquals(listOf(mienne.id), registre.list(moi).map { it.id })
        assertEquals(2, registre.all().size, "le registre porte bien les deux entrees")
    }

    // ------------------------------------------------------------------
    // Le statut
    // ------------------------------------------------------------------

    @Test
    fun `le statut est ecrit et relu`() = runTest {
        val registre = nouveauRegistre()
        val item = registre.add(source.absolutePath, 1, 7, 5_000L, moi)
        assertEquals(RecitationSyncStatus.PENDING, item.syncStatus)

        registre.markStatus(item.id, RecitationSyncStatus.SYNCED)

        assertEquals(RecitationSyncStatus.SYNCED, registre.list(moi).single().syncStatus)
    }

    @Test
    fun `marquer un identifiant inconnu ne cree aucune entree`() = runTest {
        // L'entree a pu etre retiree pendant un depot : une ecriture qui ne trouve rien ne doit
        // pas en faire apparaitre une.
        val registre = nouveauRegistre()
        registre.add(source.absolutePath, 1, 7, 5_000L, moi)

        registre.markStatus("inconnu", RecitationSyncStatus.SYNCED)

        assertEquals(1, registre.all().size)
        assertEquals(RecitationSyncStatus.PENDING, registre.list(moi).single().syncStatus)
    }

    // ------------------------------------------------------------------
    // Le retrait
    // ------------------------------------------------------------------

    @Test
    fun `le retrait efface le fichier et l'entree`() = runTest {
        val registre = nouveauRegistre()
        val item = registre.add(source.absolutePath, 1, 7, 5_000L, moi)
        val fichier = registre.fileOf(item)

        registre.remove(item)

        assertFalse(fichier.isFile, "le fichier audio doit etre efface")
        assertTrue(registre.all().isEmpty(), "l'entree doit quitter le registre")
    }

    @Test
    fun `le retrait ne touche pas l'entree d'un autre compte`() = runTest {
        // Le filtre de l'original porte sur le couple (id, compte) : une entree ne peut pas etre
        // retiree au nom d'un autre.
        val registre = nouveauRegistre()
        val item = registre.add(source.absolutePath, 1, 7, 5_000L, moi)

        registre.remove(item.copy(userId = autre))

        assertEquals(1, registre.all().size, "l'entree doit survivre")
        assertTrue(registre.fileOf(item).isFile, "le fichier doit survivre")
    }

    @Test
    fun `un fichier deja efface ne bloque pas le retrait`() = runTest {
        // L'effacement rend `false` au lieu de lever : bloquer le retrait de l'entree sur un
        // fichier deja absent laisserait la recitation affichee pour toujours.
        val registre = nouveauRegistre()
        val item = registre.add(source.absolutePath, 1, 7, 5_000L, moi)
        registre.fileOf(item).delete()

        registre.remove(item)

        assertTrue(registre.all().isEmpty())
    }

    // ------------------------------------------------------------------
    // Les octets, et la persistance
    // ------------------------------------------------------------------

    @Test
    fun `les octets rendus sont ceux du fichier`() = runTest {
        val registre = nouveauRegistre()
        val item = registre.add(source.absolutePath, 1, 7, 5_000L, moi)

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), registre.bytesOf(item))
        assertTrue(registre.exists(item))
    }

    @Test
    fun `un fichier disparu rend null`() = runTest {
        // Le cas reel : une restauration de sauvegarde rend le registre sans les fichiers. La
        // synchronisation doit alors marquer un echec plutot que d'envoyer une ligne vers un
        // fichier que personne ne pourra ecouter.
        val registre = nouveauRegistre()
        val item = registre.add(source.absolutePath, 1, 7, 5_000L, moi)
        registre.fileOf(item).delete()

        assertNull(registre.bytesOf(item))
        assertFalse(registre.exists(item))
    }

    @Test
    fun `le registre survit a un redemarrage`() = runTest {
        val premier = nouveauRegistre()
        val item = premier.add(source.absolutePath, 1, 7, 5_000L, moi)
        premier.markStatus(item.id, RecitationSyncStatus.SYNCED)

        val second = nouveauRegistre()

        val relu = second.list(moi).single()
        assertEquals(item.id, relu.id)
        assertEquals(item.uri, relu.uri)
        assertEquals(5_000L, relu.durationMs)
        assertEquals(RecitationSyncStatus.SYNCED, relu.syncStatus)
        assertEquals(1, relu.start)
        assertEquals(7, relu.end)
        assertEquals(Dates.parseIsoMillis(item.createdAt), Dates.parseIsoMillis(relu.createdAt))
    }
}
