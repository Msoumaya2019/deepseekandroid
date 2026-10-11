package com.msoumaya.deepseekandroid.feature.recitations

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tient le **branchement de l'arrivée** d'une récitation à partager.
 *
 * ## Pourquoi un contrôle de forme, et non un test de comportement
 *
 * La règle est éprouvée **pour de vrai** dans `RecitationsArrivalTest` : elle est pure. Ce qui ne
 * peut pas s'éprouver, c'est ce qui l'entoure — l'attente, la relecture, et l'ordre des deux
 * gestes. Il faudrait pour cela une horloge : le module n'a **pas** `kotlinx-coroutines-test`, et
 * un test qui attendrait vraiment trente secondes ne serait pas joué. Le câblage est donc lu, comme
 * partout ailleurs dans ce dépôt.
 *
 * ## Ce qui disparaîtrait sans un mot
 *
 * Quatre choses, et chacune laisserait l'écran parfaitement normal en apparence :
 *
 *  - **l'ordre des deux gestes** : `onOpen` remet `sharingId` à `null`. Les intervertir ferait
 *    ouvrir la ligne **après** avoir ouvert le choix d'ami, donc le refermer aussitôt — la ligne
 *    s'afficherait, dépliée, sans le choix d'ami. C'est le défaut que ce fichier existe pour
 *    attraper, et il ne se voit qu'à l'exécution ;
 *  - **la relecture** : sans `refresh()`, la boucle attendrait dix fois en relisant le **même**
 *    état, et renoncerait toujours — le partage depuis le lecteur ne marcherait jamais, sans une
 *    seule erreur ;
 *  - **la borne** : une boucle sans borne ferait tourner un travail de fond indéfiniment sur une
 *    récitation jamais déposée ;
 *  - **le passage de l'identifiant à la fabrique** : la liste s'ouvrirait sur elle-même, et
 *    personne ne saurait quelle récitation on venait de partager.
 *
 * ## Ce qu'il ne prouve pas
 *
 * Il ne prouve pas que l'attente dure exactement trente secondes : il prouve que le `ViewModel`
 * **passe par** `RecitationsArrival`, dont les deux constantes sont mesurées là-bas.
 */
class RecitationsArrivalWiringTest {

    @Test
    fun `l'arrivee est suivie quand un identifiant est attendu`() {
        val vm = sourceDuViewModel()
        assertTrue(
            vm.contains("initialSharingId?.let { id ->"),
            "Le `ViewModel` ne suit plus l'arrivée de la récitation à partager : la liste " +
                "s'ouvrirait sans jamais ouvrir le choix d'ami.",
        )
        assertTrue(
            vm.contains("viewModelScope.launch { suivreLArrivee(id) }"),
            "L'arrivée n'est plus suivie dans la portée du `ViewModel` : elle mourrait avec la " +
                "première recomposition, ou ne partirait jamais.",
        )
    }

    @Test
    fun `la decision passe par la regle pure`() {
        val vm = sourceDuViewModel()
        assertTrue(
            vm.contains("RecitationsArrival.decide("),
            "Le `ViewModel` ne consulte plus la règle : il la réécrirait à la main, et les deux " +
                "copies finiraient par diverger — c'est celle qu'on ne relit pas qui resterait.",
        )
        for (argument in listOf("pendingId = id,", "openId = inputs.value.openId,", "attempts = essais,")) {
            assertTrue(
                vm.contains(argument),
                "La décision ne reçoit plus `$argument` : elle jugerait sur un fait inventé, et " +
                    "la règle mesurée par `RecitationsArrivalTest` ne serait plus celle qui agit.",
            )
        }
        assertTrue(
            vm.contains("remoteIds = repository.state.value.remote.map { it.id },"),
            "La décision ne lit plus la liste **distante** : elle jugerait sur la liste locale, " +
                "et ouvrirait un partage sur une récitation que le serveur refuse encore.",
        )
    }

    @Test
    fun `la ligne s'ouvre avant que le choix d'ami ne s'ouvre`() {
        // **L'ordre est la règle.** `onOpen` remet `sharingId` à `null` : intervertir les deux
        // gestes refermerait le choix d'ami aussitôt ouvert, et la ligne s'afficherait sans lui.
        val vm = sourceDuViewModel()
        val marqueur = "RecitationsArrival.Decision.OPEN -> {"
        assertTrue(
            vm.contains(marqueur),
            "Le `ViewModel` ne traite plus le cas où la récitation est arrivée.",
        )
        val bloc = vm.substringAfter(marqueur).substringBefore("\n                }")
        assertTrue(
            bloc.length < vm.length,
            "La borne du cas « arrivée » n'a pas mordu : le bloc lu est le fichier entier, et " +
                "l'ordre mesuré ne serait pas celui du cas.",
        )
        val ouverture = bloc.indexOf("onOpen(id)")
        val partage = bloc.indexOf("onShare(id)")
        assertTrue(ouverture >= 0, "La récitation arrivée n'est plus ouverte : le choix d'ami " +
            "s'ouvrirait sous une ligne repliée.")
        assertTrue(partage >= 0, "Le choix d'ami n'est plus ouvert : le geste « Partager » du " +
            "lecteur mènerait à la liste sans rien y faire.")
        assertTrue(
            ouverture < partage,
            "La ligne est ouverte **après** le choix d'ami : `onOpen` remet `sharingId` à `null`, " +
                "donc le choix serait refermé aussitôt — et rien ne le dirait.",
        )
    }

