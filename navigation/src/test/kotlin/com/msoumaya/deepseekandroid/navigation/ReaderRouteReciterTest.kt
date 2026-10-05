package com.msoumaya.deepseekandroid.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tient le **branchement du récitateur** dans la route du lecteur.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * Les deux conditions sont réunies, et il faut les deux.
 *
 *  1. **Le comportement est hors de portée.** `ReaderRoute` est une fonction `@Composable` qui
 *     prend un `AppContainer` réel : la déclencher demanderait un hôte Compose **et** un
 *     conteneur complet — disque, compte, réseau.
 *  2. **Sa disparition serait silencieuse.** La règle de préférence est éprouvée dans
 *     `core:domain` (`ReciterPreferenceTest`), et le dépôt dans `core:data`
 *     (`AudioSettingsRepositoryTest`). Entre les deux, **personne ne regarde la route** : si elle
 *     cessait de lire l'état du compte, ou de lui reporter la valeur de l'appareil, les deux
 *     suites resteraient vertes et le seul symptôme serait un récitateur qui ne suit pas la
 *     personne d'un appareil à l'autre.
 *
 * ## Les trois décisions que ce contrôle vise
 *
 *  - **le compte est lu** : c'est ce qui fait qu'un choix fait ailleurs s'applique ici ;
 *  - **l'appareil apprend, le compte apprend — une fois chacun** : l'adoption d'une valeur sans
 *    propriétaire et le report vers le compte sont deux écritures distinctes, et le report ne
 *    part que si le compte **ignorait** la valeur ;
 *  - **un changement de vitesse n'est pas un choix de récitateur** : la feuille appelle la même
 *    lambda pour tous les réglages, et confondre les deux écrirait dans le compte un récitateur
 *    que personne n'a choisi.
 *
 * ## Ce qu'il prouve, et ce qu'il ne prouve pas
 *
 * Il prouve que la route **branche** la règle. Il ne prouve pas que la règle est juste : c'est
 * `ReciterPreferenceTest`, dans `core:domain`.
 */
class ReaderRouteReciterTest {

    @Test
    fun `la route lit le recitateur du compte`() {
        assertTrue(
            sourceDeLaRoute().contains("synced = userState?.audioPreferences?.reciterId,"),
            "La route ne lit plus le récitateur de l'état du compte : un choix fait sur un autre " +
                "appareil serait ignoré ici, et la mémoire locale l'emporterait.",
        )
    }

    @Test
    fun `la route donne a la regle les deux memoires de l'appareil`() {
        val source = sourceDeLaRoute()
        assertTrue(
            source.contains("stored = storedReciterId,"),
            "La mémoire de l'appareil n'est plus transmise : la règle ne pourrait plus la retenir.",
        )
        assertTrue(
            source.contains("owner = storedReciterOwnerId,"),
            "Le propriétaire de la mémoire de l'appareil n'est plus transmis : la règle ne " +
                "pourrait plus distinguer le choix de ce compte de celui d'un autre.",
        )
    }

    @Test
    fun `le lecteur recoit le recitateur retenu`() {
        assertTrue(
            sourceDeLaRoute().contains("initialReciterId = reciterRetenu,"),
            "Le lecteur ne reçoit plus le récitateur retenu par la règle : il retomberait sur son " +
                "défaut, et le choix de la personne serait ignoré à chaque ouverture.",
        )
    }

    @Test
    fun `le defaut est nomme, pour pouvoir reconnaitre un choix`() {
        // Sans cette valeur nommée, la comparaison de la lambda de changement porterait sur
        // `null`, et n'importe quel réglage ferait passer le défaut pour un choix.
        assertTrue(
            sourceDeLaRoute().contains("reciterResolution.reciterId ?: Audio.defaultReciter.id"),
            "Le récitateur de départ n'est plus nommé : la route ne peut plus distinguer un " +
                "changement de récitateur d'un changement de vitesse.",
        )
    }

    @Test
    fun `l'appareil apprend ce que le compte sait`() {
        assertTrue(
            blocDeLaResolution().contains("container.audioSettings.remember(retenu, proprietaire)"),
            "L'appareil n'apprend plus à qui appartient la valeur retenue : le document garderait " +
                "un récitateur sans propriétaire, que le compte suivant adopterait.",
        )
    }

