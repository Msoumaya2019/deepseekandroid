package com.msoumaya.deepseekandroid.core.domain

/**
 * Les mots du signalement de problème.
 *
 * Toutes ces phrases sont **recopiées du client d'origine** — `src/services/problemReports.ts`
 * pour la file, `src/ui/ProblemReport.tsx` pour l'écran —, et aucune n'est inventée : une phrase
 * réécrite serait un écart d'interface invisible au portage, et deux clients qui disent la même
 * chose de deux façons finissent par ne plus dire la même chose.
 *
 * Les apostrophes sont **typographiques** (`’`) partout où l'original en porte une, et droites
 * (`'`) nulle part. La distinction n'est pas cosmétique : c'est le caractère que l'original
 * affiche, et le remplacer changerait le texte rendu. Un test le fige.
 *
 * **Ce fichier grandit avec ses tranches.** Il porte les mots de la **file** — ceux qu'un envoi
 * fait apparaître, et ceux qu'une panne d'envoi fait dire —, et ceux de l'**écran** — le titre de
 * la carte, l'invite du champ, le compteur, les deux libellés de la capture —, arrivés avec lui.
 * C'est le mouvement qu'a suivi `RecitationText`.
 */
object ProblemReportText {

    // -------------------------------------------------------------------------------------------
    // Les refus, avant tout envoi
    // -------------------------------------------------------------------------------------------

    /**
     * Aucun compte ouvert.
     *
     * L'original l'écrit pour le geste qui n'a pas de sens sans compte : un signalement appartient
     * à quelqu'un, et la politique d'insertion du schéma exige `user_id = auth.uid()`. La phrase
     * dit **où** aller se connecter, et c'est l'original — plus longue que le `Connecte-toi.` de
     * la file des récitations, parce que le geste refusé n'est pas le même.
     */
    const val NOT_SIGNED_IN: String = "Connecte-toi pour envoyer un signalement."

    /**
     * La description manque, ou dépasse la borne.
     *
     * **Une seule phrase pour les deux causes, comme l'original.** `ProblemReports` les distingue
     * pour que la règle soit lisible et éprouvable ; l'original, lui, lève la même erreur pour les
     * deux. Donner ici deux phrases serait un écart d'interface assumé par personne — et la
     * personne qui écrit 501 caractères voit bien, à l'écran, le compteur qui la dépasse.
     */
    const val DESCRIPTION_INVALID: String = "Décris le problème en 500 caractères maximum."

    /** Le fichier choisi n'est ni un JPEG ni un PNG. */
    const val ATTACHMENT_FORMAT: String = "Choisis une capture au format JPEG ou PNG."

    /**
     * Le fichier choisi dépasse la borne.
     *
     * Le chiffre est écrit en clair, comme dans l'original : « 5 Mo » se lit, `5 242 880` ne se
     * lit pas. La borne réelle est [ProblemReports.SCREENSHOT_MAX_BYTES], et c'est elle qui décide.
     */
    const val ATTACHMENT_TOO_LARGE: String = "La capture doit faire moins de 5 Mo."

    /**
     * Le serveur n'est pas configuré, et rien ne peut donc partir.
     *
     * C'est le `client()` de l'original, qui lève quand `supabase` est absent. Ici, le mode sans
     * projet est **normal** — l'application fonctionne entièrement hors ligne —, et la phrase dit
     * une indisponibilité plutôt qu'une panne.
     */
    const val SERVER_UNAVAILABLE: String = "Connexion au serveur indisponible."

    // -------------------------------------------------------------------------------------------
    // Les pannes de la file
    // -------------------------------------------------------------------------------------------

    /**
     * Le signalement a été écrit, mais le serveur ne le rend pas encore.
     *
     * La phrase est celle de l'original, et elle décrit un état **réel** : après l'écriture de la
     * ligne, le client la relit avant de retirer l'entrée de la file. Sans cette relecture, une
     * politique d'insertion qui accepterait la ligne sans la rendre — ou un cache de lecture en
     * retard — ferait disparaître un signalement que personne ne pourrait plus renvoyer.
     */
    const val CONFIRMATION_PENDING: String = "Confirmation du signalement en attente."

    /**
     * La capture a disparu de l'appareil, et la ligne ne doit donc pas partir.
     *
     * **Ce message ne s'affiche jamais** : la file est une boîte d'envoi, et personne ne regarde
     * son contenu. Il est écrit pour le diagnostic — une trace qui dit « fichier introuvable »
     * vaut mieux qu'une trace qui dit « exception ».
     *
     * L'original lève au même endroit, mais avec le message du système de fichiers
     * (`new File(local_uri).bytes()`), qui ne dit pas de quel fichier il parle.
     */
    const val SCREENSHOT_MISSING: String = "Capture du signalement introuvable."

    // -------------------------------------------------------------------------------------------
    // Ce qu'un envoi annonce
    // -------------------------------------------------------------------------------------------

    /**
     * Le signalement est arrivé.
     *
     * L'apostrophe est **typographique** (`’`), comme celle de l'original.
     */
    const val SENT: String = "Ton signalement a été envoyé à l’administrateur. Merci !"

    /**
     * Le signalement est gardé, et partira seul.
     *
     * La phrase promet une chose précise — l'envoi **automatique** au retour de la connexion —, et
     * c'est vrai : la file est vidée au retour du réseau, à l'ouverture et au retour au premier
     * plan. Elle ne dit pas *quand*, parce que personne ne le sait.
     */
    const val QUEUED: String =
        "Ton signalement est enregistré. Il sera envoyé automatiquement dès que la connexion " +
            "sera disponible."

