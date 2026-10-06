package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.GroupRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Mots de l'espace « Amis ».
 *
 * Un libellé faux ne casse rien : il affirme quelque chose de faux sur **un tiers** —
 * « En ligne » sur quelqu'un qui ne l'est pas, « Lu » sur un message qui ne l'a pas été. Ces
 * tests tiennent les mots, pas la mise en page.
 */
class SocialTextTest {

    @Test
    fun `les trois filtres sont dans l'ordre du selecteur`() {
        assertEquals("Tous", SocialText.FILTER_ALL)
        assertEquals("En ligne", SocialText.FILTER_ONLINE)
        assertEquals("Demandes", SocialText.FILTER_REQUESTS)
    }

    @Test
    fun `le filtre En ligne et la presence En ligne sont deux constantes distinctes`() {
        // L'original ecrit `'En ligne'` a deux endroits qui n'ont rien a voir : le **filtre** de
        // la liste et la **presence** d'un ami. Les deux valeurs coincident, et c'est la
        // coincidence qu'on fige ici : si l'une devait changer, ce test le dirait au lieu de
        // laisser l'autre suivre en silence.
        assertEquals(SocialText.FILTER_ONLINE, SocialText.ONLINE)
    }

    @Test
    fun `la presence et l'absence se disent en deux mots distincts`() {
        assertNotEquals(SocialText.ONLINE, SocialText.OFFLINE)
    }

    @Test
    fun `l'accuse de lecture se dit en deux mots distincts`() {
        assertNotEquals(SocialText.READ, SocialText.SENT)
        assertEquals("Lu", SocialText.READ)
        assertEquals("Envoyé", SocialText.SENT)
    }

    @Test
    fun `le nom de soi-meme est Moi, le repli est Membre`() {
        assertEquals("Moi", SocialText.ME)
        assertEquals("Membre", SocialText.MEMBER)
        assertNotEquals(SocialText.ME, SocialText.MEMBER)
    }

    @Test
    fun `les replis de phrase sont en minuscule, comme dans l'original`() {
        // « Invitation de un membre » et « Message a cet ami » : l'original les ecrit en
        // minuscule parce qu'ils s'inserent dans une phrase, alors que FRIEND ouvre un libelle.
        assertEquals("un membre", SocialText.A_MEMBER)
        assertEquals("cet ami", SocialText.THIS_FRIEND)
        assertEquals("Ami", SocialText.FRIEND)
        assertTrue(SocialText.A_MEMBER.first().isLowerCase())
        assertTrue(SocialText.THIS_FRIEND.first().isLowerCase())
    }

    @Test
    fun `le repli de service cite le code entre parentheses`() {
        assertEquals("Erreur de service (42).", SocialText.serviceError("42"))
        assertEquals("Erreur de service (PGRST301).", SocialText.serviceError("PGRST301"))
    }

    @Test
    fun `la duree complete les secondes a deux chiffres, pas les minutes`() {
        assertEquals("0:00", SocialText.duration(0, 0))
        assertEquals("0:05", SocialText.duration(0, 5))
        assertEquals("0:59", SocialText.duration(0, 59))
        assertEquals("1:00", SocialText.duration(1, 0))
        assertEquals("1:05", SocialText.duration(1, 5))
        assertEquals("10:00", SocialText.duration(10, 0))
        assertEquals("120:07", SocialText.duration(120, 7))
    }

    @Test
    fun `les deux replis d'erreur sont distincts et non vides`() {
        assertNotEquals(SocialText.GENERIC_ERROR, SocialText.CONNECTION_NEEDED)
        assertTrue(SocialText.GENERIC_ERROR.isNotBlank())
        assertTrue(SocialText.CONNECTION_NEEDED.isNotBlank())
        assertTrue(
            SocialText.GENERIC_ERROR.endsWith("."),
            "une phrase d'erreur se termine par un point : ${SocialText.GENERIC_ERROR}",
        )
    }

    @Test
    fun `toutes les etiquettes fixes sont renseignees`() {
        // **Toutes**, et non une liste recopiee a la main. Une liste ecrite a la main cesse
        // d'etre exhaustive des qu'une constante est ajoutee — c'est-a-dire exactement au moment
        // ou le controle devrait parler. Les champs sont donc lus sur l'objet lui-meme.
        val champs = SocialText::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.name to (it.get(SocialText) as String) }