    @Test
    fun `le compte ne recoit que ce que l'appareil savait`() {
        val bloc = blocDeLaResolution()
        // La condition **et** l'écriture : sans la condition, la route réécrirait le compte à
        // chaque composition ; sans l'écriture, le report n'aurait pas lieu du tout.
        assertTrue(
            bloc.contains("if (reciterResolution.pushToState && retenu != null && userState != null) {"),
            "Le report vers le compte n'est plus conditionné à une valeur que le compte ignore : " +
                "un compte qui a déjà choisi pourrait être réécrit par l'appareil.",
        )
        assertTrue(
            bloc.contains("container.userState.mutate { state ->"),
            "Le report vers le compte n'écrit plus rien : un choix fait hors ligne resterait " +
                "invisible depuis les autres appareils.",
        )
    }

    @Test
    fun `le recitateur local est ecrit avec son proprietaire`() {
        assertTrue(
            blocDuChangement().contains("container.audioSettings.save(settings, reciterId, proprietaire)"),
            "Le récitateur local n'est plus enregistré avec son propriétaire : deux comptes sur " +
                "le même appareil partageraient la même mémoire, et le choix du premier serait " +
                "écrit dans le compte du second.",
        )
    }

    @Test
    fun `un changement de vitesse n'ecrit pas de choix de recitateur`() {
        assertTrue(
            blocDuChangement().contains("if (reciterId != reciterRetenu) {"),
            "La route écrit le récitateur dans le compte à chaque réglage : changer la vitesse " +
                "ferait pousser une synchronisation, et un défaut jamais choisi deviendrait un choix.",
        )
    }

    @Test
    fun `hors connexion, le proprietaire est l'invite`() {
        // C'est le `userId ?? 'guest'` de la clé d'origine. Sans lui, un choix fait sans compte
        // appartiendrait à « personne », et le premier compte connecté se l'approprierait.
        assertTrue(
            blocDuChangement().contains("userState?.userId ?: ReciterPreference.GUEST"),
            "Le propriétaire n'est plus désigné hors connexion : un choix fait sans compte " +
                "pourrait être adopté par le premier compte venu.",
        )
    }

    @Test
    fun `le source lu est bien celui de la route`() {
        assertTrue(
            sourceDeLaRoute().contains("fun ReaderRoute("),
            "Le fichier lu ne déclare pas `fun ReaderRoute(` : le chemin résolu ne désigne pas la " +
                "route du lecteur.",
        )
    }

    /**
     * L'effet qui apprend à l'appareil et au compte, et lui seul.
     *
     * **Borné** : `container.userState.mutate` et `runCatching` vivent aussi ailleurs dans ce
     * fichier — le signet, le marquage, la suppression. Une recherche sans borne resterait verte
     * si cet effet perdait sa propre écriture.
     *
     * La borne est accompagnée de son contrôle : `substringAfter` **sans repli** rend la chaîne
     * entière quand le délimiteur est absent.
     */
    private fun blocDeLaResolution(): String {
        val source = sourceDeLaRoute()
        val marqueur = "LaunchedEffect(reciterResolution, userState) {"
        assertTrue(
            source.contains(marqueur),
            "La route n'a plus d'effet sur la décision du récitateur : ni l'appareil ni le compte " +
                "n'apprendraient plus rien.",
        )
        return source.substringAfter(marqueur).substringBefore("\n    }")
    }

    /**
     * La lambda qui enregistre un changement de réglage, et elle seule.
     *
     * **Bornée** : `runCatching` et `container.audioSettings.save` sont employés ailleurs, et
     * `container.userState.mutate` l'est aussi plus bas dans le même fichier.
     */
    private fun blocDuChangement(): String {
        val source = sourceDeLaRoute()
        val marqueur = "onAudioSettingsChanged = {"
        assertTrue(
            source.contains(marqueur),
            "La route n'enregistre plus les réglages d'écoute : ni l'appareil ni le compte ne " +
                "retiendraient un changement.",
        )
        return source.substringAfter(marqueur).substringBefore("\n        },")
    }

    /**
     * Le source de la route du lecteur.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part
     * de là.
     */
    private fun sourceDeLaRoute(): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/navigation/ReaderRoute.kt"
        val candidats = listOf(File(relatif), File("navigation/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "ReaderRoute.kt introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }
}
