package com.msoumaya.deepseekandroid.core.design.component

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Zones tactiles
// ---------------------------------------------------------------------------
// Le cahier des charges est explicite : un petit pictogramme ne doit pas offrir une petite zone
// tactile. Android fixe la cible minimale confortable à 48 px ; ce modificateur la pose sans
// changer la taille visible de l'icône, puisque `sizeIn` agit sur les contraintes et non sur le
// dessin.
// ---------------------------------------------------------------------------

/** Cible tactile minimale recommandée par Android. */
val MinimumTouchTarget = 48.dp

/**
 * Garantit une zone tactile d'au moins 48 px autour du contenu.
 *
 * À poser sur le conteneur cliquable, pas sur l'icône : c'est le conteneur qui reçoit le geste.
 */
fun Modifier.minimumTouchTarget(): Modifier =
    this.sizeIn(minWidth = MinimumTouchTarget, minHeight = MinimumTouchTarget)
