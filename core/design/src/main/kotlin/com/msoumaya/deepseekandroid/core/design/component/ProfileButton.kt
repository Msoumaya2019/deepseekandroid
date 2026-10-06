package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme
import java.util.Locale

// ---------------------------------------------------------------------------
// Bouton de profil de la barre supérieure
// ---------------------------------------------------------------------------
// Portage de `src/ui/ProfileHeaderButton.tsx`.
//
// Le dépôt d'origine dessinait la silhouette sans nom avec deux rectangles empilés — une tête et
// un buste. C'est repris tel quel : une icône de bibliothèque aurait été plus courte à écrire
// mais aurait changé le dessin, et ce bouton est le seul repère de profil de l'application.
//
// Différence assumée : React Native écrivait `toLocaleUpperCase('fr-FR')` sur le premier
// caractère **pris en point de code** (`Array.from`), pas en unité UTF-16. La distinction compte
// pour les prénoms hors plan multilingue de base ; elle est conservée ici.
// ---------------------------------------------------------------------------

/**
 * Pastille de profil : initiale du prénom dans un cercle vert bordé d'or, ou silhouette si le
 * prénom n'est pas connu.
 *
 * @param firstName prénom servant à l'initiale. `null` ou vide affiche la silhouette.
 */
@Composable
fun AppProfileButton(
    firstName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val initiale = initialeDe(firstName)

    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.green)
                .border(1.dp, colors.gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (initiale != null) {
                Text(
                    text = initiale,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            } else {
                // Silhouette : tête puis buste, tous deux blancs.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 17.dp, height = 9.dp)
                            .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp))
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

/**
 * Première lettre du prénom, en capitale.
 *
 * Le prénom est d'abord rogné, puis lu par **point de code** : c'est ce que faisait
 * `Array.from(...)[0]` côté React Native. `firstOrNull()` aurait rendu une unité UTF-16 et
 * affiché un demi-caractère pour un prénom commençant par un emoji ou une lettre hors BMP.
 *
 * @return la lettre en capitale, ou `null` si le prénom est absent ou blanc.
 *
 * **Publique, et non `internal`.** Le médaillon d'un ami n'existe pas que dans l'en-tête : l'écran
 * du Quiz affiche le même — un rond, une initiale, rien d'autre — pour l'adversaire d'un défi et
 * pour l'ami à défier. `feature:social` en a écrit un second de son côté, qui lit une seule unité
 * UTF-16 là où celle-ci lit un point de code ; le jour où il adoptera celle-ci, les deux médaillons
 * cesseront de diverger sur un prénom qui commence hors du plan de base.
 */
fun initialeDe(firstName: String?): String? {
    val rogne = firstName?.trim().orEmpty()
    if (rogne.isEmpty()) return null
    val point = rogne.codePointAt(0)
    return String(Character.toChars(point)).uppercase(Locale.FRANCE)
}
