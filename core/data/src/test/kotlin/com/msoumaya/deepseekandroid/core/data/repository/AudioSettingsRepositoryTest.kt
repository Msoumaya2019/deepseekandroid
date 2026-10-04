package com.msoumaya.deepseekandroid.core.data.repository

import com.msoumaya.deepseekandroid.core.data.local.JsonFileStore
import com.msoumaya.deepseekandroid.core.domain.AudioCount
import com.msoumaya.deepseekandroid.core.domain.AudioSession
import com.msoumaya.deepseekandroid.core.domain.StoredAudioSettings
import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.RepeatMode
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Éprouve le dépôt des réglages d'écoute : ce qu'il publie, et ce qu'il écrit.
 *
 * Le fichier est un vrai fichier dans un dossier temporaire — le magasin ne dépend que d'un
 * `File`, jamais d'un `Context` Android — donc ce qui est mesuré ici est le code qui tourne sur
 * l'appareil, à la lecture du disque près.
 *
 * Trois règles sont tenues, et ce sont celles qui se voient à l'usage :
 *
 *  1. **un premier démarrage n'est pas une panne.** Aucun document, ou un document illisible,
 *     publie les valeurs par défaut au lieu de faire échouer l'ouverture du lecteur ;
 *  2. **une préférence abîmée ne fait pas perdre ses voisines.** Un silence qui n'existe pas
 *     retombe seul sur sa valeur par défaut ; le récitateur, la vitesse et le mode survivent.
 *     Et un document entièrement illisible est **mis de côté**, jamais écrasé ;
 *  3. **ce qui est publié est ce qui est sur le disque.** L'écriture précède la publication :
 *     une écriture qui échoue ne laisse pas un état qui annonce « enregistré ».
 */
class AudioSettingsRepositoryTest {

    private lateinit var root: File
    private lateinit var file: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("audio-settings-test").toFile()
        file = File(root, "audio.json")
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    // ---------------------------------------------------------------- outillage

    /**
     * Le magasin, monté exactement comme le conteneur le monte.
     *
     * `validate` est laissé par défaut : le dépôt ne pose pas de contrôle de schéma, et le
     * `default` est nommé pour que la lambda finale ne soit pas liée au mauvais paramètre.
     */
    private fun store(target: File = file): JsonFileStore<StoredAudioSettings> = JsonFileStore(
        file = target,
        serializer = StoredAudioSettings.serializer(),
        default = { StoredAudioSettings() },
    )

    private fun repository(target: File = file): AudioSettingsRepository =
        AudioSettingsRepository(store(target))

    /** Écrit un document à la main : c'est la forme exacte que laisse un démarrage précédent. */
    private fun ecrire(document: String) {
        file.parentFile?.mkdirs()
        file.writeText(document)
    }

    // ------------------------------------------------------- premier démarrage

    @Test
    fun `un premier demarrage publie les valeurs par defaut`() = runTest {
        val repository = repository()

        repository.prime()

        assertEquals(AudioSession(), repository.settings.value)
        assertNull(repository.reciterId.value, "aucun recitateur n'a jamais ete choisi")
        assertFalse(file.exists(), "une lecture ne doit rien ecrire")
    }

    @Test
    fun `un document enregistre est relu au demarrage`() = runTest {
        ecrire(
            """
            {"repeat":{"countChoice":"custom","customCount":"7","repeatMode":"each-verse",
            "gap":5,"speed":1.25,"autoStop":false},"reciterId":"husary"}
            """.trimIndent(),
        )
        val repository = repository()

        repository.prime()

        assertEquals(
            AudioSession(
                countChoice = AudioCount.CUSTOM,
                customCount = "7",
                mode = RepeatMode.EACH_VERSE,
                gapSeconds = 5,
                speed = 1.25f,
                autoStop = false,
            ),
            repository.settings.value,
        )
        assertEquals("husary", repository.reciterId.value)
    }

    // ------------------------------------------------------- document abîmé

