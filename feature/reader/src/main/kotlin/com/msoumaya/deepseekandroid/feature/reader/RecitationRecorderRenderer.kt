package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.runtime.Immutable
import com.msoumaya.deepseekandroid.core.domain.RecitationAction
import com.msoumaya.deepseekandroid.core.domain.RecitationPhase
import com.msoumaya.deepseekandroid.core.domain.RecitationRecorder

// ---------------------------------------------------------------------------
// La barre d'enregistrement réduite
// ---------------------------------------------------------------------------
// Portage de la branche `compact` de `src/RecitationRecorder.tsx` — lignes 81 à 91, la seule
// partie du fichier d'origine qui **décide ce qui s'affiche** sans exécuter quoi que ce soit.
//
// **Pourquoi ce rendu est séparé.** L'original écrit l'état de sa barre au milieu de son JSX : le
// libellé d'état est un ternaire en cascade de quatre étages, la durée un enchaînement de deux
// `??`, les gestes six conditions indépendantes, et la mise en avant un booléen répété à chaque
// appel. Rien de tout cela ne se vérifie sans monter un composant — donc rien de tout cela n'était
// vérifié.
//
// La séparation est celle du dépôt, et elle est nette :
//
//  - ce qui **décide** vit dans `core:domain/RecitationRecorder.kt` — quelle phase suit quelle
//    autre, quels gestes sont offerts dans quelle phase, ce qui empêche de commencer, quel mot
//    porte un geste, quelle durée montrer. C'est déjà écrit, et déjà éprouvé là-bas ;
//  - ce qui **assemble** vit ici : quelle ligne de texte, dans quel ordre, avec quelle mise en
//    avant, et s'il y a une phrase à lire. Un `MM:SS`, un séparateur et un ordre de boutons se
//    vérifient alors en une milliseconde, sans appareil, sans microphone et sans coroutine.
//
// **Ce que ce fichier ne fait pas.** Il ne touche ni à Compose, ni au réseau, ni à une horloge :
// les trois durées, la capacité de partage et le message lui sont **passés**. Il ne connaît pas non
// plus le drapeau `busy` de l'original, et c'est une décision — voir la note de [render].
// ---------------------------------------------------------------------------

/**
 * Ce que la barre d'enregistrement réduite affiche.
 *
 * Quatre champs, ceux du rendu de l'original, et rien de plus : la ligne d'état, la couleur qu'elle
 * prend, les gestes offerts, et la phrase à lire.
 *
 * **La durée n'est pas un champ, et c'est une mesure.** L'original la dessine **dans** la ligne
 * d'état — un seul nœud de texte, `{libellé}{' · '+durée}` — et la séparer ici obligerait la
 * surface à recoller les deux morceaux, donc à choisir elle-même le séparateur. Une règle écrite à
 * deux endroits finit par diverger des deux ; le séparateur est donc appliqué ici, une fois, et
 * [status] arrive prêt à poser.
 */
@Immutable
internal data class RecitationRecorderUi(
    /**
     * La ligne d'état, durée comprise : « ● Enregistrement · 00:07 ».
     *
     * La durée n'y est **que** lorsque la phase en porte une : en [RecitationPhase.IDLE], la ligne
     * dit « Enregistrement personnel » et rien de plus. Un `00:00` figé à côté de l'invitation à
     * enregistrer se lirait comme un enregistrement vide.
     */
    val status: String,

    /**
     * `true` quand le microphone capte.
     *
     * C'est la seule phase où la ligne d'état prend la couleur d'alerte ; en pause, en aperçu et
     * une fois gardée, elle garde la couleur ordinaire, parce que plus rien ne capte. La surface
     * choisit la couleur, ce champ dit **laquelle**.
     */
    val capturing: Boolean,

    /**
     * Les gestes offerts, dans l'ordre où ils sont dessinés.
     *
     * Jamais vide : toute phase en offre au moins un, et un test le fixe sur les cinq. C'est ce qui
     * distingue cette barre de `RevisionActionBar`, qui, elle, **disparaît** quand aucun geste n'a
     * de destination — ici il y a toujours une destination, la barre est le seul chemin.
     */
    val actions: List<RecitationActionUi>,

    /**
     * La phrase à lire sous les gestes, ou `null` quand il n'y a rien à dire.
     *
     * Une chaîne **vide** vaut `null` : l'original écrit `{!!message && <Label>…</Label>}`, et la
     * véracité d'une chaîne en JavaScript est sa **longueur**. Une chaîne d'espaces reste donc
     * affichée, et le test qui la produit est `ifEmpty` ; `ifBlank` serait un écart.
     *
     * La surface la borne à deux lignes, comme l'original (`numberOfLines={2}`) : cette borne est
     * une mise en page, et elle reste là-bas.
     */
    val message: String?,
)

/**
 * Un geste de la barre réduite, prêt à dessiner.
 *
 * [action] est l'identité du geste — c'est elle qui choisit l'icône et la destination —, et les
 * deux autres champs sont ce que le rendu en a décidé : le mot de la **barre réduite**, et la mise
 * en avant.
 *
 * **Les trois viennent du domaine, chacun d'une fonction, et aucun n'est recalculé par la
 * surface.** Le mot passe par `compactActionLabel` — et non par un libellé porté par
 * l'énumération — parce que la pleine page dit « Terminer et sauvegarder » là où la barre dit
 * « Terminer ». C'est le même geste, et deux mots ; les confondre ferait disparaître l'un des deux.
 */
