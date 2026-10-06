package com.msoumaya.deepseekandroid.core.domain

/**
 * Libellés de l'écran « Quiz ».
 *
 * Porté de `src/ui/QuizScreen.tsx`. Les chaînes sont reprises **caractère pour caractère** du
 * client d'origine, comme [SocialText] et [ProgramText] : une reformulation ferait diverger les
 * deux clients sur des mots que l'utilisateur voit, et personne ne saurait plus laquelle des
 * deux formulations est la bonne.
 *
 * **Ce fichier ne porte que ce que le dépôt prononce.** Les libellés de l'écran — les titres,
 * les invitations, les boutons — viendront avec l'écran lui-même : une constante que personne
 * n'affiche est une surface à relire sans preuve, et la déclarer « pour plus tard » reviendrait
 * à la figer avant de savoir si elle convient. Ce qui est écrit ici est **prononcé** : les trois
 * messages qu'une action du dépôt peut produire, et le repli d'erreur.
 */
object QuizText {

    /**
     * Message affiché quand une panne ne dit rien de lisible.
     *
     * **Il diffère de [SocialText.GENERIC_ERROR], et c'est délibéré.** L'original du Quiz écrit
     * `Connexion nécessaire. Réessaie.` là où l'espace social écrit `Une erreur est survenue.` :
     * toutes les actions du Quiz passent par le réseau — lire l'instantané, répondre, défier —,
     * donc la cause est presque toujours l'absence de connexion, et annoncer « une erreur est
     * survenue » ferait chercher au mauvais endroit.
     */
    const val GENERIC_ERROR = "Connexion nécessaire. Réessaie."

    /**
     * La fonction serveur n'existe pas : la migration du Quiz n'est pas déployée.
     *
     * C'est le message que `quizRpc` de `services/quiz.ts` prononce sur le code PostgREST
     * `PGRST202`. Il dit à qui exploite le projet ce qu'aucun autre message ne dirait : le client
     * est correct, c'est le schéma qui manque. Le confondre avec une panne de réseau enverrait
     * chercher la connexion au lieu de la migration.
     */
    const val SERVICE_MISSING = "Le service Quiz doit être activé sur le serveur."

    /** Aucun compte ouvert : il n'y a personne à qui attribuer la réponse. */
    const val SIGNED_OUT = "Connecte-toi pour enregistrer ta réponse."

    /**
     * L'appareil n'a pas joint le serveur, donc le défi n'a pas pu être créé.
     *
     * L'original prononce ces mots **avant** d'essayer, en interrogeant `NetInfo` ; ici ils sont
     * prononcés **après**, quand la requête est revenue sans avoir atteint le serveur. La phrase
     * est la même, et le fait est mieux établi : on ne devine plus l'absence de réseau, on la
     * constate. Le raisonnement complet est dans `QuizRepository.createChallenge`.
     */
    const val CHALLENGE_OFFLINE = "Connexion nécessaire pour lancer ce défi"
}
