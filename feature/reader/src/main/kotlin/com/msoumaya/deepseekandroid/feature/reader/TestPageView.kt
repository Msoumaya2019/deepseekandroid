package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.PageNavigation
import com.msoumaya.deepseekandroid.core.domain.TestPageOverlay
import com.msoumaya.deepseekandroid.core.domain.TestPageSession

/**
 * L'écran immersif : la composition d'une page, et ses deux voisines prêtes.
 *
 * Porté depuis `src/coranTest/CoranTestScreen.tsx`. C'est la surface de la source
 * « Coran avec règles de Tajwid », qui est la **source par défaut** du lecteur.
 *
 * ## Trois documents au plus, et pourquoi
 *
 * La page courante et ses deux voisines sont montées en même temps, chacune dans sa WebView.
 * Les voisines sont **transparentes, mais peintes et mesurées** : c'est ce qui fait qu'un
 * balayage n'affiche pas un temps de chargement. Le prix est de trois moteurs de rendu, et il
 * est borné — un seul endroit décide de ce qu'on garde ([TestPageSession.toKeep]), et le
 * chargeur en garde deux de plus en mémoire pour amortir les allers-retours.
 *
 * ## La page affichée est composée en dernier
 *
 * Dans une `Box`, le dernier enfant est dessus. L'ordre est donc forcé pour que la page
 * affichée soit la dernière : c'est elle qui reçoit le doigt, et pas une voisine invisible.
 *
 * Cela dit, **la correction ne repose pas sur cet ordre**. La règle de routage écarte les
 * messages d'une page qui n'est pas affichée — un `tap` émis par une voisine ne désigne rien,
 * qu'il soit reçu ou non. L'ordre est ce qui empêche le doigt de se perdre ; la règle est ce qui
 * empêche un message perdu de faire quelque chose. Les deux sont là, et pour deux raisons
 * différentes.
 *
 * ## Un écart assumé : le toucher sur une page vide ne fait rien
 *
 * Dans le client d'origine, `onTap` est branché sur `revealCommands`, qui est une fonction
 * **vide** : un appui court hors d'un verset n'y produit donc rien du tout. Le portage ne
 * comble pas ce trou — inventer un geste que l'original n'a pas serait un changement de
 * comportement, et la coquille du lecteur reste affichée puisqu'elle n'est jamais masquée. Le
 * rappel existe pour que l'appelant puisse en décider ; aujourd'hui, personne ne s'en sert.
 *
 * @param page la page demandée.
 * @param markers ce que l'application sait des marques — et **le mode de pose**, qui décide à la
 *   fois de ce que le document colore et de ce qu'un appui désigne. Une seule valeur pour les
 *   deux : les séparer les ferait diverger, et la page colorerait autre chose que ce qu'un appui
 *   enregistrerait.
 * @param background le fond de la composition. Il vient du même jeton que `markers.background`.
 * @param onPage demande une autre page après un balayage.
 * @param onSaveBookmark enregistre le verset touché. `null` retire le geste : sans
 *   enregistrement, le mode de pose n'est de toute façon jamais armé.
 * @param onLongPressVerse ouvre les actions du verset touché.
 * @param onBlankLongPress ouvre les options — le geste de l'original sur une marge.
 * @param onTap l'appui court qui ne désigne aucun verset. Voir l'écart assumé ci-dessus.
 */
