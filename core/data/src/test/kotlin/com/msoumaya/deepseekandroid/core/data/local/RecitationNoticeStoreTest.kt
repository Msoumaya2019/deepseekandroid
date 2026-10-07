package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.RecitationRecorder
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve le magasin qui retient la notice acceptée, **sur de vrais fichiers**.
 *
 * ## Ce qui est mesuré ici, et pourquoi c'est ici
 *
 * Trois choses, et aucune ne se voit à l'écran :
 *
 *  - le **partage par compte**. La clé de l'original porte l'identifiant du compte
 *    (`recitation-info-${userId}`), parce que la notice parle des récitations d'une personne. Une
 *    clé globale ferait taire la notice pour un second compte ouvert sur le même téléphone — donc
 *    pour quelqu'un qui ne l'a jamais lue ;
 *  - la **persistance**. C'est la seule raison d'être du magasin : une acceptation qui ne
 *    survivrait pas au redémarrage reposerait la question à chaque lancement, et une notice qu'on
 *    repose sans arrêt se lit comme un avertissement qu'on peut ignorer ;
 *  - le **jugement du domaine**. Le magasin range et relit, mais c'est `RecitationRecorder` qui
 *    décide de ce qu'une valeur signifie. Une valeur que le domaine ne reconnaît pas doit valoir
 *    « pas encore lue », et c'est ce qui fait qu'une version antérieure ayant écrit un autre mot
 *    repose la question au lieu de la passer sous silence.
 *
 * ## Ce qui n'est pas mesuré ici
 *
 * La mise de côté d'un document illisible est le comportement de [JsonFileStore] et s'éprouve avec
 * lui. Le **texte** de la notice et le geste d'acceptation ne sont pas ici non plus : ils
 * appartiennent à `RecitationText` et à la surface.
 */
class RecitationNoticeStoreTest {

    private val moi = "moi-0000"
    private val autre = "autre-0000"

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("recitation-notice-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun magasin(): RecitationNoticeStore = RecitationNoticeStore(root)

    private fun document(): File = File(root, RecitationNoticeStore.FILE_NAME)

    // ------------------------------------------------------------------
    // Le partage par compte
    // ------------------------------------------------------------------

    @Test
    fun `un compte qui n'a rien accepte n'est pas informe`() = runTest {
        assertFalse(magasin().accepted(moi), "personne n'a encore rien accepté sur cet appareil")
    }

    @Test
    fun `l'acceptation ne vaut que pour le compte qui l'a donnee`() = runTest {
        val store = magasin()
        store.accept(moi)

        assertTrue(store.accepted(moi), "le compte qui a accepté doit être informé")
        assertFalse(store.accepted(autre), "un second compte sur le même appareil ne l'a pas lue")
    }

    @Test
    fun `l'acceptation survit a un redemarrage`() = runTest {
        magasin().accept(moi)

        // Un magasin neuf, sur le même dossier : c'est la seule façon de savoir que l'acceptation
        // est sur le disque et non en mémoire.
        assertTrue(magasin().accepted(moi))
    }

    // ------------------------------------------------------------------
    // La clé et la valeur, celles du domaine
    // ------------------------------------------------------------------

    @Test
    fun `le document porte la cle et la valeur du domaine`() = runTest {
        magasin().accept(moi)

        val texte = document().readText()
        // Les deux constantes sont celles du domaine, et non recopiées : ce qui est vérifié ici
        // est que le magasin écrit bien **la clé du compte** et **le mot que le domaine relira**.
        assertTrue(
            texte.contains(RecitationRecorder.noticeKey(moi)),
            "le document doit porter la clé du compte, pas une clé globale : $texte",
        )
        assertTrue(
            texte.contains(RecitationRecorder.NOTICE_ACCEPTED),
            "le document doit porter le mot du domaine : $texte",
        )
        assertFalse(
            texte.contains(RecitationRecorder.noticeKey(autre)),
            "le compte qui n'a rien accepté ne doit pas figurer au document : $texte",
        )
    }

    @Test
    fun `une valeur que le domaine ne reconnait pas ne vaut pas une acceptation`() = runTest {
        // Un document écrit à la main, tel qu'une version antérieure aurait pu le laisser : la clé
        // est la bonne, le mot ne l'est pas. Écrit en clair plutôt que par le magasin, sans quoi
        // le contrôle ne porterait que sur sa propre écriture.
        document().writeText("""{"accepted":{"${RecitationRecorder.noticeKey(moi)}":"oui"}}""")

        assertFalse(
            magasin().accepted(moi),
            "seul le mot du domaine vaut acceptation : le doute penche du côté de la personne à informer",
        )
    }

    @Test
    fun `accepter deux fois n'ecrit qu'une entree`() = runTest {
        val store = magasin()
        store.accept(moi)
        store.accept(moi)

        assertTrue(store.accepted(moi))
        val cle = RecitationRecorder.noticeKey(moi)
        val occurrences = document().readText().split(cle).size - 1
        assertEquals(1, occurrences, "un second appui ne doit pas ajouter une seconde ligne")
    }
}
