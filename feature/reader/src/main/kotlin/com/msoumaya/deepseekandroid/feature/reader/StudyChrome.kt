package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.minimumTouchTarget
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import com.msoumaya.deepseekandroid.core.domain.StudySession
import com.msoumaya.deepseekandroid.core.model.Range

/**
 * Le bandeau de séance : ce que le lecteur annonce qu'il est en train de faire.
 *
 * ## Pourquoi il est posé **au-dessus** de la page, et non par-dessus
 *
 * Il prend sa hauteur, comme la coquille du bas : la page reste **entière**. Une bande flottante
 * masquerait le premier verset, et c'est précisément le verset qu'on vient de commencer à
 * apprendre — celui qu'on relit le plus.
 *
 * ## Ce qu'il ne calcule pas
 *
 * Tous ses textes arrivent **résolus**, dans un [StudySession.Banner]. Le rendu ne compte rien,
 * ne décide rien, ne compare rien : c'est ce qui permet d'éprouver « Consolidation · J+3 » ou
 * « 12 / 30 versets » dans `core:domain`, sans appareil. Un libellé faux ne se verrait pas à
 * l'œil — il faut connaître la séance pour le reconnaître — et il décide pourtant de ce que la
 * personne croit valider.
 *
 * ## La ligne de progression est **absente** pour une consolidation, et c'est voulu
 *
 * Une étape de consolidation porte sur une poignée de versets. Y afficher « 0 / 3 » la ferait
 * ressembler à une séance qu'on n'a pas commencée, alors qu'une consolidation se valide d'un
 * geste. La règle vient du domaine — `progress` vaut alors `null` — et le rendu se contente de
 * ne pas la peindre.
 *
 * @param banner les textes déjà résolus par le domaine.
 * @param onPress ouvre la validation. Jamais `null` : un bandeau qui s'annonce sans rien
 *   permettre serait un bouton mort, et c'est exactement ce que ce lecteur refuse ailleurs.
 */
@Composable
internal fun StudyBanner(
    banner: StudySession.Banner,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .minimumTouchTarget()
                .alpha(if (pressed) PRESSED_ALPHA else 1f)
                .clip(RoundedCornerShape(STUDY_BANNER_RADIUS))
                .background(colors.reviewSoft)
                .border(1.dp, STUDY_BANNER_BORDER, RoundedCornerShape(STUDY_BANNER_RADIUS))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = banner.actionLabel,
                    onClick = onPress,
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = colors.review,
            )

            Column(modifier = Modifier.weight(1f)) {
                AppLabel(
                    text = banner.title,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.review,
                    fontSize = AppTheme.typeScale.secondary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    selectable = false,
                )
                banner.progress?.let { ligne ->
                    AppLabel(
                        text = ligne,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        color = colors.review,
                        fontSize = AppTheme.typeScale.metadata,
                        maxLines = 1,
                        selectable = false,
                    )
                }
            }

            // Le trait de séparation et le bloc de droite, mesurés sur la hauteur du contenu :
            // `IntrinsicSize.Min` est ce qui permet au trait de couvrir exactement le bloc, sans
            // hauteur écrite en dur — une hauteur fixe mentirait dès que la police change.
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(STUDY_BANNER_DIVIDER),
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    AppLabel(
                        text = banner.pageLabel,
                        color = colors.review,
                        fontSize = AppTheme.typeScale.metadata,
                        maxLines = 1,
                        selectable = false,
                    )
                    AppLabel(
                        text = banner.pageText,
                        color = colors.review,
                        fontSize = AppTheme.typeScale.secondary,
                        maxLines = 1,
                        selectable = false,
                    )
                }
            }
        }
    }
}

/**
 * Ce que la coquille d'étude a besoin de savoir de la séance en cours.
 *
 * Un seul objet plutôt que cinq paramètres, et c'est délibéré : les cinq valeurs
 * viennent **ensemble** de la même requête, et les faire voyager séparément laisserait
 * croire qu'on peut n'en passer que quatre. Le jour où l'une manquerait, la feuille
 * calculerait un point d'arrêt sur la mauvaise plage — un défaut silencieux, puisqu'il
 * produirait un verset plausible.
 *
 * @param banner les textes du bandeau, résolus par le domaine.
 * @param learning vrai pour un apprentissage : décide des mots de la feuille, et rien
 *   d'autre.
 * @param range la plage **prévue** — celle que le bandeau compte, et non celle qui a
 *   été demandée. Voir `StudySession.plannedRange`.
 * @param through le dernier verset déjà validé.
 * @param source la source de lecture, **déjà repliée** par
 *   `StudyProgressCalculator.sourceKey` : c'est sous cette forme que les règles
 *   d'étude cherchent leurs tables de pages.
 *
 * ## Pourquoi ce type est **public**, et non interne
 *
 * Le lecteur est appelé depuis le module de navigation, qui construit cet objet à partir de la
 * requête de séance. Un type `internal` ne franchit pas cette frontière : le compilateur refuse
 * de l'exposer dans la signature d'une fonction publique, et c'est heureux — sans ce refus, on
 * croirait ce contrat privé alors qu'il est l'interface entre deux modules.
 */
data class StudyChromeState(
    val banner: StudySession.Banner,
    val learning: Boolean,
    val range: Range,
    val through: Int,
    val source: String,
)

/** Le rayon de la pastille. L'original pose 22. */
private val STUDY_BANNER_RADIUS = 22.dp

/**
 * La bordure et le trait de séparation du bandeau.
 *
 * Ce sont les littéraux du client d'origine — `#DDECDC` et `#C6DDC5` — et non des jetons de la
 * palette : ils n'existent que là, et les remonter dans les jetons laisserait croire qu'ils
 * servent ailleurs. Le jour où un second écran en aura besoin, ce sera le moment de les y mettre.
 */
private val STUDY_BANNER_BORDER = Color(0xFFDDECDC)
private val STUDY_BANNER_DIVIDER = Color(0xFFC6DDC5)

// L'opacité à l'appui — `PRESSED_ALPHA` — vient de `SheetChrome`, et n'est pas recopiée : c'est
// le même geste que celui des feuilles du lecteur, et deux constantes pour une même intention
// divergeraient au premier ajustement sans que rien ne le dise.
