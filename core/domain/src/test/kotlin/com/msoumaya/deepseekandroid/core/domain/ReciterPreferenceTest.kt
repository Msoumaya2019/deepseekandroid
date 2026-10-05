package com.msoumaya.deepseekandroid.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La règle du récitateur : lequel des deux souvenirs retenir, à qui il appartient, et s'il faut
 * le remonter au compte.
 *
 * ## Pourquoi ces cas, et pas d'autres
 *
 * Chaque cas vise une **décision**, et non une branche : la précédence, l'écran que fait la
 * valeur du compte, l'adoption d'une valeur sans propriétaire, le refus d'une valeur d'autrui, et
 * la migration en un sens unique. Un cas qui ne ferait que constater `null` en entrée et `null`
 * en sortie ne prouverait rien — il est donc accompagné, partout où c'est possible, d'un second
 * souvenir non vide qui **aurait** pu être retenu.
 *
 * Le catalogue est remplacé par un prédicat explicite : la règle ne dépend pas des récitateurs
 * réels, et un test qui les lirait changerait de sens le jour où l'un d'eux est ajouté.
 */
class ReciterPreferenceTest {

    private val catalogue = setOf("ar.husary", "ar.alafasy", "ar.shaatree")
    private val connu: (String) -> Boolean = { it in catalogue }

    private fun resoudre(
        synced: String? = null,
        stored: String? = null,
        owner: String? = null,
        userId: String? = "compte-a",
    ) = ReciterPreference.resolve(synced, stored, owner, userId, connu)

    @Test
    fun `l'etat du compte gagne sur la memoire de l'appareil`() {
        val decision = resoudre(synced = "ar.alafasy", stored = "ar.husary", owner = "compte-a")
        assertEquals(
            "ar.alafasy",
            decision.reciterId,
            "Le choix fait sur un autre appareil doit s'appliquer ici : la mémoire locale ne " +
                "doit pas écraser ce que le compte sait.",
        )
    }

    @Test
    fun `l'appareil apprend ce que le compte sait`() {
        // L'effet du source écrit la clé locale dès que l'identifiant est connu, même quand il
        // vient du compte : c'est ce qui permet à l'appareil de retrouver le bon récitateur hors
        // ligne, avant que le compte soit chargé.
        val decision = resoudre(synced = "ar.alafasy", stored = null, owner = null)
        assertEquals("compte-a", decision.ownerId, "L'appareil doit retenir à qui appartient la valeur.")
    }

    @Test
    fun `le recitateur du compte n'est jamais reporte vers le compte`() {
        val decision = resoudre(synced = "ar.alafasy", stored = "ar.husary", owner = "compte-a")
        assertFalse(
            decision.pushToState,
            "Un compte qui a déjà choisi ne doit jamais être réécrit par l'appareil : le report " +
                "est une migration en un sens unique, pas une synchronisation.",
        )
    }

    @Test
    fun `sans etat, la memoire de l'appareil est retenue`() {
        val decision = resoudre(stored = "ar.husary", owner = "compte-a")
        assertEquals("ar.husary", decision.reciterId, "Le choix de l'appareil doit survivre à un compte muet.")
    }

    @Test
    fun `la valeur de l'appareil est reportee au compte`() {
        // C'est la migration : ce que l'appareil sait et que le compte ignore remonte une fois.
        val decision = resoudre(stored = "ar.husary", owner = "compte-a")
        assertTrue(
            decision.pushToState,
            "Un choix fait hors ligne doit remonter au compte, sinon il resterait invisible " +
                "depuis les autres appareils.",
        )
    }

    @Test
    fun `une valeur sans proprietaire est adoptee`() {
        // L'ancienne clé globale du source, ou un document écrit avant cette règle : le premier
        // compte qui la trouve l'adopte, et le marqueur de propriétaire l'empêche de servir deux
        // fois.
        val decision = resoudre(stored = "ar.husary", owner = null)
        assertEquals("ar.husary", decision.reciterId)
        assertEquals("compte-a", decision.ownerId, "L'adoption doit nommer son propriétaire.")
        assertTrue(decision.pushToState)
    }

    @Test
    fun `la valeur d'un autre compte est ignoree`() {
        // Le point le plus grave que cette règle évite : sans elle, le choix du premier compte
        // serait **écrit** dans le compte du second sur un appareil partagé.
        val decision = resoudre(stored = "ar.husary", owner = "compte-b")
        assertNull(
            decision.reciterId,
            "La mémoire d'un autre compte ne doit pas être appliquée, ni reportée : la fuite " +
                "serait persistée, pas seulement affichée.",
        )
        assertNull(decision.ownerId, "Et elle ne doit pas être réécrite : l'autre compte la garde.")
        assertFalse(decision.pushToState)
    }

    @Test
    fun `un choix fait hors connexion n'est pas repris par le premier compte venu`() {
        val decision = resoudre(stored = "ar.husary", owner = ReciterPreference.GUEST)
        assertNull(
            decision.reciterId,
            "Un choix fait sans compte appartient à « personne » : le premier compte connecté ne " +
                "doit pas se l'approprier.",
        )
    }

