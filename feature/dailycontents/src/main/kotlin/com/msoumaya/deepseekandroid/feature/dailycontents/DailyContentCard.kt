package com.msoumaya.deepseekandroid.feature.dailycontents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.ArabicText
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import kotlinx.coroutines.CancellationException

// ---------------------------------------------------------------------------
// La fiche d'un contenu du jour
// ---------------------------------------------------------------------------
// Portage de `ContentCard` (`src/DailyContentsScreen.tsx:15`). Elle sert **deux fois** : sous
// chaque ligne de l'écran de liste, et seule quand la liste a cédé la place à une fiche ouverte.
//
// **Elle ne décide de rien.** Le titre, les textes, la provenance, le glyphe du favori et le texte
// partagé arrivent résolus dans [DailyCard] : l'écran ne compare aucun type et ne joint aucune
// chaîne. C'est ce qui rend les règles éprouvables sans appareil — voir
// `DailyContentsListRendererTest`.
//
// **Ce qui n'est pas porté, et pourquoi.**
//
//   1. **Le bouton d'écoute** — « ▶ Écouter » / « Ⅱ Pause » — et son lecteur. L'application a déjà
//      deux sources audio, la séance d'écoute du moushaf et le lecteur d'une récitation ; la fiche
//      en serait une troisième, et l'original tranche « une seule à la fois » par une variable
//      **globale de module**. Ce n'est pas une décision de cette carte, et le portage ne
//      l'invente pas. Voir le bloc d'absence de `DailyContentsText`.
//   2. **Le bouton d'enregistrement** et son `RecitationRecorder` : la capacité du lecteur exige
//      une **plage de versets**, et une invocation n'en a pas.
//   3. **L'avis local de la carte.** L'original en tient un par carte et le pose au bas de
//      celle-ci. Ici, il arrive par [notice], et l'écran de liste choisit de n'en passer aucun —
//      son message d'erreur a **un seul** domicile, l'en-tête. La fiche ouverte, elle, n'a pas
//      d'en-tête visible sous lequel le poser, et reçoit donc le message.
//
// **Deux mesures de la source, relevées et reprises.**
//
//   1. La carte de la fiche n'a **pas** la géométrie des autres : `padding:18,borderRadius:26`,
//      là où la carte du dépôt vaut 16 et le rayon du thème 20. C'est pourquoi [AppCard] expose
//      son rayon.
//   2. L'arabe est en **24 px vert**, et non en 22 px couleur de texte comme dans la vignette du
//      carrousel : la fiche est l'endroit où on lit le texte, la vignette celui où on le
//      reconnaît. L'interligne de la source (42 px) n'est **pas** repris : `ArabicText` laisse
//      volontairement Amiri décider du sien, et la vignette suit déjà la même règle.
// ---------------------------------------------------------------------------

/** Marge interne de la fiche (`padding:18` à la source). */
private val CARD_PADDING = 18.dp

/** Rayon de la fiche (`borderRadius:26` à la source), plus large que celui des autres cartes. */
private val CARD_RADIUS = 26.dp

/** Côté de l'illustration (`width:40,height:40` à la source). */
private val ART_SIZE = 40.dp

/** Rayon de l'illustration (`borderRadius:10` à la source). */
private val ART_RADIUS = 10.dp

/** Côté de la bascule des favoris (`minWidth:44,minHeight:44` à la source). */
private val FAVORITE_SIZE = 44.dp

/** Corps du glyphe de la bascule (`fontSize:24` à la source). */
private val FAVORITE_GLYPH_SIZE = 24.sp

/** Corps de l'arabe de la fiche (`fontSize:24` à la source). */
private val ARABIC_SIZE = 24.sp

/** Interligne du français (`lineHeight:23` pour un corps de 14 à la source). */
private val FRENCH_LINE_HEIGHT = 23.sp

/** Interligne de l'explication (`lineHeight:24` à la source). */
private val EXPLANATION_LINE_HEIGHT = 24.sp

/** Marge verticale de l'arabe (`marginVertical:6` à la source). */
private val ARABIC_MARGIN = 6.dp

/**
 * La fiche d'un contenu.
 *
 * @param card ce qu'il faut afficher, déjà résolu en texte. L'adresse de l'illustration y reste
 *   **brute** : c'est ici qu'elle se signe.
 * @param resolve signature d'une adresse de média. Passée en paramètre plutôt que lue d'un dépôt :
 *   une carte n'a pas à connaître `core:data`, et une prévisualisation peut alors la remplacer par
 *   la fonction identité.
 * @param onToggleFavorite bascule du favori. La carte ne connaît que le **geste** : c'est l'écran
 *   qui sait de quel contenu il s'agit et dans quel sens aller.
 * @param onShare partage du texte résolu. La carte fournit [DailyCard.shareText], l'écran décide
 *   de ce qu'un partage veut dire sur la plateforme.
 * @param notice message à poser au bas de la carte, ou `null`. Voir l'écart n° 3 de l'en-tête.
 */