    /**
     * L'envoi a échoué pour une raison que la file ne sait pas nommer.
     *
     * C'est le repli de l'écran de l'original (`catch(e){setNotice(e instanceof Error?e.message:'Envoi impossible. Réessaie.')}`),
     * gardé ici parce que deux écrans pourraient un jour l'écrire et qu'une phrase recopiée
     * finirait par diverger de l'autre.
     */
    const val SEND_FAILED: String = "Envoi impossible. Réessaie."

    /** Le repli quand la capture n'a pas pu être ajoutée, et que l'erreur ne dit rien. */
    const val ATTACH_FAILED: String = "Impossible d’ajouter la capture."

    /**
     * Ce que la file annonce pour une issue donnée.
     *
     * Le choix entre les deux phrases appartient au **client d'origine**, et il l'écrit dans son
     * écran. Il est ici parce que c'est la même décision que [ProblemReports.outcome] : deux
     * endroits qui traduiraient la même issue finiraient par en traduire une différemment.
     */
    fun outcomeMessage(outcome: ProblemReportOutcome): String = when (outcome) {
        ProblemReportOutcome.SENT -> SENT
        ProblemReportOutcome.QUEUED -> QUEUED
    }

    // -------------------------------------------------------------------------------------------
    // Les mots de l'écran de signalement
    // -------------------------------------------------------------------------------------------
    // Portés depuis `src/ui/ProblemReport.tsx`. Ils sont ici, et non dans l'écran, pour la même
    // raison que ceux de la file : le titre de la carte est aussi l'étiquette que lit un lecteur
    // d'écran, et deux écrans — l'accueil et, un jour, un écran d'administration — pourraient
    // écrire les mêmes phrases.
    // -------------------------------------------------------------------------------------------

    /** Titre de la carte de l'accueil. **C'est aussi l'étiquette d'accessibilité de son geste.** */
    const val CARD_TITLE: String = "Un problème avec l’application ?"

    /** Sous-titre de la carte : ce que le geste permet de faire. */
    const val CARD_SUBTITLE: String =
        "Signaler une erreur, un bug ou un dysfonctionnement à l’administrateur"

    /** Titre de la feuille ouverte. */
    const val SHEET_TITLE: String = "Signaler un problème"

    /** Sous-titre de la feuille ouverte. */
    const val SHEET_SUBTITLE: String = "Envoyez un signalement à l’administrateur"

    /**
     * Le bouton qui referme.
     *
     * **Une seule phrase pour trois gestes** : le bouton de la barre de titre, celui de l'écran de
     * confirmation, et l'étiquette du bouton de fermeture. C'est ce que fait l'original, qui écrit
     * `Fermer` aux trois endroits — et les trois gestes font bien la même chose.
     */
    const val CLOSE: String = "Fermer"

    /**
     * L'étiquette du **fond** de la feuille, qui la referme aussi.
     *
     * Elle est distincte de [CLOSE] parce que l'original l'écrit différemment
     * (`accessibilityLabel="Fermer le signalement"`) : un lecteur d'écran annonce ainsi ce qu'on
     * ferme, au lieu d'un « Fermer » qui pourrait désigner le clavier ouvert par-dessus.
     */
    const val DISMISS: String = "Fermer le signalement"

    /** Intertitre du groupe des cinq natures. */
    const val TYPE_LABEL: String = "Type de problème"

    /** Intertitre du champ de description. */
    const val DESCRIPTION_LABEL: String = "Décrivez le problème"

    /**
     * L'invite du champ de description.
     *
     * Elle **énumère ce qui est utile** — la description, les étapes de reproduction —, et elle
     * finit par un caractère de suspension (`…`), comme l'original.
     */
    const val DESCRIPTION_PLACEHOLDER: String =
        "Décrivez le problème rencontré, les étapes pour le reproduire, ou toute information utile…"

    /**
     * Le compteur de caractères, sous le champ.
     *
     * Il compte le texte **brut**, et non rogné : c'est `{description.length}/500` de l'original.
     * La différence avec la règle d'envoi n'est pas une incohérence, elle est visible — on compte
     * ce qu'on a tapé, on refuse sur ce qui reste une fois les bords enlevés.
     *
     * La borne vient de [ProblemReports.DESCRIPTION_MAX], et non d'un `500` recopié : la phrase
     * [DESCRIPTION_INVALID] et le `maxLength` du champ disent le même nombre, et trois copies
     * finiraient par diverger.
     */
    fun counter(length: Int): String = "$length/${ProblemReports.DESCRIPTION_MAX}"

    /** Titre de la ligne de capture quand il n'y en a pas. */
    const val ATTACH_ADD: String = "Ajouter une capture"

    /** Titre de la ligne de capture quand il y en a une. */
    const val ATTACH_ADDED: String = "Capture ajoutée"

    /** Sous-titre de la ligne de capture quand il n'y en a pas. */
    const val ATTACH_HINT: String = "Photo ou capture d’écran (optionnel)"

    /** Sous-titre de la ligne de capture quand il y en a une. */
    const val ATTACH_REPLACE: String = "Toucher pour la remplacer"

    /** Le bouton qui détache la capture choisie. */
    const val ATTACH_REMOVE: String = "Retirer la capture"

    /** Le bouton d'envoi. */
    const val SEND: String = "Envoyer à l’administrateur"

    /**
     * Le bouton d'envoi pendant l'envoi.
     *
     * Il dit ce qui se passe **et** empêche le second appui : le bouton est désactivé, et son
     * libellé l'explique. Un bouton désactivé qui garderait « Envoyer » laisserait croire à une
     * panne plutôt qu'à un travail en cours.
     */
    const val SENDING: String = "Envoi…"
}
