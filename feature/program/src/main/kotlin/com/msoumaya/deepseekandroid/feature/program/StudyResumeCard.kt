package com.msoumaya.deepseekandroid.feature.program

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.component.AppButton
import com.msoumaya.deepseekandroid.core.design.component.AppCard
import com.msoumaya.deepseekandroid.core.design.component.AppLabel
import com.msoumaya.deepseekandroid.core.design.component.ProgressTrack
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Carte de reprise
// ---------------------------------------------------------------------------
// Portage de `StudyResumeCard` (`src/ui/StudySession.tsx:29`).
//
// Elle n'est pas décorative : c'est le seul endroit qui dise **où l'on s'est arrêté**, et le
// seul bouton qui rouvre la tâche sur son reste au lieu de son début. Un défaut d'un verset sur
// ce qu'elle propose ferait relire ou sauter un verset sans que rien ne le dise.
//
// Trois mesures relevées à la source, qui ne se devinent pas :
//
//   1. **Les couleurs de la validation sont des littéraux.** `#e8f3e6`, `#356c3d`, `#507454` et
//      `#57965c` ne figurent dans aucune palette du dépôt d'origine — elles sont écrites en
//      clair dans le composant. Les remplacer par `review` / `reviewSoft` de la palette
//      changerait la teinte, donc le rendu ; elles sont donc conservées telles quelles et
//      nommées ici.
//
//   2. **Les deux boutons font la même chose.** « Reprendre » et « Voir dans le Coran »
//      appellent tous les deux `onResume`, c'est-à-dire l'ouverture du lecteur sur le **reste**
//      de la tâche. Ce n'est pas une erreur de portage : c'est le comportement de l'original, et
//      le corriger supposerait de décider ce que « voir » veut dire quand « reprendre » existe.
//
//   3. **`Contrast` est l'équivalent exact de `circle-half-full`.** L'icône d'une ligne entamée
//      est un cercle dont une moitié est pleine : ni `Adjust` (un point au centre), ni
//      `Timelapse` (un quart). Vérifié dans l'artefact livré, et non supposé.
//
// **Ce qui n'est pas ici.** Le dépliage du détail est un état d'interface : il est tenu par
// `rememberSaveable`, donc il survit à une rotation, et il ne remonte pas dans l'état affichable
// parce qu'il ne change rien à ce que l'application croit appris.
// ---------------------------------------------------------------------------

/** Fond du bloc de validation. Littéral du dépôt d'origine. */
private val VALIDATION_BACKGROUND = Color(0xFFE8F3E6)

/** Texte de la validation. Littéral du dépôt d'origine. */
private val VALIDATION_TEXT = Color(0xFF356C3D)

/** Précision sous la validation. Littéral du dépôt d'origine. */
private val VALIDATION_DETAIL = Color(0xFF507454)

/** Coche d'une ligne faite. Littéral du dépôt d'origine. */
private val ROW_DONE_TINT = Color(0xFF57965C)

/** Combien de lignes sont montrées avant « Voir tout ». */
private const val COLLAPSED_ROWS = 12

/**
 * Une tâche interrompue, proposée à la reprise.
 *
 * @param line tout ce qui s'affiche, déjà résolu par `ProgramRenderer`.
 * @param onResume rouvre la tâche sur son reste. Les deux boutons de la carte l'appellent.
 */
@Composable
internal fun StudyResumeCard(
    line: ResumeLine,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var expanded by rememberSaveable(line.id) { mutableStateOf(false) }

    AppCard(modifier = modifier) {
        AppLabel(
            text = line.title,
            selectable = false,
            fontSize = 22.sp,
            fontFamily = AppTheme.fonts.titleFamily,
            fontWeight = FontWeight.SemiBold,
            color = colors.green,
        )
        AppLabel(
            text = "Poursuivez là où vous vous êtes arrêté",
            selectable = false,
            fontSize = 13.sp,
            color = colors.muted,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.soft)
                .padding(14.dp),
        ) {
            AppLabel(
                text = line.where,
                selectable = false,
                fontSize = 18.sp,
                fontFamily = AppTheme.fonts.titleFamily,
                fontWeight = FontWeight.SemiBold,
            )
            AppLabel(
                text = line.remaining,
                selectable = false,
                color = colors.green,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        AppLabel(
            text = line.progress,
            selectable = false,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        ProgressTrack(value = line.ratio)
        AppButton(
            // Le mode se lit sur la **requête**, et non sur la ligne : c'est elle qui sait si
            // elle porte une séance d'apprentissage ou une tâche de révision, et `learning` est
            // exactement ce discriminant. Le relire sur un autre champ ferait deux lectures de la
            // même vérité, qui finiraient par diverger.
            text = "Reprendre " + if (line.study.learning) "mon apprentissage" else "ma révision",
            onClick = onResume,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(VALIDATION_BACKGROUND)
                .padding(12.dp),
        ) {
            AppLabel(
                text = line.done,
                selectable = false,
                fontWeight = FontWeight.Bold,
                color = VALIDATION_TEXT,
            )
            AppLabel(
                text = "Validation effectuée",
                selectable = false,
                fontSize = 12.sp,
                color = VALIDATION_DETAIL,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLabel(
                text = "Détail de la séance",
                selectable = false,
                fontSize = 18.sp,
                fontFamily = AppTheme.fonts.titleFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AppButton(text = "Voir dans le Coran", onClick = onResume, secondary = true, small = true)
        }

        AppLabel(
            text = line.total,
            selectable = false,
            fontSize = 12.sp,
            color = colors.muted,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        // Les douze premières lignes tant que le détail est replié : une reprise d'un juz’
        // entier en compterait des dizaines, et la carte noierait le reste de l'écran.
        val visibles = if (expanded) line.rows else line.rows.take(COLLAPSED_ROWS)
        visibles.forEach { row -> ResumeRowLine(row) }

        if (line.rows.size > COLLAPSED_ROWS) {
            AppButton(
                text = if (expanded) "Réduire" else "Voir tout",
                onClick = { expanded = !expanded },
                secondary = true,
                small = true,
            )
        }
    }
}

/**
 * Une ligne du détail.
 *
 * L'icône porte l'état : cercle coché pour une ligne faite, cercle à moitié plein pour une ligne
 * entamée, cercle vide sinon. Le mot à droite la précise — mais c'est l'icône qui se lit d'un
 * coup d'œil sur une liste de trente lignes.
 */
@Composable
private fun ResumeRowLine(row: ResumeRow, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val done = row.state == RowState.DONE

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .bottomDivider(colors.line),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = when (row.state) {
                RowState.DONE -> Icons.Outlined.CheckCircle
                RowState.PARTIAL -> Icons.Outlined.Contrast
                RowState.TODO -> Icons.Outlined.CheckCircleOutline
            },
            contentDescription = null,
            tint = if (done) ROW_DONE_TINT else colors.green2,
            modifier = Modifier.size(22.dp),
        )
        AppLabel(
            text = row.label,
            selectable = false,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        AppLabel(
            text = row.status,
            selectable = false,
            fontSize = 11.sp,
            color = if (done) VALIDATION_TEXT else colors.green,
        )
    }
}

/** Trait horizontal de 1 px sur le bord bas, à l'intérieur du composant. */
private fun Modifier.bottomDivider(color: Color, thickness: Dp = 1.dp): Modifier =
    drawBehind {
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - thickness.toPx()),
            size = Size(size.width, thickness.toPx()),
        )
    }
