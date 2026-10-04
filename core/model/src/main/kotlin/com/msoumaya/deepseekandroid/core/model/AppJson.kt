package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.json.Json

/**
 * Configuration JSON canonique.
 *
 * Les trois réglages ci-dessous ne sont pas des préférences : ce sont des exigences de
 * compatibilité avec le client React Native.
 *
 *  - [Json.ignoreUnknownKeys] : un client Android plus ancien doit pouvoir lire un état
 *    écrit par un client plus récent sans perdre ses propres champs inconnus ;
 *  - [Json.explicitNulls] = `false` : le client d'origine distingue « absent » de « null ».
 *    Écrire `"profile": null` là où l'original n'écrit rien produirait une différence
 *    permanente entre les deux clients à chaque synchronisation ;
 *  - [Json.encodeDefaults] = `true` : l'état est toujours écrit complet, comme le fait
 *    `defaultState()` côté TypeScript ;
 *  - [Json.coerceInputValues] : un état écrit par une version antérieure peut contenir une
 *    valeur d'énumération qui n'existe plus. La remplacer par la valeur par défaut est
 *    préférable à un échec de lecture : l'utilisateur retrouve son programme, et la
 *    migration corrige la valeur.
 *
 * **Note sur `explicitNulls = false`.** Le client d'origine distingue `undefined` (clé
 * absente) de `null` (clé présente et nulle). Écrire des `null` explicites ferait croire au
 * client React Native que ces champs sont renseignés ; pour `reviewCycle` par exemple, il
 * écraserait son cycle de révision local par `null`. Omettre la clé laisse le client
 * d'origine reprendre sa valeur locale : c'est le choix conservateur.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
    prettyPrint = false
}