    @Test
    fun `un champ hors bornes ne fait pas perdre ses voisins`() = runTest {
        // 7 secondes de silence n'existent pas dans les choix, et le mode est absent du
        // document. Les deux retombent sur leur defaut ; le reste doit survivre intact.
        ecrire(
            """
            {"repeat":{"countChoice":"custom","customCount":"7","gap":7,"speed":1.25,
            "autoStop":false},"reciterId":"husary"}
            """.trimIndent(),
        )
        val repository = repository()

        repository.prime()

        val session = repository.settings.value
        assertEquals(0, session.gapSeconds, "un silence inexistant retombe sur son defaut")
        assertEquals(RepeatMode.PASSAGE, session.mode)
        assertEquals(AudioCount.CUSTOM, session.countChoice, "le choix doit survivre")
        assertEquals("7", session.customCount)
        assertEquals(1.25f, session.speed)
        assertEquals(false, session.autoStop)
        assertEquals("husary", repository.reciterId.value, "le recitateur doit survivre")
    }

    @Test
    fun `un recitateur jamais choisi reste absent`() = runTest {
        // `null` veut dire « jamais choisi », et non « le premier de la liste » : le lecteur
        // retombe sur son defaut, mais le document ne pretend pas qu'un choix a eu lieu.
        ecrire("""{"repeat":{"countChoice":"1"}}""")
        val repository = repository()

        repository.prime()

        assertEquals(AudioCount.ONE, repository.settings.value.countChoice)
        assertNull(repository.reciterId.value)
    }

    @Test
    fun `un document illisible est mis de cote, pas ecrase`() = runTest {
        ecrire("{ ceci n'est pas du JSON")
        val repository = repository()

        repository.prime()

        assertEquals(AudioSession(), repository.settings.value)
        val archives = root.listFiles { _, name -> name.startsWith("audio.json.corrupt-") }
            ?: emptyArray()
        assertEquals(1, archives.size, "le document fautif doit rester inspectable")
        assertFalse(file.exists(), "le document fautif est deplace, pas duplique")
    }

    // ------------------------------------------------------------- écriture

    @Test
    fun `un enregistrement se relit depuis le disque`() = runTest {
        val session = AudioSession(
            countChoice = AudioCount.CUSTOM,
            customCount = "12",
            mode = RepeatMode.EACH_VERSE,
            gapSeconds = 10,
            speed = 0.75f,
            autoStop = false,
        )
        repository().save(session, "husary")

        // Un second depot, sur le meme fichier, ne partage rien avec le premier — ni cache, ni
        // verrou. Ce qu'il lit vient du disque, et de lui seul.
        val relu = repository()
        relu.prime()

        assertEquals(session, relu.settings.value)
        assertEquals("husary", relu.reciterId.value)
    }

    @Test
    fun `le document porte les deux morceaux en une seule ecriture`() = runTest {
        repository().save(
            AudioSession(countChoice = AudioCount.CUSTOM, customCount = "12", gapSeconds = 5),
            "husary",
        )

        // Les repetitions et le recitateur tiennent dans **un** document, alors que le client
        // d'origine les range dans deux cles d'`AsyncStorage`. La forme enregistree le dit.
        val document = AppJson.decodeFromString(StoredAudioSettings.serializer(), file.readText())
        assertEquals("custom", document.repeat.countChoice)
        assertEquals("12", document.repeat.customCount)
        assertEquals(5, document.repeat.gap)
        assertEquals("husary", document.reciterId)
    }

    @Test
    fun `une ecriture impossible ne publie rien`() = runTest {
        // Le chemin d'ecriture passe sous un fichier : aucun dossier ne peut y etre cree.
        val bloquant = File(root, "bloquant").apply { writeText("un fichier, pas un dossier") }
        val repository = repository(File(bloquant, "audio.json"))
        repository.prime()
        assertEquals(AudioSession(), repository.settings.value)

        val voulu = AudioSession(countChoice = AudioCount.ONE)
        assertFailsWith<Exception> { repository.save(voulu, "husary") }

        // L'echec remonte, et rien n'est publie : un etat qui annoncerait « enregistre » sans
        // l'etre ferait mentir la feuille des le prochain rendu.
        assertEquals(AudioSession(), repository.settings.value)
        assertNull(repository.reciterId.value)
    }
}
