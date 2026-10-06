package com.msoumaya.deepseekandroid.core.data.remote

import com.msoumaya.deepseekandroid.core.model.QuizSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Accès aux données du Quiz
// ---------------------------------------------------------------------------
// Portage de la partie « lecture et gestes » de `src/services/quiz.ts`.
//
// **L'interface existe pour la même raison que `SocialSource`.** Le dépôt qui la consomme
// porte des règles qui ne se voient pas à l'écran : ne pas interroger le serveur sans session,
// ne pas perdre une réponse faite hors ligne, ne jamais écraser par une réponse locale une
// réponse que le serveur connaît déjà. Ces règles se mesurent en substituant une source en
// mémoire — sans réseau, sans serveur et sans appareil.
//
// **Aucune méthode ne prend l'identifiant du compte courant.** Les fonctions serveur le lisent
// elles-mêmes dans le jeton (`auth.uid()`) ; le passer laisserait croire que le client décide
// pour qui il interroge, alors que c'est la politique du serveur qui tranche.
//
// **Ce qui n'est pas ici, et pourquoi.** Les six fonctions d'administration du quiz
// (`quiz_admin_list`, `quiz_admin_save`, `quiz_admin_delete`, `quiz_admin_sets`,
// `quiz_admin_save_set`, `quiz_admin_delete_set`) ne sont **pas** déclarées. L'administration du
// quiz est hors périmètre du client Android — la ligne est déjà écrite dans
// `ANDROID_MIGRATION.md`, section 12 : « l'administration reste sur le web ». Les déclarer « pour
// plus tard » reviendrait à entretenir une surface que rien n'appelle, et qu'aucun test ne
// pourrait couvrir autrement qu'en la singeant.
//
// **Le temps réel n'est pas ici non plus.** L'original relit l'instantané toutes les trente
// secondes, au retour au premier plan et à chaque reconnexion ; ici c'est le dépôt qui décide
// quand relire. C'est une différence de **fraîcheur**, pas de contenu : rien de ce qui se
// décide dans ce fichier n'en dépend.
// ---------------------------------------------------------------------------

/**
 * Les arguments de `quiz_answer_daily` — et, du même coup, **le document qui part sur le
 * réseau**.
 *
 * Les champs portent le `p_` de la migration, et non des noms Kotlin, parce que ce type **est**
 * le corps de l'appel. PostgREST apparie les arguments d'une fonction RPC **par nom** : un
 * `questionId` envoyé tel quel ne serait pas un argument mal typé, mais une fonction
 * introuvable — donc un message qui désigne l'appel et jamais l'argument. Renommer ces champs
 * ne serait pas cosmétique : cela casserait l'appel.
 *
 * **Un seul document pour les deux chemins, exactement comme dans l'original.** `answerDaily()`
 * de `services/quiz.ts` construit cet objet, l'enfile, puis le renvoie **tel quel** depuis la
 * file (`JSON.parse(row.payload)`). Une réponse faite hors ligne et une réponse faite en ligne
 * empruntent donc la même forme — et c'est ce qui rend la file rejouable : ce qui a été rangé
 * est mot pour mot ce qui sera envoyé.
 */
@Serializable
data class DailyAnswerPayload(
    @SerialName("p_question") val questionId: String,
    @SerialName("p_answer") val answerId: String,
    @SerialName("p_day") val day: String,
    @SerialName("p_answered_at") val answeredAt: String,
)

/**
 * Lecture et gestes du Quiz.
 *
 * Chaque méthode correspond à une fonction exportée de `services/quiz.ts` et porte le même nom
 * d'usage. Quatre parlent au serveur ; la cinquième écrit une préférence qui appartient au
 * **compte** et non à l'appareil, parce que c'est le serveur qui pousse la question du jour.
 */
interface QuizSource {

    /**
     * L'instantané du quiz pour [day] : la question du jour, **mes** réponses passées, mes défis,
     * les quiz thématiques disponibles et l'état de mes notifications.
     *
     * **La question du jour rendue ici n'a pas de solution.** La fonction serveur retire
     * `correctAnswerId`, l'explication, la source, l'arabe et la traduction avant de l'envoyer —
     * c'est la réponse **enregistrée** qui les porte. Un écran qui chercherait la solution dans
     * `daily` ne l'aurait donc jamais : il afficherait une correction vide sans que rien ne lève.
     * Le repli de l'original — `response?.question ?? daily` — n'est pas une commodité, c'est la
     * seule façon d'avoir la solution.
     *
     * @param day jour demandé, au format `AAAA-MM-JJ`, dans le fuseau de l'appareil.
     */
    suspend fun snapshot(day: String): QuizSnapshot

    /**
     * Enregistre la réponse du jour.
     *
     * Le serveur est **idempotent** : une seconde réponse pour le même jour ne fait rien. Il
     * refuse en revanche une question qui n'est pas celle du jour demandé, une réponse qui n'est
     * pas dans ses propositions, et un instant hors de la fenêtre de tolérance — c'est
     * précisément ce qui autorise à envoyer **plus tard** une réponse faite hors ligne.
     *
     * Le document est passé tel quel, et non en quatre arguments : c'est la même forme que celle
     * rangée dans la file, donc la réponse différée et la réponse immédiate empruntent le même
     * chemin — comme dans l'original.
     *
     * @param payload arguments de l'appel. [DailyAnswerPayload.answeredAt] est l'instant de la
     *   réponse **sur l'appareil**, et non celui de l'envoi : c'est lui que le serveur compare à
     *   la fenêtre du jour.
     */
    suspend fun answerDaily(payload: DailyAnswerPayload)

    /**
     * Crée un défi contre [opponentId] et rend son identifiant.
     *
     * Le serveur refuse un compte qui n'est pas un **ami accepté**, un nombre de questions autre
     * que 5 ou 10, et un quiz thématique qui ne compte pas exactement dix questions disponibles.
     * Les trois refus sont volontairement côté serveur : le client ne connaît pas l'état des
     * questions au moment où il demande.
     *
     * @param quizSetId quiz thématique, ou `null` pour des questions tirées au hasard. Un quiz
     *   thématique impose **dix** questions : en demander cinq avec un thème est refusé.
     */
    suspend fun createChallenge(opponentId: String, count: Int, quizSetId: String? = null): String

    /**
     * Répond à une question de défi.
     *
     * Le serveur refuse un défi terminé ou expiré, une question qui n'appartient pas au défi, et
     * une réponse hors des propositions. Il est idempotent : une réponse déjà donnée ne compte
     * pas deux fois.
     */
    suspend fun answerChallenge(challengeId: String, questionId: String, answerId: String)

    /**
     * Active ou coupe les notifications de quiz pour le compte courant.
     *
     * [timezone] est **vérifiée par le serveur** contre la liste des fuseaux connus, et un
     * fuseau inventé est refusé. Ce n'est pas une coquetterie : la question du jour est poussée
     * à une heure **locale**, donc un fuseau faux ferait sonner au mauvais moment sans que rien
     * ne le signale.
     */
    suspend fun setNotifications(enabled: Boolean, timezone: String)
}
