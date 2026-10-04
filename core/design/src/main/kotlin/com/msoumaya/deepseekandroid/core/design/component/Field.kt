package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.msoumaya.deepseekandroid.core.design.theme.AppTheme

// ---------------------------------------------------------------------------
// Champ de saisie
// ---------------------------------------------------------------------------
// Portage de `Field`.
//
// Le dépôt d'origine posait un `TextInput` React Native avec une bordure de 1 px, un rayon de
// 14 et un remplissage 14/12, sans état de focus visible. Android apporte mieux, et le cahier
// des charges demande de l'adapter plutôt que de le copier : on utilise donc un
// `OutlinedTextField` Material, qui ajoute l'état de focus coloré, le curseur, les poignées de
// sélection et de copier-coller, et les actions de clavier.
//
// La géométrie reste proche — bordure fine, rayon 14, fond `paper` — mais le champ Material est
// un peu plus haut que le champ React Native, qui n'avait pas de hauteur minimale imposée.
// ---------------------------------------------------------------------------

/**
 * Champ de saisie.
 *
 * @param maxLength limite de caractères, appliquée en refusant la frappe au-delà (et non en
 *   tronquant après coup, ce qui ferait sauter le curseur).
 * @param multiline champ de texte long ; la hauteur minimale de 100 px du composant d'origine
 *   est conservée via `minLines`.
 */
@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secure: Boolean = false,
    multiline: Boolean = false,
    maxLines: Int = if (multiline) 6 else 1,
    maxLength: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(AppTheme.radius.small)

    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (maxLength == null || candidate.length <= maxLength) {
                onValueChange(candidate)
            }
        },
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        enabled = enabled,
        placeholder = {
            Text(
                text = placeholder,
                style = TextStyle(
                    color = colors.muted,
                    fontSize = AppTheme.typeScale.body,
                    fontFamily = AppTheme.fonts.interfaceFamily,
                ),
            )
        },
        textStyle = TextStyle(
            color = colors.text,
            fontSize = AppTheme.typeScale.body,
            fontFamily = AppTheme.fonts.interfaceFamily,
        ),
        singleLine = !multiline,
        minLines = if (multiline) 4 else 1,
        maxLines = maxLines,
        shape = shape,
        keyboardOptions = keyboardOptions,
        visualTransformation = if (secure) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.green,
            unfocusedBorderColor = colors.line,
            disabledBorderColor = colors.line,
            focusedContainerColor = colors.paper,
            unfocusedContainerColor = colors.paper,
            disabledContainerColor = colors.soft,
            cursorColor = colors.green,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            disabledTextColor = colors.muted,
            focusedPlaceholderColor = colors.muted,
            unfocusedPlaceholderColor = colors.muted,
        ),
    )
}
