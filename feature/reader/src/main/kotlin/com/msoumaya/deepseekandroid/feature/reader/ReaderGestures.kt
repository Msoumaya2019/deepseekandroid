package com.msoumaya.deepseekandroid.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import com.msoumaya.deepseekandroid.core.domain.DragIntent
import com.msoumaya.deepseekandroid.core.domain.ReaderGesture
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

/**
 * Le geste unique du lecteur.
 *
 * **Un seul** gestionnaire de pointeurs, et non trois superposés. C'est le point où un lecteur
 * de moushaf se casse : `detectTransformGestures`, `detectTapGestures` et
 * `detectHorizontalDragGestures` posés côte à côte se disputent les mêmes événements, et
 * Compose donne la main au premier qui consomme. Il en résulte des gestes qui marchent « sauf
 * quand » — un balayage qui zoome, un appui long qui tourne la page.
 *
 * Ici, chaque geste est décidé au même endroit :
 *
 * | Ce que fait le doigt | Ce qui se passe |
 * |---|---|
 * | deux doigts | zoom et déplacement, jamais de changement de page |
 * | un doigt, page **agrandie** | la page se déplace sous le doigt, elle ne tourne pas |
 * | un doigt, page **entière**, mouvement court | appui : la coquille s'affiche ou disparaît |
 * | un doigt, page **entière**, appui long | sélection du verset sous le doigt |
 * | un doigt, page **entière**, glissement franc | page suivante ou précédente |
 *
 * Un pincement **neutralise le reste du geste** jusqu'au lever : après avoir zoomé, le doigt
 * qui reste ne doit ni faire défiler la page ni la tourner. C'est le défaut le plus fréquent
 * des lecteurs tactiles, et il est fermé ici par `pinched`.
 *
 * La **décision** n'est pas prise ici : `ReaderGesture.dragIntent` dit l'intention et
 * `PageNavigation` dit le numéro. Ce fichier ne fait que traduire des événements en
 * intentions — c'est ce qui rend les deux règles éprouvables sans écran.
 */
internal object ReaderGestures {

    /** Durée d'un appui long, identique au client d'origine. */
    const val LONG_PRESS_MILLIS = 450L

    /**
     * Tolérance de mouvement pour qu'un appui reste un appui.
     * Au-delà, le doigt a glissé : c'est un balayage, pas une pression.
     */
    const val TAP_SLOP = 10f
}

/**
 * Installe le geste.
 *
 * @param zoomed échelle courante, relue à chaque événement : elle change pendant le geste.
 * @param onTransform pincement : centroid, déplacement et facteur d'échelle.
 * @param onPan déplacement d'une page agrandie.
 * @param onTap appui court, à la position du doigt.
 * @param onLongPress appui long, à la position du doigt.
 * @param onSwipe glissement franc : déplacements cumulés, à passer à `PageNavigation`.
 */
internal suspend fun PointerInputScope.readerGesture(
    zoomed: () -> Boolean,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onPan: (Offset) -> Unit,
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    onSwipe: (dx: Float, dy: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var last = down.position
        var dx = 0f
        var dy = 0f
        var travelled = 0f
        var pinched = false
        var panned = false
        var longPressed = false
        var elapsed = 0L
        var released = false

        while (!released) {
            // L'appui long n'arrive pas avec un événement : il arrive parce que **rien**
            // n'arrive. Il faut donc une attente bornée — c'est la seule raison d'être du
            // `withTimeoutOrNull`, un doigt immobile ne produisant aucun événement.
            // Compose emploie exactement ce procédé dans `detectTapGestures`.
            val waitingForLongPress =
                !longPressed && !pinched && !panned && travelled < ReaderGestures.TAP_SLOP

            val event = if (waitingForLongPress) {
                val started = System.currentTimeMillis()
                val next = withTimeoutOrNull(ReaderGestures.LONG_PRESS_MILLIS - elapsed) { awaitPointerEvent() }
                elapsed += System.currentTimeMillis() - started
                if (next == null) {
                    longPressed = true
                    onLongPress(last)
                    continue
                }
                next
            } else {
                awaitPointerEvent()
            }

            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                released = true
                continue
            }

            if (pressed.size >= 2) {
                pinched = true
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (zoom != 1f || pan != Offset.Zero) {
                    onTransform(event.calculateCentroid(useCurrent = false), pan, zoom)
                }
                event.changes.forEach { it.consume() }
                last = pressed.first().position
                continue
            }

            val change = pressed.first()
            val delta = change.positionChange()
            last = change.position

            when {
                // Après un pincement, le doigt restant est neutralisé jusqu'au lever.
                pinched -> change.consume()

                zoomed() -> {
                    panned = true
                    onPan(delta)
                    change.consume()
                }

                else -> {
                    dx += delta.x
                    dy += delta.y
                    travelled = hypot(dx, dy)
                }
            }
        }

        if (pinched || panned || longPressed) return@awaitEachGesture

        when (ReaderGesture.dragIntent(dx = dx, dy = dy, scale = if (zoomed()) 2f else 1f)) {
            DragIntent.TURN_NEXT, DragIntent.TURN_PREVIOUS -> onSwipe(dx, dy)

            // La page a déjà suivi le doigt : il n'y a plus rien à décider.
            DragIntent.PAN -> Unit

            DragIntent.NONE -> if (travelled < ReaderGestures.TAP_SLOP) onTap(last)
        }
    }
}
