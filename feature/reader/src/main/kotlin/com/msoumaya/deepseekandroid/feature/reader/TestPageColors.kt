package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * La couleur d'un jeton de thème, telle que le document l'attend.
 *
 * ## Pourquoi deux fonctions pour six chiffres
 *
 * Le document valide la forme du fond par `/^#[0-9a-f]{6}$/i` et **retombe sur son propre fond**
 * quand elle ne correspond pas. Une couleur mal écrite ne casse donc rien : elle disparaît en
 * silence, et on la cherche ensuite sur une capture d'écran. La forme est un contrat, et c'est ce
 * qui justifie qu'elle soit écrite une fois — et éprouvée.
 *
 * ## Pourquoi l'analyse est écrite ici plutôt que déléguée à la plateforme
 *
 * `android.graphics.Color.parseColor` ferait le travail, mais elle n'existe pas dans un test
 * JVM : la fonction ne serait alors éprouvable que sur un appareil, c'est-à-dire jamais au
 * moment où l'on écrit. Écrite en Kotlin pur, elle s'éprouve, et elle **impose exactement la
 * forme que le document valide** au lieu de s'en remettre à une bibliothèque qui accepte aussi
 * `#RGB`, les noms de couleurs et l'alpha.
 */
internal object TestPageColors {

    /**
     * La forme acceptée, et la seule : un croisillon et six chiffres hexadécimaux.
     *
     * C'est la même que celle du document. Elle est écrite ici en clair plutôt que recopiée d'un
     * commentaire : une forme recopiée n'est pas une mesure.
     */
    private val FORM = Regex("^#[0-9a-fA-F]{6}$")

    /**
     * [color] écrite `#RRGGBB`.
     *
     * L'alpha est écarté, et non replié sur le blanc : le document ne connaît que six chiffres,
     * et lui donner un fond translucide laisserait voir le blanc de la WebView derrière la page.
     * Les jetons du thème sont tous opaques ; la conversion ne fait donc que rendre explicite ce
     * qui est déjà vrai.
     */
    fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

    /**
     * La couleur d'un texte `#RRGGBB` — le chemin inverse de [hex].
     *
     * Il existe pour la même raison que l'autre : le fond est écrit **une** fois, puis lu par le
     * document et par la vue. Deux écritures séparées finiraient par diverger, et l'écran
     * clignoterait d'un fond à l'autre au moment du chargement.
     *
     * Refuse tout ce qui n'est pas la forme exacte, et le dit. Rendre une couleur de repli en
     * silence ferait passer une faute de frappe pour un choix — et l'appelant ne peut de toute
     * façon lui donner que les couleurs du catalogue `Texts`.
     */
    fun parse(hex: String): Color {
        require(FORM.matches(hex)) { "Couleur illisible : $hex" }
        return Color(0xFF000000L or hex.substring(1).toLong(16))
    }
}