@Composable
internal fun DailyContentCard(
    card: DailyCard,
    resolve: suspend (String?) -> String?,
    onToggleFavorite: () -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    val colors = AppTheme.colors

    // **L'illustration se résout ici, et non dans le ViewModel.** L'original la résout par carte,
    // dans un effet qui se rejoue quand l'adresse change : c'est une suspension courte dont le
    // résultat ne concerne que cette carte. La remonter ferait un état partagé pour une donnée
    // que personne d'autre ne lit.
    //
    // `remember(card.imageUrl)` porte les deux réinitialisations de l'original — son
    // `useEffect(()=>setImageError(false),[item.image_url])` et son `setImageUrl(null)` —, donc
    // changer de contenu ne laisse ni l'image du précédent ni son échec.
    var resolved by remember(card.imageUrl) { mutableStateOf<String?>(null) }
    var imageError by remember(card.imageUrl) { mutableStateOf(false) }

    LaunchedEffect(card.imageUrl) {
        resolved = null
        try {
            resolved = resolve(card.imageUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // L'original publie ici un avis dans la carte. Le portage n'en pose pas : la
            // conséquence visible est la même que la sienne — pas d'illustration —, et elle ne
            // ment pas.
            resolved = null
        }
    }

    AppCard(
        modifier = modifier,
        padding = CARD_PADDING,
        shape = RoundedCornerShape(CARD_RADIUS),
    ) {
        // **L'en-tête dépend de l'illustration.** L'original écrit
        // `!!item.title || (!!item.image_url && !imageError)` : un contenu sans titre dont
        // l'image échoue n'a donc **aucun** en-tête. C'est la raison pour laquelle l'échec est un
        // état suivi, et non un simple silence. Voir `DailyCard.hasTitle`.
        val illustration = resolved?.takeIf { !imageError }
        if (card.hasTitle || (card.imageUrl != null && !imageError)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
            ) {
                AppLabel(
                    text = card.title,
                    selectable = false,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )

                if (illustration != null) {
                    AsyncImage(
                        model = illustration,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        // Coil ne prévient pas par un `onError` : c'est l'état du peintre qui le
                        // dit. Un `Error` fige l'échec pour cet URL, donc la recomposition qui
                        // suit ne relance pas la requête.
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Error) imageError = true
                        },
                        modifier = Modifier
                            .size(ART_SIZE)
                            .clip(RoundedCornerShape(ART_RADIUS)),
                    )
                }
            }
        }

        card.arabic?.let { arabic ->
            ArabicText(
                text = arabic,
                color = colors.green,
                fontSize = ARABIC_SIZE,
                modifier = Modifier.padding(vertical = ARABIC_MARGIN),
            )
        }

        card.phonetic?.let { phonetic ->
            AppLabel(
                text = phonetic,
                selectable = false,
                textAlign = TextAlign.Center,
                color = colors.muted,
                style = TextStyle(fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(bottom = AppTheme.spacing.md),
            )
        }

        AppLabel(
            text = card.french,
            selectable = false,
            textAlign = TextAlign.Center,
            style = TextStyle(lineHeight = FRENCH_LINE_HEIGHT),
            modifier = Modifier.padding(vertical = AppTheme.spacing.sm),
        )

        card.explanation?.let { explanation ->
            AppLabel(
                text = explanation,
                selectable = false,
                color = colors.muted,
                style = TextStyle(lineHeight = EXPLANATION_LINE_HEIGHT),
                modifier = Modifier.padding(bottom = AppTheme.spacing.sm),
            )
        }

        card.source?.let { source ->
            AppLabel(
                text = source,
                selectable = false,
                fontSize = AppTheme.typeScale.secondary,
                color = colors.muted,
                modifier = Modifier.padding(top = AppTheme.spacing.sm),
            )
        }

        // **Le favori est à droite, le partage encore plus.** L'original aligne les deux à la fin
        // de la ligne, dans cet ordre, et le glyphe occupe une pastille de 44 px — c'est sa zone
        // tactile, pas une décoration.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FavoriteToggle(
                glyph = card.favoriteGlyph,
                label = card.favoriteLabel,
                onPress = onToggleFavorite,
            )
            AppButton(
                text = card.shareLabel,
                onClick = { onShare(card.shareText) },
                secondary = true,
                small = true,
            )
        }

        notice?.let { message ->
            AppLabel(
                text = message,
                selectable = false,
                fontSize = AppTheme.typeScale.secondary,
                color = colors.muted,
            )
        }
    }
}

/**
 * La bascule des favoris : un cœur, dans une pastille.
 *
 * **Le glyphe est remplacé pour le lecteur d'écran.** L'original pose un `accessibilityLabel` sur
 * la zone entière ; sans la fusion, TalkBack annoncerait « ♥, bouton » — un cœur, là où la
 * personne a besoin d'entendre « Ajouter aux favoris ».
 */
@Composable
private fun FavoriteToggle(glyph: String, label: String, onPress: () -> Unit) {
    Box(
        modifier = Modifier
            .size(FAVORITE_SIZE)
            .clip(CircleShape)
            .background(AppTheme.colors.soft)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Button, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        AppLabel(
            text = glyph,
            selectable = false,
            fontSize = FAVORITE_GLYPH_SIZE,
            color = AppTheme.colors.green,
        )
    }
}
