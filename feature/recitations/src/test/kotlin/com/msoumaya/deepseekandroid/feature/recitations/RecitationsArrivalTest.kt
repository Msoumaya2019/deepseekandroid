package com.msoumaya.deepseekandroid.feature.recitations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Les règles de l'**arrivée d'une récitation à partager**, éprouvées **pour de vrai**.
 *
 * ## Pourquoi ces règles existent
 *
 * Le lecteur enregistre une récitation, puis quitte l'écran pour la liste en emportant
 * l'identifiant de ce qu'il vient d'écrire. Mais la liste ne peut pas l'ouvrir tout de suite : la
 * récitation vient d'être inscrite au **registre local**, et son dépôt est un autre geste — elle
 * peut n'être **pas encore** sur le serveur, et le partage ne s'ouvre que sur une récitation
 * **distante**, parce que c'est ce que la fonction `can_play_shared_recitation` exige.
 *
 * L'original attend donc, et renonce au bout de dix relectures (`RecitationsScreen.tsx:35`).
 *
 * ## Ce que ce fichier mesure, et ce qu'il ne mesure pas
 *
 * Il mesure la **décision** : ouvrir, relire, ou renoncer. Il ne mesure pas l'attente elle-même —
 * cela demanderait une horloge, et un test qui attendrait vraiment trente secondes ne serait pas
 * joué. L'attente est tenue par `RecitationsArrivalWiringTest`, qui vérifie que le `ViewModel`
 * passe bien par cette règle et par les deux constantes ci-dessous.
 */
class RecitationsArrivalTest {

    private fun decide(
        pendingId: String? = "r1",
        remoteIds: Collection<String> = emptyList(),
        openId: String? = null,
        attempts: Int = 0,
    ): RecitationsArrival.Decision =
        RecitationsArrival.decide(pendingId, remoteIds, openId, attempts)

    @Test
    fun `sans identifiant attendu, il n'y a rien a faire`() {
        assertEquals(
            RecitationsArrival.Decision.IDLE,
            decide(pendingId = null, remoteIds = listOf("r1")),
        )
    }

    @Test
    fun `une recitation distante fait ouvrir la ligne`() {
        assertEquals(
            RecitationsArrival.Decision.OPEN,
            decide(remoteIds = listOf("r0", "r1", "r2")),
        )
    }

    @Test
    fun `une recitation seulement locale ne s'ouvre pas encore`() {
        // **La règle centrale.** Le registre local connaît la récitation — c'est lui qui vient de
        // l'inscrire —, mais la liste distante ne l'a pas encore : le partage ne peut pas s'ouvrir
        // sur elle, et c'est pour cela qu'on attend. Une règle qui se contenterait du local
        // ouvrirait un choix d'ami sur une récitation que le serveur refuse encore.
        assertEquals(
            RecitationsArrival.Decision.RETRY,
            decide(remoteIds = listOf("r0", "r2")),
        )
    }

    @Test
    fun `la ligne deja ouverte ne se rouvre pas`() {
        // `onOpen` **bascule** : rouvrir une ligne déjà ouverte la **referme**. Sans cette garde,
        // le partage refermerait la récitation qu'on venait de partager — et le choix d'ami
        // s'ouvrirait sous une ligne repliée.
        assertEquals(
            RecitationsArrival.Decision.IDLE,
            decide(remoteIds = listOf("r1"), openId = "r1"),
        )
    }

    @Test
    fun `une autre ligne ouverte n'empeche pas d'ouvrir`() {
        // La garde ne porte que sur la ligne **attendue**, et non sur « une ligne est ouverte » :
        // `onOpen` sur une autre ligne remplace la précédente, ce qui est le comportement voulu.
        assertEquals(
            RecitationsArrival.Decision.OPEN,
            decide(remoteIds = listOf("r1"), openId = "r2"),
        )
    }

    @Test
    fun `les essais sont bornes`() {
        // Le dernier essai est encore un essai : c'est à la borne atteinte qu'on renonce, et non
        // un essai trop tôt. Un décalage d'un cran ferait renoncer sur une récitation qui serait
        // arrivée à la dixième relecture.
        assertEquals(
            RecitationsArrival.Decision.RETRY,
            decide(attempts = RecitationsArrival.MAX_ATTEMPTS - 1),
        )
        assertEquals(
            RecitationsArrival.Decision.GIVE_UP,
            decide(attempts = RecitationsArrival.MAX_ATTEMPTS),
        )
        assertEquals(
            RecitationsArrival.Decision.GIVE_UP,
            decide(attempts = RecitationsArrival.MAX_ATTEMPTS + 1),
        )
    }

    @Test
    fun `la recitation arrivee gagne sur les essais epuises`() {
        // L'ordre des règles compte : l'arrivée est examinée **avant** la borne. Sinon une
        // récitation arrivée à la dernière relecture serait abandonnée alors qu'elle est là.
        assertEquals(
            RecitationsArrival.Decision.OPEN,
            decide(remoteIds = listOf("r1"), attempts = RecitationsArrival.MAX_ATTEMPTS + 5),
        )
    }

    @Test
    fun `la borne et l'intervalle sont ceux de l'original`() {
        // Dix relectures, toutes les trois secondes : ce sont les nombres de
        // `RecitationsScreen.tsx:35` (`attempts > 10`, `3000`). Les réduire ferait renoncer plus
        // tôt que l'original sur un dépôt lent.
        assertEquals(10, RecitationsArrival.MAX_ATTEMPTS)
        assertEquals(3_000L, RecitationsArrival.INTERVAL_MS)
    }

    @Test
    fun `l'attente totale ne depasse pas trente secondes`() {
        // Ce que la borne **veut dire** : au-delà de trente secondes, la récitation n'est pas
        // arrivée, et la liste reste ouverte sur elle-même plutôt que de faire attendre
        // indéfiniment quelqu'un qui a déjà tourné la page.
        assertTrue(
            RecitationsArrival.MAX_ATTEMPTS * RecitationsArrival.INTERVAL_MS == 30_000L,
            "L'attente bornée n'est plus de trente secondes : la liste ferait attendre plus ou " +
                "moins longtemps que l'original, et rien ne le dirait.",
        )
    }
}
