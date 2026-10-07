package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les décisions d'une notification : où elle mène, si elle s'affiche, ce qui la dédoublonne, et
 * les deux textes que le client fabrique lui-même.
 *
 * ## Pourquoi ces cas, et pas d'autres
 *
 * Chaque cas vise une règle qui **peut mentir en silence** : une notification supprimée à tort
 * n'affiche rien et ne casse rien ; un `kind` renommé fait disparaître une notification sans
 * qu'aucune compilation ne s'en plaigne ; un défaut de préférence inversé prévient les amis d'un
 * élève qui n'a rien demandé. Les cas qui ne feraient que constater une valeur d'entrée sont donc
 * accompagnés d'un **second** cas non vide qui aurait pu être retenu — c'est la même règle que
 * dans les autres bancs du domaine.
 *
 * ## Ce que ce banc ne peut pas faire
 *
 * Il n'affiche rien et ne planifie rien : ni Android, ni Firebase, ni horloge. La couche de
 * **transport** n'est pas livrée, et `SUPABASE_COMPATIBILITY.md` dit pourquoi, mesure en main —
 * la colonne `push_devices.expo_push_token` exige un jeton Expo et le serveur envoie par
 * `exp.host`. Ce banc éprouve donc exactement ce qui a été porté : les décisions.
 */
class NotificationsTest {

    private fun charge(vararg paires: Pair<String, String?>): Map<String, String?> =
        mapOf(*paires)

    private fun contexte(
        activeLinkId: String? = null,
        recitationsVisible: Boolean = false,
        visibility: Notifications.Visibility = Notifications.Visibility.ACTIVE,
        messagesEnabled: Boolean = true,
        progressEnabled: Boolean = false,
        correctionsEnabled: Boolean = true,
        adminEnabled: Boolean = true,
    ) = Notifications.Context(
        activeLinkId = activeLinkId,
        recitationsVisible = recitationsVisible,
        visibility = visibility,
        messagesEnabled = messagesEnabled,
        progressEnabled = progressEnabled,
        correctionsEnabled = correctionsEnabled,
        adminEnabled = adminEnabled,
    )

    // -------------------------------------------------------------------------------------------
    // Les littéraux
    // -------------------------------------------------------------------------------------------

    @Test
    fun `les douze litteraux de la charge utile sont ceux du serveur`() {
        // Ces chaînes sont écrites par les fonctions de `supabase/*.sql`. Les renommer ici ferait
        // disparaître une notification sans que rien ne le signale : aucun test ne les relit
        // ailleurs, et le serveur ne se plaint pas d'un client qui ne comprend pas.
        assertEquals(
            listOf(
                "learning-reminder",
                "revision-reminder",
                "private-message",
                "friend-progress",
                "recitation-corrected",
                "admin-reminder",
                "quiz-daily",
                "quiz-challenge",
                "quiz-result",
                "friend-request",
                "friend-accepted",
                "notification-test",
            ),
            Notifications.kinds,
            "Un littéral de charge utile a changé : le serveur continue d'écrire l'ancien.",
        )
    }

    // -------------------------------------------------------------------------------------------
    // La destination
    // -------------------------------------------------------------------------------------------

    @Test
    fun `les trois kind de quiz menent a l'ecran de quiz`() {
        for (kind in listOf(
            Notifications.QUIZ_DAILY,
            Notifications.QUIZ_CHALLENGE,
            Notifications.QUIZ_RESULT,
        )) {
            val destination = Notifications.destination(
                charge("kind" to kind, "challengeId" to "defi-7"),
            )
            assertEquals(
                Notifications.Destination.Quiz("defi-7"),
                destination,
                "Le kind `$kind` doit ouvrir le Quiz sur son défi.",
            )
        }
    }

    @Test
    fun `la question du jour n'ouvre aucun defi`() {
        // `quiz-daily` n'a pas de `challengeId` : l'original écrit `undefined`, et l'écran ouvre
        // alors sa question du jour. Rendre un identifiant vide au lieu de l'absence ferait
        // chercher un défi qui n'existe pas.
        assertEquals(
            Notifications.Destination.Quiz(null),
            Notifications.destination(charge("kind" to Notifications.QUIZ_DAILY)),
        )
    }

