package ai.storyteller.lightcraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A point of a grading wheel, [dx] right and [dy] down from its centre, as a hue (degrees, 0 at the
 * right, counter-clockwise, as the desktop's wheels) and a saturation (0–100, the rim is 100).
 */
fun wheelValue(dx: Double, dy: Double, radius: Double): Pair<Double, Double> {
    val hue = (Math.toDegrees(atan2(-dy, dx)) + 360.0) % 360.0
    val sat = if (radius <= 0.0) 0.0 else min(hypot(dx, dy) / radius * 100.0, 100.0)
    return hue.roundToInt().toDouble() % 360.0 to sat.roundToInt().toDouble()
}

/** The hues around the wheel, from the right going clockwise on screen (hue 360 − angle). */
private val wheelColors: List<Color> = (0..12).map { i -> Color.hsv(((360 - i * 30) % 360).toFloat(), 0.75f, 0.8f) }

/**
 * Colour grading's wheel for [range]: the hue around it, the saturation out from its grey middle.
 * Touch or drag anywhere on it to move its point (one undo step until the finger lifts).
 */
@Composable
fun GradingWheel(vm: AppViewModel, range: String, hue: Double, sat: Double, diameter: Dp = 150.dp) {
    Canvas(
        Modifier
            .size(diameter)
            .semantics { contentDescription = "$range wheel" }
            .pointerInput(range) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val r = size.width / 2f
                    val c = Offset(r, size.height / 2f)
                    if ((down.position - c).getDistance() > r + 12.dp.toPx()) return@awaitEachGesture
                    // the list under it doesn't scroll while a finger is on the wheel
                    down.consume()
                    fun set(p: Offset) {
                        val (h, s) = wheelValue((p.x - c.x).toDouble(), (p.y - c.y).toDouble(), r.toDouble())
                        vm.dragWheel(range, h, s)
                    }
                    vm.beginWheel(range)
                    set(down.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        set(change.position)
                    }
                    vm.endWheel()
                }
            },
    ) {
        val r = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(wheelColors, center), r, center)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF808080), Color(0x00808080)), center, r), r, center)
        drawCircle(Color(0xFF282828), r, center, style = Stroke(1.dp.toPx()))
        val a = Math.toRadians(hue)
        val p = center + Offset(cos(a).toFloat(), -sin(a).toFloat()) * (r * (sat / 100.0).toFloat().coerceIn(0f, 1f))
        drawCircle(Color.White, 6.dp.toPx(), p)
        drawCircle(Color.Black, 6.dp.toPx(), p, style = Stroke(1.dp.toPx()))
    }
}
