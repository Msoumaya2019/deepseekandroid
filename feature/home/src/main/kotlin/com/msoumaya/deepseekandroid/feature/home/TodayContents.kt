package com.msoumaya.deepseekandroid.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppHeading
import com.msoumaya.deepseekandroid.core.design.component.AppInlineIcon
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.AppSectionHeader
import com.msoumaya.deepseekandroid.core.design.component.ArabicText
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.design.theme.ReadingArt
import com.msoumaya.deepseekandroid.core.domain.DailyContentsText

// ---------------------------------------------------------------------------
// Le carrousel des contenus du jour
// ---------------------------------------------------------------------------
// Portage de `TodayContents` (`src/DailyContentsScreen.tsx:47`).
//
// **Pourquoi un fichier à part.** L'accueil est déjà long, et ce bloc n'a rien à voir avec lui :
// il ne lit ni l'état applicatif ni le programme, ne compte rien, et ne connaît que le texte
// qu'on lui donne. Le poser ici évite d'ajouter une quinzaine d'imports de mise en page à
// `HomeScreen.kt` pour un bloc qui se lit seul — la même raison que `QuizHomeCards`.
//
// **Il ne décide de rien.** Les titres, les textes, la ligne de provenance et le libellé du
// lecteur d'écran arrivent résolus dans [DailyCarousel] : l'écran ne compare aucun type et ne
// joint aucune chaîne. C'est ce qui rend les règles éprouvables sans appareil — voir
// `HomeRendererTest`.
//
// **Ce qui n'est pas porté, et pourquoi.** `resolveContentMedia` n'est pas là : l'illustration est
// celle du dépôt (`ReadingArt`), c'est-à-dire exactement ce que l'original affiche **quand l'image
// manque** — et `DailyPreview` a ce repli dans les deux branches (`uri && !failed ? {uri} :
// readingArt`). La résolution des adresses signées reste donc à faire, et le jour où elle
// arrivera, seule la source de l'`Image` changera. La carte de détail, elle, n'est pas ici non
// plus : l'accueil d'origine ne l'ouvre **pas** — il appelle `onOpen`, et c'est l'écran de liste
// qui porte la fiche (`initialId`).
//
// **Trois mesures relevées à la source.**
//
//   1. La vignette est en retrait de **1 px** (`padding:1` sur le `Pressable`), et ce n'est pas du
//      confort : l'ombre de la carte déborde d'un pixel, et sans cette marge elle serait rognée.
//   2. Les pastilles n'apparaissent qu'à partir de **deux** vignettes (`entries.length>1`), et
//      l'espacement vaut **7** — pas 6, pas 8.
//   3. Le titre de section suit la vignette **affichée**, pas la première : c'est l'en-tête qui
//      change quand on fait défiler le carrousel, et non un titre fixe au-dessus d'un pager.
// ---------------------------------------------------------------------------

/** Côté de l'illustration de la vignette (`width:88,height:98` à la source). */
private val PREVIEW_ART_WIDTH = 88.dp
private val PREVIEW_ART_HEIGHT = 98.dp

/** Rayon de l'illustration (`borderRadius:12` à la source). */
private val PREVIEW_ART_RADIUS = 12.dp

/** Marge interne de la carte de vignette (`padding:9` à la source, contre 18 pour la carte standard). */
private val PREVIEW_PADDING = 9.dp

/** Marge qui laisse respirer l'ombre (`padding:1` sur le `Pressable` de la source). */
private val PREVIEW_SHADOW_ROOM = 1.dp

/** Côté d'une pastille de pagination (`width:6,height:6` à la source). */
private val DOT = 6.dp

/** Espacement entre deux pastilles (`gap:7` à la source). */
private val DOT_GAP = 7.dp

/** Marge verticale de la rangée de pastilles (`paddingVertical:9` à la source). */
private val DOT_ROW_PADDING = 9.dp

/** Côté du chevron (`size={18}` à la source). */
private val CHEVRON = 18.dp

/**
 * Le carrousel : un en-tête, au plus deux vignettes, et une pagination quand il y en a deux.
 *
 * @param carousel ce qu'il faut afficher, déjà résolu. La forme vide porte sa phrase d'état.
 * @param onOpen ouverture d'un contenu par son identifiant. L'accueil d'origine y branche
 *   l'écran de liste, et non une fiche : c'est là que la carte complète vit.
 */
@Composable
internal fun TodayContents(
    carousel: DailyCarousel,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (carousel) {
        is DailyCarousel.Empty -> TodayContentsEmpty(carousel = carousel, modifier = modifier)
        is DailyCarousel.Slides -> TodayContentsSlides(
            carousel = carousel,
            onOpen = onOpen,
            modifier = modifier,
        )
    }
}

/**
 * Le carrousel n'a rien à montrer : un en-tête de repli, et une carte qui dit pourquoi.
 *
 * **L'en-tête reste.** L'original écrit son `SectionHeader` **avant** de tester le contenu, donc
 * un carrousel vide est quand même coiffé de « Rappel du jour ». Le retirer ferait sauter une
 * ligne de l'accueil selon l'état du réseau, et l'écran bougerait sous le doigt.
 */