@Immutable
internal data class RecitationActionUi(
    /** Le geste lui-même : ce que la surface doit faire, et quelle icône dessiner. */
    val action: RecitationAction,

    /** Le mot de la barre réduite. */
    val label: String,

    /** `true` si le geste est mis en avant — un seul par phase. */
    val primary: Boolean,
)

/**
 * Compose l'état affiché par la barre d'enregistrement réduite.
 *
 * ## Ce que le rendu reçoit, et pourquoi ces trois durées
 *
 * Les trois durées sont passées **séparément** au lieu d'une seule déjà choisie : la priorité
 * entre le brouillon en attente d'aperçu, la récitation gardée et le compteur vivant est une
 * **règle**, elle vit dans `RecitationRecorder.shownDurationMs` et elle y est éprouvée. La choisir
 * ici — ou dans la surface — ferait deux endroits qui décident de la même chose, et le second
 * serait celui qu'on oublie de corriger.
 *
 * ## Pourquoi `busy` n'est pas un paramètre
 *
 * L'original reçoit un drapeau `busy`, vrai pendant qu'une opération est en vol — demander le
 * compte, arrêter l'enregistreur, écrire le fichier, synchroniser —, et il en fait deux choses :
 * il **désactive** chaque geste, et il **divise par deux** leur opacité.
 *
 * Ni l'une ni l'autre ne change ce que la barre **dit** : le libellé d'état, la durée, les gestes
 * et le message sont les mêmes pendant l'opération et après. `busy` est un **transitoire de la
 * surface**, pas une propriété de l'état affichable — et la surface le tient déjà, puisque
 * `RecitationRecorder.isActive(phase, busy)`, la fonction qui dit si l'écran peut être quitté, le
 * prend en paramètre. Le passer par ici ne produirait rien : le rendu le recopierait tel quel, et
 * un paramètre recopié est un paramètre qui appartient à l'appelant.
 *
 * C'est aussi la frontière que `RevisionActionBar` annonce pour son propre `disabled` : « le jour
 * où il arrivera, le paramètre reviendra avec lui » — dans la barre, donc, et non dans un rendu.
 *
 * @param phase la phase de l'enregistrement. Elle décide du libellé d'état, des gestes offerts et
 *   de la couleur de la ligne.
 * @param draftMs la durée de l'enregistrement arrêté qui attend d'être gardé, ou `null`.
 * @param itemMs la durée de la récitation déjà gardée, ou `null`.
 * @param liveMs la durée du compteur du système, qui court tant que l'enregistreur est ouvert.
 * @param canShare `true` s'il y a une récitation à partager **et** un écran qui sait quoi en faire.
 *   Les deux conditions sont réunies en une, comme `RecitationRecorder.actionsFor` les réunit : dans
 *   les deux cas, le bouton n'apparaît pas.
 * @param message la phrase de la surface, ou `null`. Une chaîne vide vaut `null`.
 */
internal object RecitationRecorderRenderer {

    /**
     * Le séparateur entre le libellé d'état et la durée.
     *
     * `' · '` dans l'original : un **point médian** (U+00B7) entre deux espaces. Écrit ici par son
     * point de code, et non recopié : le fichier d'origine est en UTF-8, et un caractère recopié à
     * travers un canal qui ne l'est pas devient un autre caractère sans que rien ne le signale. Un
     * point médian devenu un point ordinaire, ou un `?`, ne se verrait pas à la lecture — et c'est
     * exactement le genre de détail qu'un test doit pouvoir nommer.
     */
    internal const val STATUS_SEPARATOR: String = " \u00B7 "

    fun render(
        phase: RecitationPhase,
        draftMs: Long?,
        itemMs: Long?,
        liveMs: Long,
        canShare: Boolean,
        message: String?,
    ): RecitationRecorderUi {
        val duree = RecitationRecorder.shownDurationMs(phase, draftMs, itemMs, liveMs)

        return RecitationRecorderUi(
            // L'original écrit `phase!=='idle' ? ' · '+duration(…) : ''` : la condition y est la
            // **phase**. Ici c'est la présence d'une durée — et les deux coïncident, parce que
            // `shownDurationMs` ne rend `null` qu'en `IDLE` et rend toujours une valeur sinon.
            // S'en servir plutôt que de retester la phase évite d'écrire deux fois la même
            // décision ; le jour où l'une des deux changerait, un test du domaine le dirait.
            status = RecitationRecorder.compactLabel(phase) +
                if (duree == null) "" else STATUS_SEPARATOR + RecitationRecorder.clock(duree),

            capturing = phase == RecitationPhase.RECORDING,

            // L'ordre est celui du domaine, et il est conservé : c'est une barre d'actions, et
            // permuter deux boutons change le geste que fait le pouce. Le rendu ne trie rien, ne
            // filtre rien, et n'ajoute rien.
            actions = RecitationRecorder.actionsFor(phase, canShare).map { action ->
                RecitationActionUi(
                    action = action,
                    label = RecitationRecorder.compactActionLabel(action),
                    primary = RecitationRecorder.isPrimary(action),
                )
            },

            message = message?.ifEmpty { null },
        )
    }
}
