package com.msoumaya.deepseekandroid.core.domain

import com.msoumaya.deepseekandroid.core.model.AppJson
import com.msoumaya.deepseekandroid.core.model.AppState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Fusion hors ligne à trois voies : état de base, état local, état serveur.
 *
 * Porté depuis `src/core/offlineMerge.ts`. La règle est volontairement simple et prudente :
 *
 *  - un champ local **inchangé** depuis la base hérite de la valeur serveur ;
 *  - un champ local **modifié** l'emporte, y compris une suppression ;
 *  - deux listes d'objets portant un `id` sont fusionnées par identifiant, jamais remplacées ;
 *  - trois listes ont une règle propre, parce que ce sont des **journaux** qu'on ne doit
 *    jamais raccourcir : les validations et les historiques sont réunis, `readPages` et
 *    `completed` sont des ensembles triés.
 *
 * La fusion s'applique sur l'**arbre JSON** plutôt que sur les types Kotlin. C'est
 * délibéré : un client Android plus ancien doit pouvoir fusionner un état écrit par un
 * client plus récent, dont il ignore certains champs, sans les perdre. Une fusion typée
 * ferait disparaître les champs inconnus.
 */
object OfflineMerge {

    /**
     * Fusionne trois états.
     *
     * Renvoie [local] sans modification si l'un des trois est absent ou si les identifiants
     * d'utilisateur divergent : mieux vaut ne rien fusionner que de mélanger deux comptes.
     */
    fun mergeOfflineState(base: AppState?, local: AppState, remote: AppState?): AppState {
        if (remote == null || base == null) return local
        if (base.userId != local.userId || remote.userId != local.userId) return local
        // Une remise à zéro volontaire ne doit pas être annulée par le serveur.
        if (base.onboardingDone && !local.onboardingDone) return local

        val merged = merge(
            base = AppJson.encodeToJsonElement(AppState.serializer(), base),
            local = AppJson.encodeToJsonElement(AppState.serializer(), local),
            remote = AppJson.encodeToJsonElement(AppState.serializer(), remote),
            path = "",
        ) ?: return local

        val decoded = runCatching {
            AppJson.decodeFromJsonElement(AppState.serializer(), merged)
        }.getOrElse { return local }

        // `updatedAt` doit dépasser strictement les deux horodatages lus : sinon la prochaine
        // synchronisation considérerait l'état fusionné comme plus ancien que l'un des deux.
        val updatedAt = Dates.iso(
            maxOf(
                System.currentTimeMillis(),
                Dates.parseIsoMillis(local.updatedAt) + 1,
                Dates.parseIsoMillis(remote.updatedAt) + 1,
            ),
        )
        return decoded.copy(userId = local.userId, updatedAt = updatedAt)
    }

