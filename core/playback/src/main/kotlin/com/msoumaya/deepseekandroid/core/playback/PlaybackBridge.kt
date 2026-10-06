package com.msoumaya.deepseekandroid.core.playback

import androidx.media3.common.Player

/**
 * Le pont entre l'application et le service.
 *
 * Un service d'avant-plan a une vie à part : Android peut le **réveiller alors que l'application
 * n'existe plus en mémoire**. Il ne peut donc pas recevoir le lecteur par constructeur, comme
 * le fait [AudioSessionHolder]. Il faut un endroit où l'application **dépose** le lecteur au
 * démarrage, et où le service le **reprend** quand il se réveille.
 *
 * Ce pont est un objet unique et mutable, ce qui mérite d'être justifié plutôt que caché :
 *
 *  - **Il est écrit une fois.** `DeepSeekApplication.onCreate` dépose le lecteur unique de
 *    l'application. Il n'y a jamais deux lecteurs, donc jamais de course à l'écriture.
 *  - **Il ne décide rien.** Il ne joue pas, ne met pas en pause, ne connaît ni verset ni
 *    récitateur. C'est un porte-manteau, et il ne doit jamais devenir autre chose.
 *  - **Il distingue « pas encore déposé » de « retiré ».** `null` veut dire *rien à publier*,
 *    dans les deux sens : le service construit alors **aucune** session, plutôt qu'une session
 *    vide. C'est le cas légitime du système qui réveille le service sans lecture en cours.
 *
 * `@Volatile` : l'écriture a lieu sur le fil principal au démarrage de l'application, la lecture
 * sur le fil du service. Sans cela, rien ne garantit que le service voie ce dépôt.
 */
object PlaybackBridge {

    @Volatile
    private var published: Player? = null

    /** Le lecteur à publier, ou `null` si l'application n'en a pas déposé. */
    val player: Player? get() = published

    /**
     * Dépose [player] pour que le service puisse le publier.
     *
     * Appelé une fois, au démarrage de l'application. Redéposer **remplace** : c'est ce qu'il
     * faut si l'application reconstruit un jour son conteneur. Ne **retire** pas l'ancien — un
     * `release` ici tuerait un lecteur que le service est peut-être en train d'utiliser.
     */
    fun publish(player: Player) {
        published = player
    }

    /**
     * Retire ce qui a été déposé.
     *
     * À n'appeler que lorsque le lecteur est réellement libéré — à la fermeture de
     * l'application, pas à la fermeture d'une séance : une séance fermée laisse le lecteur
     * vivant, prêt pour la suivante, et le service doit continuer de le trouver.
     */
    fun clear() {
        published = null
    }
}