    @Test
    fun `les deux rappels menent au programme`() {
        for (kind in listOf(Notifications.ADMIN_REMINDER, Notifications.LEARNING_REMINDER)) {
            assertEquals(
                Notifications.Destination.Program,
                Notifications.destination(charge("kind" to kind)),
                "Le kind `$kind` doit mener à l'onglet Programme.",
            )
        }
    }

    @Test
    fun `le rappel de revision mene aux revisions`() {
        assertEquals(
            Notifications.Destination.Reviews,
            Notifications.destination(charge("kind" to Notifications.REVISION_REMINDER)),
        )
    }

    @Test
    fun `une correction sans identifiant ne mene nulle part`() {
        // L'original exige `typeof data.recitationId === 'string'`. Une correction qui arriverait
        // sans identifiant s'afficherait donc sans rien ouvrir — et c'est le comportement voulu,
        // non un défaut : mieux vaut une notification muette qu'un écran ouvert au hasard.
        assertNull(
            Notifications.destination(charge("kind" to Notifications.RECITATION_CORRECTED)),
            "Sans identifiant de récitation, la correction ne doit mener nulle part.",
        )
    }

    @Test
    fun `une correction identifiee ouvre sa recitation`() {
        assertEquals(
            Notifications.Destination.Recitation("rec-42"),
            Notifications.destination(
                charge(
                    "kind" to Notifications.RECITATION_CORRECTED,
                    "recitationId" to "rec-42",
                    "revision" to "2",
                ),
            ),
        )
    }

    @Test
    fun `un message sans lien ne mene nulle part`() {
        // C'est le cas qui distingue un portage fidèle d'un portage généreux : la notification
        // s'affiche, et le toucher ne fait rien. Ouvrir l'onglet Amis « puisqu'il n'y a qu'un
        // onglet » serait une invention.
        assertNull(
            Notifications.destination(charge("kind" to Notifications.PRIVATE_MESSAGE)),
            "Un message sans `linkId` ne doit mener nulle part.",
        )
    }

    @Test
    fun `les quatre familles de discussion menent a la conversation`() {
        for (kind in listOf(
            Notifications.PRIVATE_MESSAGE,
            Notifications.FRIEND_PROGRESS,
            Notifications.FRIEND_REQUEST,
            Notifications.FRIEND_ACCEPTED,
        )) {
            assertEquals(
                Notifications.Destination.Conversation("lien-3"),
                Notifications.destination(charge("kind" to kind, "linkId" to "lien-3")),
                "Le kind `$kind` doit ouvrir la discussion de son lien.",
            )
        }
    }

