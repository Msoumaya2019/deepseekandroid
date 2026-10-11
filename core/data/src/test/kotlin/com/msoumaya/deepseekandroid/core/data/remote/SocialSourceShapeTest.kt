package com.msoumaya.deepseekandroid.core.data.remote

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient, par la **source**, les règles du dépôt social qu'aucun test de comportement ne peut voir.
 *
 * Trois contrôles, et chacun protège une règle qui ne se voit pas autrement :
 *
 *  - la **taille d'une page de messages** n'est pas recopiée mais lue au domaine, sans quoi la
 *    requête et la règle « en reste-t-il ? » divergeraient en silence ;
 *  - un **partage** s'écrit dans une conversation, jamais dans un cercle, sans quoi le serveur le
 *    refuserait après que l'écran a annoncé un envoi ;
 *  - le **message d'erreur** est lu sur le champ que le serveur a écrit (`restMessage()`), et non
 *    sur `message`, qui y accole un bloc de débogage **et l'adresse du projet**.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux premières règles vivent dans une requête que les tests du dépôt ne construisent
 * jamais : la source y est remplacée par une doublure. La troisième, elle, **ne change pas le
 * comportement** — pour une exception ordinaire, `restMessage()` retombe sur `message`, donc les
 * deux lectures rendent la même chose, et tous les tests de comportement restent verts. Un
 * contrôle qui lit le source est ici le seul outil qui mesure la bonne chose.
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

    @Test
    fun `le message d'erreur lit le champ du serveur, pas le bloc de debogage`() {
        // Le constructeur de `RestException` dereference `request.url` : une telle exception ne
        // s'instancie pas en test, et la lecture du champ `error` est etablie par `javap` sur
        // l'artefact installe (voir `RestError.kt`). Ce qui se mesure **ici** est autre chose, et
        // aucune doublure ne le verrait : que le depot **appelle** le helper qui lit ce champ.
        val source = sourceDepot()
        val bloc = blocDescribe(source)

        // L'ancre d'abord : sans elle, les controles qui suivent pourraient porter sur un fichier
        // qui n'a plus de `describe` du tout, et ne mesureraient rien.
        assertTrue(
            bloc.contains("private fun describe(error: Throwable): String"),
            "L'ancre a bouge : `describe` n'existe plus sous cette forme dans le depot. Ce " +
                "controle ne mesure donc plus rien, et doit etre repris avant d'etre cru.",
        )
        assertTrue(
            bloc.contains("error.restMessage()"),
            "Le depot lit de nouveau `message`, qui accole au texte du serveur un bloc de " +
                "debogage et l'adresse du projet : quelqu'un qui perd sa connexion lira une URL " +
                "au lieu de la raison du refus.",
        )
        assertTrue(
            bloc.contains("error.restCode()"),
            "Le depot ne transmet plus le code du serveur : un refus sans texte perd son " +
                "diagnostic d'installation, et l'ecran retombe sur le message generique.",
        )
        assertTrue(
            !bloc.contains("error.message"),
            "Le depot est revenu a `error.message` : l'ecran affichera le bloc de debogage.",
        )
    }

    /**
     * Le bloc de `describe`, borné à sa **propre** déclaration.
     *
     * `error.message` est un jeton qu'un `contains` sur le fichier entier satisferait par une
     * occurrence étrangère : la borne est donc ce qui donne son sens au contrôle. Le second
     * `check` est le témoin qu'elle a bien mordu — sans lui, une ancre déplacée ferait lire tout
     * le fichier, et le contrôle passerait pour la mauvaise raison.
     */
    private fun blocDescribe(source: String): String {
        val ancre = "private fun describe(error: Throwable): String"
        val debut = source.indexOf(ancre)
        check(debut >= 0) { "l'ancre de `describe` est introuvable dans SocialRepository.kt" }
        val bloc = source.substring(debut).take(FENETRE_DESCRIBE).substringBefore("\n}")
        check(bloc.length < source.length) {
            "la borne n'a pas mordu : le bloc couvre tout le fichier, donc le controle ne " +
                "distingue plus l'appel de `describe` d'une occurrence ailleurs."
        }
        return bloc
    }

    /** Le source du dépôt social, cherché depuis le module **et** depuis la racine. */
    private fun sourceDepot(): String {
        val relatif =
            "src/main/kotlin/com/msoumaya/deepseekandroid/core/data/repository/SocialRepository.kt"
        val candidats = listOf(File(relatif), File("core/data/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "SocialRepository.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }

    /** Assez de caractères pour couvrir le KDoc de `describe` et son corps. */
    private val FENETRE_DESCRIBE = 1500

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