    private fun merge(base: JsonElement?, local: JsonElement?, remote: JsonElement?, path: String): JsonElement? {
        if (same(local, base)) return remote
        if (same(remote, base) || same(local, remote)) return local

        if (local is JsonArray && remote is JsonArray) {
            if ((local + remote).all { it.isIdentifiedObject() }) {
                val b = keyed(base as? JsonArray)
                val l = keyed(local)
                val r = keyed(remote)
                val ids = LinkedHashSet<String>().apply { addAll(l.keys); addAll(r.keys) }
                val rows = ArrayList<JsonElement>(ids.size)
                for (id in ids) {
                    val left = l[id]
                    val right = r[id]
                    val value = if (left == null && (right as? JsonObject)?.statusString() == "done") {
                        right
                    } else {
                        merge(b[id], left, right, "$path.$id")
                    }
                    if (value != null && value !is JsonNull) rows.add(value)
                }
                return JsonArray(rows)
            }

            if (path.endsWith("validations") || path.endsWith("History")) {
                // Journaux : on réunit sans jamais écraser, en dédoublonnant par contenu.
                val seen = LinkedHashMap<String, JsonElement>()
                for (v in remote) seen[v.toString()] = v
                for (v in local) seen[v.toString()] = v
                return JsonArray(seen.values.toList())
            }

            if (path.endsWith("readPages")) {
                return JsonArray(sortedDistinctNumbers(local, remote))
            }

            if (path.endsWith("completed")) {
                val numbers = sortedDistinctNumbersOrNull(local, remote)
                if (numbers != null) return JsonArray(numbers)
            }

            return local
        }

        val localObj = local as? JsonObject
        val baseObj = base as? JsonObject
        if (remote is JsonObject && (localObj != null || (local == null && baseObj != null))) {
            val localSource = localObj ?: JsonObject(emptyMap())
            val keys = LinkedHashSet<String>().apply {
                addAll(baseObj?.keys ?: emptySet())
                addAll(localSource.keys)
                addAll(remote.keys)
            }
            val result = LinkedHashMap<String, JsonElement>(keys.size)
            for (key in keys) {
                val value = merge(baseObj?.get(key), localSource[key], remote[key], "$path.$key")
                if (value != null && value !is JsonNull) result[key] = value
            }
            // Un suivi d'étude porte `through` et `end` : le statut se recalcule, il ne se
            // fusionne pas. C'est ce qui évite un « terminé » conservé alors que la fin a
            // été reculée sur l'autre appareil.
            val through = (result["through"] as? JsonPrimitive)?.intOrNull
            val end = (result["end"] as? JsonPrimitive)?.intOrNull
            if (through != null && end != null) {
                result["status"] = JsonPrimitive(if (through >= end) "completed" else "partial")
            }
            return JsonObject(result)
        }

        if (path.endsWith(".through")) {
            val l = (local as? JsonPrimitive)?.intOrNull
            val r = (remote as? JsonPrimitive)?.intOrNull
            if (l != null && r != null) return JsonPrimitive(maxOf(l, r))
        }

        if (path.endsWith(".status")) {
            val l = (local as? JsonPrimitive)?.contentOrNull
            val r = (remote as? JsonPrimitive)?.contentOrNull
            if (l == "done" || r == "done") return JsonPrimitive("done")
        }

        return local
    }

    /**
     * Égalité structurelle de deux éléments JSON.
     *
     * Le client d'origine compare `JSON.stringify(a) === JSON.stringify(b)`. Ici, l'égalité
     * de `JsonObject` est indépendante de l'ordre des clés : c'est une différence assumée,
     * dans le sens d'une fusion plus stable (deux écritures équivalentes sont reconnues
     * comme identiques au lieu de déclencher un conflit artificiel).
     */
    private fun same(a: JsonElement?, b: JsonElement?): Boolean = a == b

    private fun JsonElement.isIdentifiedObject(): Boolean =
        this is JsonObject && (this["id"] as? JsonPrimitive)?.isString == true

    private fun JsonObject.statusString(): String? =
        (this["status"] as? JsonPrimitive)?.contentOrNull

    private fun keyed(array: JsonArray?): Map<String, JsonElement> {
        if (array == null) return emptyMap()
        val out = LinkedHashMap<String, JsonElement>(array.size)
        for (element in array) {
            val id = ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull ?: continue
            out[id] = element
        }
        return out
    }

    private fun sortedDistinctNumbers(local: JsonArray, remote: JsonArray): List<JsonElement> =
        (local + remote)
            .mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            .distinct()
            .sorted()
            .map { JsonPrimitive(it) }

    /** Renvoie `null` si un seul élément n'est pas un nombre : la règle ne s'applique alors pas. */
    private fun sortedDistinctNumbersOrNull(local: JsonArray, remote: JsonArray): List<JsonElement>? {
        val all = local + remote
        if (!all.all { (it as? JsonPrimitive)?.intOrNull != null }) return null
        return all.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.distinct().sorted().map { JsonPrimitive(it) }
    }
}