@Composable
private fun TodayContentsEmpty(
    carousel: DailyCarousel.Empty,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AppSectionHeader(title = carousel.title)
        AppCard {
            AppLabel(
                text = carousel.message,
                selectable = false,
                fontSize = 12.sp,
                color = AppTheme.colors.muted,
            )
        }
    }
}

/**
 * Le carrousel a des vignettes : un pager horizontal, et la pagination s'il y en a plus d'une.
 *
 * Le pager est **paginé** (`pagingEnabled` à la source), donc une vignette occupe toute la largeur
 * et le défilement s'arrête sur une frontière. C'est ce qui rend l'en-tête lisible : il change
 * quand la page change, et non au milieu d'un glissement.
 */
@Composable
private fun TodayContentsSlides(
    carousel: DailyCarousel.Slides,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { carousel.slides.size })

    Column(modifier = modifier) {
        // Le titre suit la vignette **affichée**. `getOrNull` n'est pas une précaution de style :
        // `currentPage` peut désigner une page que la liste ne porte plus le temps d'un
        // recomposition — un contenu retiré pendant que le carrousel est à l'écran.
        AppSectionHeader(
            title = carousel.slides.getOrNull(pager.currentPage)?.title
                ?: DailyContentsText.HOME_REMINDER,
        )

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val slide = carousel.slides[page]
            DailyPreview(slide = slide, onPress = { onOpen(slide.id) })
        }

        if (carousel.slides.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = DOT_ROW_PADDING),
                horizontalArrangement = Arrangement.spacedBy(
                    space = DOT_GAP,
                    alignment = Alignment.CenterHorizontally,
                ),
            ) {
                carousel.slides.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(DOT)
                            .clip(CircleShape)
                            .background(
                                if (index == pager.currentPage) {
                                    AppTheme.colors.green
                                } else {
                                    AppTheme.colors.line
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Une vignette : illustration, titre, arabe, phonétique, français, provenance, chevron.
 *
 * **Le contenu est fusionné pour le lecteur d'écran.** L'original pose `accessibilityLabel` sur le
 * bouton entier, ce qui remplace les cinq textes par cette seule phrase. Sans la fusion, TalkBack
 * annoncerait « Rappel du jour, arabe, phonétique, français, source, bouton » — cinq phrases pour
 * une vignette qui s'ouvre d'un seul geste.
 *
 * **L'illustration est celle du dépôt.** C'est le repli de l'original, dans les deux branches de
 * son `uri && !failed ? {uri} : readingArt`. Voir l'en-tête du fichier.
 */
@Composable
private fun DailyPreview(
    slide: DailySlide,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    AppCard(
        modifier = modifier
            .padding(PREVIEW_SHADOW_ROOM)
            .semantics(mergeDescendants = true) { contentDescription = slide.label },
        // La carte de vignette ne porte pas l'espacement bas de la carte standard : c'est le
        // carrousel qui décide de ce qui le suit, et deux marges feraient un trou.
        spaced = false,
        padding = PREVIEW_PADDING,
        onClick = onPress,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PREVIEW_ART_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(ReadingArt),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = PREVIEW_ART_WIDTH, height = PREVIEW_ART_HEIGHT)
                    .clip(RoundedCornerShape(PREVIEW_ART_RADIUS)),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                AppHeading(text = slide.title, size = 17.sp)

                // L'arabe est aligné à droite et **sur une seule ligne**, comme l'original
                // (`numberOfLines={1}`, `textAlign:'right'`) : une invocation longue ne doit pas
                // repousser le français hors de la vignette.
                slide.arabic?.let { arabic ->
                    ArabicText(
                        text = arabic,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Right,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }

                slide.phonetic?.let { phonetic ->
                    AppLabel(
                        text = phonetic,
                        selectable = false,
                        fontSize = 10.sp,
                        color = colors.muted,
                        maxLines = 1,
                    )
                }

                AppLabel(
                    text = slide.french,
                    selectable = false,
                    fontSize = 12.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 3.dp),
                )

                // La ligne de provenance est **déjà jointe et déjà filtrée** : une référence
                // absente ne laisse pas de séparateur orphelin, et le filtre vit dans le renderer,
                // où il se mesure sans appareil. Elle est affichée **sans condition**, comme
                // l'original, qui n'enveloppe pas ce `Label` dans un test — et cela ne peut pas
                // produire de ligne fantôme : la base exige une source non vide, donc la ligne
                // jointe ne l'est jamais.
                AppLabel(
                    text = slide.source,
                    selectable = false,
                    fontSize = 10.sp,
                    color = colors.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                AppInlineIcon(
                    icon = Icons.Outlined.ChevronRight,
                    tint = colors.green,
                    size = CHEVRON,
                )
            }
        }
    }
}
