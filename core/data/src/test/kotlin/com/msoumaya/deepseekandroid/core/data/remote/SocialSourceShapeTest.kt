package com.msoumaya.deepseekandroid.core.data.remote

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient la **source unique** de la taille d'une page de messages.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Le nombre vit dans `Social.MESSAGE_PAGE`, et il sert deux fois : la requête le demande au
 * serveur, et la règle « reste-t-il des messages plus anciens ? » le relit pour savoir si la page
 * reçue est **pleine**. Les deux lectures passent par le même `const val` — mais seulement tant
 * que la source ne le recopie pas.
 *
 * Aucun test de comportement ne peut le voir : les tests du dépôt remplacent la source par une
 * doublure, donc la requête réelle n'est jamais construite. Un contrôle qui lit le source est ici
 * le seul outil qui mesure la bonne chose.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Si quelqu'un réécrivait `50L` en dur dans la source, **rien** ne le signalerait : la
 * conversation continuerait de s'afficher, et le bouton « Charger les messages précédents »
 * apparaîtrait ou disparaîtrait au mauvais moment — une page pleine comparée à un nombre plus
 * grand annonce « il n'y a rien avant », ce qui est faux et silencieux.
 */
class SocialSourceShapeTest {

    @Test
    fun `la source ne recopie pas la taille de page, elle la lit au domaine`() {
        val source = sourceSupabase()

        // L'ancre d'abord : sans elle, les deux contrôles qui suivent pourraient passer sur un
        // fichier qui ne construit plus la requête du tout, et ne mesureraient rien.
        assertTrue(
            source.contains("limit(MESSAGE_PAGE)"),
            "L'ancre a bougé : la requête de messages n'utilise plus `limit(MESSAGE_PAGE)`. Ce " +
                "contrôle ne mesure donc plus rien, et doit être repris avant d'être cru.",
        )
        assertTrue(
            source.contains("private val MESSAGE_PAGE = Social.MESSAGE_PAGE.toLong()"),
            "La source ne lit plus la taille de page au domaine : elle la recopie, et la requête " +
                "et la règle « en reste-t-il ? » peuvent alors diverger en silence.",
        )
        assertTrue(
            !source.contains("MESSAGE_PAGE = 50"),
            "La taille de page est de nouveau écrite en dur dans la source.",
        )
    }

    @Test
    fun `un partage s'ecrit dans une conversation, et jamais dans un cercle`() {
        // Le declencheur `private.validate_recitation_message()` exige `group_id is null` et un
        // `link_id` non nul : un partage depose dans un cercle est **refuse par le serveur**. La
        // requete reelle n'est jamais construite par les tests du depot — la source y est
        // remplacee par une doublure —, donc un controle qui lit le source est, ici encore, le
        // seul outil qui mesure la bonne chose.
        val source = sourceSupabase()

        // L'ancre d'abord : sans elle, les controles qui suivent pourraient passer sur un fichier
        // qui ne porte plus de partage du tout, et ne mesureraient rien.
        assertTrue(
            source.contains("override suspend fun shareRecitation("),
            "L'ancre a bouge : la source n'implemente plus `shareRecitation`. Ce controle ne " +
                "mesure donc plus rien, et doit etre repris avant d'etre cru.",
        )
        assertTrue(
            source.contains("groupId = null,"),
            "Un partage peut de nouveau partir dans un cercle : le serveur le refusera, et " +
                "l'ecran aura annonce un envoi qui n'a pas eu lieu.",
        )
        assertTrue(
            source.contains("recitationId = recitationId,"),
            "Le message partage ne porte plus l'identifiant de la recitation : l'ami recevrait " +
                "un message qui ne mene a rien a ecouter.",
        )
        assertTrue(
            source.contains("body = description.take(SHARE_BODY_MAX),"),
            "Le corps du message n'est plus borne : le serveur refuse un corps trop long, et " +
                "l'envoi echouerait sans que rien ne l'ait annonce.",
        )
    }

    /** Le source de la source Supabase, cherché depuis le module **et** depuis la racine. */
    private fun sourceSupabase(): String {
        val relatif =
            "src/main/kotlin/com/msoumaya/deepseekandroid/core/data/remote/SupabaseSocialSource.kt"
        val candidats = listOf(File(relatif), File("core/data/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "SupabaseSocialSource.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
