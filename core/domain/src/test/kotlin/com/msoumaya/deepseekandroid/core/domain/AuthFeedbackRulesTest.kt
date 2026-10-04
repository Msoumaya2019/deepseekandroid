package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve la traduction d'une issue de connexion en message.
 *
 * Le défaut que ces tests visent est précis : annoncer « vérifie ton mot de passe » à
 * quelqu'un qui n'a simplement pas de réseau. C'est ce que faisait le client d'origine, faute
 * de distinguer les causes — et c'est la seule phrase de l'écran de connexion qui puisse
 * envoyer l'utilisateur chercher une faute qui n'existe pas.
 */
class AuthFeedbackRulesTest {

    private fun feedback(outcome: AuthOutcome, registering: Boolean = false) =
        AuthFeedbackRules.of(outcome, registering)

    private fun text(outcome: AuthOutcome, registering: Boolean = false): String =
        when (val result = feedback(outcome, registering)) {
            AuthFeedback.Proceed -> error("cette issue n'affiche pas de message")
            is AuthFeedback.Info -> result.text
            is AuthFeedback.Error -> result.text
        }

    // ------------------------------------------------------------------
    // Réussite
    // ------------------------------------------------------------------

    @Test
    fun `une connexion reussie passe la main sans message`() {
        assertEquals(AuthFeedback.Proceed, feedback(AuthOutcome.Success))
        assertEquals(AuthFeedback.Proceed, feedback(AuthOutcome.Success, registering = true))
    }

    // ------------------------------------------------------------------
    // Le cas que le client d'origine confondait
    // ------------------------------------------------------------------

    @Test
    fun `une panne reseau n'accuse pas le mot de passe`() {
        val message = text(AuthOutcome.Offline)

        assertTrue(message.contains("réseau"), "le message doit nommer la cause : $message")
        assertFalse(
            message.contains("mot de passe"),
            "une panne réseau ne doit pas envoyer chercher une faute de mot de passe : $message",
        )
    }

    @Test
    fun `une panne reseau ne se confond pas avec un projet absent`() {
        // Les deux se ressemblent — rien ne part vers le serveur — mais l'un se répare en
        // retrouvant du réseau, l'autre jamais. Les confondre ferait réessayer indéfiniment.
        val offline = text(AuthOutcome.Offline)
        val absent = text(AuthOutcome.NotConfigured)

        assertTrue(absent.contains("hors ligne"), "le mode sans serveur doit dire qu'il reste utilisable : $absent")
        assertFalse(offline == absent, "deux causes distinctes ne doivent pas partager un message")
    }

    @Test
    fun `des identifiants refuses accusent bien la saisie`() {
        val message = text(AuthOutcome.InvalidCredentials)

        assertTrue(message.contains("adresse"), message)
        assertTrue(message.contains("mot de passe"), message)
    }

    // ------------------------------------------------------------------
    // Textes repris du client d'origine
    // ------------------------------------------------------------------

    @Test
    fun `la confirmation par courriel garde le texte d'origine`() {
        // Repris mot pour mot de `AccountWelcome`. Ce n'est pas une information d'échec mais
        // une étape : la présenter comme une erreur ferait croire à une inscription ratée.
        val result = feedback(AuthOutcome.EmailConfirmationRequired)

        assertTrue(result is AuthFeedback.Info, "une confirmation attendue n'est pas une erreur")
        assertEquals(
            "Un courriel de confirmation t’a été envoyé. Ouvre le lien sur ce téléphone, " +
                "puis commence ton programme.",
            result.text,
        )
    }

    @Test
    fun `le refus d'identifiants garde le texte d'origine`() {
        assertEquals(
            "Connexion impossible. Vérifie ton adresse et ton mot de passe.",
            text(AuthOutcome.InvalidCredentials),
        )
    }

    // ------------------------------------------------------------------
    // Messages qui dépendent de l'intention
    // ------------------------------------------------------------------