    @Test
    fun `hors connexion, la memoire de l'appareil reste celle de l'invite`() {
        val decision = resoudre(stored = "ar.husary", owner = ReciterPreference.GUEST, userId = null)
        assertEquals("ar.husary", decision.reciterId, "L'invité retrouve son propre choix.")
        assertEquals(ReciterPreference.GUEST, decision.ownerId)
    }

    @Test
    fun `hors connexion, une valeur sans proprietaire est adoptee par l'invite`() {
        val decision = resoudre(stored = "ar.husary", owner = null, userId = null)
        assertEquals("ar.husary", decision.reciterId)
        assertEquals(ReciterPreference.GUEST, decision.ownerId)
    }

    @Test
    fun `un identifiant inconnu du compte fait ecran`() {
        // `let id = reciterPreference ?? …` : la valeur du compte masque la mémoire locale même
        // quand aucun récitateur ne la porte. Descendre d'un cran appliquerait ici le choix d'un
        // autre appareil au moment précis où le compte dit autre chose.
        val decision = resoudre(synced = "ar.disparu", stored = "ar.husary", owner = "compte-a")
        assertNull(
            decision.reciterId,
            "La valeur du compte fait écran : un identifiant inconnu donne le récitateur par " +
                "défaut, et non celui de l'appareil.",
        )
        assertNull(decision.ownerId, "Rien n'a été retenu : rien ne doit être réécrit.")
        assertFalse(decision.pushToState, "Et surtout, on ne reporte pas le choix de l'appareil.")
    }

    @Test
    fun `un identifiant inconnu de l'appareil est ignore`() {
        val decision = resoudre(stored = "ar.disparu", owner = "compte-a")
        assertNull(
            decision.reciterId,
            "Un récitateur que le catalogue ne connaît plus ne doit pas être appliqué.",
        )
        assertNull(decision.ownerId, "Et il ne doit pas devenir un propriétaire : rien n'est retenu.")
        assertFalse(decision.pushToState)
    }

    @Test
    fun `rien a retenir quand les deux memoires sont vides`() {
        val decision = resoudre()
        assertNull(decision.reciterId, "Sans aucun souvenir, le lecteur retombe sur son défaut.")
        assertNull(decision.ownerId)
        assertFalse(decision.pushToState)
    }

    @Test
    fun `la meme decision est rendue pour un appareil qui a deja le choix du compte`() {
        // Deux appels identiques doivent rendre la même décision : la règle est pure, et un
        // lecteur recomposé ne doit pas voir la valeur changer sous lui.
        val premier = resoudre(synced = "ar.alafasy", stored = "ar.alafasy", owner = "compte-a")
        val second = resoudre(synced = "ar.alafasy", stored = "ar.alafasy", owner = "compte-a")
        assertEquals(premier, second)
        assertEquals("ar.alafasy", premier.reciterId)
        assertFalse(premier.pushToState, "Le compte connaît déjà la valeur : rien à remonter.")
    }

    @Test
    fun `le recitateur et son proprietaire sont retenus ensemble, ou pas du tout`() {
        // L'invariant qui porte une décision : nommer un propriétaire sans écrire la valeur
        // laisserait l'appareil porter le choix d'un **autre** compte sous le nom du compte
        // courant — et la fuite ne se verrait qu'au lancement suivant.
        val cas = listOf(
            resoudre(),
            resoudre(synced = "ar.disparu", stored = "ar.husary", owner = "compte-a"),
            resoudre(stored = "ar.disparu", owner = "compte-a"),
            resoudre(stored = "ar.husary", owner = "compte-b"),
            resoudre(synced = "ar.alafasy"),
            resoudre(stored = "ar.husary", owner = "compte-a"),
            resoudre(stored = "ar.husary", owner = null),
        )
        for (decision in cas) {
            assertEquals(
                decision.reciterId == null,
                decision.ownerId == null,
                "Rien à retenir et rien à adopter vont ensemble : $decision",
            )
        }
        // Et au moins un cas doit porter une valeur, sinon la boucle ne prouverait que le vide.
        assertTrue(cas.any { it.reciterId != null }, "Aucun cas ne retient de récitateur.")
    }

    @Test
    fun `le catalogue reel est interroge par defaut`() {
        // Le prédicat par défaut n'est pas décoratif : c'est lui que la route emploiera, et un
        // défaut qui répondrait « toujours vrai » laisserait passer n'importe quel identifiant.
        val defaut = ReciterPreference.resolve(
            synced = null,
            stored = Audio.defaultReciter.id,
            owner = null,
            userId = "compte-a",
        )
        assertEquals(Audio.defaultReciter.id, defaut.reciterId)

        val inconnu = ReciterPreference.resolve(
            synced = null,
            stored = "ar.inexistant",
            owner = null,
            userId = "compte-a",
        )
        assertNull(inconnu.reciterId, "Un identifiant hors catalogue ne doit pas être retenu.")
    }
}
