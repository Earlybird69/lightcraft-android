package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.core.CropFrame
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import org.json.JSONObject

// ------------------------------------------------------------------------------------ crop math

/**
 * A point of the uncropped photo ([nx], [ny], 0–1) in the straightened frame the crop rectangle
 * lives in: turned by [angle] degrees about the middle, in pixels of a [w] × [h] photo (the engine's
 * `to_straight`).
 */
fun toStraight(nx: Double, ny: Double, angle: Double, w: Double, h: Double): Pair<Double, Double> {
    val a = Math.toRadians(angle)
    val dx = nx * w - w / 2
    val dy = ny * h - h / 2
    return (w / 2 + dx * cos(a) - dy * sin(a)) / w to (h / 2 + dx * sin(a) + dy * cos(a)) / h
}

/** The corners (top-left, top-right, bottom-right, bottom-left) of crop [rect] in the uncropped photo (the engine's `crop_quad_norm`). */
fun cropCorners(rect: DoubleArray, angle: Double, w: Double, h: Double): List<Pair<Double, Double>> {
    val a = Math.toRadians(-angle)
    return listOf(rect[0] to rect[1], rect[2] to rect[1], rect[2] to rect[3], rect[0] to rect[3]).map { (x, y) ->
        val dx = x * w - w / 2
        val dy = y * h - h / 2
        (w / 2 + dx * cos(a) - dy * sin(a)) / w to (h / 2 + dx * sin(a) + dy * cos(a)) / h
    }
}

/** What a drag holds: a corner (0–3: top-left clockwise), an edge (4–7: top, right, bottom, left) or the whole crop (8). */
const val CROP_MOVE = 8

/**
 * Crop rectangle [start] (`x0, y0, x1, y1` in the straightened frame) after dragging [handle] by
 * [dx], [dy] (straightened, 0–1). With an aspect locked ([aspectW] × [aspectH]) a corner keeps it.
 * Moving keeps the crop inside the photo; null when it would be too small (under 2 %).
 */
fun draggedCrop(start: DoubleArray, handle: Int, dx: Double, dy: Double, aspectW: Int, aspectH: Int, w: Double, h: Double): DoubleArray? {
    var (x0, y0, x1, y1) = start.toList()
    when (handle) {
        0 -> { x0 += dx; y0 += dy }
        1 -> { x1 += dx; y0 += dy }
        2 -> { x1 += dx; y1 += dy }
        3 -> { x0 += dx; y1 += dy }
        4 -> y0 += dy
        5 -> x1 += dx
        6 -> y1 += dy
        7 -> x0 += dx
        else -> {
            // the whole crop, kept inside
            val mx = dx.coerceIn(-start[0], 1.0 - start[2])
            val my = dy.coerceIn(-start[1], 1.0 - start[3])
            return doubleArrayOf(start[0] + mx, start[1] + my, start[2] + mx, start[3] + my)
        }
    }
    x0 = x0.coerceIn(0.0, 1.0)
    x1 = x1.coerceIn(0.0, 1.0)
    y0 = y0.coerceIn(0.0, 1.0)
    y1 = y1.coerceIn(0.0, 1.0)
    if (aspectW > 0 && aspectH > 0 && handle < 4 && w > 0 && h > 0) {
        // the locked shape in the photo's orientation, height from width (as the desktop)
        val a = if ((w >= h) == (aspectW >= aspectH)) aspectW.toDouble() / aspectH else aspectH.toDouble() / aspectW
        val hn = abs(x1 - x0) * w / a / h
        if (handle == 0 || handle == 1) y0 = y1 - hn else y1 = y0 + hn
        if (y0 < 0.0 || y1 > 1.0) return null
    }
    val r = doubleArrayOf(min(x0, x1), min(y0, y1), maxOf(x0, x1), maxOf(y0, y1))
    return if (r[2] - r[0] > 0.02 && r[3] - r[1] > 0.02) r else null
}

/** Where the [image]-sized photo sits in a [view] it is fitted to (centred). */
fun fittedRect(view: IntSize, image: IntSize): Rect {
    if (view.width <= 0 || view.height <= 0 || image.width <= 0 || image.height <= 0) return Rect.Zero
    val s = min(view.width / image.width.toFloat(), view.height / image.height.toFloat())
    val w = image.width * s
    val h = image.height * s
    val left = (view.width - w) / 2
    val top = (view.height - h) / 2
    return Rect(left, top, left + w, top + h)
}

// ---------------------------------------------------------------------------------- the overlay

/**
 * The crop over the uncropped photo: everything outside it dimmed, its border, thirds while
 * dragging, corner and edge handles. Drag a handle to resize, inside to move it; one undo step a
 * drag. [frame] is the crop as the engine has it.
 */
