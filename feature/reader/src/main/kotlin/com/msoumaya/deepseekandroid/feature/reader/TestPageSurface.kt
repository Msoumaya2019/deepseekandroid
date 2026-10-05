package com.msoumaya.deepseekandroid.feature.reader

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * La surface d'une page de la composition : une WebView, et le pont qui la fait parler.
 *
 * Porté depuis `src/coranTest/PageSurface.tsx`. Le document se peint lui-même et se mesure
 * lui-même ; cette surface n'a que trois responsabilités — le charger, lui pousser l'état de
 * surimpression, et rapporter ce qu'il envoie.
 *
 * ## Ce qui est fermé, et pourquoi c'est fermé
 *
 * Le document est **le nôtre**, écrit à partir de nos propres ressources ; il n'a rien à aller
 * chercher. La fermeture est donc à trois niveaux, et aucun n'est décoratif :
 *
 * - la politique de sécurité du document (`default-src 'none'`) interdit tout chargement, et
 *   n'autorise que les polices `data:` — celles que le document porte lui-même ;
 * - la navigation est **refusée** : `shouldOverrideUrlLoading` rend toujours vrai, donc ni un
 *   lien, ni une redirection, ni une intention ne peuvent emmener la surface ailleurs ;
 * - l'accès aux fichiers et aux fournisseurs de contenu est coupé. Sans cela, une page sans
 *   origine pourrait lire le disque de l'application.
 *
 * ## Le pont, et le fil sur lequel il parle
 *
 * `addJavascriptInterface` expose [BRIDGE] au document, qui s'en sert **en premier** — avant le
 * pont de React Native, qui n'existe plus ici, et avant `window.parent`, qui ne sert qu'à
 * l'aperçu dans un navigateur.
 *
 * Une méthode exposée est appelée sur le fil du pont JavaScript, **pas** sur le fil principal.
 * Tout ce qu'elle déclenche est de l'état Compose : le message est donc reposé sur le fil
 * principal avant d'être livré. Sans cela, l'écran serait modifié depuis un fil qui n'a pas le
 * droit d'y toucher — un défaut qui ne se voit ni à la compilation, ni la plupart du temps à
 * l'exécution.
 *
 * ## Ce que le système ne doit pas faire au document
 *
 * Le **zoom de texte** du système est neutralisé : il agrandirait les glyphes sans agrandir les
 * lignes de 122 px que le document compose, donc les mots se chevaucheraient, et les rectangles
 * mesurés le seraient dans une échelle que le document n'a pas choisie. C'est un réglage
 * d'accessibilité légitime pour une page de texte ; il ne l'est pas pour une composition
 * mesurée, qui porte son propre agrandissement (pincement, jusqu'à trois fois).
 *
 * @param html le document complet, polices comprises.
 * @param stateJson l'état de surimpression, déjà sérialisé — voir `TestPageOverlay`.
 * @param visible la page est-elle celle qu'on lit ? Les autres sont montées, mesurées, et
 *   transparentes : c'est ce qui fait qu'un balayage n'attend pas.
 * @param onMessage le texte brut envoyé par le document. Il est analysé par l'appelant, qui
 *   seul sait à quelle page cette surface correspond.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun TestPageSurface(
    html: String,
    stateJson: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    // L'état et le rappel sont lus **au moment de l'appel**, jamais capturés : le pont et le
    // client vivent aussi longtemps que la WebView, alors que leurs valeurs changent à chaque
    // recomposition. Les figer ferait livrer le premier message reçu au premier état de l'écran.
    val latestMessage = rememberUpdatedState(onMessage)
    val latestState = rememberUpdatedState(stateJson)

    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.domStorageEnabled = false
            settings.mediaPlaybackRequiresUserGesture = true
            settings.textZoom = 100
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AndroidColor.TRANSPARENT)
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun postMessage(message: String) {
                        post { latestMessage.value(message) }
                    }
                },
                BRIDGE,
            )
            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                override fun onPageFinished(view: WebView, url: String) {
                    // L'état est repoussé à chaque fin de chargement : celui poussé avant que le
                    // document existe n'a rien trouvé à qui parler.
                    view.evaluateJavascript(stateCall(latestState.value), null)
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // Le moteur de rendu est mort : la surface est inutilisable. Rendre `faux`
                    // ferait tomber l'application entière ; on le dit, et l'écran propose de
                    // recharger la page — ce que l'original fait de la même façon.
                    post { latestMessage.value("""{"type":"error"}""") }
                    return true
                }
            }
        }
    }

    // Le document est rechargé quand il change, et seulement alors : c'est ce qui permet à une
    // page déjà chargée de survivre à un changement de page voisine sans se repeindre.
    LaunchedEffect(web, html) {
        web.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
    }

    // L'état part à chaque changement. Pousser avant que le document soit chargé ne fait rien,
    // et `onPageFinished` s'en charge alors — les deux ensemble couvrent la course.
    LaunchedEffect(web, stateJson) {
        web.evaluateJavascript(stateCall(stateJson), null)
    }

    AndroidView(
        factory = { web },
        modifier = modifier.fillMaxSize(),
        // L'alpha est posé sur la vue elle-même : la page voisine reste **mesurée et peinte**,
        // donc prête, mais invisible. C'est exactement l'`opacity` du client d'origine.
        update = { view -> view.alpha = if (visible) 1f else 0f },
        onRelease = { view ->
            // Une WebView doit être détachée avant d'être détruite : détruire une vue encore
            // attachée laisse un moteur de rendu orphelin.
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        },
    )
}

/** Le nom exposé au document, et celui qu'il essaie en premier. */
private const val BRIDGE = "CoranTest"

/**
 * La base du document. Il n'a pas d'origine : la politique de sécurité du document et le refus
 * de navigation font le reste.
 */
private const val BASE_URL = "about:blank"

/**
 * L'appel qui pousse l'état au document.
 *
 * Le JSON est écrit tel quel dans l'expression JavaScript. C'est sûr ici, et vérifiable : le
 * texte produit par `TestPageOverlay` ne contient que des booléens, des chiffres, des
 * deux-points, des accolades, des crochets, des guillemets et des couleurs hexadécimales —
 * aucun texte libre n'y entre, donc aucun caractère à échapper. Un contrôle du domaine mesure
 * cette propriété plutôt que de la supposer.
 */
private fun stateCall(json: String): String =
    "window.applyReaderState&&window.applyReaderState($json);true;"
