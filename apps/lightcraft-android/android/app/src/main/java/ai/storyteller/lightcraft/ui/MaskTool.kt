package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.core.DevelopControl
import ai.storyteller.lightcraft.core.MaskInfo
import ai.storyteller.lightcraft.core.MaskPart
import ai.storyteller.lightcraft.core.SliderTrack
import ai.storyteller.lightcraft.core.ViewGeometry
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
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
enum class MaskSection(val label: String, val icon: Glyph, val keys: List<String>) {
    LIGHT("Light", MdIcon.LightMode, listOf("exposure", "contrast", "highlights", "shadows", "whites", "blacks")),
    COLOR("Color", MdIcon.Thermostat, listOf("temp", "tint", "saturation", "hue")),
    EFFECTS("Effects", MdIcon.Vignette, listOf("texture", "clarity", "dehaze")),
    // as the reference's: Detail is noise and sharpness, Optics moiré and defringe
    DETAIL("Detail", LcIcon.SectionDetail, listOf("noise", "sharpness")),
    OPTICS("Optics", LcIcon.SectionOptics, listOf("moire", "defringe")),
}

/** The kinds of mask the phone makes (the AI ones need a model the phone doesn't have). */
val maskKinds = listOf(
    Triple("brush", "Brush", LcIcon.Pencil),
    Triple("linear", "Linear Gradient", LcIcon.Segments),
    Triple("radial", "Radial Gradient", LcIcon.Radial),
    Triple("colorRange", "Color Range", LcIcon.Picker),
    Triple("luminanceRange", "Luminance Range", LcIcon.Sliders),
)

fun maskIcon(kind: String): LcIcon = maskKinds.firstOrNull { it.first == kind }?.third ?: LcIcon.Effects

/**
 * A mask kind's picture for the new mask sheet: a brush; a square fading from top to bottom; a
 * disc fading out from its middle; a dropper over a colour wheel; a dropper over a grey ramp.
 */
@Composable
fun MaskKindIcon(kind: String, modifier: Modifier) {
    when (kind) {
        "linear" -> Canvas(modifier) {
            val s = size.minDimension * 0.78f
            val tl = Offset((size.width - s) / 2, (size.height - s) / 2)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFFB8B8B8)), tl.y, tl.y + s), tl, Size(s, s))
            drawRect(Color(0xFFB8B8B8), tl, Size(s, s), style = Stroke(1.5.dp.toPx()))
        }
        "radial" -> Canvas(modifier) {
            val r = size.minDimension * 0.42f
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF9A9A9A)), center, r), r, center)
            drawCircle(Color(0xFFB8B8B8), r, center, style = Stroke(1.5.dp.toPx()))
        }
        "colorRange" -> Box(modifier) {
            Canvas(Modifier.matchParentSize()) {
                val r = size.minDimension * 0.34f
                val c = Offset(size.width * 0.4f, size.height * 0.6f)
                drawCircle(Brush.sweepGradient(listOf(Color(0xFFB04848), Color(0xFFB0A048), Color(0xFF48A060), Color(0xFF4878B0), Color(0xFF9A48B0), Color(0xFFB04848)), c), r, c)
            }
            MdIcon(MdIcon.Colorize, null, tint = Color(0xFFD0D0D0), size = 16.dp, modifier = Modifier.align(Alignment.TopEnd))
        }
        "luminanceRange" -> Box(modifier) {
            Canvas(Modifier.matchParentSize()) {
                val r = size.minDimension * 0.34f
                val c = Offset(size.width * 0.4f, size.height * 0.6f)
                drawCircle(Brush.horizontalGradient(listOf(Color(0xFF202020), Color(0xFFE0E0E0)), c.x - r, c.x + r), r, c)
            }
            MdIcon(MdIcon.Colorize, null, tint = Color(0xFFD0D0D0), size = 16.dp, modifier = Modifier.align(Alignment.TopEnd))
        }
        else -> MdIcon(MdIcon.Brush, null, tint = Color(0xFFB8B8B8), size = 22.dp, modifier = modifier.padding(1.dp))
    }
}

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
 * Masking's column against the right edge, as the reference's: 76 dp of its grey (8 dp corners at
 * the left) holding the blue + (a new mask) over a 50 dp picture of each mask, the one edited
 * framed; the handle at its left (or a sideways drag) opens it to 232 dp, each mask's name and its
 * menu beside its picture, as the reference's: add to it, subtract from it, hide (show) it, rename,
 * duplicate, invert, duplicate and invert, delete. A hidden mask's picture and name show faint.
 */