        assertTrue(champs.isNotEmpty(), "aucune constante lue : le controle ne mesurerait rien")
        champs.forEach { (nom, valeur) ->
            assertTrue(valeur.isNotBlank(), "SocialText.$nom est vide")
        }

        // Une seule repetition attendue : le **filtre** « En ligne » et la **presence** « En
        // ligne », que la note de classe explique. Toute autre repetition serait une etiquette
        // posee deux fois la ou le lecteur en attend deux differentes.
        val valeurs = champs.map { it.second }
        assertEquals(valeurs.size, valeurs.distinct().size + 1, "seul En ligne se repete")
    }

    @Test
    fun `l'instant d'un message est ecrit au format de l'original`() {
        // Le motif est celui demande par l'original — jour numerique, mois abrege, heure et
        // minute —, et la locale est **fixee** a fr-FR : un telephone regle en anglais doit
        // afficher « 2 mars 09:30 » et non « Mar 2, 09:30 ».
        //
        // L'attendu est recalcule par le JDK avec le meme motif et la meme locale, et non ecrit
        // en dur : un test qui fixe « 2 mars 11:30 » echouerait sur une machine reglee sur un
        // autre fuseau, et personne ne saurait si c'est le code ou le fuseau qui a bouge.
        val iso = "2026-03-02T09:30:00Z"
        val attendu = java.time.format.DateTimeFormatter
            .ofPattern("d MMM HH:mm", java.util.Locale.FRANCE)
            .format(java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()))

        assertEquals(attendu, SocialText.dayStamp(iso))
        assertTrue(attendu.contains("mars"), "le mois doit etre ecrit en francais : $attendu")
    }

    @Test
    fun `un instant illisible est absent, pas une date d'erreur`() {
        // L'original ecrit « Invalid Date », qui n'apprend rien a personne. Ici l'instant
        // illisible est traite comme **absent**, et l'appelant retombe sur un texte vrai.
        assertEquals(null, SocialText.dayStamp("pas une date"))
        assertEquals(null, SocialText.dayStamp(""))
        assertEquals(null, SocialText.dayStamp("2026-13-45T99:99:99Z"))
    }

    // ------------------------------------------------------------------ conversation

    @Test
    fun `l'heure d'un message suit la locale fixee`() {
        // Meme regle que `dayStamp` : l'attendu est **recalcule par le JDK** avec le meme motif et
        // la meme locale, jamais ecrit en dur — une machine reglee sur un autre fuseau ferait
        // echouer un attendu fige, sans qu'on sache si c'est le code ou le fuseau qui a bouge.
        val iso = "2026-03-10T17:00:00Z"
        val attendu = java.time.format.DateTimeFormatter
            .ofPattern("HH:mm", java.util.Locale.FRANCE)
            .format(java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()))

        assertEquals(attendu, SocialText.clock(iso))
        assertTrue(attendu.matches(Regex("""\d{2}:\d{2}""")), "heure et minute sur deux chiffres : $attendu")
    }

    @Test
    fun `une heure illisible est absente, pas une heure d'erreur`() {
        assertEquals(null, SocialText.clock("pas une heure"))
        assertEquals(null, SocialText.clock(""))
        assertEquals(null, SocialText.clock("2026-13-45T99:99:99Z"))
    }

    @Test
    fun `l'instant d'un rendez-vous porte les secondes, comme toLocaleString`() {
        // L'original appelle `toLocaleString('fr-FR')` **sans options** : le format par defaut
        // porte les secondes. Les retrancher ferait diverger les deux clients sur la meme date.
        val iso = "2026-03-10T17:00:00Z"
        val attendu = java.time.format.DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss", java.util.Locale.FRANCE)
            .format(java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()))

        assertEquals(attendu, SocialText.appointmentStamp(iso))
        assertTrue(attendu.endsWith(":00"), "les secondes sont ecrites : $attendu")
        assertEquals(null, SocialText.appointmentStamp(""))
    }

    @Test
    fun `le partage d'etape arrondit le ratio, il ne le tronque pas`() {
        assertEquals(
            "Mon objectif Juz’ ‘Amma est atteint à 50 %. Cette semaine, j’ai appris 12 versets.",
            SocialText.sharedProgress("Juz’ ‘Amma", 0.5, 12),
        )
        // 0.125 * 100 vaut 12.5 **exactement** en binaire : l'original arrondit au superieur
        // (`Math.round`), et tronquer donnerait 12.
        assertTrue(SocialText.sharedProgress("X", 0.125, 0).contains("13 %"))
        assertTrue(SocialText.sharedProgress("X", 0.994, 0).contains("99 %"), "99,4 s'arrondit a 99")
        assertTrue(SocialText.sharedProgress("X", 0.0, 0).contains("0 %"))
        assertTrue(SocialText.sharedProgress("X", 1.0, 0).contains("100 %"))
    }

    @Test
    fun `le pourcentage du partage se met en forme comme celui de l'ecran Progres`() {
        // Le « % » vient de `ProgressText.percent` : deux ecrans qui annoncent le meme objectif ne
        // doivent pas l'ecrire de deux facons.
        assertTrue(SocialText.sharedProgress("X", 0.42, 1).contains(ProgressText.percent(42)))
    }

    @Test
    fun `le role d'un membre est traduit, pas recopie de la base`() {
        // **Ecart assume** : l'original affiche `{m.role}`, donc « owner », « moderator »,
        // « member » — le vocabulaire du serveur dans une interface francaise. La note de
        // `SocialText.role` declare l'ecart ; ce test le fige.
        assertEquals("Propriétaire", SocialText.role(GroupRole.OWNER))
        assertEquals("Modérateur", SocialText.role(GroupRole.MODERATOR))
        assertEquals("Membre", SocialText.role(GroupRole.MEMBER))
        assertNotEquals("owner", SocialText.role(GroupRole.OWNER))
    }

    @Test
    fun `la ligne de membre mentionne l'invitation en attente`() {
        assertEquals("Amina · Modérateur", SocialText.memberLine("Amina", "Modérateur", pending = false))
        assertEquals(
            "Amina · Modérateur · invitation en attente",
            SocialText.memberLine("Amina", "Modérateur", pending = true),
        )
    }

    @Test
    fun `le compte des membres porte le plafond du cercle`() {
        assertEquals("Membres (3/5)", SocialText.membersCount(3))
        assertEquals(Social.MAX_GROUP_MEMBERS, 5)
    }

    @Test
    fun `la duree d'un enregistrement joint suit le meme format que l'accuse`() {
        assertEquals("Durée : 1:05", SocialText.durationLabel(65_000))
        assertEquals("Durée : 0:00", SocialText.durationLabel(0))
        assertEquals("Durée : 2:00", SocialText.durationLabel(120_000))
        // La meme duree que celle de l'accuse d'un message vocal, mot pour mot.
        assertTrue(SocialText.durationLabel(65_000).endsWith(SocialText.duration(1, 5)))
    }

    @Test
    fun `la semaine d'un objectif partage est ecrite telle que le serveur la stocke`() {
        assertEquals("Semaine du 2026-03-09 · 5 séances", SocialText.weekGoal("2026-03-09", 5))
    }

    @Test
    fun `l'acceptation d'un objectif et d'un rendez-vous se dit en deux etats distincts`() {
        assertNotEquals(SocialText.GOAL_ACCEPTED, SocialText.GOAL_PENDING)
        assertNotEquals(SocialText.APPOINTMENT_CONFIRMED, SocialText.APPOINTMENT_PENDING)
    }

    @Test
    fun `le libelle de l'etat dans l'en-tete couvre les quatre cas`() {
        assertEquals("Écrit un message…", SocialText.TYPING)
        assertEquals("En ligne", SocialText.ONLINE)
        assertEquals("Hors ligne", SocialText.OFFLINE)
        assertEquals("Contact administrateur", SocialText.ADMIN_CONTACT_LABEL)
        assertNotEquals(SocialText.TYPING, SocialText.ONLINE)
    }
}
