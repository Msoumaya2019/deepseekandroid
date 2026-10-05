package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Système de design
// ---------------------------------------------------------------------------
// Portage de `src/ui/DesignSystem.tsx`, limité aux composants dont l'accueil a besoin.
//
// Deux composants de ce fichier d'origine en sont sortis quand un écran les a demandés :
// `SegmentedControl` vers `Segmented.kt`, `QuranNumberMedallion` vers `Medallion.kt`. Les deux
// derniers, `ThemeSelector` et `AccentSelector`, attendent encore leur appelant — les écrire
// maintenant serait écrire du code sans usage.
//
// Une différence assumée : React Native positionnait la pastille et le chevron d'une carte de
// tâche en `position: absolute` avec des décalages en dur. Ici la carte est une `Box` dont le
// contenu est indenté et dont les deux repères sont alignés sur les coins. Le résultat visuel
// est le même, et la carte ne dépend plus de décalages qui ne veulent rien dire.
// ---------------------------------------------------------------------------

/**
 * Titre de section ou de carte.
 *
 * Remplace `Heading` : police de titre, graisse SemiBold, interligne **1,2 fois** la taille —
 * valeur explicite du dépôt d'origine, reprise telle quelle.
 *
 * @param lineHeight interligne explicite. `null` garde la règle du composant, soit 1,2 fois la
 *   taille. Le seul appelant qui s'en écarte est la bande d'en-tête, dont le titre porte 35 px
 *   pour un corps de 30 : la source y pose une valeur que le ratio ne redonne pas.
 */
@Composable
fun AppHeading(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = AppTheme.typeScale.section,
    color: Color = AppTheme.colors.green,
    lineHeight: TextUnit? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            lineHeight = lineHeight ?: (size.value * 1.2f).sp,
            fontFamily = AppTheme.fonts.titleFamily,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

/**
 * En-tête de section : un titre à gauche, une action facultative à droite.
 *
 * Remplace `SectionHeader`. L'action est une zone tactile de 44 px de haut, comme dans le
 * dépôt d'origine, même si son libellé ne fait que 12 px.
 */
@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppHeading(text = title)
        if (action != null && onAction != null) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable(role = Role.Button, onClick = onAction),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLabel(
                    text = action,
                    selectable = false,
                    fontSize = AppTheme.typeScale.secondary,
                    color = AppTheme.colors.green,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = AppTheme.colors.green,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/**
 * Bouton à icône seule, de 44 × 44.
 *
 * Remplace `IconButton`. Le libellé n'est pas affiché : il sert de description pour TalkBack,
 * ce que le dépôt d'origine faisait déjà avec `accessibilityLabel`.
 *
 * @param background fond du bouton, transparent par défaut. Il sert à marquer une action
 *   **active** : le client d'origine pose `colors.soft` derrière l'action sélectionnée de sa
 *   barre flottante, sans changer la teinte de l'icône. Transparent, il ne change rien aux
 *   appelants qui ne marquent aucun état.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AppTheme.colors.green,
    size: Dp = 44.dp,
    // Fond **facultatif** : transparent par defaut, donc les appelants qui ne marquent aucun
    // etat gardent exactement le rendu d'avant. Il sert a signaler une action **active**.
    background: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else AppTheme.colors.mutedLight,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Carte de tâche du jour : apprentissage ou révision.
 *
 * Remplace `DailyTaskCard`. La pastille de gauche change de couleur et d'icône selon qu'il
 * s'agit d'une révision — `reviewSoft` / `review` — ou d'un apprentissage — `selected` /
 * `green`. Ce n'est pas une décoration : c'est le seul repère qui distingue les deux tâches
 * quand on les survole du regard.
 */
@Composable
fun AppDailyTaskCard(
    title: String,
    passage: String,
    details: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    revision: Boolean = false,
) {
    val colors = AppTheme.colors
    val accent = if (revision) colors.review else colors.green
    val pastille = if (revision) colors.reviewSoft else colors.selected

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 100.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.paper)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onPress)
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 36.dp, end = 16.dp),
        ) {
            AppHeading(text = title, size = 14.sp, color = accent)
            AppLabel(
                text = passage,
                selectable = false,
                maxLines = 2,
                fontSize = AppTheme.typeScale.secondary,
                style = TextStyle(lineHeight = 18.sp),
                modifier = Modifier.padding(top = 5.dp),
            )
            AppLabel(
                text = details,
                selectable = false,
                fontSize = AppTheme.typeScale.metadata,
                color = colors.muted,
                modifier = Modifier.padding(top = 5.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(30.dp)
                .clip(CircleShape)
                .background(pastille),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (revision) Icons.Outlined.Autorenew else Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(16.dp),
        )
    }
}

/**
 * Carte de statistique : une icône, un libellé, une valeur.
 *
 * Remplace `StatCard`. La valeur réduit de 24 à 17 px au-delà de dix caractères, comme dans le
 * dépôt d'origine, sinon « 12 jours d'affilée » déborderait de sa carte.
 */
@Composable
fun AppStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors

    AppCard(modifier = modifier, spaced = false) {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(35.dp)
                    .clip(CircleShape)
                    .background(colors.selected),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.green,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                AppLabel(
                    text = title,
                    selectable = false,
                    fontSize = AppTheme.typeScale.secondary,
                    color = colors.muted,
                )
                AppHeading(
                    text = value,
                    size = if (value.length > 10) 17.sp else 24.sp,
                )
            }
        }
    }
}

/** Icône seule, sans zone tactile : pour un repère décoratif dans une ligne de texte. */
@Composable
fun AppInlineIcon(icon: ImageVector, tint: Color, size: Dp = 20.dp) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

/** Alignement RTL explicite, requis par le texte coranique. */
@Composable
fun RtlText(text: String, modifier: Modifier = Modifier, color: Color = AppTheme.colors.text) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontFamily = AppTheme.fonts.arabicFamily,
            textAlign = TextAlign.End,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
