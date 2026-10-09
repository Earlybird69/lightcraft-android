package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.core.DevelopControl
import ai.storyteller.lightcraft.core.SliderTrack
import ai.storyteller.lightcraft.core.SpotInfo
import ai.storyteller.lightcraft.core.ViewGeometry
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** A 2-D affine map `x' = a·x + c·y + e`, `y' = b·x + d·y + f` (the engine's layout of the six numbers). */
class Affine(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double) {
    fun apply(x: Double, y: Double): Pair<Double, Double> = (a * x + c * y + e) to (b * x + d * y + f)

    fun inverse(): Affine? {
        val det = a * d - b * c
        if (abs(det) < 1e-12) return null
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Affine(ia, ib, ic, id, -(ia * e + ic * f), -(ib * e + id * f))
    }

    companion object {
        fun of(v: List<Double>): Affine? = if (v.size == 6) Affine(v[0], v[1], v[2], v[3], v[4], v[5]) else null
    }
}

/** The view ↔ photo maps of [g], and how many screen pixels a photo pixel is when the view fills [shown] (its rectangle on screen). */
class ViewMap(g: ViewGeometry, private val shown: Rect) {
    private val toPhoto = Affine.of(g.toPhoto) ?: Affine(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    private val toView = toPhoto.inverse() ?: Affine(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    private val long = max(g.width, g.height)

    /** Screen pixels per photo pixel. */
    val scale: Double = shown.width / hypot(toPhoto.a * g.width, toPhoto.b * g.height).coerceAtLeast(1e-9)

    fun photo(p: Offset): Pair<Double, Double> = toPhoto.apply(((p.x - shown.left) / shown.width).toDouble(), ((p.y - shown.top) / shown.height).toDouble())

    fun screen(x: Double, y: Double): Offset {
        val (u, v) = toView.apply(x, y)
        return Offset(shown.left + (u * shown.width).toFloat(), shown.top + (v * shown.height).toFloat())
    }

    /** A size given as a fraction of the photo's long edge, in screen pixels. */
    fun radius(size: Double): Float = (size * long * scale).toFloat()
}

/**
 * The spots over the photo, and the finger's brush: paint over something to take it away (one
 * spot along the path), tap for a round spot, tap a spot to pick it.
 */
@Composable
fun RemoveOverlay(vm: AppViewModel, ed: EditorState, geometry: ViewGeometry, imageSize: IntSize, modifier: Modifier) {
    var stroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val spots = ed.spots
    Canvas(
        modifier.pointerInput(geometry, imageSize, spots) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val map = ViewMap(geometry, fittedRect(size, imageSize))
                val path = mutableListOf(down.position)
                stroke = path.toList()
                var moved = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull() ?: break
                    if (!c.pressed) break
                    if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (moved && (c.position - path.last()).getDistance() > 4f) {
                        path += c.position
                        stroke = path.toList()
                    }
                    c.consume()
                }
                stroke = emptyList()
                if (!moved) {
                    // a tap on a spot picks it; elsewhere it makes a round one
                    val hit = spots.indexOfFirst { s -> spotPoints(s).any { (x, y) -> (map.screen(x, y) - down.position).getDistance() <= max(map.radius(s.size), 24f) } }
                    if (hit >= 0) vm.selectSpot(hit) else vm.addSpot(listOf(map.photo(down.position)))
                } else {
                    vm.addSpot(path.map { map.photo(it) })
                }
            }
        },
    ) {
        val map = ViewMap(geometry, fittedRect(IntSize(size.width.toInt(), size.height.toInt()), imageSize))
        spots.forEachIndexed { i, s ->
            val selected = i == ed.selectedSpot
            val color = if (selected) Color.White else Color.White.copy(alpha = 0.55f)
            val pts = spotPoints(s).map { (x, y) -> map.screen(x, y) }
            val r = map.radius(s.size)
            drawSpot(pts, r, color, selected)
            // a picked spot shows where it takes its pixels from
            val src = s.source
            if (selected && src != null && src.size == 2) {
                val from = spotPoints(s).map { (x, y) -> map.screen(x + src[0], y + src[1]) }
                drawSpot(from, r, Color.White.copy(alpha = 0.4f), false)
                if (pts.isNotEmpty() && from.isNotEmpty()) drawLine(Color.White.copy(alpha = 0.6f), from.first(), pts.first(), 1.dp.toPx())
            }
        }
        if (stroke.isNotEmpty()) {
            val r = map.radius(ed.brushSize.toDouble())
            val path = Path().apply {
                moveTo(stroke[0].x, stroke[0].y)
                stroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, Color.White.copy(alpha = 0.35f), style = Stroke(width = 2 * r, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (stroke.size == 1) drawCircle(Color.White.copy(alpha = 0.35f), r, stroke[0])
        }
    }
}

private fun spotPoints(s: SpotInfo): List<Pair<Double, Double>> = s.points.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }

/** A spot's outline: a circle, or the brushed path's outline as a wide stroke's edges. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpot(pts: List<Offset>, r: Float, color: Color, selected: Boolean) {
    if (pts.isEmpty()) return
    val w = if (selected) 2.dp.toPx() else 1.5.dp.toPx()
    if (pts.size == 1) {
        drawCircle(color, r, pts[0], style = Stroke(w))
        return
    }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
    }
    // the brushed area, faint, with its centre line
    drawPath(path, color.copy(alpha = color.alpha * 0.25f), style = Stroke(width = 2 * r, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A synthetic slider of the brush (and the picked spot). */
private fun brushControl(id: String, label: String, min: Double, max: Double, default: Double, value: Double) =
    DevelopControl(id, label, "", "", min, max, default, 1.0, 0u, value, SliderTrack.Plain)

/** How Remove takes things away: its name and icon. */
private val removeModes = listOf(Triple("remove", "Remove", LcIcon.Heal), Triple("heal", "Heal", LcIcon.HealCross), Triple("clone", "Clone", LcIcon.Clone))

/**
 * Remove's panel, laid out as the reference's: the way it takes things away (a 40 dp button with
 * its name under it; tap it for the others) beside the brush's size, then LightCraft's feather,
 * opacity, find dust and, for a picked spot, delete. Each spot is a step of its own (no cancel).
 */
@Composable
fun RemoveSheet(vm: AppViewModel, ed: EditorState, modifier: Modifier) {
    val selected = ed.selectedSpot?.let { ed.spots.getOrNull(it) }
    // the brush's settings: a picked spot's while one is picked (changing them changes it)
    val size = ed.brushSize.toDouble() * 1000
    val feather = ed.brushFeather.toDouble()
    val opacity = ed.brushOpacity.toDouble()
    val mode = selected?.mode ?: ed.removeMode
    Column(modifier) {
        LazyColumn(Modifier.sheetList(), contentPadding = PaddingValues(top = 20.dp, bottom = 16.dp)) {
            item(key = "mode-size") {
                Row(Modifier.fillMaxWidth().padding(start = 19.9.dp, end = 3.9.dp)) {
                    RemoveModeButton(mode) { vm.setRemoveMode(it) }
                    val c = brushControl("brush.size", "Size", 2.0, 100.0, 20.0, size)
                    DevelopSlider(
                        c,
                        onChange = { vm.setBrush(size = (it / 1000).toFloat()) },
                        onFinished = { vm.brushDone() },
                        onReset = { vm.setBrush(size = 0.02f); vm.brushDone() },
                        modifier = Modifier.weight(1f).padding(start = 3.9.dp),
                    )
                }
            }
            item(key = "feather") {
                val c = brushControl("brush.feather", "Feather", 0.0, 100.0, 50.0, feather)
                DevelopSlider(c, onChange = { vm.setBrush(feather = it.toFloat()) }, onFinished = { vm.brushDone() }, onReset = { vm.setBrush(feather = 50f); vm.brushDone() })
            }
            item(key = "opacity") {
                val c = brushControl("brush.opacity", "Opacity", 1.0, 100.0, 100.0, opacity)
                DevelopSlider(c, onChange = { vm.setBrush(opacity = it.toFloat()) }, onFinished = { vm.brushDone() }, onReset = { vm.setBrush(opacity = 100f); vm.brushDone() })
            }
            item(key = "buttons") {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelButton("Find dust", LcIcon.Search) { vm.findDust() }
                    if (selected != null) PanelButton("Delete spot", LcIcon.Trash) { vm.deleteSpot() }
                }
            }
        }
    }
}

/** The way Remove works: a 40 dp round button, its name under it; a tap lists the others. */
@Composable
private fun RemoveModeButton(mode: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val (_, label, icon) = removeModes.firstOrNull { it.first == mode } ?: removeModes.first()
    Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, Lc.buttonBorder, CircleShape)
                    .clickable(role = Role.DropdownList) { open = true }
                    .semantics { contentDescription = "$label: change how spots are removed" },
                contentAlignment = Alignment.Center,
            ) {
                LcIcon(icon, null, tint = Lc.text, size = 22.dp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Lc.bottomBar) {
                removeModes.forEach { (id, name, ic) ->
                    DropdownMenuItem(
                        text = { Text(name, fontFamily = Inter, fontSize = 15.sp, color = if (id == mode) Lc.selected else Lc.text) },
                        leadingIcon = { LcIcon(ic, null, tint = if (id == mode) Lc.selected else Lc.text, size = 22.dp) },
                        onClick = {
                            open = false
                            onPick(id)
                        },
                    )
                }
            }
        }
        Text(label, color = Lc.textLabel, fontFamily = Inter, fontSize = 11.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 7.8.dp).wrapContentWidth(unbounded = true))
    }
}
