package com.msoumaya.deepseekandroid.feature.reader

import com.msoumaya.deepseekandroid.core.audio.AudioRecorder
import com.msoumaya.deepseekandroid.core.audio.RecitationPlayer
import com.msoumaya.deepseekandroid.core.model.LocalRecitation
import com.msoumaya.deepseekandroid.core.model.Range

// ---------------------------------------------------------------------------
// Ce dont l'enregistrement a besoin, et ce que le lecteur n'ira pas chercher
// ---------------------------------------------------------------------------
// Le lecteur porte une **contrainte**, écrite dans son fichier de construction : il ne dépend pas
// de `core:data`. Elle n'est pas une économie de dépendance, elle est la condition d'une
// propriété — le lecteur doit s'ouvrir en avion, donc il n'a rien à demander à la couche qui parle
// au réseau et au disque.
//
// Or l'enregistrement, lui, a bien besoin des deux : il lit le compte ouvert, il écrit un fichier
// au registre, il partage. Le portage résout cela de la même façon que pour la séance d'écoute —
// **par injection**. Le lecteur reçoit une capacité, et cette capacité est faite de **ports** et de
// **gestes**, jamais de types de la couche de données.
//
// Ce fichier est donc la frontière : tout ce qui est au-dessus (le lecteur, la barre, l'écran) ne
// connaît que ce paquet-ci ; tout ce qui est en dessous (le registre, le compartiment, le compte)
// est tenu par l'appelant, seul à savoir comment.
// ---------------------------------------------------------------------------

/**
 * Ce que le lecteur doit pouvoir faire pour enregistrer une récitation.
 *
 * ## Pourquoi un paquet, et non sept paramètres de plus
 *
 * `ReaderScreen` reçoit déjà chacune de ses capacités en paramètre, et c'est ce qui fait qu'une
 * capacité absente **retire** son geste au lieu de le laisser mener nulle part. Mais sept
 * paramètres de plus sur une signature qui en porte déjà vingt-quatre rendraient chaque appel
 * illisible, et surtout impossibles à faire évoluer : ajouter un besoin de l'enregistreur
 * toucherait tous les appelants du lecteur, y compris ceux qui n'enregistrent pas.
 *
 * Un paquet dit la même chose en un seul endroit : **ou bien le lecteur sait enregistrer, ou bien
 * il ne sait pas**. C'est une capacité, pas une collection d'options.
 *
 * ## Ce que le paquet ne contient pas, et pourquoi
 *
 * **Le microphone.** Demander une permission est un geste qui passe par une `Activity` — un
 * lanceur de résultat, que seul un composable peut installer. La capacité reste donc libre de
 * toute notion d'écran, et c'est la surface qui interroge et demande l'accès. Le domaine, lui,
 * reçoit la réponse sous forme de booléen — voir `RecitationRecorder.startProblem`.
 *
 * **Le texte de la notice et son geste.** Ils vivent dans `RecitationText` et dans la surface. Ici
 * on ne fait que **lire** si elle a été acceptée, et l'inscrire quand elle l'est.
 *
 * ## L'identité de ce paquet compte
 *
 * Ses champs sont des fonctions, donc deux constructions successives ne sont jamais égales. Un
 * appelant qui en construirait un à chaque recomposition ferait recomposer le lecteur à chaque
 * image. Il doit donc le construire **une fois** — `remember` — et le garder.
 *
 * @param recorder le port de l'enregistreur. Il décide du format, lève si on l'utilise après
 *   libération, et rend `false` quand le matériel refuse — un refus du système n'est pas une panne.
 * @param player le lecteur de la récitation enregistrée. C'est celui de l'application, et non un
 *   second : les deux écrans qui s'en servent ne sont jamais visibles ensemble, et un second
 *   lecteur Media3 coûterait une seconde prise d'attention audio.
 * @param owner l'identifiant du compte ouvert, ou `null`. **Lu au moment du geste**, et non
 *   capturé : un compte peut s'ouvrir ou se fermer sans que le lecteur soit reconstruit, et une
 *   valeur figée enregistrerait une récitation sous un compte qui n'est plus le sien.
 * @param noticeAccepted dit si ce compte a déjà lu la notice. Elle est **demandée par compte**,
 *   comme la clé du magasin : un second compte sur le même téléphone ne l'a pas lue.
 * @param acceptNotice inscrit que ce compte l'a acceptée. Idempotent.
 * @param save copie le brouillon au registre et rend la récitation inscrite, ou `null`. C'est le
 *   seul endroit qui connaisse les bornes — un passage a des versets, une invocation n'en a pas.
 * @param share propose la récitation à un ami. Reçoit la récitation **inscrite**, donc son
 *   identifiant, qui est ce que le partage transmet. **Facultatif**, comme le `onShare?` de
 *   l'original : un écran qui n'a pas de quoi partir vers le partage retire le geste au lieu de
 *   l'offrir sans effet — et c'est exactement ce que fait le lecteur aujourd'hui, dont le partage
 *   quitte l'écran pour la liste des récitations, une sortie que sa route ne sait pas encore
 *   demander. Voir la note du lecteur, à l'endroit où la capacité est construite.
 */
class RecitationRecorderCapability(
    val recorder: AudioRecorder,
    val player: RecitationPlayer,
    val owner: () -> String?,
    val noticeAccepted: suspend (String) -> Boolean,
    val acceptNotice: suspend (String) -> Unit,
    val save: suspend (path: String, durationMs: Long, range: Range) -> LocalRecitation?,
    val share: ((LocalRecitation) -> Unit)?,
)
