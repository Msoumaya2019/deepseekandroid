package com.msoumaya.deepseekandroid.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.component.AppInlineIcon
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Les deux cartes de quiz de l'accueil
// ---------------------------------------------------------------------------
// Portage de `QuizHomeCards` (`src/ui/QuizScreen.tsx:11`).
//
// **Pourquoi un fichier à part.** L'accueil est déjà long, et ces deux cartes n'ont rien à voir
// avec lui : elles ne lisent pas l'état applicatif, ne comptent rien et ne connaissent que le
// texte qu'on leur donne. Les poser ici évite d'ajouter six imports de mise en page à
// `HomeScreen.kt` pour un bloc qui se lit seul.
//
// **Elles ne décident de rien.** Le sous-titre, le détail et la pastille arrivent résolus dans
// [QuizCards] : l'écran ne compare aucune date et ne choisit entre aucun libellé. C'est ce qui rend
// la règle éprouvable sans appareil — voir `HomeRendererTest`.
//
// **Un écart de géométrie assumé, et mesuré.** La source pose `borderRadius:18` là où la carte
// standard du dépôt vaut 20 : les cartes de quiz sont légèrement plus serrées. `AppCard` ne sait
// pas l'exprimer — il dessine toujours 20 et la couleur de trait du thème —, donc la carte est
// écrite ici, comme celle du Quiz et comme `AppDailyTaskCard` avant elle.
// ---------------------------------------------------------------------------

/** Rayon des cartes de quiz (`borderRadius:18` à la source, contre 20 pour la carte standard). */
private val CARD_RADIUS = 18.dp

/** Côté du rond qui porte l'icône (`width:34,height:34` à la source). */
private val MEDALLION = 34.dp

/** Côté de l'icône dans le rond (`size={23}` à la source). */
private val ICON_SIZE = 23.dp

/** Côté de la pastille rouge (`width:6,height:6` à la source). */
private val ALERT_DOT = 6.dp

/** Décalage de la pastille depuis le coin (`right:9,top:9` à la source). */
private val ALERT_INSET = 9.dp

/** Hauteur minimale d'une carte (`minHeight:92` à la source). */
private val CARD_MIN_HEIGHT = 92.dp

/**
 * Opacité du rond d'icône — `item.color+'12'` à la source, soit 18/255.
 *
 * La même valeur que le lavis d'or du Quiz, et pour la même raison : le dépôt d'origine exprime
 * ses fonds teintés par un suffixe hexadécimal d'alpha, que Compose n'a pas.
 */
private const val MEDALLION_WASH = 0.07f

/**
 * Les deux cartes de quiz, côte à côte.
 *
 * Elles sont **toujours** composées, comme dans l'original où rien ne les conditionne. Ce sont
 * leurs libellés qui disent l'état — question répondue, disponible, ou rien de publié —, et non
 * leur présence.
 *
 * @param cards textes et pastille, déjà résolus par le renderer.
 * @param onQuiz ouverture de l'écran Quiz.
 * @param onFriends bascule vers l'onglet « Amis », pour y défier quelqu'un.
 */
@Composable
internal fun QuizHomeCards(
    cards: QuizCards,
    onQuiz: () -> Unit,
    onFriends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuizCard(
            title = cards.quizTitle,
            sub = cards.quizSub,
            detail = cards.quizDetail,
            icon = Icons.Outlined.EmojiEvents,
            accent = AppTheme.colors.gold,
            alert = cards.quizAlert,
            onClick = onQuiz,
            modifier = Modifier.weight(1f),
        )
        QuizCard(
            title = cards.friendsTitle,
            sub = cards.friendsSub,
            detail = cards.friendsDetail,
            icon = Icons.Outlined.Groups,
            accent = AppTheme.colors.review,
            // La pastille n'existe que sur la carte du Quiz : la source écrit la condition dans la
            // carte, et l'amitié n'a pas d'état « non lu » à signaler.
            alert = false,
            onClick = onFriends,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Une carte : rond d'icône, trois lignes de texte, et une pastille facultative.
 *
 * **Le titre est de la couleur de l'accent**, comme dans l'original — or pour la carte Quiz, vert
 * pour celle des amis. C'est ce qui les distingue au premier regard, et non un simple libellé.
 *
 * **Le contenu est fusionné pour le lecteur d'écran** : l'original pose `accessibilityLabel` sur
 * le bouton entier, ce qui remplace les trois textes par le seul titre. Sans la fusion, le lecteur
 * annoncerait « Quiz, Question du jour disponible, Teste tes connaissances, bouton » — trois
 * phrases pour une carte.
 */
@Composable
private fun QuizCard(
    title: String,
    sub: String,
    detail: String,
    icon: ImageVector,
    accent: Color,
    alert: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(CARD_RADIUS)

    Box(
        modifier = modifier
            .heightIn(min = CARD_MIN_HEIGHT)
            .clip(shape)
            .background(colors.paper)
            .border(1.dp, colors.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = title }
            .padding(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(MEDALLION)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = MEDALLION_WASH)),
                contentAlignment = Alignment.Center,
            ) {
                AppInlineIcon(icon = icon, tint = accent, size = ICON_SIZE)
            }

            Column(modifier = Modifier.weight(1f)) {
                AppLabel(
                    text = title,
                    selectable = false,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                AppLabel(
                    text = sub,
                    selectable = false,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                AppLabel(
                    text = detail,
                    selectable = false,
                    fontSize = 10.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (alert) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ALERT_INSET, end = ALERT_INSET)
                    .size(ALERT_DOT)
                    .clip(CircleShape)
                    .background(colors.red),
            )
        }
    }
}
