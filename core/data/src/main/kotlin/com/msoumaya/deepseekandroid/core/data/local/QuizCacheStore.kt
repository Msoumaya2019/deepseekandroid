package com.msoumaya.deepseekandroid.core.data.local

import com.msoumaya.deepseekandroid.core.domain.Quiz
import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Instantané du Quiz persisté sur l'appareil.
 *
 * **Un fichier par compte**, comme la table `quiz_cache` du client d'origine, dont la clé
 * primaire est `user_id`. Un fichier unique partagé aurait deux conséquences fâcheuses, et la
 * seconde est silencieuse : un utilisateur qui se reconnecte hors ligne retrouverait un quiz
 * vide au lieu de son historique, et les réponses du compte précédent serviraient au suivant —
 * or c'est **de cet instantané** que sortent les statistiques affichées.
 *
 * **Le jour est porté par le document, et non par le nom du fichier.** L'original range
 * `quiz_cache(user_id, data)` et le jour vit dans `data` ; l'écran compare ensuite `data.day` à
 * `quizDay()` pour décider si la question affichée est bien celle du jour. Un fichier par jour
 * aurait fait disparaître cette comparaison, et avec elle la règle « un instantané d'hier ne
 * vaut pas pour aujourd'hui » — celle qui évite d'afficher la question d'hier comme si l'on
 * pouvait encore y répondre.
 *
 * **Le nom du fichier est dérivé par [LocalStateStore.fileToken], et non recopié.** Les deux
 * magasins rangent leurs fichiers dans le même dossier : deux conversions qui divergeraient
 * produiraient un fichier illisible sans que rien ne le dise.
 */
class QuizCacheStore(
    private val root: File,
    private val today: () -> String = { Quiz.quizDay() },
) {

    private val stores = ConcurrentHashMap<String, JsonFileStore<QuizSnapshot>>()

    /**
     * Magasin de l'instantané du compte [ownerId].
     *
     * Un compte sans fichier reçoit un instantané **du jour** et vide — exactement ce que rend
     * `cachedQuiz()` quand rien n'est en cache. L'écran n'a donc jamais à traiter deux cas
     * qu'il ne saurait pas distinguer : un instantané absent, et un instantané qui n'est pas
     * celui du jour.
     */
    fun accountFor(ownerId: String): JsonFileStore<QuizSnapshot> =
        stores.computeIfAbsent(ownerId) { id ->
            JsonFileStore(
                file = File(root, "quiz_${LocalStateStore.fileToken(id)}.json"),
                serializer = QuizSnapshot.serializer(),
                default = { Quiz.emptySnapshot(today()) },
            )
        }
}
