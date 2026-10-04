package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Texte
// ---------------------------------------------------------------------------
// Portage des composants `Label` et `Title` de `src/ui/theme.tsx`.
//
// Côté React Native, `Label` posait la couleur et la taille puis laissait un objet `style`
// écraser le tout. Compose fusionne les styles dans l'autre sens : on part d'un style de base
// et on applique la surcharge par-dessus, ce qui donne le même résultat mais vérifiable.
// ---------------------------------------------------------------------------

/**
 * Texte courant.
 *
 * Remplace `Label`. Le texte est **sélectionnable** par défaut : sur Android, copier un verset
 * ou une consigne est un geste courant, et l'ancien composant exposait déjà une option
 * `selectable`. Le mettre par défaut évite de l'oublier sur les écrans qui en ont besoin.
 */
@Composable
fun AppLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.text,
    fontSize: TextUnit = AppTheme.typeScale.body,
    fontFamily: FontFamily? = AppTheme.fonts.interfaceFamily,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    selectable: Boolean = true,
    style: TextStyle = TextStyle(),
) {
    // `TextStyle.textAlign` n'accepte pas `null` : l'alignement n'est posé que s'il est demandé,
    // sinon on garde celui du style ambiant au lieu de le forcer à l'alignement par défaut.
    val base = LocalTextStyle.current.merge(
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
        ),
    ).let { styled ->
        if (textAlign != null) styled.copy(textAlign = textAlign) else styled
    }.merge(style)

    if (selectable) {
        SelectionContainer {
            Text(text = text, modifier = modifier, style = base, maxLines = maxLines, overflow = overflow)
        }
    } else {
        Text(text = text, modifier = modifier, style = base, maxLines = maxLines, overflow = overflow)
    }
}

/**
 * Titre d'écran.
 *
 * Remplace `Title` : 30 sp, police de titre, graisse SemiBold, couleur principale du thème.
 * `maxLines` par défaut à 1 avec ellipse, comme un titre d'écran ne doit pas repousser le
 * contenu ; passer `maxLines = Int.MAX_VALUE` pour un titre qui doit respirer sur deux lignes.
 */
@Composable
fun AppTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.green,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = AppTheme.typeScale.screen,
            fontFamily = AppTheme.fonts.titleFamily,
            fontWeight = FontWeight.SemiBold,
        ),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/**
 * Intertitre de section : 21 sp, police de titre.
 *
 * `tokens.ts` prévoit une taille `section` que React Native posait écran par écran. Elle est
 * exposée ici comme composant pour que les écrans n'aient pas à la recopier.
 */
@Composable
fun AppSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.green,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = AppTheme.typeScale.section,
            fontFamily = AppTheme.fonts.titleFamily,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

/**
 * Texte coranique : police Amiri, taille dédiée.
 *
 * L'interligne n'est volontairement pas fixé ici. Amiri porte des signes diacritiques hauts et
 * bas ; sa propre métrique est plus fiable qu'un ratio inventé. Le lecteur du Coran, qui gère
 * aussi l'échelle de zoom, posera son interligne lui-même.
 */
@Composable
fun ArabicText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.text,
    fontSize: TextUnit = AppTheme.typeScale.arabic,
    textAlign: TextAlign = TextAlign.Center,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontFamily = AppTheme.fonts.arabicFamily,
            textAlign = textAlign,
        ),
    )
}
