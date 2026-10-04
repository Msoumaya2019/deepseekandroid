package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.Program
import com.msoumaya.deepseekandroid.core.model.AppState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Dernier état confirmé par le serveur.
 *
 * Il n'est pas un cache d'affichage : c'est la **base** de la fusion à trois voies. Sans lui,
 * `OfflineMerge` ne peut pas distinguer « ce champ n'a pas été touché localement » de « ce
 * champ a été modifié localement », et prendrait systématiquement le local — écrasant le
 * travail fait sur l'autre appareil.
 */
@Serializable
data class Snapshot(val state: AppState? = null)

/**
 * État applicatif persisté sur l'appareil.
 *
 * **Un fichier par compte**, comme la table `account_state` du client d'origine, indexée par
 * `user_id`. Un fichier unique partagé aurait deux conséquences fâcheuses : un utilisateur qui
 * se reconnecte hors ligne retrouverait un état vide au lieu de sa progression, et la base de
 * fusion d'un compte servirait à un autre — ce qui désactiverait silencieusement la fusion à
 * trois voies au pire moment, juste après un changement de compte.
 *
 * S'y ajoutent :
 *  - [anonymous] : l'état avant toute connexion. Il survit à l'installation et permet
 *    d'utiliser l'application sans compte ;
 *  - un fichier de base par compte, pour la fusion à trois voies.
 *
 * **Le `userId` porté par l'état est un garde-fou, pas une décoration.** Le client d'origine
 * ouvre sa base avec `WHERE user_id = ?` et refuse une ligne qui ne correspond pas ; ici,
 * [accountChangesFor] tient ce rôle. Sans lui, un fichier attribué par erreur à un compte
 * ferait afficher la progression d'un autre.
 */
class LocalStateStore(private val root: File) {

    val anonymous = JsonFileStore(
        file = File(root, "state_anonymous.json"),
        serializer = AppState.serializer(),
        validate = Program::isValidPersistedState,
        default = { Program.defaultState() },
    )

    private val anonymousBase = JsonFileStore(
        file = File(root, "state_base_anonymous.json"),
        serializer = Snapshot.serializer(),
        validate = { it.state == null || Program.isValidPersistedState(it.state) },
        default = { Snapshot() },
    )

    private val states = ConcurrentHashMap<String, JsonFileStore<AppState>>()
    private val bases = ConcurrentHashMap<String, JsonFileStore<Snapshot>>()

    /** Magasin de l'état du compte [ownerId]. */
    fun accountFor(ownerId: String): JsonFileStore<AppState> = states.computeIfAbsent(ownerId) { id ->
        JsonFileStore(
            file = File(root, "state_account_${fileToken(id)}.json"),
            serializer = AppState.serializer(),
            validate = Program::isValidPersistedState,
            // Un état par défaut est déjà **au nom du compte** : l'écran n'a donc jamais à
            // gérer un état anonyme pour un utilisateur connecté.
            default = { Program.defaultState().copy(userId = id) },
        )
    }

    /** Magasin de la base de fusion du compte [ownerId]. */
    fun baseFor(ownerId: String): JsonFileStore<Snapshot> = bases.computeIfAbsent(ownerId) { id ->
        JsonFileStore(
            file = File(root, "state_base_${fileToken(id)}.json"),
            serializer = Snapshot.serializer(),
            validate = { it.state == null || Program.isValidPersistedState(it.state) },
            default = { Snapshot() },
        )
    }

    /** Magasin à utiliser selon la présence d'une session ouverte. */
    fun storeFor(ownerId: String?): JsonFileStore<AppState> =
        if (ownerId == null) anonymous else accountFor(ownerId)

    /**
     * Vrai si un document d'état a déjà été écrit pour [ownerId].
     *
     * **Ce n'est pas une commodité de diagnostic, c'est un garde-fou.** Un magasin rend
     * toujours un document — un compte sans fichier reçoit `defaultState().copy(userId = id)` —
     * donc rien, dans la valeur rendue, ne distingue « compte jamais rapatrié » de « compte à
     * la progression vide ». Or ouvrir l'application sur un compte jamais rapatrié fait
     * afficher un programme vide, et la première séance validée dans ce programme vide est
     * ensuite poussée au serveur : elle écrase le vrai compte. Seul le fichier témoigne.
     *
     * Le fichier est écrit par toute synchronisation réussie, y compris celle qui n'a rien à
     * pousser, et par toute modification locale. Sa présence signifie donc bien « le serveur a
     * été lu pour ce compte, ou l'utilisateur y a travaillé ».
     */
    fun hasStoredStateFor(ownerId: String): Boolean = accountFor(ownerId).exists()

    /** Magasin de base à utiliser selon la présence d'une session ouverte. */
    fun baseStoreFor(ownerId: String?): JsonFileStore<Snapshot> =
        if (ownerId == null) anonymousBase else baseFor(ownerId)

    /**
     * Flux de l'état du compte [ownerId].
     *
     * Le `userId` enregistré est revérifié à chaque émission. Avec un fichier par compte, ce
     * contrôle ne devrait jamais se déclencher ; il est là parce qu'un fichier local n'est
     * protégé par aucune politique RLS, et qu'une collision de nom ou une modification
     * extérieure ne doit pas pouvoir faire afficher la progression d'un autre.
     */
    fun accountChangesFor(ownerId: String): Flow<AppState> = accountFor(ownerId).changes.map { stored ->
        if (stored.userId == ownerId) stored else Program.defaultState().copy(userId = ownerId)
    }

    /**
     * Efface l'état et la base d'un compte.
     *
     * L'état anonyme n'est pas touché : l'utilisateur qui se déconnecte retrouve l'état local
     * qu'il avait avant de se connecter, il ne se retrouve pas devant un écran vide. Cette
     * remise à zéro n'est pas appelée à la déconnexion — voir `AuthRepository.signOut` : la
     * séparation des fichiers et la présence du `userId` suffisent à empêcher tout mélange.
     */
    suspend fun clearAccount(ownerId: String) {
        accountFor(ownerId).update { Program.defaultState().copy(userId = ownerId) }
        baseFor(ownerId).update { Snapshot() }
    }

    companion object {
        /**
         * Rend un identifiant utilisable comme nom de fichier.
         *
         * Un `uuid` est déjà sans caractère interdit ; la conversion est une assurance contre
         * un identifiant d'une autre forme, qui produirait sinon un chemin invalide — donc un
         * démarrage impossible.
         */
        fun fileToken(ownerId: String): String = buildString(ownerId.length) {
            for (character in ownerId) {
                append(if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_')
            }
        }
    }
}
