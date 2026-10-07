package com.msoumaya.deepseekandroid.core.domain

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Les mots de la récitation.
 *
 * Toutes ces phrases sont **recopiées du client d'origine** — `src/services/recitations.ts` pour
 * la file de dépôt, `src/RecitationRecorder.tsx` pour l'enregistreur, `src/RecitationsScreen.tsx`
 * pour la liste —, et aucune n'est inventée ici : une phrase réécrite serait un écart d'interface
 * invisible au portage, et deux clients qui disent la même chose de deux façons finissent par ne
 * plus dire la même chose.
 *
 * Les apostrophes sont **typographiques** (`’`) partout où l'original en porte une, et droites
 * (`'`) nulle part. La distinction n'est pas cosmétique : c'est le caractère que l'original
 * affiche, et le remplacer changerait le texte rendu.
 *
 * Le fichier a grandi avec ses tranches : d'abord la file de dépôt, puis l'enregistreur, puis la
 * liste. Les mots des écrans qui restent viendront s'y ajouter avec eux.
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

    /**
     * On ne retire que ce qui est à soi.
     *
     * La règle est **aussi** tenue par le serveur — la politique du compartiment et celle de la
     * table comparent toutes deux `auth.uid()` —, mais la garde est posée ici, **avant tout
     * appel**, comme dans l'original. Sans elle, un identifiant d'autrui coûterait deux
     * allers-retours réseau pour se faire refuser au bout.
     */
    const val NOT_MINE: String = "Cette récitation ne t’appartient pas."

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

    // -----------------------------------------------------------------------
    // La liste des récitations — l'ossature
    // -----------------------------------------------------------------------

    /**
     * Le retour, avec le chevron de l'original.
     *
     * Le chevron est un `‹` **U+2039** (guillemet simple à chevron), et non un `<` : les deux ne
     * se ressemblent pas à l'écran, l'un étant un signe typographique et l'autre un opérateur.
     */
    const val LIST_BACK: String = "‹ Retour"

    /** Le titre de l'écran. */
    const val LIST_TITLE: String = "Mes récitations"

    /** Ce que l'écran promet sous son titre. */
    const val LIST_SUBTITLE: String =
        "Enregistrements sauvegardés sur ce téléphone et synchronisés avec ton compte."

    /** Le geste qui relance la synchronisation et la lecture du registre. */
    const val LIST_REFRESH: String = "Actualiser et synchroniser"

    /** Le filtre qui montre tout. */
    const val FILTER_ALL: String = "Toutes"

    /** Le filtre des passages du Coran. */
    const val FILTER_QURAN: String = "Coran"

    /** Le filtre des invocations. */
    const val FILTER_INVOCATION: String = "Invocations"

    /** La liste vide. La phrase dit quoi faire pour la remplir, comme l'original. */
    const val LIST_EMPTY: String =
        "Aucune récitation enregistrée. Ouvre un passage du Coran pour enregistrer ta voix."

    /** Aucun compte ouvert : la phrase dit pourquoi la liste sera vide. */
    const val LIST_SIGNED_OUT: String = "Connecte-toi pour retrouver tes récitations."

    /**
     * Aucun compte ouvert, alors que le client sait se connecter.
     *
     * Elle est plus longue que [LIST_SIGNED_OUT] et dit **où** aller : c'est l'original, qui
     * distingue le cas où le service est absent de celui où la personne ne s'est pas connectée.
     */
    const val LIST_SIGNED_OUT_PROFILE: String =
        "Connecte-toi dans Profil pour retrouver tes récitations."

    /**
     * Le réseau a échoué, mais les fichiers de l'appareil restent.
     *
     * C'est la phrase la plus importante de l'écran : elle dit que l'échec ne coûte **rien** de ce
     * qui est déjà enregistré. La cause est ajoutée telle quelle, comme l'original.
     */
    fun listLocalOnly(error: String?): String =
        if (error.isNullOrBlank()) LOCAL_ONLY else "Les fichiers locaux restent disponibles. $error"

    /**
     * Ce qu'on dit quand la lecture distante a échoué **sans laisser de message**.
     *
     * Le cas existe : une `RestException` dont le serveur n'a pas rempli le texte rend un message
     * nul, et la phrase se terminerait alors sur une espace. Elle se termine ici sur elle-même.
     * C'est l'idiome de `Quiz.errorText` et de `Social.errorText`, qui retombent tous deux sur une
     * phrase générique quand il n'y a rien à dire.
     */
    const val LOCAL_ONLY: String = "Les fichiers locaux restent disponibles."

    /** Le lecteur n'a pas pu démarrer. */
    fun playImpossible(error: String): String = "Lecture impossible : $error"

    // -----------------------------------------------------------------------
    // La liste des récitations — ce que la ligne annonce
    // -----------------------------------------------------------------------

    /** Le dépôt a abouti, ou il n'y a pas de copie locale à déposer. */
    const val SYNC_SYNCED: String = "Synchronisé"

    /**
     * Le dépôt est en cours.
     *
     * L'apostrophe est **typographique** (`’`), comme celle de l'original : c'est le caractère
     * rendu, et le remplacer changerait le texte affiché.
     */
    const val SYNC_UPLOADING: String = "En cours d’envoi"

    /** Le dépôt a échoué. La synchronisation retentera. */
    const val SYNC_FAILED: String = "Échec de synchronisation"

    /** Le dépôt n'a pas encore été tenté. */
    const val SYNC_PENDING: String = "En attente"

    /** Le préfixe d'un passage du Coran, dans le titre d'une ligne. */
    const val TITLE_PREFIX_QURAN: String = "CORAN"

    /** Le préfixe d'une invocation, dans le titre d'une ligne. */
    const val TITLE_PREFIX_INVOCATION: String = "INVOCATION"

    /**
     * Le repli d'une invocation sans titre, **dans la liste**.
     *
     * Il diffère de [INVOCATION_FALLBACK] (« Invocation »), qui est celui de l'enregistreur :
     * c'est l'original, et les deux écrans parlent de deux moments différents — celui où l'on
     * enregistre, et celui où l'on relit.
     */
    const val LIST_INVOCATION_FALLBACK: String = "Ma prononciation"

    /** Un relecteur a écouté la récitation. */
    const val STATUS_LISTENED: String = "Écoutée"

    /** Personne n'a encore écouté l'invocation. */
    const val STATUS_TO_LISTEN: String = "À écouter"

    /** Un relecteur a déposé une correction de verset ou un retour général. */
    const val STATUS_CORRECTED: String = "Corrigée"

    /** Un passage du Coran que personne n'a encore corrigé ni écouté. */
    const val STATUS_AWAITING_CORRECTION: String = "En attente de correction"

    /** Le mot qui nomme un verset dans une carte de correction. */
    const val VERSE_WORD: String = "verset"

    /**
     * La position d'écoute, sous la barre de progression.
     *
     * Le séparateur est un point médian **U+00B7**, et non un trait d'union ni un point : c'est
     * celui de l'original, dans les deux séparations de la phrase.
     */
    fun positionLabel(position: String, duration: String, status: String): String =
        "Position : $position / $duration · $status"

    // -----------------------------------------------------------------------
    // La liste des récitations — les gestes et les cartes
    // -----------------------------------------------------------------------

    /** Relancer l'écoute. Le triangle est **U+25B6**, celui de l'original. */
    const val LIST_PLAY: String = "▶ Réécouter"

    /**
     * Suspendre l'écoute.
     *
     * Le **même mot** que la pause de l'enregistreur ([PAUSE]) : c'est l'original, et deux mots
     * pour le même geste feraient douter de ce qu'il fait.
     */
    const val LIST_PAUSE: String = "Pause"

    /**
     * Reculer de dix secondes.
     *
     * Le signe est un **moins U+2212**, et non un trait d'union : c'est celui de l'original, et
     * les deux n'ont ni la même largeur ni la même hauteur à l'écran.
     */
    const val SEEK_BACK: String = "− 10 s"

    /** Avancer de dix secondes. */
    const val SEEK_FORWARD: String = "+ 10 s"

    /** Le titre d'un retour général. */
    const val FEEDBACK_TITLE: String = "Observation générale"

    /** Ce qu'un retour général sans commentaire laisse entendre : une voix a été déposée. */
    const val FEEDBACK_FALLBACK: String = "Commentaire vocal du professeur"

    /** Écouter la voix du relecteur sur un retour général. */
    const val LISTEN_TEACHER: String = "▶ Écouter le professeur"

    /** Ce qu'une correction de verset sans commentaire annonce. */
    const val CORRECTION_FALLBACK: String = "À retravailler"

    /** Écouter la voix du relecteur sur une correction de verset. */
    const val LISTEN_CORRECTION: String = "▶ Écouter la correction"

    /** Aller voir l'invocation dont la récitation est la prononciation. */
    const val VIEW_INVOCATION: String = "Voir l’invocation"

    // -----------------------------------------------------------------------
    // La liste des récitations — le partage et la suppression
    // -----------------------------------------------------------------------

    /** Ouvrir le choix d'un destinataire. */
    const val SHARE_FRIEND: String = "Partager avec un ami"

    /** Ce que le partage demande, et ce qu'il promet : rien ne part sans confirmation. */
    const val SHARE_HINT: String = "Choisis un ami. L’envoi sera confirmé avant le partage."

    /** Le titre de la confirmation de partage. */
    const val SHARE_TITLE: String = "Partager cette récitation ?"

    /**
     * Ce que la confirmation de partage annonce : qui pourra écouter, et pendant combien de temps.
     *
     * La durée n'est pas une politesse : l'accès s'arrête avec l'amitié, et le dire évite de
     * croire qu'un partage est définitif.
     */
    fun shareBody(name: String, label: String): String =
        "Seul $name pourra écouter $label tant que vous restez amis."

    /** Le geste qui confirme le partage. */
    const val SHARE_CONFIRM: String = "Partager"

    /** Le partage est fait. */
    const val SHARE_DONE: String = "Récitation partagée dans votre conversation."

    /** Aucun destinataire possible. */
    const val SHARE_NO_FRIEND: String = "Aucun ami accepté pour le moment."

    /** Le nom d'un destinataire qui n'en a pas. */
    const val FRIEND_FALLBACK: String = "Ami"

    /** Le titre de la confirmation de suppression. */
    const val DELETE_TITLE: String = "Supprimer cette récitation ?"

    /**
     * Ce que la suppression emporte.
     *
     * La phrase nomme les trois pertes — le fichier, ses corrections, les accès partagés — et dit
     * que l'action est définitive : c'est l'original, et c'est ce qui permet de consentir.
     */
    const val DELETE_BODY: String =
        "Le fichier, ses corrections et les accès partagés seront supprimés. " +
            "Cette action est définitive."

    /** Le geste qui confirme la suppression. */
    const val DELETE_CONFIRM: String = "Supprimer"

    // -----------------------------------------------------------------------
    // Les dates
    // -----------------------------------------------------------------------

    /**
     * Instant d'une récitation, tel qu'il s'écrit sous son titre.
     *
     * Le motif est celui de l'original — `new Date(...).toLocaleString('fr-FR')` —, **mesuré** et
     * non supposé : « 01/01/2026 11:00:00 ». La locale est forcée à [FR], comme pour l'espace
     * social : un téléphone réglé en anglais écrirait « 1/1/2026, 11:00:00 AM », et les deux
     * clients montreraient alors la même récitation de deux façons différentes.
     *
     * **`null` plutôt qu'une chaîne vide ou un texte d'erreur.** Le client d'origine écrit
     * « Invalid Date » pour un instant illisible, ce qui n'apprend rien à personne. Ici, un
     * instant illisible est traité comme **absent**, et l'appelant n'écrit rien.
     */
    fun dateStamp(iso: String): String? = date(iso, STAMP)

    /**
     * Jour d'une correction, **sans l'heure**.
     *
     * L'original écrit `toLocaleDateString('fr-FR')`, soit « 01/01/2026 ». Une correction se date
     * au jour : l'heure d'un commentaire n'aide personne, et l'original ne la montre pas.
     */
    fun dateOnly(iso: String): String? = date(iso, DAY)

    private fun date(iso: String, motif: DateTimeFormatter): String? =
        runCatching { Instant.parse(iso) }
            .getOrNull()
            ?.atZone(Dates.zone())
            ?.format(motif)

    /**
     * La locale des dates. Voir [dateStamp].
     *
     * Elle est déclarée **avant** les motifs, et ce n'est pas cosmétique : un `object` initialise
     * ses propriétés dans l'ordre du fichier, et les deux motifs la lisent. L'inverse ne compile
     * pas — « Variable 'FR' must be initialized ».
     */
    private val FR = Locale.FRANCE

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", FR)

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", FR)
}