@Composable
fun CropOverlay(vm: AppViewModel, frame: CropFrame, imageSize: IntSize, modifier: Modifier) {
    val touch = with(LocalDensity.current) { 28.dp.toPx() }
    var view by remember { mutableStateOf(IntSize.Zero) }
    // the crop while a finger moves it (null: as the engine has it)
    var live by remember(frame) { mutableStateOf<DoubleArray?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val rect = live ?: frame.rect.toDoubleArray()
    Canvas(
        modifier
            .pointerInput(frame, imageSize) {
                var handle = -1
                var start = rect
                var origin = 0.0 to 0.0
                fun norm(p: Offset): Pair<Double, Double> {
                    val r = fittedRect(size, imageSize)
                    return ((p.x - r.left) / r.width).toDouble() to ((p.y - r.top) / r.height).toDouble()
                }
                detectDragGestures(
                    onDragStart = { p ->
                        val r = fittedRect(size, imageSize)
                        val pts = cropCorners(frame.rect.toDoubleArray(), frame.angle, frame.width, frame.height).map { (x, y) ->
                            Offset(r.left + (x * r.width).toFloat(), r.top + (y * r.height).toFloat())
                        }
                        val handles = pts + (0 until 4).map { i -> (pts[i] + pts[(i + 1) % 4]) / 2f }
                        val near = handles.withIndex().minByOrNull { (_, h) -> (h - p).getDistance() }
                        val n = norm(p)
                        val s = toStraight(n.first, n.second, frame.angle, frame.width, frame.height)
                        val r0 = frame.rect
                        handle = when {
                            near != null && (near.value - p).getDistance() < touch -> near.index
                            s.first in r0[0]..r0[2] && s.second in r0[1]..r0[3] -> CROP_MOVE
                            else -> -1
                        }
                        start = frame.rect.toDoubleArray()
                        origin = s
                        if (handle >= 0) {
                            dragging = true
                            vm.beginCropDrag()
                        }
                    },
                    onDrag = { change, _ ->
                        if (handle < 0) return@detectDragGestures
                        change.consume()
                        val n = norm(change.position)
                        val s = toStraight(n.first, n.second, frame.angle, frame.width, frame.height)
                        draggedCrop(start, handle, s.first - origin.first, s.second - origin.second, frame.aspectW.toInt(), frame.aspectH.toInt(), frame.width, frame.height)?.let {
                            live = it
                            vm.dragCrop(it)
                        }
                    },
                    onDragEnd = {
                        if (handle >= 0) vm.endCropDrag()
                        handle = -1
                        dragging = false
                    },
                    onDragCancel = {
                        if (handle >= 0) vm.endCropDrag()
                        handle = -1
                        dragging = false
                    },
                )
            },
    ) {
        view = IntSize(size.width.toInt(), size.height.toInt())
        val r = fittedRect(view, imageSize)
        if (r.isEmpty) return@Canvas
        val pts = cropCorners(rect, frame.angle, frame.width, frame.height).map { (x, y) -> Offset(r.left + (x * r.width).toFloat(), r.top + (y * r.height).toFloat()) }
        val quad = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until 4) lineTo(pts[i].x, pts[i].y)
            close()
        }
        // dim the photo outside the crop
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(r)
            addPath(quad)
        }
        drawPath(outside, Color.Black.copy(alpha = 0.6f))
        drawPath(quad, Color.White, style = Stroke(1.dp.toPx()))
        // thirds, while moving it
        if (dragging) {
            val at = { u: Float, v: Float -> pts[0] + (pts[1] - pts[0]) * u + (pts[3] - pts[0]) * v }
            for (t in listOf(1f / 3, 2f / 3)) {
                drawLine(Color.White.copy(alpha = 0.6f), at(t, 0f), at(t, 1f), 0.8.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.6f), at(0f, t), at(1f, t), 0.8.dp.toPx())
            }
        }
        // corner handles: short thick L's along the sides; edge handles: short bars
        val len = 18.dp.toPx()
        val thick = 3.dp.toPx()
        for (i in 0 until 4) {
            val c = pts[i]
            val next = pts[(i + 1) % 4]
            val prev = pts[(i + 3) % 4]
            val toNext = (next - c) / (next - c).getDistance().coerceAtLeast(1f)
            val toPrev = (prev - c) / (prev - c).getDistance().coerceAtLeast(1f)
            drawLine(Color.White, c, c + toNext * len, thick)
            drawLine(Color.White, c, c + toPrev * len, thick)
            val mid = (c + next) / 2f
            drawLine(Color.White, mid - toNext * (len / 2), mid + toNext * (len / 2), thick)
        }
    }
}

