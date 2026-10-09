package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.core.DevelopControl
import ai.storyteller.lightcraft.core.SliderTrack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Where along the track a value sits. Linear, except the white balance temperature in Kelvin,
 * which is spaced in mireds (1/K): 2000–50000 K on a linear phone-width track would crowd every
 * useful temperature into its first fifth.
 */
private class SliderScale(private val min: Double, private val max: Double, private val mired: Boolean) {
    fun position(v: Double): Float {
        val p = if (mired) (1e6 / min - 1e6 / v.coerceIn(min, max)) / (1e6 / min - 1e6 / max) else (v - min) / (max - min)
        return p.toFloat().coerceIn(0f, 1f)
    }

    fun value(position: Float, step: Double): Double {
        val p = position.toDouble().coerceIn(0.0, 1.0)
        val v = if (mired) 1e6 / (1e6 / min - p * (1e6 / min - 1e6 / max)) else min + p * (max - min)
        val snapped = if (step > 0) min + ((v - min) / step).roundToLong() * step else v
        return snapped.coerceIn(min, max)
    }

    companion object {
        fun of(c: DevelopControl) = SliderScale(c.min, c.max, mired = c.id == "wb.temp" && c.min > 0)
    }
}

/** The value as the desktop panel shows it: its decimals, a sign on two-way sliders, Kelvin whole. */
fun DevelopControl.format(v: Double): String {
    val decimals = if (id == "wb.temp") 0 else this.decimals.toInt()
    val shown = if (abs(v) < 0.5 * 10.0.pow(-decimals)) 0.0 else v
    val s = String.format(Locale.US, "%.${decimals}f", shown)
    return if (shown > 0 && min < 0) "+$s" else s
}

val DevelopControl.isChanged: Boolean get() = abs(value - defaultValue) > max(step / 2, 1e-9)

private val labelStyle = TextStyle(fontFamily = Inter, fontSize = 14.sp)

/**
 * One develop slider, drawn like the desktop's (`crates/ui-egui/src/widgets.rs`): label and value
 * above a thin track, a hollow ring thumb with the track cut away around it. Double-tap the label
 * to reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopSlider(
    control: DevelopControl,
    onChange: (Double) -> Unit,
    onFinished: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = control.label,
    enabled: Boolean = true,
) {
    val scale = remember(control.id, control.min, control.max) { SliderScale.of(control) }
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val text = when {
        !enabled -> Lc.textDisabled
        dragged || control.isChanged -> Lc.text
        else -> Lc.textLabel
    }
    // the reference app's row: 48.6 dp, name and value on a line 2 dp down, the bar centred 34.6 dp down
    Column(modifier.fillMaxWidth().height(48.6.dp).padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp).height(19.5.dp).pointerInput(control.id) { detectTapGestures(onDoubleTap = { if (enabled) onReset() }) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = labelStyle, color = text, modifier = Modifier.weight(1f))
            Text(control.format(control.value), style = labelStyle.copy(fontFeatureSettings = "tnum"), color = text)
        }
        // 27 dp for the bar; the slider's own 48 dp touch target overhangs it, centred on the bar
        Slider(
            value = scale.position(control.value),
            onValueChange = { onChange(scale.value(it, control.step)) },
            onValueChangeFinished = onFinished,
            enabled = enabled,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().height(27.1.dp),
            thumb = { Ring(active = dragged || pressed, enabled = enabled) },
            track = { state -> Track(control.track, state.value) },
        )
    }
}

/** The hollow ring thumb, on a chrome-coloured gap in the track. */
@Composable
private fun Ring(active: Boolean, enabled: Boolean) {
    val color = when {
        !enabled -> Lc.textDisabled
        active -> Lc.thumbActive
        else -> Lc.thumb
    }
    Canvas(Modifier.size(width = 22.dp, height = 16.dp)) {
        val r = 7.dp.toPx()
        drawRect(Lc.bar, topLeft = Offset(center.x - r - 2.dp.toPx(), center.y - 3.dp.toPx()), size = Size(2 * r + 4.dp.toPx(), 6.dp.toPx()))
        drawCircle(Lc.bar, r, center)
        drawCircle(color, r, center, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun Track(track: SliderTrack, @Suppress("UNUSED_PARAMETER") position: Float) {
    val stops = remember(track) { trackStops(track) }
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        if (stops == null) {
            drawRect(Lc.track)
        } else {
            drawRect(Brush.horizontalGradient(stops))
        }
    }
}

private fun hex(s: String): Color = runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrDefault(Color.Gray)

/**
 * egui's `gamma_multiply`, which scales a premultiplied colour, alpha included: the same colour at
 * [f] times the opacity (it fades into the panel behind).
 */
private fun Color.gammaMultiply(f: Float) = copy(alpha = alpha * f)

/** The desktop's gradient stops for each track style (`track_stops` in `widgets.rs`). */
private fun trackStops(t: SliderTrack): List<Color>? = when (t) {
    SliderTrack.Plain, SliderTrack.Centered -> null
    SliderTrack.Temp -> listOf("#193961", "#47566a", "#60666d", "#70705b", "#737339", "#75742a").map(::hex)
    SliderTrack.Tint -> listOf("#246123", "#2c552c", "#394739", "#4a3d4c", "#6b336b", "#872c87").map(::hex)
    is SliderTrack.Gradient -> listOf(hex(t.from), hex(t.to))
    is SliderTrack.Hue -> {
        val i = t.band.toInt().coerceIn(0, 7)
        val (prev, me, next) = Triple(Lc.bandColors[(i + 7) % 8], Lc.bandColors[i], Lc.bandColors[(i + 1) % 8])
        listOf(prev, lerp(prev, me, 0.5f), me, lerp(me, next, 0.5f), next).map { it.gammaMultiply(0.7f) }
    }
    is SliderTrack.Sat -> {
        val c = Lc.bandColors[t.band.toInt().coerceIn(0, 7)]
        listOf(hex("#454343"), lerp(hex("#454343"), c, 0.6f), c)
    }
    is SliderTrack.Lum -> {
        val c = Lc.bandColors[t.band.toInt().coerceIn(0, 7)]
        listOf(c.gammaMultiply(0.2f), c.gammaMultiply(0.55f), lerp(c, Color.White, 0.35f), lerp(c, Color.White, 0.6f))
    }
    SliderTrack.Rainbow -> listOf("#aa0000", "#aaaa00", "#009900", "#00aaaa", "#0044cc", "#aa0077", "#aa0000").map(::hex)
}