    @Test
    fun `une adresse deja prise se dit differemment selon l'intention`() {
        val inscription = text(AuthOutcome.EmailAlreadyUsed, registering = true)
        val connexion = text(AuthOutcome.EmailAlreadyUsed, registering = false)

        assertFalse(inscription == connexion, "le message doit orienter vers la bonne action")
        assertTrue(inscription.contains("Se connecter"), inscription)
    }

    @Test
    fun `une adresse non confirmee se dit differemment selon l'intention`() {
        val inscription = text(AuthOutcome.EmailNotConfirmed, registering = true)
        val connexion = text(AuthOutcome.EmailNotConfirmed, registering = false)

        assertFalse(inscription == connexion)
        // Après une inscription c'est une étape normale ; après une connexion c'est un blocage.
        assertTrue(connexion.contains("connecte-toi"), connexion)
    }

    // ------------------------------------------------------------------
    // Cas inattendus
    // ------------------------------------------------------------------

    @Test
    fun `un cas inattendu sans texte ne laisse pas l'ecran muet`() {
        val message = text(AuthOutcome.Unexpected(""))

        assertTrue(message.isNotBlank(), "un échec sans message doit tout de même dire quelque chose")
    }

    @Test
    fun `un cas inattendu transmet le texte du serveur`() {
        assertEquals("quota dépassé", text(AuthOutcome.Unexpected("quota dépassé")))
    }

    // ------------------------------------------------------------------
    // Couverture
    // ------------------------------------------------------------------

    @Test
    fun `aucune issue ne reste sans message`() {
        // Énumération écrite à la main, et c'est voulu : si une issue est ajoutée à
        // `AuthOutcome` sans être traitée, `when` ne compilerait plus. Ce test vérifie
        // l'autre moitié — qu'aucune n'est traitée par un message vide.
        val outcomes = listOf(
            AuthOutcome.InvalidCredentials,
            AuthOutcome.EmailNotConfirmed,
            AuthOutcome.EmailAlreadyUsed,
            AuthOutcome.AccountDisabled,
            AuthOutcome.SignUpDisabled,
            AuthOutcome.WeakPassword,
            AuthOutcome.Offline,
            AuthOutcome.NotConfigured,
            AuthOutcome.InvalidEmail,
            AuthOutcome.ShortPassword,
            AuthOutcome.EmailConfirmationRequired,
        )

        for (outcome in outcomes) {
            for (registering in listOf(true, false)) {
                assertTrue(
                    text(outcome, registering).isNotBlank(),
                    "$outcome (inscription=$registering) ne dit rien",
                )
            }
        }
    }

    @Test
    fun `deux causes distinctes ne partagent jamais un message`() {
        // C'est la propriété qui empêche la régression de fond : si quelqu'un renvoie deux
        // causes vers la même phrase, l'écran se remet à mentir sur la cause, et rien d'autre
        // ne le signalerait. `Unexpected` est exclu : son texte vient du serveur, il peut
        // donc recopier n'importe qui.
        val causes = listOf(
            AuthOutcome.InvalidCredentials,
            AuthOutcome.EmailNotConfirmed,
            AuthOutcome.EmailAlreadyUsed,
            AuthOutcome.AccountDisabled,
            AuthOutcome.SignUpDisabled,
            AuthOutcome.WeakPassword,
            AuthOutcome.Offline,
            AuthOutcome.NotConfigured,
            AuthOutcome.InvalidEmail,
            AuthOutcome.ShortPassword,
        )

        // Un ensemble par cause : une cause peut légitimement varier selon l'intention, mais
        // deux causes ne doivent jamais se retrouver avec un texte commun.
        val parCause = causes.map { outcome ->
            setOf(text(outcome, registering = true), text(outcome, registering = false))
        }

        val distincts = parCause.sumOf { it.size }
        val reunis = parCause.flatten().toSet()

        assertEquals(
            distincts,
            reunis.size,
            "deux causes partagent un message : $reunis",
        )
    }
}