@Composable
fun MaskColumn(vm: AppViewModel, ed: EditorState, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (open) 232.dp else 76.dp, tween(250, easing = AccelerateDecelerate), label = "masks")
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (ed.masks.isNotEmpty()) {
            // the reference's handle: a 44 x 60 dp grip just outside the column
            Box(
                Modifier
                    .size(44.dp, 60.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, drag ->
                            change.consume()
                            if (drag < -8f) open = true else if (drag > 8f) open = false
                        }
                    }
                    .clickable(role = Role.Button) { open = !open }
                    .semantics { contentDescription = if (open) "Collapse the masks" else "Expand the masks" },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.padding(end = 6.dp).size(4.dp, 28.dp).background(Lc.panelButtonBorder, RoundedCornerShape(2.dp)))
            }
        }
        Column(
            Modifier
                .width(width)
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                .background(Lc.masksColumn)
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Row(Modifier.padding(start = 16.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Lc.primary)
                        .clickable(role = Role.Button) { ed.maskPicker = "new" }
                        .semantics { contentDescription = "Create new mask" },
                    contentAlignment = Alignment.Center,
                ) {
                    MdIcon(MdIcon.Add, null, tint = Color.White, size = 30.dp)
                }
                if (open) Text("Create new mask", color = Lc.panelText, fontFamily = Inter, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
            }
            ed.masks.forEach { m ->
                val selected = m.id == ed.selectedMask
                var menu by remember { mutableStateOf(false) }
                var renaming by remember { mutableStateOf(false) }
                val shown = if (m.visible) 1f else 0.4f
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clickable(role = Role.Button) { vm.selectMask(m.id) }.padding(start = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaskThumb(m, ed.info.width.toFloat(), ed.info.height.toFloat(), selected, Modifier.size(50.dp).alpha(shown))
                    if (open) {
                        Text(
                            m.name,
                            color = if (selected) Lc.panelText else Lc.panelTextSoft,
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(start = 12.dp).alpha(shown),
                        )
                        Box {
                            BarButton(LcIcon.More, "${m.name} options", size = 40.dp, tint = Lc.panelTextSoft) {
                                vm.selectMask(m.id)
                                menu = true
                            }
                            LrMenu(expanded = menu, onDismiss = { menu = false }) {
                                LrMenuItem(LcIcon.Plus, "Add to mask") {
                                    menu = false
                                    ed.maskPicker = "add"
                                }
                                LrMenuItem(LcIcon.Minus, "Subtract from mask") {
                                    menu = false
                                    ed.maskPicker = "subtract"
                                }
                                LrMenuItem(if (m.visible) MdIcon.VisibilityOff else MdIcon.Visibility, if (m.visible) "Hide" else "Show") {
                                    menu = false
                                    vm.toggleMaskVisible()
                                }
                                LrMenuItem(LcIcon.Pencil, "Rename") {
                                    menu = false
                                    renaming = true
                                }
                                LrMenuItem(LcIcon.Versions, "Duplicate") {
                                    menu = false
                                    vm.maskCommand("mask.duplicate")
                                }
                                LrMenuItem(LcIcon.Invert, "Invert") {
                                    menu = false
                                    vm.maskCommand("mask.invert")
                                }
                                LrMenuItem(LcIcon.Invert, "Duplicate and invert") {
                                    menu = false
                                    vm.duplicateInvertedMask()
                                }
                                LrMenuItem(LcIcon.Trash, "Delete") {
                                    menu = false
                                    vm.maskCommand("mask.delete")
                                }
                                LrMenuItem(LcIcon.Undo, "Reset Current Adjustments") {
                                    menu = false
                                    vm.resetMaskAdjustments()
                                }
                            }
                        }
                    }
                }
                if (renaming) {
                    NameDialog("Mask name", "Rename", initial = m.name, onDismiss = { renaming = false }) { name ->
                        renaming = false
                        vm.renameMask(name)
                    }
                }
            }
        }
    }
}

/**
 * A mask's picture: what it selects white on black, in the photo's shape (a radial's soft ellipse, a
 * linear's ramp from full to none; the others their kind's icon), framed white while it's edited.
 */
