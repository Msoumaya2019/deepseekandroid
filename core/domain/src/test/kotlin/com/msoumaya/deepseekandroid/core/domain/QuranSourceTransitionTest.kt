package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.MushafSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve la transition de source.
 *
 * C'est la règle qui empêche le lecteur de se retrouver sur une page sans images : la source
 * n'est **adoptée qu'après** vérification de ses ressources. Une transition qui validerait
 * d'abord et préparerait ensuite afficherait une page blanche, et l'utilisateur n'aurait aucun
 * moyen de savoir si c'est un défaut d'affichage ou un téléchargement en cours.
 */
class QuranSourceTransitionTest {

    private fun ordre(): MutableList<String> = CopyOnWriteArrayList()

    @Test
    fun `la source est validee apres la preparation`() = runBlocking {
        val journal = ordre()
        val transition = QuranSourceTransition()

        val valide = transition.change(
            source = MushafSource.CORAN_1441,
            page = 12,
            prepare = { source, page -> journal += "prepare $source $page" },
            commit = { source, page -> journal += "commit $source $page" },
        )

        assertTrue(valide)
        assertEquals(listOf("prepare CORAN_1441 12", "commit CORAN_1441 12"), journal.toList())
    }

    @Test
    fun `une preparation qui echoue ne valide rien`() = runBlocking {
        val journal = ordre()
        val transition = QuranSourceTransition()

        assertFailsWith<IllegalStateException> {
            transition.change(
                source = MushafSource.CORAN_1441,
                page = 12,
                prepare = { _, _ -> throw IllegalStateException("Les images de cette page sont manquantes.") },
                commit = { _, _ -> journal += "commit" },
            )
        }

        assertEquals(emptyList(), journal.toList(), "aucune validation ne doit avoir lieu")
    }

    @Test
    fun `une preparation qui echoue ne bloque pas la suivante`() = runBlocking {
        // Le verrou doit être rendu même quand la préparation échoue. Sans cela, un premier
        // échec — une coupure réseau, une page manquante — rendrait tout changement de source
        // définitivement impossible, jusqu'au redémarrage de l'application.
        val transition = QuranSourceTransition()
        runCatching {
            transition.change(
                source = MushafSource.CORAN_1441,
                page = 12,
                prepare = { _, _ -> throw IllegalStateException("échec") },
                commit = { _, _ -> },
            )
        }

        var validee = false
        val valide = transition.change(
            source = MushafSource.MEDINA,
            page = 12,
            prepare = { _, _ -> },
            commit = { _, _ -> validee = true },
        )

        assertTrue(valide)
        assertTrue(validee, "le changement suivant doit pouvoir aboutir")
    }

    @Test
    fun `une seconde transition pendant la premiere est refusee sans attendre`() = runBlocking {
        val transition = QuranSourceTransition()
        val engage = CountDownLatch(1)
        val peutFinir = CountDownLatch(1)
        val commits = ordre()

        val premiere = async(Dispatchers.Default) {
            transition.change(
                source = MushafSource.CORAN_1441,
                page = 12,
                prepare = { _, _ -> engage.countDown(); peutFinir.await(10, TimeUnit.SECONDS) },
                commit = { _, _ -> commits += "premiere" },
            )
        }

        assertTrue(engage.await(10, TimeUnit.SECONDS), "la première transition n'a pas démarré")

        // Refusée **pendant** que la première est en cours, et non mise en attente : c'est ce
        // que `tryLock` achète. Attendre puis appliquer ferait écraser la première par la
        // seconde, dans un ordre que personne n'a demandé.
        val seconde = transition.change(
            source = MushafSource.MEDINA,
            page = 12,
            prepare = { _, _ -> commits += "prepare seconde" },
            commit = { _, _ -> commits += "seconde" },
        )

        assertFalse(seconde)
        assertEquals(emptyList(), commits.toList(), "la seconde ne doit rien avoir fait")

        peutFinir.countDown()
        assertTrue(premiere.await())
        assertEquals(listOf("premiere"), commits.toList())
    }

    @Test
    fun `une transition disposee ne valide plus rien`() = runBlocking {
        val transition = QuranSourceTransition()
        transition.dispose()

        var validee = false
        val valide = transition.change(
            source = MushafSource.MEDINA,
            page = 12,
            prepare = { _, _ -> },
            commit = { _, _ -> validee = true },
        )

        assertFalse(valide)
        assertFalse(validee)
    }

    @Test
    fun `une page hors bornes est refusee avant toute preparation`() = runBlocking {
        val transition = QuranSourceTransition()
        val journal = ordre()

        for (page in listOf(0, -1, 605)) {
            assertFailsWith<IllegalArgumentException>("page $page") {
                transition.change(
                    source = MushafSource.MEDINA,
                    page = page,
                    prepare = { _, _ -> journal += "prepare $page" },
                    commit = { _, _ -> journal += "commit $page" },
                )
            }
        }
        assertEquals(emptyList(), journal.toList())
        assertEquals(604, QuranSourceTransition.TOTAL_PAGES)
    }

    @Test
    fun `une validation qui suspend est attendue avant que change rende`() = runBlocking {
        // La validation **écrit l'état applicatif**, donc elle suspend. Si `change` rendait
        // `true` avant la fin de l'écriture, un échec d'écriture serait perdu : l'écran aurait
        // adopté une source que l'état ne porte pas, et le redémarrage suivant l'oublierait —
        // sans que rien n'ait été signalé. C'est la propriété qui justifie que `commit` soit
        // déclaré `suspend` plutôt que lancé depuis un rappel non suspendu.
        val transition = QuranSourceTransition()
        var ecrite = false

        val valide = transition.change(
            source = MushafSource.CORAN_1441,
            page = 12,
            prepare = { _, _ -> },
            commit = { _, _ ->
                delay(50)
                ecrite = true
            },
        )

        assertTrue(valide)
        assertTrue(ecrite, "l'écriture doit être terminée quand `change` rend")
    }

    @Test
    fun `une validation qui echoue remonte et rend le verrou`() = runBlocking {
        // Le pendant de « une préparation qui échoue » : une écriture qui échoue ne doit ni
        // passer pour une validation, ni laisser le verrou pris.
        val transition = QuranSourceTransition()

        assertFailsWith<IllegalStateException> {
            transition.change(
                source = MushafSource.CORAN_1441,
                page = 12,
                prepare = { _, _ -> },
                commit = { _, _ -> throw IllegalStateException("écriture impossible") },
            )
        }

        var suivante = false
        val valide = transition.change(
            source = MushafSource.MEDINA,
            page = 12,
            prepare = { _, _ -> },
            commit = { _, _ -> suivante = true },
        )
        assertTrue(valide)
        assertTrue(suivante)
    }

    @Test
    fun `une fois la premiere terminee, la suivante passe`() = runBlocking {
        // Le pendant du test précédent : le refus ne doit pas être définitif. Un verrou gardé
        // après la fin rendrait tout changement de source impossible sans redémarrage.
        val transition = QuranSourceTransition()

        val premiere = transition.change(
            source = MushafSource.CORAN_1441,
            page = 12,
            prepare = { _, _ -> },
            commit = { _, _ -> },
        )
        var seconde = false
        val deuxieme = transition.change(
            source = MushafSource.MEDINA,
            page = 12,
            prepare = { _, _ -> },
            commit = { _, _ -> seconde = true },
        )

        assertTrue(premiere)
        assertTrue(deuxieme)
        assertTrue(seconde)
    }
}