@Composable
internal fun TestPageView(
    page: Int,
    markers: TestPageOverlay.Markers,
    background: Color,
    modifier: Modifier = Modifier,
    onPage: (Int) -> Unit = {},
    onSaveBookmark: ((Int) -> Unit)? = null,
    onLongPressVerse: (Int) -> Unit = {},
    onBlankLongPress: () -> Unit = {},
    onTap: () -> Unit = {},
) {
    val context = LocalContext.current

    // Les documents montés, la page affichée, et celles qui se sont mesurées. Ce sont trois
    // états distincts, et les confondre ferait exactement ce que l'original évite : afficher
    // une page vide en attendant qu'elle se remplisse.
    var documents by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var measured by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var displayed by rememberSaveable { mutableIntStateOf(page) }
    var failed by remember { mutableStateOf(false) }
    var attempt by rememberSaveable { mutableIntStateOf(0) }

    // L'état envoyé aux documents. Calculé une fois par état de marques : le sérialiser à chaque
    // recomposition ferait pousser un message identique à chaque image.
    val stateJson = remember(markers) { TestPageOverlay.json(markers) }

    // La clé contient `page in measured` : c'est la condition de l'original (`readyPage===page`).
    // Tant que la page demandée n'est pas mesurée, on ne charge qu'elle ; dès qu'elle l'est, ses
    // voisines se préparent — et l'effet se relance tout seul parce que la clé a changé.
    LaunchedEffect(page, attempt, page in measured) {
        val targets = TestPageSession.toLoad(page, measured = page in measured)
        for (target in targets) {
            runCatching { TestPageDocuments.document(context, target) }
                .onSuccess { html ->
                    documents = documents + (target to html)
                    // Une page qui se recharge après un échec efface l'échec : c'est la même
                    // chose que de la voir arriver pour la première fois.
                    if (target == page) failed = false
                }
                .onFailure {
                    // Seule la page demandée peut faire échouer l'écran. Une voisine qui manque
                    // n'empêche pas de lire celle qu'on a devant les yeux.
                    if (target == page) failed = true
                }
        }
        // Les trois documents de la page, et rien d'autre : sans cet élagage, la mémoire
        // croîtrait avec le nombre de pages visitées.
        val keep = TestPageSession.toKeep(page)
        documents = documents.filterKeys { it in keep }
        measured = measured.filterTo(mutableSetOf()) { it in keep }
    }

    // Une page déjà mesurée s'affiche **tout de suite** : c'est ce qui fait qu'un retour sur ses
    // pas ne repasse pas par l'écran de chargement.
    LaunchedEffect(page, measured) {
        if (page in measured) {
            displayed = page
            failed = false
        }
    }

    val handle: (Int, String) -> Unit = { target, raw ->
        val message = TestPageSession.parse(raw)
        if (message != null) {
            when (val decision = TestPageSession.route(
                target = target,
                current = page,
                displayed = displayed,
                selecting = markers.selecting,
                message = message,
            )) {
                TestPageSession.Decision.Ignored -> Unit

                is TestPageSession.Decision.Measured -> {
                    measured = measured + decision.page
                    if (decision.display) {
                        displayed = decision.page
                        failed = false
                    }
                }

                TestPageSession.Decision.Failed -> {
                    measured = measured - target
                    failed = true
                }

                is TestPageSession.Decision.Select -> onSaveBookmark?.invoke(decision.id)

                TestPageSession.Decision.Tap -> onTap()

                is TestPageSession.Decision.Mark -> onLongPressVerse(decision.id)

                TestPageSession.Decision.BlankLongPress -> onBlankLongPress()

                is TestPageSession.Decision.Turn -> {
                    // Les seuils et la borne viennent du domaine, et la borne est celle de la
                    // composition — le défaut de `pageAfterSwipe`, comme dans l'original.
                    val next = PageNavigation.pageAfterSwipe(
                        page = page,
                        dx = decision.dx.toFloat(),
                        dy = decision.dy.toFloat(),
                    )
                    if (next != page) onPage(next)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(background)) {
        // La page affichée en dernier : voir la note du composant.
        val order = documents.keys.sortedBy { if (it == displayed) 1 else 0 }
        for (target in order) {
            val html = documents.getValue(target)
            key(target) {
                TestPageSurface(
                    html = html,
                    stateJson = stateJson,
                    visible = target == displayed,
                    onMessage = { raw -> handle(target, raw) },
                )
            }
        }

        // Ce que l'original affiche par-dessus la composition tant qu'elle n'est pas là. Le
        // message n'est pas décoratif : une page blanche sans explication se confond avec une
        // panne, et c'est précisément ce que `QuranSourceReady` refuse ailleurs.
        val waiting = displayed != page || documents[page] == null
        if (failed || waiting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp),
            ) {
                if (failed) {
                    Text(
                        text = RETRY_NOTICE,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppTheme.colors.paper)
                            .clickable {
                                // Recharger la page, c'est **oublier** son document et sa
                                // mesure : sans cela, l'écran retrouverait le même document
                                // fautif et se croirait guéri.
                                failed = false
                                documents = documents - page
                                measured = measured - page
                                attempt += 1
                            }
                            .padding(12.dp),
                        color = AppTheme.colors.green,
                        fontSize = AppTheme.typeScale.metadata,
                    )
                } else {
                    Text(
                        text = LOADING_NOTICE,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTheme.colors.paper)
                            .padding(6.dp),
                        color = AppTheme.colors.muted,
                        fontSize = AppTheme.typeScale.metadata,
                    )
                }
            }
        }
    }
}

/** Les mots du client d'origine, repris tels quels : les deux clients disent la même chose. */
private const val LOADING_NOTICE = "Chargement du Coran…"
private const val RETRY_NOTICE = "La page n’a pas pu être chargée. Réessayer"