private operator fun Offset.times(f: Float) = Offset(x * f, y * f)

// ------------------------------------------------------------------------------------ the sheet

/** The reference's aspect choices, as the engine names them, with their labels. */
private val aspects = listOf("original" to "Original", "free" to "Free", "1x1" to "1 : 1", "4x5" to "4 : 5", "5x7" to "5 : 7", "2x3" to "2 : 3", "4x3" to "4 : 3", "16x9" to "16 : 9")

/** Which aspect choice [frame]'s lock is (either way round), if any. */
fun aspectChoice(frame: CropFrame): String {
    val (w, h) = frame.aspectW.toDouble() to frame.aspectH.toDouble()
    if (w <= 0 || h <= 0) return "free"
    val r = w / h
    val photo = frame.width / frame.height
    if (abs(r - photo) < 0.005 || abs(1 / r - photo) < 0.005) return "original"
    return aspects.drop(2).firstOrNull { (id, _) ->
        val (a, b) = id.split('x').map { it.toDouble() }
        abs(r - a / b) < 0.005 || abs(r - b / a) < 0.005
    }?.first ?: "custom"
}

/** Crop's panel: Aspect (the crop's shape) and Geometry (Upright, constrain, turn, flip, straighten and the transform sliders). */
@Composable
fun CropSheet(vm: AppViewModel, ed: EditorState, modifier: Modifier) {
    Column(modifier) {
        // the reference's two tabs are wider than the panels' (123 and 146 dp), the options right under them
        Tabs(listOf("aspect" to "Aspect", "geometry" to "Geometry"), ed.cropTab, minWidth = 123.dp, bottomPadding = 0.dp) { ed.cropTab = it }
        when (ed.cropTab) {
            "geometry" -> CropGeometry(vm, ed)
            else -> CropAspect(vm, ed)
        }
    }
}

/** A 67.6 dp tile of Crop's: [icon] over its name; a choice ([selected] not null) is blue when it's the crop's. */
@Composable
private fun CropTile(label: String, selected: Boolean? = null, width: Dp = 67.6.dp, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val color = if (selected == true) Lc.selected else Lc.text
    val press = if (selected != null) Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick) else Modifier.clickable(role = Role.Button, onClick = onClick)
    Column(
        Modifier.size(width = width, height = 67.6.dp).clip(RoundedCornerShape(6.dp)).then(press),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(15.6.dp))
        Box(Modifier.size(21.9.dp), contentAlignment = Alignment.Center) { icon(color) }
        Spacer(Modifier.height(7.7.dp))
        Text(label, color = color, fontFamily = Inter, fontSize = 11.sp, maxLines = 1, softWrap = false)
    }
}

/** An aspect's icon: a rectangle of its shape (the photo's for Original). */
@Composable
private fun AspectShape(w: Float, h: Float, color: Color) {
    Canvas(Modifier.size(21.9.dp)) {
        val side = size.minDimension - 2.dp.toPx()
        val (rw, rh) = if (w >= h) side to side * h / w else side * w / h to side
        drawRect(color, Offset((size.width - rw) / 2, (size.height - rh) / 2), Size(rw, rh), style = Stroke(1.5.dp.toPx()))
    }
}

/**
 * Aspect, as the reference's: a row of tiles 16 dp under the tabs (Original, Free, the ratios),
 * then LightCraft's turn-the-shape and reset. The reference's first tile, generative expand, is AI.
 */
@Composable
private fun CropAspect(vm: AppViewModel, ed: EditorState) {
    val current = ed.crop?.let { aspectChoice(it) }
    val tile: @Composable (String, String) -> Unit = { id, label ->
        CropTile(label, selected = id == current, onClick = { vm.setCropAspect(id) }) { color ->
            when (id) {
                "original" -> LcIcon(LcIcon.Original, null, tint = color, size = 21.9.dp)
                "free" -> LcIcon(LcIcon.FreeAspect, null, tint = color, size = 21.9.dp)
                else -> {
                    val (a, b) = id.split('x').map { it.toFloat() }
                    AspectShape(a, b, color)
                }
            }
        }
    }
    val turn: @Composable () -> Unit = { CropTile("Turn", onClick = { vm.cropCommand("crop.rotateAspect") }) { LcIcon(LcIcon.Versions, null, tint = it, size = 21.9.dp) } }
    val reset: @Composable () -> Unit = { CropTile("Reset", onClick = { vm.cropCommand("crop.reset") }) { LcIcon(LcIcon.Undo, null, tint = it, size = 21.9.dp) } }
    if (LocalSheetLimit.current == null) {
        // sideways: the panel is tall and narrow, so the tiles fill it in rows
        FlowRow(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.1.dp, end = 16.dp, top = 16.dp, bottom = 16.1.dp),
            horizontalArrangement = Arrangement.spacedBy(7.8.dp),
            verticalArrangement = Arrangement.spacedBy(7.8.dp),
        ) {
            aspects.forEach { (id, label) -> tile(id, label) }
            turn()
            reset()
        }
        return
    }
    LazyRow(contentPadding = PaddingValues(start = 16.1.dp, end = 16.dp, top = 16.dp, bottom = 16.1.dp), horizontalArrangement = Arrangement.spacedBy(7.8.dp)) {
        items(aspects, key = { it.first }) { (id, label) -> tile(id, label) }
        item(key = "turn") { turn() }
        item(key = "reset") { reset() }
    }
}

