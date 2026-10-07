package com.msoumaya.deepseekandroid.core.domain

/**
 * Les mots de la récitation.
 *
 * Toutes ces phrases sont **recopiées du client d'origine** — `src/services/recitations.ts` pour
 * la file de dépôt, `src/RecitationRecorder.tsx` pour l'enregistreur —, et aucune n'est inventée
 * ici : une phrase réécrite serait un écart d'interface invisible au portage, et deux clients qui
 * disent la même chose de deux façons finissent par ne plus dire la même chose.
 *
 * Les apostrophes sont **typographiques** (`’`) partout où l'original en porte une, et droites
 * (`'`) nulle part. La distinction n'est pas cosmétique : c'est le caractère que l'original
 * affiche, et le remplacer changerait le texte rendu.
 *
 * Le fichier a grandi avec ses tranches : d'abord la file de dépôt, puis l'enregistreur. Les mots
 * de la liste et des écrans de récitation viendront s'y ajouter avec eux.
 */
object RecitationText {

    // -----------------------------------------------------------------------
    // La file de dépôt
    // -----------------------------------------------------------------------

    /**
     * Aucun compte ouvert.
     *
     * L'original l'écrit pour les gestes qui n'ont pas de sens sans compte (`Connecte-toi.`) ;
     * c'est la même phrase qui convient à un enregistrement, qui n'appartient à personne tant
     * qu'il n'y a personne.
     */
    const val SIGNED_OUT: String = "Connecte-toi."

    /**
     * Le passage demandé n'existe pas, ou il manque un propriétaire, une source ou une durée.
     *
     * **Une seule phrase pour les quatre causes, comme l'original.** `Recitations.saveProblem`
     * les distingue pour que la règle soit lisible et éprouvable ; l'original, lui, lève la même
     * erreur pour les quatre. Donner ici quatre phrases serait un écart d'interface assumé par
     * personne — et le cas est de toute façon inatteignable depuis l'écran, qui ne propose que
     * des passages du Coran et une durée mesurée.
     */
    const val INVALID_RECORDING: String = "Récitation ou passage invalide."

    /**
     * Le fichier audio a disparu de l'appareil.
     *
     * Le cas se produit réellement : le système efface les fichiers d'un dossier de cache, et une
     * restauration de sauvegarde peut rendre le registre sans les fichiers. La synchronisation
     * marque alors la récitation en échec plutôt que d'écrire une ligne distante vers un fichier
     * que personne ne pourra jamais écouter.
     */
    const val LOCAL_FILE_MISSING: String = "Fichier local introuvable."

    // -----------------------------------------------------------------------
    // L'enregistreur — les titres
    // -----------------------------------------------------------------------

    /** Le titre du panneau quand on enregistre un passage du Coran. */
    const val RECORDER_TITLE_QURAN: String = "Enregistrer ma voix"

    /** Le titre du panneau quand on enregistre une invocation. */
    const val RECORDER_TITLE_INVOCATION: String = "Ma prononciation"

    /**
     * Le repli quand une invocation n'a **pas** de titre.
     *
     * Il ne joue que sur `null`, comme le `??` de l'original : un titre vide rend une ligne vide.
     */
    const val INVOCATION_FALLBACK: String = "Invocation"

    /**
     * Ce que la ligne du titre rappelle toujours : l'enregistrement reste sur l'appareil.
     *
     * L'original écrit la ligne complète sous la forme `<référence> · enregistrement personnel`,
     * séparateur compris. C'est la personne qui enregistre qui doit le voir, pas seulement celle
     * qui lit la notice.
     */
    const val PERSONAL_RECORDING: String = "enregistrement personnel"

    // -----------------------------------------------------------------------
    // L'enregistreur — les états
    // -----------------------------------------------------------------------

    /** L'état « le microphone capte ». Le même mot dans les deux mises en page. */
    const val RECORDING: String = "● Enregistrement"

    /**
     * L'état « la capture est suspendue », **dans la barre réduite**.
     *
     * Sans le symbole, contrairement à la pleine page : la barre réduite tient dans une ligne de
     * texte, et l'original y a laissé le mot seul.
     */
    const val PAUSED_COMPACT: String = "En pause"

    /**
     * L'état « la capture est suspendue », **en pleine page**.
     *
     * Le symbole de pause est un caractère romain `Ⅱ` (U+2161), et non deux barres verticales :
     * c'est celui de l'original, et les deux ne se ressemblent pas à l'écran.
     */
    const val PAUSED_FULL: String = "Ⅱ En pause"

    /** L'état de repos, quand rien n'a encore été enregistré. */
    const val PHASE_IDLE: String = "Enregistrement personnel"

    /** L'état d'un enregistrement arrêté qu'on peut réécouter avant de le garder. */
    const val PHASE_PREVIEW: String = "Prêt à réécouter"

    /** L'état d'un enregistrement rangé sur l'appareil. */
    const val PHASE_SAVED: String = "Récitation enregistrée"

    // -----------------------------------------------------------------------
    // L'enregistreur — les gestes
    // -----------------------------------------------------------------------

    /** Le libellé du geste de départ, dans la barre réduite. */
    const val BAR_BEGIN: String = "Commencer"

    /** Le libellé du geste de départ, en pleine page. */
    const val PAGE_BEGIN: String = "● Enregistrer ma voix"

    /** Suspendre la capture. Le même mot dans les deux mises en page. */
    const val PAUSE: String = "Pause"

    /** Reprendre la capture. Le même mot dans les deux mises en page. */
    const val RESUME: String = "Reprendre"

    /** Arrêter et garder, dans la barre réduite. */
    const val BAR_FINISH: String = "Terminer"

