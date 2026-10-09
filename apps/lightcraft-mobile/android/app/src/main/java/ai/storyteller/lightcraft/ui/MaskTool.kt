package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.core.DevelopControl
import ai.storyteller.lightcraft.core.MaskInfo
import ai.storyteller.lightcraft.core.MaskPart
import ai.storyteller.lightcraft.core.SliderTrack
import ai.storyteller.lightcraft.core.ViewGeometry
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import org.json.JSONObject

// ------------------------------------------------------------------------------------- sliders

/** A local adjustment slider (the desktop's `masking.rs` → `LOCAL`), keyed by its `mask.adjust` name. */
class LocalSpec(val key: String, val label: String, val min: Double, val max: Double, val step: Double, val decimals: Int, val track: SliderTrack)

private val localSpecs = listOf(
    LocalSpec("temp", "Temp", -100.0, 100.0, 1.0, 0, SliderTrack.Temp),
    LocalSpec("tint", "Tint", -100.0, 100.0, 1.0, 0, SliderTrack.Tint),
    LocalSpec("exposure", "Exposure", -4.0, 4.0, 0.01, 2, SliderTrack.Centered),
    LocalSpec("contrast", "Contrast", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("highlights", "Highlights", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("shadows", "Shadows", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("whites", "Whites", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("blacks", "Blacks", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("texture", "Texture", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("clarity", "Clarity", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("dehaze", "Dehaze", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("hue", "Hue", -100.0, 100.0, 1.0, 0, SliderTrack.Rainbow),
    LocalSpec("saturation", "Saturation", -100.0, 100.0, 1.0, 0, SliderTrack.Gradient("#7a7a7a", "#e04a3a")),
    LocalSpec("sharpness", "Sharpness", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("noise", "Noise", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("moire", "Moiré", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
    LocalSpec("defringe", "Defringe", -100.0, 100.0, 1.0, 0, SliderTrack.Centered),
).associateBy { it.key }

/** The reference app's sections in Masking (its local adjustments), with their sliders. */
enum class MaskSection(val label: String, val icon: LcIcon, val keys: List<String>) {
    LIGHT("Light", LcIcon.Light, listOf("exposure", "contrast", "highlights", "shadows", "whites", "blacks")),
    COLOR("Color", LcIcon.Color, listOf("temp", "tint", "hue", "saturation")),
    EFFECTS("Effects", LcIcon.Effects, listOf("texture", "clarity", "dehaze")),
    DETAIL("Detail", LcIcon.Detail, listOf("sharpness", "noise", "moire")),
    OPTICS("Optics", LcIcon.Optics, listOf("defringe")),
}

/** The kinds of mask the phone makes (the AI ones need a model the phone doesn't have). */
val maskKinds = listOf(
    Triple("brush", "Brush", LcIcon.Pencil),
    Triple("linear", "Linear gradient", LcIcon.Segments),
    Triple("radial", "Radial gradient", LcIcon.Radial),
    Triple("colorRange", "Color range", LcIcon.Picker),
    Triple("luminanceRange", "Luminance range", LcIcon.Sliders),
)

fun maskIcon(kind: String): LcIcon = maskKinds.firstOrNull { it.first == kind }?.third ?: LcIcon.Effects

private fun localControl(spec: LocalSpec, value: Double) =
    DevelopControl("mask.${spec.key}", spec.label, "", "", spec.min, spec.max, 0.0, spec.step, spec.decimals.toUByte(), value, spec.track)

private fun adjustOf(m: MaskInfo): JSONObject = runCatching { JSONObject(m.adjust) }.getOrDefault(JSONObject())

fun shapeOf(m: MaskInfo): JSONObject = runCatching { JSONObject(m.shape) }.getOrDefault(JSONObject())

/** Part [i] of [m] (its first when there's no such part): what it does, its kind and its shape. */
fun partOf(m: MaskInfo, i: Int): MaskPart? = m.components.getOrNull(i) ?: m.components.firstOrNull()

fun partKind(m: MaskInfo, i: Int): String = partOf(m, i)?.kind ?: m.kind

fun partShape(m: MaskInfo, i: Int): JSONObject = partOf(m, i)?.let { runCatching { JSONObject(it.shape) }.getOrNull() } ?: shapeOf(m)

/** A kind of mask's name (`Radial gradient`…). */
fun kindLabel(kind: String): String = maskKinds.firstOrNull { it.first == kind }?.second ?: kind.replaceFirstChar { it.uppercase() }

// ------------------------------------------------------------------------------ masks button

/**
 * Masking's round 43.7 dp button over the photo's foot at the right, as the reference's: no masks
 * yet, a "+" with a hint beside it (it opens the new mask sheet); else the edited mask's kind (it
 * lists the masks).
 */
@Composable
fun MaskButton(vm: AppViewModel, ed: EditorState, modifier: Modifier) {
    val mask = ed.masks.firstOrNull { it.id == ed.selectedMask }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (ed.masks.isEmpty()) {
            Text(
                "Start here: add a mask",
                color = Lc.text,
                fontFamily = Inter,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Box(
            Modifier
                .size(43.7.dp)
                .background(Lc.bar, CircleShape)
                .border(1.dp, Lc.buttonBorder, CircleShape)
                .clip(CircleShape)
                .clickable(role = Role.Button) { vm.openMaskPicker() }
                .semantics { contentDescription = if (ed.masks.isEmpty()) "Add a mask" else "Masks" },
            contentAlignment = Alignment.Center,
        ) {
            LcIcon(mask?.let { maskIcon(it.kind) } ?: LcIcon.Plus, null, tint = if (mask != null) Lc.selected else Lc.text, size = 22.dp)
        }
    }
}

/**
 * The new mask sheet, as the reference's: "Create new mask" over 47.7 dp rows every 55.5 dp in
 * groups 17.5 dp apart (its first group, Select Subject, Sky and Background, is AI and left out).
 */
@Composable
fun NewMaskSheet(vm: AppViewModel, op: String?, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val title = when (op) {
        "add" -> "Add to the mask"
        "subtract" -> "Subtract from the mask"
        else -> "Create new mask"
    }
    LcSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 16.1.dp, top = 3.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Lc.text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.weight(1f))
            BarButton(LcIcon.Help, "Help", size = 48.dp, tint = Lc.text) { runCatching { uri.openUri(HELP_URL) } }
        }
        listOf(maskKinds.take(3), maskKinds.drop(3)).forEachIndexed { g, group ->
            if (g > 0) Spacer(Modifier.height(17.5.dp))
            group.forEachIndexed { i, (kind, label, icon) ->
                if (i > 0) Spacer(Modifier.height(7.8.dp))
                Row(
                    Modifier.fillMaxWidth().height(47.7.dp).clickable(role = Role.Button) { if (op == null) vm.addMask(kind) else vm.addMaskPart(op, kind) }.padding(start = 16.1.dp, end = 16.1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LcIcon(icon, null, tint = Lc.text, size = 24.dp)
                    Spacer(Modifier.width(21.6.dp))
                    Text(label, color = Lc.text, fontFamily = Inter, fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The photo's masks (tap one to edit it), and a new one. */
@Composable
fun MasksSheet(vm: AppViewModel, ed: EditorState, onDismiss: () -> Unit) {
    LcSheet(onDismiss) {
        SheetHeading("Masks")
        SheetRow(LcIcon.Plus, "Create new mask") { ed.maskPicker = "new" }
        SheetDivider()
        ed.masks.forEach { m ->
            SheetRow(maskIcon(m.kind), m.name, selected = m.id == ed.selectedMask) {
                onDismiss()
                vm.selectMask(m.id)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// --------------------------------------------------------------------------------------- sheet

/** Masking's panel for the edited mask: its own settings, or a section's local sliders. */
@Composable
fun MaskSheet(vm: AppViewModel, ed: EditorState, modifier: Modifier) {
    val mask = ed.masks.firstOrNull { it.id == ed.selectedMask }
    Column(modifier) {
        LazyColumn(Modifier.sheetList(), contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)) {
            when {
                mask == null -> {}
                ed.maskSection == "mask" -> {
                    item(key = "title") { MaskSheetTitle(mask.name) }
                    item(key = "parts") { MaskParts(ed, mask) }
                    item(key = "kind") { MaskShapeControls(vm, ed, mask) }
                    item(key = "actions") {
                        // wrapped onto a second line when the panel is narrow (sideways)
                        FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PanelButton("Invert", LcIcon.Invert, active = mask.invert) { vm.maskCommand("mask.invert") }
                            PanelButton("Duplicate", LcIcon.Versions) { vm.maskCommand("mask.duplicate") }
                            PanelButton("Delete", LcIcon.Trash) { vm.maskCommand("mask.delete") }
                        }
                    }
                }
                else -> {
                    val section = MaskSection.entries.firstOrNull { it.name.lowercase() == ed.maskSection } ?: MaskSection.LIGHT
                    val adjust = adjustOf(mask)
                    items(section.keys, key = { it }) { key ->
                        localSpecs[key]?.let { spec ->
                            val c = localControl(spec, ed.liveLocal[key] ?: adjust.optDouble(key, 0.0))
                            DevelopSlider(c, onChange = { vm.setLocal(key, it) }, onFinished = vm::localDone, onReset = { vm.setLocal(key, 0.0); vm.localDone() })
                        }
                    }
                }
            }
        }
    }
}

/** The mask's parts (tap one to edit it; shown once there are two), then add or subtract another, as the reference's. */
@Composable
private fun MaskParts(ed: EditorState, mask: MaskInfo) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mask.components.size > 1) {
            itemsIndexed(mask.components) { i, part ->
                val sign = when (part.op) {
                    "subtract" -> "− "
                    "intersect" -> "∩ "
                    else -> ""
                }
                PanelButton(sign + kindLabel(part.kind), maskIcon(part.kind), active = i == ed.maskPart) { ed.maskPart = i }
            }
        }
        item(key = "add") { PanelButton("Add", LcIcon.Plus) { ed.maskPicker = "add" } }
        item(key = "subtract") { PanelButton("Subtract", LcIcon.Minus) { ed.maskPicker = "subtract" } }
    }
}

@Composable
private fun MaskSheetTitle(text: String) {
    Text(text, color = Lc.text, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp))
}

/** What a mask's kind needs besides the photo: the brush, a range's bounds, colour sampling. */
@Composable
private fun MaskShapeControls(vm: AppViewModel, ed: EditorState, mask: MaskInfo) {
    when (partKind(mask, ed.maskPart)) {
        "brush" -> Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelButton("Paint", LcIcon.Pencil, active = !ed.maskErase) { ed.maskErase = false }
                PanelButton("Erase", LcIcon.Close, active = ed.maskErase) { ed.maskErase = true }
            }
            val size = DevelopControl("mask.brushSize", "Size", "", "", 2.0, 100.0, 30.0, 1.0, 0u, ed.maskBrushSize * 1000.0, SliderTrack.Plain)
            DevelopSlider(size, onChange = { ed.maskBrushSize = (it / 1000).toFloat() }, onFinished = {}, onReset = { ed.maskBrushSize = 0.03f })
            val feather = DevelopControl("mask.brushFeather", "Feather", "", "", 0.0, 100.0, 50.0, 1.0, 0u, ed.maskBrushFeather.toDouble(), SliderTrack.Plain)
            DevelopSlider(feather, onChange = { ed.maskBrushFeather = it.toFloat() }, onFinished = {}, onReset = { ed.maskBrushFeather = 50f })
            val flow = DevelopControl("mask.brushFlow", "Flow", "", "", 1.0, 100.0, 100.0, 1.0, 0u, ed.maskBrushFlow.toDouble(), SliderTrack.Plain)
            DevelopSlider(flow, onChange = { ed.maskBrushFlow = it.toFloat() }, onFinished = {}, onReset = { ed.maskBrushFlow = 100f })
        }
        "luminanceRange" -> {
            val shape = partShape(mask, ed.maskPart)
            Column {
                listOf("lo" to "Darkest", "hi" to "Lightest").forEach { (key, label) ->
                    val c = DevelopControl("mask.$key", label, "", "", 0.0, 100.0, if (key == "lo") 60.0 else 100.0, 1.0, 0u, (ed.liveShape[key] ?: shape.optDouble(key, 0.0)) * 100, SliderTrack.Gradient("#101010", "#f0f0f0"))
                    DevelopSlider(c, onChange = { vm.setShapeValue(key, it / 100) }, onFinished = vm::shapeDone, onReset = {})
                }
            }
        }
        "colorRange" -> {
            Text("Tap the photo on the colour to select; tap again to add more.", color = Lc.textDim, fontFamily = Inter, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp))
            val shape = partShape(mask, ed.maskPart)
            val c = DevelopControl("mask.refine", "Refine", "", "", 0.0, 100.0, 50.0, 1.0, 0u, ed.liveShape["refine"] ?: shape.optDouble("refine", 50.0), SliderTrack.Plain)
            DevelopSlider(c, onChange = { vm.setShapeValue("refine", it) }, onFinished = vm::shapeDone, onReset = {})
        }
        "radial" -> {
            Text("Drag the middle to move it, the handles to size it.", color = Lc.textDim, fontFamily = Inter, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp))
            val shape = partShape(mask, ed.maskPart)
            val c = DevelopControl("mask.feather", "Feather", "", "", 0.0, 100.0, 50.0, 1.0, 0u, ed.liveShape["feather"] ?: shape.optDouble("feather", 50.0), SliderTrack.Plain)
            DevelopSlider(c, onChange = { vm.setShapeValue("feather", it) }, onFinished = vm::shapeDone, onReset = {})
        }
        "linear" -> Text("Drag the lines to place the gradient: full at the solid one, none at the dashed one.", color = Lc.textDim, fontFamily = Inter, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        else -> {}
    }
}

// ------------------------------------------------------------------------------------- overlay

/**
 * The selected mask on the photo: a radial gradient's ellipse and handles, a linear gradient's
 * lines, or the brush (paint or erase with a finger), or a tap that samples a colour.
 */
@Composable
fun MaskOverlay(vm: AppViewModel, ed: EditorState, mask: MaskInfo, geometry: ViewGeometry, imageSize: IntSize, modifier: Modifier) {
    var stroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // the part being edited
    val part = ed.maskPart
    val kind = partKind(mask, part)
    val shape = partShape(mask, part)
    val live = ed.liveShape
    // read when a finger goes down: a drag's draft renders change the image's size, and restarting
    // the gesture then would cut the drag off before its end
    val currentShape by rememberUpdatedState(shape)
    val currentGeometry by rememberUpdatedState(geometry)
    val currentSize by rememberUpdatedState(imageSize)
    Canvas(
        modifier.pointerInput(mask.id, kind, part) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val shape = currentShape
                val geometry = currentGeometry
                val map = ViewMap(geometry, fittedRect(size, currentSize))
                val long = max(geometry.width, geometry.height)
                when (kind) {
                    "brush" -> {
                        val path = mutableListOf(down.position)
                        stroke = path.toList()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            if ((c.position - path.last()).getDistance() > 4f) {
                                path += c.position
                                stroke = path.toList()
                            }
                            c.consume()
                        }
                        stroke = emptyList()
                        vm.brushStroke(path.map { map.photo(it) })
                    }
                    "colorRange" -> {
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        }
                        if (!moved) {
                            val (x, y) = map.photo(down.position)
                            vm.sampleColor(x, y)
                        }
                    }
                    "radial", "linear" -> {
                        val handles = shapeHandles(kind, shape, map, geometry)
                        val near = handles.withIndex().minByOrNull { (_, h) -> (h - down.position).getDistance() }
                        val handle = if (near != null && (near.value - down.position).getDistance() < 36.dp.toPx()) near.index else -1
                        if (handle < 0) return@awaitEachGesture
                        vm.beginShapeDrag()
                        val start = JSONObject(shape.toString())
                        val (x0, y0) = map.photo(down.position)
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull() ?: break
                                if (!c.pressed) break
                                c.consume()
                                val (x, y) = map.photo(c.position)
                                vm.dragShape(draggedShape(kind, start, handle, x - x0, y - y0, x, y, geometry.width, geometry.height, long))
                            }
                        } finally {
                            vm.endShapeDrag()
                        }
                    }
                }
            }
        },
    ) {
        val map = ViewMap(geometry, fittedRect(IntSize(size.width.toInt(), size.height.toInt()), imageSize))
        val current = JSONObject(shape.toString()).also { s -> live.forEach { (k, v) -> s.put(k, v) } }
        ed.liveShapeJson?.let { j -> runCatching { JSONObject(j) }.getOrNull() }?.let { drawShape(kind, it, map, geometry) } ?: drawShape(kind, current, map, geometry)
        if (stroke.isNotEmpty()) {
            val r = map.radius(ed.maskBrushSize.toDouble())
            val path = Path().apply {
                moveTo(stroke[0].x, stroke[0].y)
                stroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val color = if (ed.maskErase) Color(0x66E04A4A) else Color(0x5566AAFF)
            drawPath(path, color, style = Stroke(width = 2 * r, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (stroke.size == 1) drawCircle(color, r, stroke[0])
        }
    }
}

private fun pt(o: JSONObject, key: String): Pair<Double, Double> = o.optJSONObject(key)?.let { it.optDouble("x", 0.5) to it.optDouble("y", 0.5) } ?: (0.5 to 0.5)

/** A point of the photo, `dx`, `dy` long-edge units from [c] turned by [angle] degrees (the radial's frame). */
private fun around(c: Pair<Double, Double>, dx: Double, dy: Double, angle: Double, w: Double, h: Double): Pair<Double, Double> {
    val a = Math.toRadians(angle)
    val long = max(w, h)
    val rx = dx * cos(a) - dy * sin(a)
    val ry = dx * sin(a) + dy * cos(a)
    return (c.first + rx * long / w) to (c.second + ry * long / h)
}

/** Where a radial's (centre, x radius, y radius) or a linear's (start, end, middle) handles are on screen. */
private fun shapeHandles(kind: String, s: JSONObject, map: ViewMap, g: ViewGeometry): List<Offset> = when (kind) {
    "radial" -> {
        val c = pt(s, "center")
        val (rx, ry, a) = Triple(s.optDouble("rx", 0.2), s.optDouble("ry", 0.15), s.optDouble("angle", 0.0))
        listOf(c, around(c, rx, 0.0, a, g.width, g.height), around(c, 0.0, ry, a, g.width, g.height)).map { (x, y) -> map.screen(x, y) }
    }
    else -> {
        val (a, b) = pt(s, "start") to pt(s, "end")
        listOf(a, b, ((a.first + b.first) / 2) to ((a.second + b.second) / 2)).map { (x, y) -> map.screen(x, y) }
    }
}

/**
 * The shape after dragging [handle] by [dx], [dy] (the photo's 0–1; the finger at [x], [y]):
 * a radial's centre moves it, its handles set the radii; a linear's ends move one by one, its middle both.
 */
fun draggedShape(kind: String, start: JSONObject, handle: Int, dx: Double, dy: Double, x: Double, y: Double, w: Double, h: Double, long: Double): JSONObject {
    val s = JSONObject(start.toString())
    fun move(key: String) {
        val (px, py) = pt(start, key)
        s.put(key, JSONObject().put("x", px + dx).put("y", py + dy))
    }
    when (kind) {
        "radial" -> when (handle) {
            0 -> move("center")
            else -> {
                val c = pt(start, "center")
                // the finger's distance from the centre in long-edge units, along the handle's axis
                val ux = (x - c.first) * w / long
                val uy = (y - c.second) * h / long
                val a = Math.toRadians(start.optDouble("angle", 0.0))
                val along = if (handle == 1) ux * cos(a) + uy * sin(a) else -ux * sin(a) + uy * cos(a)
                s.put(if (handle == 1) "rx" else "ry", kotlin.math.abs(along).coerceIn(0.01, 2.0))
            }
        }
        else -> when (handle) {
            0 -> move("start")
            1 -> move("end")
            else -> {
                move("start")
                move("end")
            }
        }
    }
    return s
}

/** A radial's ellipse or a linear's three lines, with their handles. */
private fun DrawScope.drawShape(kind: String, s: JSONObject, map: ViewMap, g: ViewGeometry) {
    val white = Color.White
    val handleR = 7.dp.toPx()
    when (kind) {
        "radial" -> {
            val c = pt(s, "center")
            val (rx, ry, a) = Triple(s.optDouble("rx", 0.2), s.optDouble("ry", 0.15), s.optDouble("angle", 0.0))
            val path = Path()
            for (i in 0..72) {
                val t = Math.toRadians(i * 5.0)
                val (x, y) = around(c, rx * cos(t), ry * sin(t), a, g.width, g.height)
                val p = map.screen(x, y)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, Color.Black.copy(alpha = 0.5f), style = Stroke(3.dp.toPx()))
            drawPath(path, white, style = Stroke(1.5.dp.toPx()))
            shapeHandles(kind, s, map, g).forEachIndexed { i, h ->
                drawCircle(if (i == 0) white else Color.Black.copy(alpha = 0.6f), handleR, h)
                drawCircle(white, handleR, h, style = Stroke(2.dp.toPx()))
            }
        }
        "linear" -> {
            val (a, b) = pt(s, "start") to pt(s, "end")
            val pa = map.screen(a.first, a.second)
            val pb = map.screen(b.first, b.second)
            val dir = pb - pa
            val len = hypot(dir.x, dir.y).coerceAtLeast(1f)
            val normal = Offset(-dir.y / len, dir.x / len) * (size.width + size.height)
            val mid = (pa + pb) / 2f
            drawLine(white, pa - normal, pa + normal, 1.5.dp.toPx())
            drawLine(white.copy(alpha = 0.7f), mid - normal, mid + normal, 1.dp.toPx())
            drawLine(white, pb - normal, pb + normal, 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            listOf(pa, pb, mid).forEach { h ->
                drawCircle(Color.Black.copy(alpha = 0.6f), handleR, h)
                drawCircle(white, handleR, h, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

private operator fun Offset.times(f: Float) = Offset(x * f, y * f)