/** A 50 dp square button with an icon, its name under it (Upright's, as the reference's). A longer name widens the tile, so neighbours' names never run together. */
@Composable
private fun SquareTile(icon: LcIcon, label: String, selected: Boolean? = null, onClick: () -> Unit) {
    val color = if (selected == true) Lc.selected else Lc.text
    // a choice (Upright's) says whether it's on; the others are buttons
    val press = if (selected != null) Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick) else Modifier.clickable(role = Role.Button, onClick = onClick)
    Column(Modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(50.1.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, if (selected == true) Lc.selected else Lc.buttonBorder, RoundedCornerShape(8.dp))
                .then(press)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            LcIcon(icon, null, tint = color, size = 24.dp)
        }
        // the button already says its name
        Text(label, color = color, fontFamily = Inter, fontSize = 11.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 3.9.dp).clearAndSetSemantics {})
    }
}

/**
 * Geometry, as the reference's: Upright's tiles (tap the one on again to turn it off), Constrain
 * crop, then the manual transforms. Turning, flipping and straightening (on the photo in the
 * reference, not observable) are LightCraft's row of tiles and slider.
 */
@Composable
private fun CropGeometry(vm: AppViewModel, ed: EditorState) {
    val upright = ed.state?.upright
    val straighten = ed.control("crop.angle")
    LazyColumn(Modifier.sheetList(), contentPadding = PaddingValues(top = 5.8.dp, bottom = 16.dp)) {
        item(key = "upright") {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.9.dp, Alignment.CenterHorizontally)) {
                listOf(Triple("auto", "Auto", LcIcon.Auto), Triple("level", "Level", LcIcon.Level), Triple("vertical", "Vertical", LcIcon.Vertical), Triple("full", "Full", LcIcon.FullUpright))
                    .forEach { (id, label, icon) ->
                        SquareTile(icon, label, selected = upright == id) { vm.cropCommand("geometry.upright", JSONObject().put("mode", if (upright == id) "off" else id)) }
                    }
            }
        }
        item(key = "constrain") { SwitchRow("Constrain crop", ed.state?.constrainCrop == true) { vm.setConstrainCrop(it) } }
        item(key = "turn") {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.9.dp, Alignment.CenterHorizontally)) {
                SquareTile(LcIcon.FixAngle, "Straighten") { vm.cropCommand("crop.autoStraighten") }
                SquareTile(LcIcon.RotateLeft, "Turn left") { vm.cropCommand("photo.rotateLeft", photo = true) }
                SquareTile(LcIcon.RotateRight, "Turn right") { vm.cropCommand("photo.rotateRight", photo = true) }
                SquareTile(LcIcon.FlipH, "Flip across") { vm.cropCommand("photo.flipHorizontal", photo = true) }
                SquareTile(LcIcon.FlipV, "Flip down") { vm.cropCommand("photo.flipVertical", photo = true) }
            }
        }
        item(key = "heading") {
            Text("Manual transforms", color = Lc.text, fontFamily = Inter, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
        }
        straighten?.let { c ->
            item(key = "straighten") {
                DevelopSlider(c, onChange = { vm.onControlChange(c, it) }, onFinished = vm::onControlChangeFinished, onReset = { vm.resetControl(c) }, label = "Straighten")
            }
        }
        items(listOf("geometry.vertical", "geometry.horizontal", "geometry.rotate", "geometry.aspect", "geometry.scale", "geometry.offsetX", "geometry.offsetY"), key = { it }) { id ->
            ed.control(id)?.let { c -> DevelopSlider(c, onChange = { vm.onControlChange(c, it) }, onFinished = vm::onControlChangeFinished, onReset = { vm.resetControl(c) }) }
        }
    }
}