    @Test
    fun `un lien vide est rendu tel quel`() {
        // L'original teste `typeof … === 'string'`, et la chaîne vide satisfait ce test : la
        // destination est donc bien rendue. C'est le **consommateur** qui refuse ensuite, en se
        // fiant à la fausseté de la chaîne vide en JavaScript. Corriger ici ferait diverger la
        // fonction de sa source sans que rien ne le dise.
        assertEquals(
            Notifications.Destination.Conversation(""),
            Notifications.destination(charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "")),
            "Un `linkId` vide passe le test de type de l'original : il doit être rendu tel quel.",
        )
    }

    @Test
    fun `un kind inconnu ne mene nulle part`() {
        // Le serveur peut ajouter un `kind` avant que ce client ne le connaisse. La source rend
        // `null` : la notification s'affiche et le toucher ne fait rien.
        assertNull(
            Notifications.destination(charge("kind" to "kind-que-ce-client-ignore")),
            "Un `kind` inconnu doit mener nulle part, et non lever.",
        )
    }

    // -------------------------------------------------------------------------------------------
    // La garde des révisions
    // -------------------------------------------------------------------------------------------

    @Test
    fun `un rappel de revision n'ouvre pas un espace eteint`() {
        assertFalse(
            Notifications.revisionsOpen(false),
            "L'élève a éteint l'espace Révisions : un rappel ne doit pas l'ouvrir.",
        )
    }

    @Test
    fun `l'espace des revisions est ouvert par defaut`() {
        // `reviewsEnabled = state.reviewSettings?.enabled !== false` : l'absence de réglage vaut
        // ouverture. Un portage qui exigerait `true` fermerait l'espace à tout le monde.
        assertTrue(Notifications.revisionsOpen(null), "L'absence de réglage vaut ouverture.")
        assertTrue(Notifications.revisionsOpen(true), "Un réglage ouvert vaut ouverture.")
    }

    // -------------------------------------------------------------------------------------------
    // L'identité
    // -------------------------------------------------------------------------------------------

    @Test
    fun `le messageId gagne sur le couple de correction`() {
        // `messageId || correctionId || adminId` : le premier présent gagne, quel que soit le
        // `kind`. Une correction qui porterait un `messageId` serait identifiée par lui.
        assertEquals(
            "msg-1",
            Notifications.identity(
                charge(
                    "kind" to Notifications.RECITATION_CORRECTED,
                    "messageId" to "msg-1",
                    "recitationId" to "rec-42",
                ),
            ),
            "Un `messageId` présent doit gagner, même sur une correction.",
        )
    }

    @Test
    fun `un messageId vide ne gagne pas`() {
        // L'opérateur `||` de JavaScript saute la chaîne vide. Si ce portage retenait `""` comme
        // identité, toutes les notifications sans `messageId` mais avec la clé vide se
        // dédoublonneraient l'une l'autre.
        assertEquals(
            "rec-42:2",
            Notifications.identity(
                charge(
                    "kind" to Notifications.RECITATION_CORRECTED,
                    "messageId" to "",
                    "recitationId" to "rec-42",
                    "revision" to "2",
                ),
            ),
            "Un `messageId` vide doit laisser la place au couple de correction.",
        )
    }

    @Test
    fun `le couple d'une correction porte toujours ses deux points`() {
        // Le couple s'écrit `"${recitationId}:${revision ?? ''}"`. Un `recitationId` vide donne
        // donc `":"` — une chaîne **vraie**, retenue comme identité. Deux corrections sans
        // identifiant se dédoublonneraient l'une l'autre ; le serveur envoie un uuid, donc le cas
        // est improbable, et il est écrit ici plutôt que découvert plus tard.
        assertEquals(
            ":",
            Notifications.identity(
                charge(
                    "kind" to Notifications.RECITATION_CORRECTED,
                    "recitationId" to "",
                ),
            ),
            "Le couple d'une correction n'est jamais vide, même avec des parties vides.",
        )
    }

    @Test
    fun `le rappel du professeur s'identifie par son notificationId`() {
        assertEquals(
            "notif-9",
            Notifications.identity(
                charge("kind" to Notifications.ADMIN_REMINDER, "notificationId" to "notif-9"),
            ),
        )
    }

    @Test
    fun `le notificationId d'un autre kind est ignore`() {
        // `adminId` n'est calculé que si le `kind` est celui du professeur. Une charge utile qui
        // porterait la clé sans le `kind` n'a pas d'identité — sinon deux notifications sans
        // rapport se dédoublonneraient.
        assertNull(
            Notifications.identity(
                charge("kind" to Notifications.PRIVATE_MESSAGE, "notificationId" to "notif-9"),
            ),
        )
    }

    @Test
    fun `une notification sans identite n'en a pas`() {
        assertNull(
            Notifications.identity(charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3")),
            "Un message ordinaire ne porte pas d'identité : il n'est donc jamais dédoublonné.",
        )
    }

    // -------------------------------------------------------------------------------------------
    // La porte de présentation
    // -------------------------------------------------------------------------------------------

    @Test
    fun `une notification ordinaire s'affiche`() {
        val decision = Notifications.present(
            charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3", "messageId" to "m1"),
            contexte(activeLinkId = "lien-4"),
            mutableSetOf(),
        )
        assertTrue(decision.show, "Rien ne s'oppose à l'affichage : la notification doit s'afficher.")
    }

    @Test
    fun `la discussion ouverte fait taire son propre message`() {
        val decision = Notifications.present(
            charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3", "messageId" to "m1"),
            contexte(activeLinkId = "lien-3"),
            mutableSetOf(),
        )
        assertFalse(
            decision.show,
            "On est en train de lire la discussion : la notification serait redondante.",
        )
    }

    @Test
    fun `la progression compte comme une discussion`() {
        // `isChat = kind === 'private-message' || kind === 'friend-progress'` — et c'est mesuré :
        // une progression partagée arrive en **silence** quand la discussion est ouverte, alors
        // qu'elle n'est pas un message. Un portage qui ne couvrirait que `private-message`
        // afficherait un bandeau pour quelque chose qu'on est en train de regarder.
        //
        // **La préférence de progression est allumée ici, et ce n'est pas un détail.** Le défaut
        // du contexte l'éteint, et sous ce défaut la notification serait supprimée par la
        // **préférence** et non par la discussion : le test serait vert sans rien prouver. La
        // campagne de falsification l'a montré — la mutation de `isChat` ne le faisait pas tomber.
        val decision = Notifications.present(
            charge("kind" to Notifications.FRIEND_PROGRESS, "linkId" to "lien-3"),
            contexte(activeLinkId = "lien-3", progressEnabled = true),
            mutableSetOf(),
        )
        assertFalse(
            decision.show,
            "La progression partagée compte comme une discussion : elle doit se taire aussi.",
        )
    }

    @Test
    fun `sans discussion ouverte rien n'est tu`() {
        // Le piège que cette fonction existe pour ne pas retomber dans : l'original compare
        // `undefined === null`, qui est **faux** en JavaScript. En Kotlin, `null == null` est
        // vrai — un portage naïf supprimerait donc la notification ici.
        val decision = Notifications.present(
            charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3"),
            contexte(activeLinkId = null),
            mutableSetOf(),
        )
        assertTrue(
            decision.show,
            "Aucune discussion n'est ouverte : la notification doit s'afficher.",
        )
    }

    @Test
    fun `une charge utile sans lien ne fait rien taire`() {
        // Les deux côtés absents ne doivent pas se rejoindre non plus. La source compare deux
        // `undefined`, qui sont égaux par `===` — mais `isChat` est vrai et `activeLinkId` est
        // `null`, donc `undefined === null` est faux. Le portage exige les deux côtés non nuls.
        val decision = Notifications.present(
            charge("kind" to Notifications.PRIVATE_MESSAGE),
            contexte(activeLinkId = null),
            mutableSetOf(),
        )
        assertTrue(decision.show, "Deux absences ne sont pas une égalité.")
    }

    @Test
    fun `une discussion ouverte en arriere-plan ne fait rien taire`() {
        // `DeviceAppState.currentState === 'active'` fait partie des deux conditions « même
        // endroit ». En arrière-plan, l'écran laissé n'est pas sous les yeux : la notification
        // doit s'afficher, sinon on ne saurait pas qu'elle est arrivée en revenant.
        val decision = Notifications.present(
            charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3", "messageId" to "m1"),
            contexte(activeLinkId = "lien-3", visibility = Notifications.Visibility.BACKGROUND),
            mutableSetOf(),
        )
        assertTrue(decision.show, "Une discussion ouverte en arrière-plan ne fait rien taire.")
    }

    @Test
    fun `l'ecran des recitations fait taire une correction`() {
        val decision = Notifications.present(
            charge("kind" to Notifications.RECITATION_CORRECTED, "recitationId" to "rec-42"),
            contexte(recitationsVisible = true),
            mutableSetOf(),
        )
        assertFalse(
            decision.show,
            "L'écran des récitations est ouvert : la correction est déjà sous les yeux.",
        )
    }

    @Test
    fun `l'ecran des recitations ne fait taire que les corrections`() {
        // La garde est `data?.kind === correctionKind && recitationsVisible && …`. L'appliquer à
        // tout ce qui arrive ferait taire les messages pendant qu'on regarde ses récitations.
        val decision = Notifications.present(
            charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3", "messageId" to "m1"),
            contexte(recitationsVisible = true),
            mutableSetOf(),
        )
        assertTrue(decision.show, "Un message doit s'afficher même sur l'écran des récitations.")
    }

    @Test
    fun `l'ecran des recitations en arriere-plan ne fait rien taire`() {
        // Le premier plan fait partie des **deux** conditions « même endroit » : celle des
        // discussions comme celle des récitations. L'écran des récitations laissé derrière soi
        // n'est pas sous les yeux — une correction arrivée entre-temps doit s'afficher.
        val decision = Notifications.present(
            charge("kind" to Notifications.RECITATION_CORRECTED, "recitationId" to "rec-42"),
            contexte(recitationsVisible = true, visibility = Notifications.Visibility.BACKGROUND),
            mutableSetOf(),
        )
        assertTrue(
            decision.show,
            "L'écran des récitations en arrière-plan ne fait rien taire.",
        )
    }

    @Test
    fun `une correction ne fait pas taire un message`() {
        // Réciproque du cas précédent, du côté de la condition « même endroit » : la correction
        // n'entre pas dans `isChat`.
        val decision = Notifications.present(
            charge("kind" to Notifications.RECITATION_CORRECTED, "recitationId" to "rec-42"),
            contexte(activeLinkId = "lien-3"),
            mutableSetOf(),
        )
        assertTrue(decision.show, "Une discussion ouverte ne fait pas taire une correction.")
    }

    @Test
    fun `la meme notification ne s'affiche pas deux fois`() {
        val dejaVues = mutableSetOf<String>()
        val payload = charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3", "messageId" to "m1")

        val premiere = Notifications.present(payload, contexte(), dejaVues)
        val seconde = Notifications.present(payload, contexte(), dejaVues)

        assertTrue(premiere.show, "La première occurrence doit s'afficher.")
        assertFalse(
            seconde.show,
            "La seconde occurrence porte le même identifiant : elle ne doit pas s'afficher.",
        )
    }

    @Test
    fun `le dedoublonnage porte sur l'identite et non sur la charge utile`() {
        // Deux charges utiles différentes peuvent désigner la même notification — le serveur
        // rejoue le même `messageId` avec un corps différent, par exemple. Compter les charges
        // utiles ne dédoublonnerait rien.
        val dejaVues = mutableSetOf<String>()
        val premiere = charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3", "messageId" to "m1")
        val seconde = charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3", "messageId" to "m1")

        assertTrue(Notifications.present(premiere, contexte(), dejaVues).show)
        assertFalse(
            Notifications.present(seconde, contexte(), dejaVues).show,
            "Même identifiant : la seconde doit être retenue, même si le corps a changé.",
        )
    }

    @Test
    fun `quatre kind obeissent a une preference et les autres non`() {
        // Les quatre gardes de la source, éteintes toutes ensemble.
        val toutEteint = contexte(
            messagesEnabled = false,
            progressEnabled = false,
            correctionsEnabled = false,
            adminEnabled = false,
        )
        val gardes = mapOf(
            Notifications.PRIVATE_MESSAGE to "messages",
            Notifications.FRIEND_PROGRESS to "sharedProgress",
            Notifications.RECITATION_CORRECTED to "corrections",
            Notifications.ADMIN_REMINDER to "adminMessages",
        )
        for ((kind, quoi) in gardes) {
            assertFalse(
                Notifications.present(charge("kind" to kind), toutEteint, mutableSetOf()).show,
                "La préférence « $quoi » éteinte doit faire taire le kind `$kind`.",
            )
        }

        // Et les `kind` sans garde côté client : le Quiz et les invitations sont retenus par le
        // **serveur**, pas ici. Leur en inventer une serait plus strict que la source.
        for (kind in listOf(
            Notifications.QUIZ_DAILY,
            Notifications.QUIZ_CHALLENGE,
            Notifications.QUIZ_RESULT,
            Notifications.FRIEND_REQUEST,
            Notifications.FRIEND_ACCEPTED,
        )) {
            assertTrue(
                Notifications.present(charge("kind" to kind, "linkId" to "lien-3"), toutEteint, mutableSetOf()).show,
                "Le kind `$kind` n'a aucune garde côté client : il doit s'afficher.",
            )
        }
    }

    @Test
    fun `le kind du quiz ignore la garde des corrections`() {
        // Les gardes sont nommées une par une et ne se confondent pas : éteindre les corrections
        // ne doit pas taire un quiz.
        assertTrue(
            Notifications.present(
                charge("kind" to Notifications.QUIZ_DAILY),
                contexte(correctionsEnabled = false),
                mutableSetOf(),
            ).show,
        )
    }

    @Test
    fun `une notification taise est tout de meme retenue`() {
        // L'original ajoute l'identifiant **avant** de savoir s'il affichera : `if(uniqueId){add}`
        // est inconditionnel. La conséquence est réelle — une notification tue par une
        // préférence n'est pas rejouée quand on rallume la préférence.
        val dejaVues = mutableSetOf<String>()
        val payload = charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3", "messageId" to "m1")

        val taise = Notifications.present(payload, contexte(messagesEnabled = false), dejaVues)
        assertFalse(taise.show, "La préférence est éteinte : rien ne doit s'afficher.")
        assertTrue(dejaVues.contains("m1"), "L'identifiant est retenu même quand rien ne s'affiche.")

        val rallumee = Notifications.present(payload, contexte(messagesEnabled = true), dejaVues)
        assertFalse(
            rallumee.show,
            "Rallumer la préférence ne rejoue pas une notification déjà vue : l'identifiant est " +
                "déjà dans la mémoire.",
        )
    }

    @Test
    fun `le plafond vide la memoire au lieu d'evincer`() {
        // `if(size>200)clear()` — ce n'est pas un tampon circulaire : au 201ᵉ identifiant,
        // l'ensemble entier est vidé, **y compris celui qui vient d'être ajouté**. Le portage
        // doit faire la même chose, sinon la mémoire grandirait sans borne.
        val dejaVues = mutableSetOf<String>()
        for (index in 1..Notifications.DISPLAYED_CAP) {
            Notifications.present(
                charge("kind" to Notifications.PRIVATE_MESSAGE, "messageId" to "m$index"),
                contexte(),
                dejaVues,
            )
        }
        assertEquals(
            Notifications.DISPLAYED_CAP,
            dejaVues.size,
            "Au plafond exactement, la mémoire n'est pas encore vidée : la condition est `>`.",
        )

        Notifications.present(
            charge("kind" to Notifications.PRIVATE_MESSAGE, "messageId" to "m201"),
            contexte(),
            dejaVues,
        )
        assertEquals(
            0,
            dejaVues.size,
            "Au 201ᵉ identifiant, la mémoire est vidée **entièrement** — le dernier ajouté part " +
                "avec les autres.",
        )
    }

    @Test
    fun `une notification sans identite ne touche pas a la memoire`() {
        // Le plafond n'est atteint que par une notification **qui porte une identité**. Sans cela,
        // un flux de notifications sans identifiant viderait la mémoire à répétition.
        val dejaVues = mutableSetOf<String>()
        repeat(Notifications.DISPLAYED_CAP + 1) {
            Notifications.present(
                charge("kind" to Notifications.PRIVATE_MESSAGE, "linkId" to "lien-3"),
                contexte(),
                dejaVues,
            )
        }
        assertTrue(
            dejaVues.isEmpty(),
            "Une charge utile sans identifiant n'entre pas dans la mémoire.",
        )
    }

    @Test
    fun `la decision annonce l'identite retenue`() {
        // L'appelant a besoin de savoir ce qui a été retenu, pour ne pas le recalculer — et le
        // recalculer ailleurs serait le second endroit où la précédence peut diverger.
        val decision = Notifications.present(
            charge("kind" to Notifications.PRIVATE_MESSAGE, "messageId" to "m1"),
            contexte(),
            mutableSetOf(),
        )
        assertEquals("m1", decision.identity)
    }

    // -------------------------------------------------------------------------------------------
    // Les canaux
    // -------------------------------------------------------------------------------------------

    @Test
    fun `les quatre canaux sont ceux de l'original`() {
        assertEquals(
            listOf("messages", "learning", "corrections", "admin"),
            Notifications.channels.map { it.id },
            "L'ordre compte : c'est celui des réglages du téléphone.",
        )
        assertEquals(
            listOf(
                "Messages privés",
                "Rappels d’apprentissage",
                "Corrections des récitations",
                "Rappels du professeur",
            ),
            Notifications.channels.map { it.name },
            "Les libellés sont ceux de l'original, apostrophe typographique comprise.",
        )
        assertTrue(
            Notifications.channels.all { it.highImportance },
            "Les quatre canaux sont en importance haute : un rappel discret ne serait pas un rappel.",
        )
    }

    @Test
    fun `l'apostrophe des canaux est typographique`() {
        // Un caractère droit casserait l'affichage sans qu'aucune compilation ne s'en plaigne :
        // c'est le même piège que le `Juz’` de `feature:profile`, et il mérite son propre cas
        // plutôt que d'être noyé dans la comparaison de liste ci-dessus.
        val apprentissage = Notifications.channels.first { it.id == "learning" }
        assertTrue(
            apprentissage.name.contains('\u2019'),
            "« Rappels d’apprentissage » porte l'apostrophe typographique de l'original.",
        )
        assertFalse(
            apprentissage.name.contains('\''),
            "Un caractère droit s'est glissé dans le libellé du canal d'apprentissage.",
        )
    }

    // -------------------------------------------------------------------------------------------
    // Les préférences
    // -------------------------------------------------------------------------------------------

    @Test
    fun `les sept preferences et leurs colonnes`() {
        assertEquals(
            listOf(
                "messages" to "messages_enabled",
                "friendRequests" to "friend_requests_enabled",
                "sharedProgress" to "shared_progress_enabled",
                "revision" to "revision_reminders_enabled",
                "corrections" to "corrections_enabled",
                "adminMessages" to "admin_messages_enabled",
                "messagePreview" to "message_preview_enabled",
            ),
            Notifications.preferenceColumns,
            "Les noms de colonnes sont ceux du schéma partagé : les renommer écrirait dans le vide.",
        )
        assertEquals(
            7,
            Notifications.preferenceColumns.size,
            "Sept préférences seulement : `quiz_enabled` et `quiz_timezone` passent par la couche " +
                "du Quiz.",
        )
    }

    @Test
    fun `trois preferences sont ouvertes par defaut et une est fermee`() {
        // Les défauts de `App.tsx` ligne 177, et ils ne se ressemblent pas : `!== false` ouvre,
        // `=== true` ferme. Confondre les deux formes activerait la progression partagée pour
        // tout le monde — c'est-à-dire préviendrait les amis d'un élève qui n'a rien demandé.
        val defauts = Notifications.preferencesFrom(
            messages = null,
            friendRequests = null,
            sharedProgress = null,
            corrections = null,
            adminMessages = null,
            messagePreview = null,
        )
        assertTrue(defauts.messages, "Les messages sont ouverts par défaut.")
        assertTrue(defauts.friendRequests, "Les demandes d'ami sont ouvertes par défaut.")
        assertTrue(defauts.corrections, "Les corrections sont ouvertes par défaut.")
        assertTrue(defauts.adminMessages, "Les rappels du professeur sont ouverts par défaut.")
        assertTrue(defauts.messagePreview, "L'aperçu des messages est ouvert par défaut.")
        assertFalse(
            defauts.sharedProgress,
            "La progression partagée est **fermée** par défaut : elle se demande.",
        )
    }

    @Test
    fun `la progression partagee s'ouvre sur un accord explicite`() {
        assertTrue(
            Notifications.preferencesFrom(
                messages = null,
                friendRequests = null,
                sharedProgress = true,
                corrections = null,
                adminMessages = null,
                messagePreview = null,
            ).sharedProgress,
        )
        assertFalse(
            Notifications.preferencesFrom(
                messages = null,
                friendRequests = null,
                sharedProgress = false,
                corrections = null,
                adminMessages = null,
                messagePreview = null,
            ).sharedProgress,
        )
    }

    @Test
    fun `une preference refusee le reste`() {
        val refusees = Notifications.preferencesFrom(
            messages = false,
            friendRequests = false,
            sharedProgress = null,
            corrections = false,
            adminMessages = false,
            messagePreview = false,
        )
        assertFalse(refusees.messages)
        assertFalse(refusees.friendRequests)
        assertFalse(refusees.corrections)
        assertFalse(refusees.adminMessages)
        assertFalse(refusees.messagePreview)
    }

    @Test
    fun `le rappel de revision n'est jamais ecrit vrai`() {
        // Divergence de l'original, reproduite et non corrigée : `App.tsx` écrit `revision: false`
        // en dur, alors que l'écran des réglages offre l'interrupteur et que la colonne
        // `revision_reminders_enabled` existe. Le portage garde le comportement du client
        // d'origine ; ce cas existe pour qu'un futur « correcteur » ne le change pas sans savoir
        // ce qu'il change — et il n'y a rien à lui passer, puisque la fonction n'a **pas** de
        // paramètre de révision : c'est la forme la plus forte de la règle.
        assertFalse(
            Notifications.preferencesFrom(
                messages = null,
                friendRequests = null,
                sharedProgress = null,
                corrections = null,
                adminMessages = null,
                messagePreview = null,
            ).revision,
            "La préférence de révision est écrite fausse quoi qu'il arrive.",
        )
        assertFalse(
            Notifications.preferencesFrom(
                messages = true,
                friendRequests = true,
                sharedProgress = true,
                corrections = true,
                adminMessages = true,
                messagePreview = true,
            ).revision,
            "Même avec tout le reste ouvert, la révision reste fausse.",
        )
    }

    // -------------------------------------------------------------------------------------------
    // Les deux notifications que le client fabrique
    // -------------------------------------------------------------------------------------------

    @Test
    fun `le rappel du soir est a dix-neuf heures sur le canal learning`() {
        val rappel = Notifications.learningReminder
        assertEquals(19, rappel.hour, "L'heure du rappel d'apprentissage est 19 h.")
        assertEquals(0, rappel.minute)
        assertEquals("learning", rappel.channelId, "Le rappel du soir va sur le canal d'apprentissage.")
        assertEquals("Ton programme du Coran", rappel.title)
        assertEquals(
            "Retrouve ton passage du jour et prends un moment pour apprendre.",
            rappel.body,
        )
    }

    @Test
    fun `le rappel s'annule toujours et ne se planifie que si active et autorise`() {
        // `syncLearningReminder` retire d'abord **toutes** les notifications de ce `kind`, puis
        // n'en planifie une que si le réglage est actif **et** la permission accordée. Sans
        // l'annulation, chaque passage de l'effet ajouterait un rappel : le téléphone sonnerait
        // autant de fois qu'on a ouvert l'écran.
        assertTrue(
            Notifications.shouldScheduleLearningReminder(enabled = true, granted = true),
            "Actif et autorisé : un rappel doit être planifié.",
        )
        assertFalse(
            Notifications.shouldScheduleLearningReminder(enabled = false, granted = true),
            "Le réglage est éteint : rien ne doit être planifié.",
        )
        assertFalse(
            Notifications.shouldScheduleLearningReminder(enabled = true, granted = false),
            "La permission manque : rien ne doit être planifié.",
        )
        assertFalse(
            Notifications.shouldScheduleLearningReminder(enabled = false, granted = false),
            "Ni l'un ni l'autre : rien ne doit être planifié.",
        )
    }

    @Test
    fun `la notification de test attend cinq secondes`() {
        assertEquals(5, Notifications.TEST_DELAY_SECONDS)
        assertEquals("Test des notifications", Notifications.TEST_TITLE)
        assertEquals(
            "Les notifications sont autorisées sur ce téléphone.",
            Notifications.TEST_BODY,
        )
    }
}
