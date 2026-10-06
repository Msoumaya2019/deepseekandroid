package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Valeurs d'énumération de l'espace social, telles qu'elles sont écrites en base.
 *
 * `friend_links.status`, `friend_group_members.role`, `friend_messages.kind` et
 * `friend_message_reports.status` sont des colonnes **partagées** avec le client React Native,
 * qui y écrit ces quatre littéraux. Les changer ne casse rien ici : la lecture d'une ligne
 * écrite par l'autre client rendrait simplement `null` sur un champ non-nullable, ou tomberait
 * sur la valeur par défaut — donc « en attente » pour une amitié acceptée, ou « membre » pour
 * le propriétaire d'un cercle.
 *
 * **Ce que ce test mesure.** Les valeurs sont lues **par la sérialisation réelle**, et non
 * recopiées depuis une seconde table qui pourrait diverger. Chaque entrée est donc couverte :
 * ajouter une valeur d'énumération sans l'inscrire ici fait échouer le dernier test, au lieu de
 * laisser une valeur non éprouvée passer en production.
 *
 * **Ce qu'il ne mesure pas.** Les noms de **champs** ne sont pas sérialisés en `snake_case` :
 * `AppJson` n'a pas de stratégie de nommage, et c'est la couche de données qui fait la
 * correspondance avec les colonnes au moment où elle lit. Ce partage est délibéré — un
 * `@SerialName` par champ dupliquerait cette correspondance en deux endroits qui ne peuvent pas
 * se vérifier l'un l'autre —, et il est dit ici pour qu'on ne le découvre pas en lisant un
 * `display_name` qui vaut `null`.
 */
class SocialEnumKeyTest {

    /** Valeur telle qu'elle part sur le réseau : la chaîne JSON sans ses guillemets. */
    private inline fun <reified T> fil(value: T): String = AppJson.encodeToString(value).trim('"')

    @Test
    fun `l'etat d'un lien est celui des colonnes de friend_links`() {
        assertEquals("pending", fil(FriendLinkStatus.PENDING))
        assertEquals("accepted", fil(FriendLinkStatus.ACCEPTED))
        assertEquals("blocked", fil(FriendLinkStatus.BLOCKED))
    }

    @Test
    fun `le role d'un membre est celui des colonnes de friend_group_members`() {
        assertEquals("owner", fil(GroupRole.OWNER))
        assertEquals("moderator", fil(GroupRole.MODERATOR))
        assertEquals("member", fil(GroupRole.MEMBER))
    }

    @Test
    fun `la nature d'un message est celle des colonnes de friend_messages`() {
        assertEquals("text", fil(ChatMessageKind.TEXT))
        assertEquals("encouragement", fil(ChatMessageKind.ENCOURAGEMENT))
        assertEquals("progress", fil(ChatMessageKind.PROGRESS))
        assertEquals("recitation", fil(ChatMessageKind.RECITATION))
    }

    @Test
    fun `l'etat d'un signalement est celui des colonnes de friend_message_reports`() {
        assertEquals("open", fil(ReportStatus.OPEN))
        assertEquals("reviewed", fil(ReportStatus.REVIEWED))
    }

    @Test
    fun `aucune valeur ne se repete dans une meme enumeration`() {
        // Deux valeurs identiques rendraient une ligne ambigue : la lecture choisirait la
        // premiere, et l'ecriture en produirait une que l'autre client ne saurait pas relire.
        val ensembles = listOf(
            FriendLinkStatus.entries.map { fil(it) },
            GroupRole.entries.map { fil(it) },
            ChatMessageKind.entries.map { fil(it) },
            ReportStatus.entries.map { fil(it) },
        )
        for (valeurs in ensembles) {
            assertEquals(valeurs.size, valeurs.toSet().size, "valeurs en double : $valeurs")
        }
    }

    @Test
    fun `chaque enumeration est couverte en entier`() {
        // Le compte est fige : ajouter une valeur d'enumeration sans l'inscrire dans les tests
        // ci-dessus echoue ici, au lieu de laisser une valeur non eprouvee.
        assertEquals(3, FriendLinkStatus.entries.size)
        assertEquals(3, GroupRole.entries.size)
        assertEquals(4, ChatMessageKind.entries.size)
        assertEquals(2, ReportStatus.entries.size)
    }
}
