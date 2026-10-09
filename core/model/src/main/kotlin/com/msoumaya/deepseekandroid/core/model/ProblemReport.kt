package com.msoumaya.deepseekandroid.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modèle d'un signalement de problème.
 *
 * Porté depuis `src/services/problemReports.ts`. Un signalement est un message d'une personne
 * vers l'administrateur : un type, un texte, et parfois une capture d'écran.
 *
 * **Il n'a pas de vie locale.** Contrairement à une récitation, il ne s'affiche nulle part sur
 * l'appareil : la file qui le garde n'est pas une liste qu'on relit, c'est une **boîte d'envoi**.
 * Ce fichier ne porte donc que ce qui voyage — et rien de ce qui attend.
 */

/**
 * La nature du problème, telle que la colonne `type` la contraint.
 *
 * Les cinq valeurs sont **exactement** celles du `check` de `supabase/problem-reports.sql`
 * (ligne 5), et leurs `@SerialName` sont ces chaînes-là : `Bug`, `Affichage`, `Audio`,
 * `Notification`, `Autre`. Les traduire — « Affichage » en `DISPLAY`, par exemple — ferait écrire
 * au client une valeur que le serveur refuse, et le refus ne se verrait qu'au dépôt, longtemps
 * après le geste.
 *
 * **L'ordre est celui de l'écran**, et il compte : le groupe de boutons radio de
 * `ProblemReportSheet` parcourt `problemTypes` dans cet ordre, et c'est donc celui dans lequel
 * les cinq choix s'affichent.
 *
 * **Il n'y a pas de membre « autre chose »**, et ce n'est pas un oubli : l'original valide
 * `problemTypes.includes(type)` à l'exécution, et cette règle **disparaît** ici parce que le type
 * est une énumération. Un type inconnu n'est plus une valeur qu'on refuse, c'est une valeur qu'on
 * ne peut pas écrire — même disparition que l'« entier » de `Recitations.validRange`, qui n'a
 * plus d'équivalent parce que la borne est un `Int`. La colonne, elle, garde son `check` : un
 * client plus ancien qui écrirait autre chose resterait refusé par le serveur.
 */
@Serializable
enum class ProblemReportType {
    @SerialName("Bug")
    BUG,

    @SerialName("Affichage")
    AFFICHAGE,

    @SerialName("Audio")
    AUDIO,

    @SerialName("Notification")
    NOTIFICATION,

    @SerialName("Autre")
    AUTRE,
    ;

    /**
     * La valeur écrite dans la colonne `type`.
     *
     * Elle est **lue sur le descripteur**, et non recopiée dans une seconde table : c'est la même
     * règle que `MushafSource.persistedKey`, et pour la même raison — deux tables finissent par
     * diverger, et la divergence serait muette, le serveur refusant la ligne bien après le geste.
     */
    val wire: String
        get() = ProblemReportType.serializer().descriptor.getElementName(ordinal)

    companion object {
        /**
         * Les cinq valeurs, dans l'ordre de l'écran.
         *
         * L'original écrit `const problemTypes=[…] as const` et s'en sert deux fois : pour
         * valider, et pour dessiner les cinq boutons. Ici la validation a disparu — voir la note
         * de tête —, et il ne reste que le dessin ; la liste est donc celle des membres, et non
         * une seconde liste qu'il faudrait tenir à jour en même temps que l'énumération.
         */
        val all: List<ProblemReportType> get() = entries.toList()
    }
}
