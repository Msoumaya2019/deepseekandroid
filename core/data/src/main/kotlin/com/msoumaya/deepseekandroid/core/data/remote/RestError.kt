package com.msoumaya.deepseekandroid.core.data.remote

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.io.IOException

// ---------------------------------------------------------------------------
// Lecture d'un refus du serveur
// ---------------------------------------------------------------------------
// Une exception du client Supabase ne dit pas son message au même endroit selon sa provenance,
// et cette différence se paie à l'écran. Mesuré dans `postgrest-kt-android-3.8.0.aar` et
// `supabase-kt-3.8.0.aar`, classes `RestException` et `PostgrestRestException` :
//
//   - `RestException.error`        le texte du serveur, tel quel. Pour un `raise exception`
//                                  plpgsql, c'est **exactement** le `message` de
//                                  `supabase-js` — donc exactement ce que lisait l'original.
//   - `RestException.description`  un bloc « Code / Hint / Details » fabriqué par le client.
//   - `RestException.message`      `error` + `description` + `URL : <adresse du projet>`.
//   - `PostgrestRestException.code` le code PostgREST (`PGRST202` pour une fonction absente).
//
// **Pourquoi ne pas lire `message`.** C'est ce que fait un `catch` ordinaire, et cela passe
// inaperçu tant que rien n'est affiché. Dès qu'on l'affiche, l'écran montre le bloc de débogage
// **et l'adresse du projet** : quelqu'un qui vient de perdre sa connexion lit une URL. Le
// message lisible est dans `error`, et il faut aller l'y chercher.
// ---------------------------------------------------------------------------

/**
 * Le message lisible d'un refus du serveur, ou `null` s'il n'y en a pas.
 *
 * Rend `error` quand l'exception vient de Supabase — le texte que la fonction serveur a écrit —,
 * et retombe sur `message` pour tout le reste : une panne réseau, une erreur de programmation,
 * une exception du moteur HTTP. Ce repli n'est pas un détail : sans lui, ces erreurs-là
 * perdraient le seul texte qu'elles portent.
 */
internal fun Throwable.restMessage(): String? = when (this) {
    is RestException -> error
    else -> message
}

/**
 * Le code d'un refus PostgREST, ou `null` si l'erreur n'en porte pas.
 *
 * Seul `PostgrestRestException` en porte un. Une erreur de GoTrue (`AuthRestException`) a le
 * sien, ailleurs et sous un autre nom, et les confondre ferait passer un refus
 * d'authentification pour un refus de schéma.
 */
internal fun Throwable.restCode(): String? = (this as? PostgrestRestException)?.code

/**
 * Le code d'état HTTP d'un refus du serveur, **en texte**, ou `null` si l'erreur n'en porte pas.
 *
 * Rend une chaîne et non un `Int` parce que c'est ainsi que le client d'origine la compare :
 * `['409','Duplicate'].includes(String(error.statusCode))`. Le `String(...)` de JavaScript est
 * exactement ce que fait `toString()` ici, et le portage garde donc la comparaison textuelle
 * plutôt que d'en inventer une numérique — une conversion des deux côtés ferait diverger les deux
 * clients sur ce qui est tolérable, et la divergence serait muette.
 *
 * **Un seul type d'exception en porte un.** Relevé par `javap` sur
 * `supabase-kt-android-3.8.0.aar` : `RestException` déclare `getStatusCode(): int`, et
 * `HttpRequestException` — la panne réseau — n'en a pas. Une requête qui n'a pas atteint le
 * serveur n'a donc **pas** de code, ce qui est la vérité : il n'y a pas eu de réponse.
 */
internal fun Throwable.restStatusCode(): String? = (this as? RestException)?.statusCode?.toString()

/**
 * Vrai quand la requête **n'a pas atteint** le serveur.
 *
 * Les deux formes sont celles que `SupabaseAuthGateway` traite déjà comme « hors ligne » : le
 * moteur HTTP remonte ses pannes réseau en `IOException`, et supabase-kt les enveloppe dans une
 * `HttpRequestException`. Une troisième cause — un code d'état HTTP — n'est **pas** une absence
 * de réseau : le serveur a répondu, et dire le contraire enverrait chercher la connexion au lieu
 * du refus.
 */
internal fun Throwable.isOffline(): Boolean =
    this is HttpRequestException || this is IOException
