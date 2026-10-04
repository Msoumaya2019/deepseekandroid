package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppState

/**
 * Ce qu'une synchronisation de compte a décidé.
 *
 * [shouldPush] est une **obligation**, pas une préférence : si l'appelant ne pousse pas, une
 * donnée locale reste orpheline. C'est pourquoi le dépôt ne pousse jamais sans avoir d'abord
 * lu le serveur — pousser à l'aveugle écraserait le travail fait sur l'autre appareil.
 */
data class SyncOutcome(val state: AppState, val shouldPush: Boolean)

/**
 * Compose les deux fusions du domaine pour une synchronisation de compte.
 *
 * Le domaine dispose de deux règles, écrites pour deux situations différentes, et les
 * enchaîner naïvement produirait une perte de données. Cette fonction fixe l'ordre et
 * documente pourquoi.
 *
 * **1. `Program.accountState` — l'identité et le premier chargement.** C'est la seule règle
 * qui sait qu'un appareil neuf (`cached == null`) doit **adopter** l'état du serveur au lieu
 * de pousser son état par défaut. L'inverser viderait le compte d'un utilisateur qui
 * réinstalle l'application : c'est le scénario le plus destructeur possible, et il décide de
 * l'ordre.
 *
 * **2. `OfflineMerge.mergeOfflineState` — la fusion à trois voies.** Elle n'a de sens
 * qu'avec une [base], c'est-à-dire un état serveur déjà confirmé. Sa force est d'être
 * **indépendante de l'horloge** : elle compare le local à la base pour savoir ce que
 * l'utilisateur a modifié, au lieu de comparer deux horodatages. Or `accountState` tranche,
 * lui, à l'horodatage près. Une horloge d'appareil en retard — cas banal, un téléphone mal
 * réglé ou sans réseau depuis longtemps — ferait donc perdre à `accountState` seul les
 * validations faites hors ligne. En repassant par la fusion à trois voies dès qu'une base
 * existe, ces modifications l'emportent sans dépendre d'une horloge.
 *
 * [pending] indique que la file d'attente locale contient des modifications non envoyées.
 * Il force [SyncOutcome.shouldPush] : une opération en attente doit atteindre le serveur,
 * même si le serveur paraît plus récent. Renvoyer un état identique est sans effet — l'écrit
 * serveur est un `upsert` idempotent — alors que ne pas renvoyer une modification la perdrait
 * définitivement.
 */
object AccountSync {

    fun merge(
        userId: String,
        cached: AppState?,
        remote: AppState?,
        base: AppState?,
        pending: Boolean,
    ): SyncOutcome {
        val account = Program.accountState(userId, cached, remote)

        // Le cache local n'est utilisé que s'il appartient au compte demandé : sans ce filtre,
        // la fusion à trois voies mélangerait le travail de l'utilisateur précédent.
        val local = cached?.takeIf { it.userId == userId }

        val state = if (base != null && remote != null && local != null) {
            OfflineMerge.mergeOfflineState(base, local, remote)
        } else {
            account.state
        }

        return SyncOutcome(
            state = state.copy(userId = userId),
            shouldPush = account.shouldPush || pending,
        )
    }
}