    @Test
    fun `la relecture passe par l'attente et par le depot`() {
        val vm = sourceDuViewModel()
        // **Le bloc, et non le fichier — et c'est une leçon payée.** `RecitationsViewModel` porte
        // **trois** appels à `repository.refresh()` : celui d'`onVisible`, celui d'`onRefresh`, et
        // celui de la relecture. La première version cherchait le motif dans le fichier entier, et
        // la falsification l'a dit : la mutation « la relecture ne relit plus la liste » retirait
        // le seul appel qui compte et **ne faisait tomber aucun test**. Une assertion non bornée
        // sur un motif qui existe ailleurs ne mesure rien. La borne est donc le cas `RETRY`, et
        // elle est vérifiée comme celle du cas `OPEN`.
        val marqueur = "RecitationsArrival.Decision.RETRY -> {"
        assertTrue(
            vm.contains(marqueur),
            "Le `ViewModel` ne traite plus le cas où la récitation n'est pas encore arrivée : la " +
                "liste ne serait jamais relue, et le partage depuis le lecteur ne s'ouvrirait pas.",
        )
        val bloc = vm.substringAfter(marqueur).substringBefore("\n                }")
        assertTrue(
            bloc.length < vm.length,
            "La borne du cas « réessayer » n'a pas mordu : le bloc lu est le fichier entier, et " +
                "la relecture mesurée ne serait pas celle du cas.",
        )
        assertTrue(
            bloc.contains("delay(RecitationsArrival.INTERVAL_MS)"),
            "La relecture n'attend plus l'intervalle de la règle : elle bouclerait à vide, ou " +
                "écrirait un nombre recopié qui finirait par diverger.",
        )
        assertTrue(
            bloc.contains("repository.refresh()"),
            "La relecture ne relit plus la liste : la boucle attendrait dix fois en relisant le " +
                "même état, et renoncerait toujours — le partage depuis le lecteur ne marcherait " +
                "jamais, sans une seule erreur.",
        )
        assertFalse(
            vm.contains("delay(3_000") || vm.contains(">= 10"),
            "Le `ViewModel` écrit à la main la borne ou l'intervalle : les deux constantes de " +
                "`RecitationsArrival` ne seraient plus la source, et la règle mesurée ne serait " +
                "plus celle qui agit.",
        )
    }

    @Test
    fun `l'identifiant traverse la fabrique jusqu'a l'ecran`() {
        assertTrue(
            sourceDuViewModel().contains("initialSharingId: String? = null,"),
            "La fabrique ne reçoit plus l'identifiant attendu : la liste s'ouvrirait sur " +
                "elle-même, et personne ne saurait quelle récitation on venait de partager.",
        )
        assertTrue(
            sourceDuViewModel().contains("initialSharingId = initialSharingId,"),
            "La fabrique n'annonce plus l'identifiant au `ViewModel` : le paramètre serait reçu " +
                "et jamais transmis.",
        )
        assertTrue(
            sourceDeLEcran().contains("initialSharingId: String? = null,"),
            "L'écran ne reçoit plus la récitation à partager : la route aurait beau la lui " +
                "porter, elle serait perdue à l'entrée.",
        )
        assertTrue(
            sourceDeLEcran().contains(
                "RecitationsViewModel.factory(LocalAppContainer.current, initialSharingId)",
            ),
            "L'écran ne passe plus l'identifiant à la fabrique : le paramètre serait reçu et " +
                "jamais transmis, ce qui est le propre d'un défaut muet.",
        )
    }

    /**
     * Le source d'un fichier du module, lu depuis la tâche de test.
     *
     * Deux chemins sont essayés, et non un seul : la tâche `Test` de Gradle s'exécute dans le
     * dossier **du module**, alors qu'un contrôle joué à la main depuis la racine du dépôt part de
     * là.
     */
    private fun sourceDe(nom: String): String {
        val relatif = "src/main/kotlin/com/msoumaya/deepseekandroid/feature/recitations/$nom"
        val candidats = listOf(File(relatif), File("feature/recitations/$relatif"))
        val fichier = candidats.firstOrNull { it.isFile }
            ?: error(
                "$nom introuvable. Chemins essayés : " +
                    candidats.joinToString { it.absolutePath },
            )
        return fichier.readText()
    }

    private fun sourceDuViewModel(): String = sourceDe("RecitationsViewModel.kt")

    private fun sourceDeLEcran(): String = sourceDe("RecitationsScreen.kt")
}
