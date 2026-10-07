package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.RecitationRecorder
import kotlinx.serialization.Serializable
import java.io.File

// ---------------------------------------------------------------------------
// La notice des récitations, et son acceptation
// ---------------------------------------------------------------------------
// Portage de deux appels d'`AsyncStorage` dans `src/RecitationRecorder.tsx` :
//
//     const informed = await AsyncStorage.getItem(`recitation-info-${userId}`);
//     if (informed !== 'yes') { … Alert … AsyncStorage.setItem(`recitation-info-${userId}`, 'yes') … }
//
// **Pourquoi un magasin, et non un `DataStore` de réglages.** `AsyncStorage` est un espace de
// clés plat, et l'original y range une clé **par compte**. Un `DataStore` typé porterait des clés
// fixes, ou une chaîne encodée à la main ; un document JSON dit exactement ce qui est rangé — des
// couples (clé, valeur) —, et il est relu et écrit par [JsonFileStore], qui met de côté un
// document illisible au lieu de l'écraser.
//
// **La clé et la valeur viennent du domaine, et ne sont pas recopiées.** `RecitationRecorder`
// porte `noticeKey` et `noticeAccepted`, et c'est lui qui décide de ce qu'une valeur signifie :
// seule `"yes"` vaut « acceptée », toute autre valeur — `"oui"`, ou une chaîne tronquée — vaut
// « pas encore lue ». Le magasin ne juge pas ; il range et il relit.
// ---------------------------------------------------------------------------

/**
 * Ce que l'appareil retient des notices déjà lues.
 *
 * Un document unique, une entrée par compte, et la valeur **telle qu'elle a été écrite**. Le
 * portage conserve la valeur, et non un simple booléen, parce que c'est la comparaison exacte du
 * domaine qui décide : un `true` perdrait la distinction entre « acceptée » et « écrite un jour
 * avec un autre mot », que `noticeAccepted` sait faire — et c'est cette distinction qui décide
 * qu'on repose la question.
 */
@Serializable
internal data class RecitationNotices(
    val accepted: Map<String, String> = emptyMap(),
)

/**
 * Retient, par compte, que la notice sur les récitations a été acceptée.
 *
 * ## Pourquoi par compte, et pas par appareil
 *
 * C'est la règle de l'original, et elle est dans la clé : `recitation-info-${userId}`. La notice
 * parle des récitations **d'une personne** — où elles sont déposées, qui peut les écouter —, et
 * un second compte ouvert sur le même téléphone ne l'a pas lue. Une clé unique ferait taire la
 * notice pour quelqu'un qui ne l'a jamais vue, et l'application enregistrerait sa voix sans lui
 * avoir rien dit.
 *
 * ## Ce que le magasin ne fait pas
 *
 * Il ne **montre** rien et ne demande rien : le texte de la notice et le geste d'acceptation
 * vivent dans `RecitationText` et dans la surface. Ici on lit et on écrit, et c'est ce qui rend
 * la règle éprouvable sur de **vrais fichiers**, sans écran et sans appareil.
 */
class RecitationNoticeStore(root: File) {

    private val store = JsonFileStore(
        file = File(root, FILE_NAME),
        serializer = RecitationNotices.serializer(),
        default = { RecitationNotices() },
    )

    /**
     * `true` si ce compte a déjà accepté la notice.
     *
     * Le jugement appartient au domaine : la valeur rangée lui est passée telle quelle. Un compte
     * absent du document n'a rien accepté, et le `null` que rend la lecture vaut donc « non » —
     * c'est `noticeAccepted` qui le dit, pas ce magasin.
     */
    suspend fun accepted(userId: String): Boolean = RecitationRecorder.noticeAccepted(
        store.current().accepted[RecitationRecorder.noticeKey(userId)],
    )

    /**
     * Inscrit que ce compte a accepté la notice.
     *
     * La valeur écrite est celle du domaine (`NOTICE_ACCEPTED`), et non un littéral : c'est elle
     * que [accepted] relira, et deux littéraux écrits à deux endroits finiraient par diverger.
     *
     * Réaccepter écrase la même entrée : l'opération est **idempotente**, comme le `setItem` de
     * l'original — un second appui sur « Compris, enregistrer » n'ajoute pas une seconde ligne.
     */
    suspend fun accept(userId: String) {
        val cle = RecitationRecorder.noticeKey(userId)
        store.update { document ->
            document.copy(
                accepted = document.accepted + (cle to RecitationRecorder.NOTICE_ACCEPTED),
            )
        }
    }

    companion object {
        /**
         * Le nom du fichier, dans le dossier d'état de l'application.
         *
         * Il est **public** pour que le test puisse nommer le document qu'il inspecte : vérifier
         * que la clé écrite est bien `recitation-info-<compte>` demande de lire le fichier, et un
         * nom recopié dans le test ne prouverait que sa propre exactitude.
         */
        const val FILE_NAME: String = "recitation_notice.json"
    }
}
