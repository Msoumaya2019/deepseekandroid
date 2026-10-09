package com.msoumaya.deepseekandroid.feature.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.msoumaya.deepseekandroid.core.data.repository.ProblemReportAttachment
import com.msoumaya.deepseekandroid.core.domain.ProblemReportText
import com.msoumaya.deepseekandroid.core.domain.ProblemReports
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Choisir une capture d'écran
// ---------------------------------------------------------------------------
// C'est la frontière de l'écran de signalement : tout ce qui est au-dessus — la carte, la feuille,
// le calcul — ne connaît que ce fichier ; tout ce qui est en dessous — le sélecteur du système, la
// lecture d'un `content://`, la copie dans le cache — est tenu par l'implémentation, et n'existe
// pas dans les tests.
//
// Le même choix que pour l'enregistreur de récitation : **la capacité est injectée**, et l'écran
// reste éprouvable sans appareil. Ici, ce qui est injecté est un geste qui rend un fichier, et non
// une permission — la différence est que le sélecteur du système ne demande aucun droit, donc rien
// à interroger avant de l'ouvrir.
//
// ## Ce que la capacité fait, et que l'original laisse à `expo-image-picker`
//
// L'original reçoit d'`expo-image-picker` un `asset` qui porte **déjà** un fichier — la
// bibliothèque a copié l'image dans le cache de l'application —, sa taille et son type MIME. Le
// portage reçoit une adresse `content://` qui appartient au fournisseur, et doit donc faire les
// trois lui-même : lire le type, copier les octets, mesurer le fichier. C'est ce que fait
// [materialiser].
//
// ## La validation, et où elle tombe
//
// Le fichier est **d'abord copié, puis mesuré**, et non l'inverse. La raison est mesurable : la
// taille d'un `content://` se lit sur un descripteur, qui rend `UNKNOWN_LENGTH` (-1) chez certains
// fournisseurs, alors que celle d'un fichier sur le disque ne ment jamais. Mesurer la copie donne
// donc toujours la bonne réponse, et la règle « c'est la taille sur le disque qui décide » est
// exactement celle que le dépôt applique au moment de l'envoi — voir `ProblemReportRepository.send`.
//
// Le prix est de copier un fichier qui sera refusé. Il est borné : la copie est sur le répartiteur
// d'entrées-sorties, et elle est suivie de l'effacement du refusé.
// ---------------------------------------------------------------------------

/**
 * Ouvre le sélecteur d'images du système et rend la capture choisie.
 *
 * Rend `null` quand la personne renonce — un renoncement n'est pas une erreur, et l'écran ne doit
 * rien dire. Lève [ProblemReportPickException] quand le fichier choisi est refusé.
 */
fun interface ProblemReportScreenshotPicker {
    suspend fun pick(): ProblemReportAttachment?
}

/**
 * Un fichier choisi que les règles refusent.
 *
 * Le message est celui de [ProblemReportText], et il est **déjà** la phrase à afficher : l'écran
 * n'a rien à traduire, il pose le texte. Une exception qui porterait un code obligerait l'écran à
 * refaire la traduction que `ProblemReportRenderer.pickProblem` vient de faire.
 */
class ProblemReportPickException(message: String) : Exception(message)

/**
 * La capacité, installée sur le sélecteur d'images du système.
 *
 * `remember` est nécessaire, et pas cosmétique : la capacité est une lambda, donc deux
 * constructions successives ne sont jamais égales, et la reconstruire à chaque recomposition
 * ferait recomposer la feuille à chaque frappe.
 *
 * Le sélecteur ne demande **aucune permission** : `GetContent` passe par le sélecteur du système,
 * qui rend une adresse que l'application n'a le droit de lire que pour cette image-là.
 */
@Composable
internal fun rememberProblemReportPicker(): ProblemReportScreenshotPicker {
    val context = LocalContext.current

    // Le choix en attente. La sélection est asynchrone — la personne peut mettre une minute à
    // choisir une image —, et c'est ce report qui fait de `pick()` une fonction suspendue.
    val attente = remember { mutableStateOf<((Uri?) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val suite = attente.value
        attente.value = null
        suite?.invoke(uri)
    }

    return remember(context, launcher) {
        ProblemReportScreenshotPicker {
            val uri = suspendCancellableCoroutine { suite ->
                attente.value = { choix -> suite.resume(choix) }
                launcher.launch(IMAGE_MIME)
            }
            if (uri == null) null else withContext(Dispatchers.IO) { materialiser(context, uri) }
        }
    }
}

/**
 * Copie le contenu de [uri] dans le cache, le mesure, et le refuse s'il ne convient pas.
 *
 * Le fichier est rangé sous le cache et non sous `filesDir` : c'est un **brouillon**. La copie qui
 * compte — celle que la file garde jusqu'à l'envoi — est faite par `ProblemReportStore.enqueue`,
 * au moment où le signalement est inscrit. Deux copies, deux rôles : celle-ci appartient au
 * sélecteur et meurt avec lui, celle-là appartient au signalement.
 *
 * @throws ProblemReportPickException si le fichier est illisible, ou refusé par
 *   [ProblemReportRenderer.pickProblem]. La copie est alors effacée.
 */
private fun materialiser(context: Context, uri: Uri): ProblemReportAttachment {
    val declare = context.contentResolver.getType(uri)
    // Le repli sur l'extension est celui de `ProblemReports.resolveMime` : le sélecteur du système
    // rend un type MIME pour la plupart des fournisseurs, mais pas pour tous.
    val mime = ProblemReports.resolveMime(declare, uri.toString())

    val dossier = File(context.cacheDir, PICK_DIRECTORY).apply { mkdirs() }
    val cible = File(dossier, "${UUID.randomUUID()}.${ProblemReports.extensionFor(mime)}")

    try {
        val entree = context.contentResolver.openInputStream(uri)
            ?: throw ProblemReportPickException(ProblemReportText.ATTACH_FAILED)
        entree.use { flux -> cible.outputStream().use { flux.copyTo(it) } }

        ProblemReportRenderer.pickProblem(mime, cible.length())?.let {
            throw ProblemReportPickException(it)
        }
        return ProblemReportAttachment(path = cible.absolutePath, mime = mime)
    } catch (refus: ProblemReportPickException) {
        cible.delete()
        throw refus
    }
}

/** Le dossier des captures choisies, sous le cache. Un brouillon, jamais une archive. */
private const val PICK_DIRECTORY = "problem-report-pick"

/**
 * Le type demandé au sélecteur.
 *
 * Toutes les images — le préfixe `image/` suivi d'une étoile, valeur de [IMAGE_MIME] —, comme
 * l'original : ce n'est pas au sélecteur de filtrer le format, puisque le format est refusé
 * **après** le choix, avec une phrase qui dit lequel est accepté. Demander `image/png` et
 * `image/jpeg` séparément aurait demandé deux gestes pour un seul choix.
 */
private const val IMAGE_MIME = "image/*"
