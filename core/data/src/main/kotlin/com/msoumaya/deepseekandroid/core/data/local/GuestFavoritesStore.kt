package com.msoumaya.deepseekandroid.core.data.local

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * Les favoris d'un **visiteur** — quelqu'un qui n'a pas ouvert de compte.
 *
 * **Pourquoi cette voie existe.** `content_favorites` n'accorde rien à `anon` :
 * `supabase/daily-contents.sql` réserve `select,insert,update,delete` à `authenticated`, et la
 * politique `favorites_own` compare `user_id` à `auth.uid()`. Un visiteur ne peut donc pas écrire
 * un favori côté serveur — non par choix du client, mais par refus du serveur. L'original en tient
 * une seconde voie, une clé `AsyncStorage` (`guest-content-favorites`), et ce magasin en est la
 * traduction.
 *
 * **Le fichier vit à la racine de l'état, hors de tout dossier de compte**, comme
 * `daily-content.json`, `audio.json` et `outbox.json`. Ce n'est pas une incohérence avec le fait
 * que les favoris soient « par compte » : c'est précisément parce qu'ils le sont que la voie du
 * visiteur ne peut pas être rangée sous un compte. Le fichier ne porte d'ailleurs aucun
 * identifiant — c'est un document anonyme, et le nommer d'après un propriétaire serait lui en
 * inventer un.
 *
 * **Ce qui arrive à ces favoris quand un compte s'ouvre.** Rien. L'original ne les efface pas, ne
 * les envoie pas, et ne les fusionne pas : ils restent sur l'appareil, et reparaissent si le
 * compte se referme. C'est une fidélité assumée — un transfert serait un geste d'écriture que ni
 * l'original ni la politique du serveur ne prévoient.
 *
 * **Le document est une liste nue**, et non un objet : `JSON.stringify(ids)` de l'original écrit
 * exactement cela sous sa clé, et le relire tel quel évite d'inventer une enveloppe que rien
 * n'utilise. Le fichier est écrit par [JsonFileStore] — fichier temporaire puis renommage
 * atomique, et un document illisible mis de côté plutôt qu'écrasé.
 */
class GuestFavoritesStore(root: File) {

    private val store: JsonFileStore<List<String>> = JsonFileStore(
        file = File(root, "guest-content-favorites.json"),
        serializer = ListSerializer(String.serializer()),
        default = { emptyList() },
    )

    /** Les identifiants mis en favori par le visiteur. Sans fichier, la liste vide. */
    suspend fun current(): Set<String> = store.current().toSet()

    /** Remplace la liste, et rend la valeur écrite. */
    suspend fun replace(ids: Set<String>): Set<String> = store.update { ids.toList() }.toSet()
}