@Composable
private fun MaskThumb(m: MaskInfo, photoW: Float, photoH: Float, selected: Boolean, modifier: Modifier) {
    val s = shapeOf(m)
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Color.Black)
            // the reference's frames: a 2 dp blue one for the mask edited, 1 dp grey otherwise
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF378EF0) else Lc.chevron, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (m.kind) {
            "radial", "linear" -> Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val w = photoW.coerceAtLeast(1f)
                val h = photoH.coerceAtLeast(1f)
                val k = min(size.width / w, size.height / h)
                val pw = w * k
                val ph = h * k
                val left = (size.width - pw) / 2
                val top = (size.height - ph) / 2
                fun at(x: Double, y: Double) = Offset(left + (x * pw).toFloat(), top + (y * ph).toFloat())
                val white = if (m.invert) Color.Black else Color.White
                val black = if (m.invert) Color.White else Color.Black
                drawRect(black, Offset(left, top), Size(pw, ph))
                if (m.kind == "radial") {
                    val (cx, cy) = pt(s, "center")
                    val long = max(pw, ph)
                    val rx = (s.optDouble("rx", 0.2) * long).toFloat().coerceAtLeast(0.1f)
                    val ry = (s.optDouble("ry", 0.15) * long).toFloat()
                    val c = at(cx, cy)
                    rotate(s.optDouble("angle", 0.0).toFloat(), c) {
                        scale(1f, ry / rx, c) {
                            drawCircle(Brush.radialGradient(listOf(white, white, black), c, rx), rx, c)
                        }
                    }
                } else {
                    val (sx, sy) = pt(s, "start")
                    val (ex, ey) = pt(s, "end")
                    drawRect(Brush.linearGradient(listOf(white, black), at(sx, sy), at(ex, ey)), Offset(left, top), Size(pw, ph))
                }
            }
            else -> LcIcon(maskIcon(m.kind), null, tint = Lc.panelTextSoft, size = 24.dp)
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
        else -> "Create new Mask"
    }
    // the reference's: inset 12.6 dp from the screen's sides, a bold title over a line, then its kinds as dark cards in groups
    LcSheet(onDismiss, inset = 12.6.dp) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Lc.panelText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            BarButton(MdIcon.HelpOutline, "Help", size = 48.dp, iconSize = 24.dp, tint = Lc.panelText) { runCatching { uri.openUri(HELP_URL) } }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Lc.sectionChosen))
        Spacer(Modifier.height(16.dp))
        listOf(maskKinds.take(3), maskKinds.drop(3)).forEachIndexed { g, group ->
            if (g > 0) {
                Spacer(Modifier.height(7.dp))
                Box(Modifier.align(Alignment.CenterHorizontally).width(320.dp).height(2.dp).background(Lc.panelButtonBorder))
                Spacer(Modifier.height(7.dp))
            }
            group.forEachIndexed { i, (kind, label, _) ->
                if (i > 0) Spacer(Modifier.height(7.8.dp))
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(47.7.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Lc.panelButton)
                        .clickable(role = Role.Button) { if (op == null) vm.addMask(kind) else vm.addMaskPart(op, kind) }
                        .padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaskKindIcon(kind, Modifier.size(24.dp))
                    Spacer(Modifier.width(20.dp))
                    Text(label, color = Lc.panelText, fontFamily = Inter, fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
                    if (section == MaskSection.COLOR) {
                        item(key = "colorSwatch") {
                            MaskTint(
                                hue = ed.liveLocal["color_hue"] ?: adjust.optDouble("color_hue", 0.0),
                                sat = ed.liveLocal["color_sat"] ?: adjust.optDouble("color_sat", 0.0),
                                onChange = { h, sat ->
                                    vm.setLocal("color_hue", h)
                                    vm.setLocal("color_sat", sat)
                                },
                                onDone = vm::localDone,
                            )
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
            val shape = partShape(mask, ed.maskPart)
            val c = DevelopControl("mask.refine", "Refine", "", "", 0.0, 100.0, 50.0, 1.0, 0u, ed.liveShape["refine"] ?: shape.optDouble("refine", 50.0), SliderTrack.Plain)
            DevelopSlider(c, onChange = { vm.setShapeValue("refine", it) }, onFinished = vm::shapeDone, onReset = {})
        }
        "radial" -> {
            val shape = partShape(mask, ed.maskPart)
            val c = DevelopControl("mask.feather", "Feather", "", "", 0.0, 100.0, 50.0, 1.0, 0u, ed.liveShape["feather"] ?: shape.optDouble("feather", 50.0), SliderTrack.Plain)
            DevelopSlider(c, onChange = { vm.setShapeValue("feather", it) }, onFinished = vm::shapeDone, onReset = {})
        }
        else -> {}
    }
}

// ------------------------------------------------------------------------------------- the overlays' options

/** The mask overlay's colours (the reference's three) with their names. */
private val maskOverlayColors = listOf(0xFF0000 to "Red", 0x00FF00 to "Green", 0x0000FF to "Blue")

/** The mask overlay's modes, in the reference's order: the engine's ids and their names. */
val maskOverlayModes = listOf(
    "color" to "Color Overlay",
    "colorOnBw" to "Color Overlay on B&W",
    "imageOnBw" to "Image on B&W",
    "imageOnBlack" to "Image on Black",
    "imageOnWhite" to "Image on White",
    "whiteOnBlack" to "White on Black",
)

private val optionText = Color(0xFFEBEBEB)
private val optionDivider = Color(0xFF4B4B4B)

/**
 * Masking's options (the top bar's More while Masking is open), as the reference's sheet: the mask
 * overlay (always shown or not, its mode on a page of its own, its colour, its opacity), the tool
 * overlay (always shown or not), and Help.
 */
@Composable
fun MaskOptionsSheet(vm: AppViewModel, onHelp: () -> Unit, onDismiss: () -> Unit) {
    var modes by remember { mutableStateOf(false) }
    LcSheet(onDismiss) {
        OptionHeading("MASK OVERLAY")
        OptionSwitch("Always show", vm.maskOverlayAlways) { vm.setMaskOptions(always = it) }
        Row(
            Modifier.fillMaxWidth().height(48.2.dp).clickable(role = Role.Button) { modes = true }.padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mode", color = optionText, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(maskOverlayModes.firstOrNull { it.first == vm.maskOverlayMode }?.second.orEmpty(), color = optionText, fontFamily = Inter, fontSize = 15.sp)
            MdIcon(MdIcon.ChevronRight, null, tint = Lc.text, modifier = Modifier.padding(start = 14.dp))
        }
        Row(Modifier.fillMaxWidth().height(48.2.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Color", color = optionText, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.weight(1f))
            maskOverlayColors.forEach { (rgb, name) ->
                val on = vm.maskOverlayColor == rgb
                Canvas(
                    Modifier
                        .padding(end = 16.dp)
                        .size(48.2.dp)
                        .selectable(on, role = Role.RadioButton) { vm.setMaskOptions(color = rgb) }
                        .semantics { contentDescription = name },
                ) {
                    drawCircle(Color(0xFF000000 or rgb.toLong()), 9.7.dp.toPx())
                    if (on) drawCircle(optionText, 11.45.dp.toPx(), style = Stroke(1.9.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(48.2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity", color = optionText, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp).weight(1f))
            // the slider's own 16 dp margins put its bar 16 dp in from the sheet's edge (the reference's)
            Box(Modifier.width(224.3.dp).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
                DevelopSlider(
                    DevelopControl("mask.overlayOpacity", "Opacity", "", "", 0.0, 100.0, 50.0, 1.0, 0u, vm.maskOverlayOpacity.toDouble(), SliderTrack.Plain),
                    onChange = { vm.setMaskOptions(opacity = it.roundToInt()) },
                    onFinished = {},
                    onReset = { vm.setMaskOptions(opacity = 50) },
                    label = "",
                    modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true),
                )
            }
        }
        OptionDivider()
        OptionHeading("TOOL OVERLAY")
        OptionSwitch("Always show", vm.toolOverlayAlways) { vm.setMaskOptions(tools = it) }
        OptionDivider()
        Row(
            Modifier.padding(top = 6.dp).fillMaxWidth().height(48.2.dp).clickable(role = Role.Button, onClick = onHelp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.2.dp), contentAlignment = Alignment.Center) { MdIcon(MdIcon.HelpOutline, null, tint = optionText, size = 22.dp) }
            Text("Help", color = optionText, fontFamily = Inter, fontSize = 15.sp)
        }
        Spacer(Modifier.height(2.2.dp))
    }
    // the modes: a sheet over the options, the one shown ticked; a choice goes back to the options
    OverlayHost(modes.takeIf { it }) {
        LcSheet({ modes = false }) {
            Spacer(Modifier.height(4.8.dp))
            maskOverlayModes.forEach { (id, label) ->
                val on = vm.maskOverlayMode == id
                Row(
                    Modifier.fillMaxWidth().height(48.2.dp).selectable(on, role = Role.RadioButton) {
                        vm.setMaskOptions(mode = id)
                        modes = false
                    }.padding(start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = optionText, fontFamily = Inter, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (on) MdIcon(MdIcon.Check, "Selected", tint = optionText)
                }
            }
            Spacer(Modifier.height(4.9.dp))
        }
    }
}

/** A heading of the options, in capitals and bold, 16 dp around it. */
@Composable
private fun OptionHeading(text: String) {
    Text(text, color = optionText, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.5.sp, modifier = Modifier.padding(16.dp))
}

/** An option with a switch at its end (48 dp). */
@Composable
private fun OptionSwitch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.2.dp).toggleable(on, role = Role.Switch, onValueChange = onChange).padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = optionText, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.weight(1f))
        LcSwitch(on)
    }
}

/** The line between the options' groups: 2 dp, 32 dp in from the sides, 16 dp below what's above. */
@Composable
private fun OptionDivider() = Box(Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp).fillMaxWidth().height(2.dp).background(optionDivider))

// ------------------------------------------------------------------------------------- overlay

/**
 * The selected mask on the photo: a radial gradient's ellipse and handles, a linear gradient's
 * lines, or the brush (paint or erase with a finger), or a tap that samples a colour.
 */
@Composable
fun MaskOverlay(vm: AppViewModel, ed: EditorState, mask: MaskInfo, geometry: ViewGeometry, imageSize: IntSize, modifier: Modifier) {
    var stroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // the tool overlay (the shape's lines and handles): always, or while a finger is on the photo and a
    // moment after (the masking options' choice)
    var touching by remember { mutableStateOf(false) }
    var lingering by remember { mutableStateOf(true) }
    LaunchedEffect(touching) {
        if (touching) {
            lingering = true
        } else {
            delay(1500)
            lingering = false
        }
    }
    val tools = vm.toolOverlayAlways || touching || lingering
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
        modifier.pointerInput(Unit) {
            // watches every touch without taking it
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touching = true
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                } while (ev.changes.any { it.pressed })
                touching = false
            }
        }.pointerInput(mask.id, kind, part) {
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
        if (tools) {
            ed.liveShapeJson?.let { j -> runCatching { JSONObject(j) }.getOrNull() }?.let { drawShape(kind, it, map, geometry) } ?: drawShape(kind, current, map, geometry)
        }
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

/** The hues round the tint's wheel at full strength, from the right going clockwise on screen (hue 360 − angle). */
private val tintWheel: List<Color> = (0..12).map { i -> Color.hsv(((360 - i * 30) % 360).toFloat(), 1f, 1f) }

/**
 * A mask's colour, as the reference's: a swatch under the Color sliders (empty while there's none)
 * that tints the masked area; tapped, a wheel opens under it (its hue round it, its strength out from
 * the middle; one step a drag) with Clear to take the colour off.
 */
@Composable
private fun MaskTint(hue: Double, sat: Double, onChange: (Double, Double) -> Unit, onDone: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button) { open = !open }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Color", color = Lc.panelText, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(26.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, Lc.panelButtonBorder, RoundedCornerShape(4.dp))) {
                if (sat > 0.0) {
                    drawRect(Color.hsv(hue.toFloat().mod(360f), (sat / 100.0).toFloat().coerceIn(0f, 1f), 1f))
                } else {
                    // none: a line across the empty swatch
                    drawLine(Lc.panelTextSoft, Offset(size.width, 0f), Offset(0f, size.height), 1.5.dp.toPx())
                }
            }
        }
        if (open) {
            val change by rememberUpdatedState(onChange)
            val done by rememberUpdatedState(onDone)
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(
                    Modifier
                        .size(160.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val r = size.width / 2.0
                                fun pick(at: Offset) {
                                    val (h, s) = wheelValue(at.x - r, at.y - r, r)
                                    change(h, s)
                                }
                                pick(down.position)
                                do {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (c.pressed) {
                                        c.consume()
                                        pick(c.position)
                                    }
                                } while (c.pressed)
                                done()
                            }
                        },
                ) {
                    val r = size.minDimension / 2
                    drawCircle(Brush.sweepGradient(tintWheel), r)
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
                    if (sat > 0.0) {
                        val a = Math.toRadians(hue)
                        val d = (sat / 100.0 * r).toFloat()
                        val at = center + Offset((cos(a) * d).toFloat(), (-sin(a) * d).toFloat())
                        drawCircle(Color.White, 7.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                    }
                }
                Spacer(Modifier.weight(1f))
                PanelButton("Clear", LcIcon.Close, enabled = sat > 0.0) {
                    change(hue, 0.0)
                    done()
                }
            }
        }
    }
}
