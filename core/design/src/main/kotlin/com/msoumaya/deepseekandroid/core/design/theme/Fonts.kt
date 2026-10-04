// L'axe de variation d'une police est encore marqué expérimental côté Compose
// (`androidx.compose.ui.text.ExperimentalTextApi`, vérifié dans l'artefact compilé).
// L'opt-in est posé au niveau du fichier parce que ce fichier ne fait qu'une chose :
// construire des polices. Il n'y a rien d'autre à protéger derrière.
@file:OptIn(ExperimentalTextApi::class)

package com.msoumaya.deepseekandroid.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.msoumaya.deepseekandroid.core.design.R
import com.msoumaya.deepseekandroid.core.model.UiFont

// ---------------------------------------------------------------------------
// Polices
// ---------------------------------------------------------------------------
// Portage de `src/theme/fonts.ts`. Le dépôt d'origine chargeait trois fichiers via les paquets
// `@expo-google-fonts/*` :
//
//   CormorantGaramond_600SemiBold.ttf -> titres
//   CormorantGaramond_400Regular.ttf  -> interface, uniquement pour la police « classique »
//   Amiri_400Regular.ttf              -> texte coranique
//
// Les deux fichiers de Cormorant ont été remplacés par **une seule police variable**, sous
// licence OFL, dont l'axe `wght` va de 300 à 700. Deux conséquences à ne pas oublier :
//
//  1. l'instance par défaut de cette police est **300 (Light)**, pas 400. Utiliser
//     `Font(R.font.cormorant_garamond)` sans préciser l'axe afficherait du Light, ce qui ne
//     ressemblerait pas au Regular du dépôt d'origine : chaque graisse est donc demandée
//     explicitement via `FontVariation.weight(...)` ;
//  2. la police contient de vraies graisses 400, 600 et 700. Là où React Native fabriquait un
//     gras synthétique à partir du Regular, Android dispose du dessin prévu par la fonderie.
//     Le rendu est plus fin, l'intention est identique.
//
// Vérification faite sur l'artefact compilé : la table `fvar` (300..700) est bien présente dans
// l'AAR, donc la compilation des ressources n'a pas aplati la police.
// ---------------------------------------------------------------------------

/**
 * Les trois rôles typographiques de l'application.
 *
 * Un `null` signifie « police du système » : c'est le comportement voulu pour l'interface en
 * mode `ELEGANT` (défaut) et pour tous les rôles en mode `SYSTEM`.
 */
@Immutable
data class AppFonts(
    val interfaceFamily: FontFamily?,
    val titleFamily: FontFamily?,
    val arabicFamily: FontFamily,
)

private val CormorantRegular = Font(
    resId = R.font.cormorant_garamond,
    weight = FontWeight.Normal,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(400)),
)

private val CormorantSemiBold = Font(
    resId = R.font.cormorant_garamond,
    weight = FontWeight.SemiBold,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(600)),
)

private val CormorantBold = Font(
    resId = R.font.cormorant_garamond,
    weight = FontWeight.Bold,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(700)),
)

private val AmiriRegular = Font(
    resId = R.font.amiri_regular,
    weight = FontWeight.Normal,
    style = FontStyle.Normal,
)

/** Cormorant Garamond, graisses 400 / 600 / 700. */
val CormorantFamily: FontFamily = FontFamily(CormorantRegular, CormorantSemiBold, CormorantBold)

/** Amiri, réservé au texte coranique (composé avec des signes diacritiques arabes). */
val AmiriFamily: FontFamily = FontFamily(AmiriRegular)

/**
 * Choisit les familles de polices pour un réglage utilisateur donné.
 *
 * Reproduit les trois fonctions du dépôt d'origine :
 *  - interface : Cormorant **uniquement** en mode `CLASSIC`, sinon la police du système ;
 *  - titres : Cormorant sauf en mode `SYSTEM` ;
 *  - coranique : toujours Amiri.
 */
fun resolveFonts(uiFont: UiFont): AppFonts = AppFonts(
    interfaceFamily = if (uiFont == UiFont.CLASSIC) CormorantFamily else null,
    titleFamily = if (uiFont == UiFont.SYSTEM) null else CormorantFamily,
    arabicFamily = AmiriFamily,
)
