package com.msoumaya.deepseekandroid.feature.home

import com.msoumaya.deepseekandroid.core.domain.ProblemReportAttachmentProblem
import com.msoumaya.deepseekandroid.core.domain.ProblemReportText
import com.msoumaya.deepseekandroid.core.domain.ProblemReports
import com.msoumaya.deepseekandroid.core.model.ProblemReportType

// ---------------------------------------------------------------------------
// Calcul de l'écran de signalement
// ---------------------------------------------------------------------------
// Portage de `ProblemReportCard` et `ProblemReportSheet` (`src/ui/ProblemReport.tsx`), **privé de
// son rendu** : ce fichier ne connaît ni Compose, ni Android, ni le dépôt. Il prend des valeurs et
// rend un [ProblemReportUiState].
//
// La raison est celle de tout le module : le corps d'un composable est rejoué à chaque
// recomposition, et ici l'écran porte un champ de saisie — donc chaque frappe recompose. Un
// `description.trim()` écrit dans le corps serait recalculé à chaque caractère, et ne serait
// éprouvable qu'avec un hôte Compose, dont ce projet n'a aucun.
//
// **Ce fichier ne décide pas des mots.** Ils vivent dans `ProblemReportText`, avec ceux de la file,
// et un test les mesure là-bas comme ici.
// ---------------------------------------------------------------------------

internal object ProblemReportRenderer {

    /**
     * Calcule ce que la carte et la feuille affichent.
     *
     * **Tout est facultatif**, et c'est ce qui permet à la carte de l'accueil d'appeler `render()`
     * sans rien : elle n'affiche que deux phrases, qui ne dépendent d'aucun état. La feuille, elle,
     * passe tout.
     *
     * @param description le texte **brut** du champ. C'est lui qui donne le compteur, et c'est lui
     *   que la règle d'envoi rognera — voir [canSend].
     * @param hasAttachment vrai quand une capture est jointe.
     * @param busy vrai pendant un envoi.
     * @param done vrai quand le dernier envoi a abouti à une issue.
     * @param notice l'issue du dernier envoi, ou `null`. Elle vient du dépôt.
     * @param attachNotice l'échec du dernier **choix de capture**, ou `null`. Elle vient de la
     *   feuille, qui seule sait qu'un sélecteur a été ouvert.
     */
    fun render(
        description: String = "",
        hasAttachment: Boolean = false,
        busy: Boolean = false,
        done: Boolean = false,
        notice: String? = null,
        attachNotice: String? = null,
    ): ProblemReportUiState = ProblemReportUiState(
        cardTitle = ProblemReportText.CARD_TITLE,
        cardSubtitle = ProblemReportText.CARD_SUBTITLE,
        sheetTitle = ProblemReportText.SHEET_TITLE,
        sheetSubtitle = ProblemReportText.SHEET_SUBTITLE,
        closeLabel = ProblemReportText.CLOSE,
        dismissLabel = ProblemReportText.DISMISS,
        typeLabel = ProblemReportText.TYPE_LABEL,
        // **L'ordre est celui du modèle**, et non une liste écrite ici. La sixième nature, le jour
        // où elle existera, apparaîtra donc à l'écran sans que ce fichier soit touché — et sans
        // pouvoir être oubliée.
        natures = ProblemReportType.all,
        initialType = ProblemReportType.all.first(),
        descriptionLabel = ProblemReportText.DESCRIPTION_LABEL,
        descriptionPlaceholder = ProblemReportText.DESCRIPTION_PLACEHOLDER,
        descriptionMax = ProblemReports.DESCRIPTION_MAX,
        counter = ProblemReportText.counter(description.length),
        attachTitle = if (hasAttachment) {
            ProblemReportText.ATTACH_ADDED
        } else {
            ProblemReportText.ATTACH_ADD
        },
        attachSubtitle = if (hasAttachment) {
            ProblemReportText.ATTACH_REPLACE
        } else {
            ProblemReportText.ATTACH_HINT
        },
        removeLabel = ProblemReportText.ATTACH_REMOVE,
        sendLabel = if (busy) ProblemReportText.SENDING else ProblemReportText.SEND,
        hasAttachment = hasAttachment,
        canSend = canSend(description, busy),
        busy = busy,
        done = done,
        // **L'échec du choix l'emporte sur l'issue de l'envoi**, et les deux ne peuvent pas être
        // posés en même temps : chaque geste commence par effacer les deux (c'est le `setNotice('')`
        // de l'original, écrit au début de `attach` et de `send`). Une capture refusée juste après
        // un envoi raté doit donc s'afficher, et l'inverse ne se produit pas.
        notice = attachNotice ?: notice,
    )

    /**
     * Vrai quand le bouton d'envoi doit être actif.
     *
     * C'est le `disabled={busy||!description.trim()}` de l'original, dans le même ordre : le travail
     * en cours d'abord, puis le texte. **Le texte est rogné avant d'être jugé**, et c'est ce qui
     * empêche d'envoyer un signalement fait d'espaces — le serveur le refuserait de toute façon
     * (`btrim(description) between 1 and 500`), mais la personne l'apprendrait après l'attente.
     *
     * Le bouton **n'est pas** désactivé quand [ProblemReportUiState.done] est vrai : à ce moment-là
     * le formulaire n'est plus à l'écran, c'est l'écran de confirmation qui le remplace. L'original
     * écrit la même chose — son `disabled` ignore `done`, et sa garde d'envoi le teste à part.
     */
    fun canSend(description: String, busy: Boolean): Boolean =
        !busy && description.trim().isNotEmpty()

    /**
     * Le message d'un fichier refusé, ou `null` s'il est acceptable.
     *
     * C'est **la** traduction des deux refus du domaine en deux phrases : `ProblemReports` dit
     * *lequel* des deux empêche l'envoi, et cette fonction dit *ce qu'on en dit*. Les deux phrases
     * sont écrites dans `ProblemReportText`, avec celles de la file, parce que le refus d'une
     * capture peut aussi survenir à l'envoi — et les deux endroits doivent dire la même chose.
     *
     * Elle est ici, et non dans le sélecteur d'images, pour être éprouvable sans appareil : le
     * sélecteur, lui, ne fait que l'appeler avec la taille et le type qu'il vient de lire.
     */
    fun pickProblem(mime: String, sizeBytes: Long): String? =
        when (ProblemReports.attachmentProblem(mime, sizeBytes)) {
            ProblemReportAttachmentProblem.FORMAT_UNSUPPORTED -> ProblemReportText.ATTACHMENT_FORMAT
            ProblemReportAttachmentProblem.TOO_LARGE -> ProblemReportText.ATTACHMENT_TOO_LARGE
            null -> null
        }
}