    /** Arrêter et garder un passage du Coran, en pleine page. */
    const val PAGE_FINISH_QURAN: String = "Terminer et sauvegarder"

    /** Arrêter et garder une invocation, en pleine page. */
    const val PAGE_FINISH_INVOCATION: String = "Arrêter"

    /**
     * Arrêter et jeter.
     *
     * Le **même mot** sert dans la barre réduite et dans la boîte de la notice, comme dans
     * l'original : les deux annulent, et leur donner deux mots différents ferait douter de ce
     * qu'ils font.
     */
    const val CANCEL: String = "Annuler"

    /** Arrêter et jeter, en pleine page. Le mot y est plus explicite, comme dans l'original. */
    const val PAGE_CANCEL: String = "Supprimer cet enregistrement"

    /** Réécouter. Le même mot dans les deux mises en page. */
    const val LISTEN: String = "Réécouter"

    /** Réécouter avant de garder — l'invitation de la pleine page, en phase d'aperçu. */
    const val PAGE_LISTEN_BEFORE_SAVE: String = "Réécouter avant sauvegarde"

    /** Tout reprendre à zéro. Le même mot dans les deux mises en page. */
    const val RESTART: String = "Recommencer"

    /** Garder l'enregistrement. Le même mot dans les deux mises en page. */
    const val SAVE: String = "Enregistrer"

    /** Proposer l'enregistrement à un ami, dans la barre réduite. */
    const val BAR_SHARE: String = "Partager"

    /** Proposer l'enregistrement à un ami, en pleine page. */
    const val PAGE_SHARE: String = "Partager avec un ami"

    /** Recommencer un enregistrement après en avoir gardé un. */
    const val PAGE_RECORD_ANOTHER: String = "Enregistrer une autre récitation"

    // -----------------------------------------------------------------------
    // L'enregistreur — les messages
    // -----------------------------------------------------------------------

    /**
     * Le compte manque, et la phrase dit **où** aller le chercher.
     *
     * Elle est plus longue que le `Connecte-toi.` de la file de dépôt, et c'est l'original : le
     * geste qui échoue ici est celui qui crée l'enregistrement, donc la phrase doit dire ce qu'on
     * perd en n'étant pas connecté, et où se connecter.
     */
    const val RECORD_OWNER_MISSING: String =
        "Connecte-toi dans Profil pour sauvegarder et synchroniser tes récitations."

    /**
     * Le microphone a été refusé.
     *
     * La phrase envoie vers les réglages du téléphone, parce que c'est le seul endroit d'où la
     * permission peut être rendue : une seconde demande dans l'application n'afficherait plus
     * rien une fois le refus définitif.
     */
    const val MICROPHONE_DENIED: String = "Autorise le microphone dans les réglages du téléphone."

    /** L'enregistrement a été jeté : rien n'est parti, et la phrase le dit. */
    const val RECORDING_CANCELLED: String = "Enregistrement annulé. Rien n’a été envoyé."

    /**
     * Il manque un compte ou un fichier après l'arrêt de la capture.
     *
     * Les deux causes sont réunies dans une phrase, comme l'original. Le cas est rare — le compte
     * a pu être fermé pendant l'enregistrement, et le fichier refusé par le système —, mais il
     * laisse un enregistrement qu'on ne peut ni garder ni rattacher, et il vaut mieux le dire que
     * de le perdre en silence.
     */
    const val AUDIO_UNAVAILABLE: String = "Compte ou fichier audio indisponible."

    /** L'enregistrement est rangé sur l'appareil ; le dépôt suivra. */
    const val SAVED_LOCAL: String = "Enregistré sur ce téléphone. Synchronisation automatique en cours."

    /** Le dépôt a été tenté. La phrase renvoie à l'écran où son résultat se lit. */
    const val SYNC_ATTEMPTED: String =
        "Synchronisation tentée. Consulte Mes récitations pour vérifier le statut."

    /** Le dépôt n'a pas pu être tenté : l'appareil est hors connexion. */
    const val SYNC_WAITING: String = "En attente de connexion pour la synchronisation."

    /** Il faut un compte pour garder un enregistrement, et il n'y en a pas. */
    const val CONNECTION_REQUIRED: String = "Connexion requise."

    /** Un enregistrement mis de côté pour aperçu vient d'être gardé. */
    const val DRAFT_SAVED: String =
        "Sauvegardé. Consulte Mes récitations pour le statut de synchronisation."

    // -----------------------------------------------------------------------
    // L'enregistreur — la notice
    // -----------------------------------------------------------------------

    /**
     * Le titre de la notice montrée avant le **premier** enregistrement.
     *
     * Elle n'est pas décorative : elle dit que l'enregistrement part à l'administrateur, qu'il
     * reste sur l'appareil, et comment le faire supprimer. L'original ne commence pas à
     * enregistrer avant qu'elle ait été acceptée, et le portage suit — c'est un consentement, pas
     * un avis.
     */
    const val NOTICE_TITLE: String = "Tes récitations"

    /** Le corps de la notice, mot pour mot celui de l'original. */
    const val NOTICE_BODY: String =
        "Vos récitations et prononciations enregistrées sont automatiquement sauvegardées et " +
            "accessibles à l’administrateur pour permettre le suivi de votre apprentissage et vos " +
            "corrections. Elles restent sur ce téléphone après synchronisation. Tu peux demander " +
            "leur suppression depuis ton compte."

    /** Le bouton qui accepte la notice **et** lance l'enregistrement. */
    const val NOTICE_ACCEPT: String = "Compris, enregistrer"
}
