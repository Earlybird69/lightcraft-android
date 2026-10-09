package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.core.DevelopControl
import ai.storyteller.lightcraft.core.PhotoFlag
import ai.storyteller.lightcraft.core.PresetInfo
import ai.storyteller.lightcraft.core.ProfileEntry
import ai.storyteller.lightcraft.core.SliderTrack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The colour mixer's eight bands, as the engine names them. */
val bandIds = listOf("red", "orange", "yellow", "green", "aqua", "blue", "purple", "magenta")

/**
 * A slider list's height upright, as the reference app's sheet: about two and a half sliders under
 * the buttons. The sheet stays this tall; a longer list scrolls inside it (the reference's lists are
 * scroll views in a sheet of fixed height), so pulling it up moves the sliders, not the sheet.
 */
val SheetPeek = 184.dp

/** The editor's sheet height upright; none (sideways, beside the photo): lists fill the panel. */
val LocalSheetLimit = staticCompositionLocalOf<Dp?> { null }

/** A list sized to its content up to the sheet's height, or filling the panel when there's none. */
@Composable
fun Modifier.sheetList(): Modifier {
    val max = LocalSheetLimit.current ?: return fillMaxSize()
    return fillMaxWidth().heightIn(max = max)
}

/** A row of a panel: a slider (by control id) or a sub-title, in the order the desktop lists them. */
private sealed interface PanelRow

/** [shown]: the slider appears only then (the reference app shows a setting's details once its amount is set). */
private data class SliderRow(
    val id: String,
    val label: String? = null,
    val enabled: (EditorState) -> Boolean = { true },
    val shown: (EditorState) -> Boolean = { true },
) : PanelRow

private data class TitleRow(val text: String) : PanelRow

private fun EditorState.value(id: String) = control(id)?.value ?: 0.0

/** An Edit section's panel. */
@Composable
fun SectionSheet(vm: AppViewModel, ed: EditorState, section: EditSection, modifier: Modifier) {
    Column(modifier) {
        when (section) {
            EditSection.LIGHT -> if (ed.sub == "curve") {
                CurvePanel(vm, ed)
            } else {
                PanelList(vm, ed, lightRows) {
                    item(key = "buttons") {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.End) {
                            PanelButton("Curve", LcIcon.Curve) { vm.openCurve() }
                        }
                    }
                }
            }
            EditSection.COLOR -> when (ed.sub) {
                "mix" -> {
                    SubPanelBar(if (ed.state?.blackAndWhite == true) "B&W mix" else "Mix") { ed.sub = null }
                    MixPanel(vm, ed)
                }
                "grading" -> {
                    SubPanelBar("Grading") { ed.sub = null }
                    GradingPanel(vm, ed)
                }
                else -> ColorPanel(vm, ed)
            }
            EditSection.EFFECTS -> {
                Tabs(listOf("effects" to "Effects", "vignette" to "Vignette", "grain" to "Grain"), ed.effectsTab) { ed.effectsTab = it }
                EffectsPanel(vm, ed)
            }
            EditSection.DETAIL -> {
                Tabs(listOf("sharpening" to "Sharpening", "noise" to "Noise", "color" to "Color noise"), ed.detailTab) { ed.detailTab = it }
                DetailPanel(vm, ed)
            }
            EditSection.OPTICS -> OpticsPanel(vm, ed)
            EditSection.CALIBRATION -> PanelList(vm, ed, calibrationRows)
            EditSection.AUTO, EditSection.PROFILES -> {}
        }
    }
}

private val lightRows: List<PanelRow> =
    listOf("light.exposure", "light.contrast", "light.highlights", "light.shadows", "light.whites", "light.blacks").map { SliderRow(it) }

private val curveRows: List<PanelRow> =
    listOf("curve.highlights", "curve.lights", "curve.darks", "curve.shadows").map { SliderRow(it) } +
        TitleRow("Splits") +
        listOf(SliderRow("curve.splitShadows", "Shadows"), SliderRow("curve.splitMid", "Midtones"), SliderRow("curve.splitHighlights", "Highlights")) +
        SliderRow("curve.refineSaturation")

private val calibrationRows: List<PanelRow> = listOf(TitleRow("Shadows"), SliderRow("calibration.shadowsTint", "Tint")) +
    listOf("Red" to "red", "Green" to "green", "Blue" to "blue").flatMap { (title, k) ->
        listOf(TitleRow("$title primary"), SliderRow("calibration.${k}Hue", "Hue"), SliderRow("calibration.${k}Sat", "Saturation"))
    }

/**
 * Light › Curve's panel (the curve itself is over the photo): back, the channels, then the
 * parametric curve's sliders, or the point-curve presets and a reset for the open channel.
 */
@Composable
private fun CurvePanel(vm: AppViewModel, ed: EditorState) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BarButton(LcIcon.ChevronLeft, "Back", size = 40.dp, tint = Lc.text) { ed.sub = null }
        Text("Curve", color = Lc.text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        TargetButton(ed.targeting == "curve") { vm.setTargeting(if (ed.targeting == "curve") null else "curve") }
        curveChannels.forEach { ch -> CurveChannelButton(ch, ch == ed.curveChannel) { vm.setCurveChannel(ch) } }
    }
    if (ed.curveChannel == "parametric") {
        PanelList(vm, ed, curveRows)
        return
    }
    val curve = ed.curve
    PanelList(vm, ed, listOf(SliderRow("curve.refineSaturation"))) {
        item(key = "presets") {
            if (LocalSheetLimit.current == null) {
                // sideways the panel is narrow: the buttons wrap instead of running off its edge
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    curve?.presets.orEmpty().forEach { name -> PanelButton(name, active = name == curve?.preset) { vm.applyCurvePreset(name) } }
                    PanelButton("Reset channel", LcIcon.Undo) { vm.resetCurveChannel() }
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(curve?.presets.orEmpty(), key = { it }) { name -> PanelButton(name, active = name == curve?.preset) { vm.applyCurvePreset(name) } }
                    item(key = "reset") { PanelButton("Reset channel", LcIcon.Undo) { vm.resetCurveChannel() } }
                }
            }
        }
    }
}

/** Targeted adjustment's 40 dp toggle: on, a vertical drag on the photo adjusts what's under the finger. */
@Composable
private fun TargetButton(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Switch, onClick = onClick)
            .semantics { contentDescription = if (on) "Targeted adjustment (on)" else "Targeted adjustment" },
        contentAlignment = Alignment.Center,
    ) {
        LcIcon(LcIcon.Target, null, tint = if (on) Lc.selected else Lc.text, size = 22.dp)
    }
}

/** A channel's 40 dp button: the parametric curve's icon, or a ring in the channel's colour (filled when open). */
@Composable
private fun CurveChannelButton(channel: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .drawBehind {
                if (selected) drawRect(Lc.selected, topLeft = Offset(8.dp.toPx(), size.height - 2.dp.toPx()), size = Size(size.width - 16.dp.toPx(), 2.dp.toPx()))
            }
            .clip(CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = curveChannelName(channel) },
        contentAlignment = Alignment.Center,
    ) {
        if (channel == "parametric") {
            LcIcon(LcIcon.Curve, null, tint = if (selected) Lc.text else Lc.textDim, size = 20.dp)
        } else {
            val color = curveColor(channel)
            Box(Modifier.size(16.dp).border(2.dp, color, CircleShape).padding(4.dp).background(if (selected) color else Color.Transparent, CircleShape))
        }
    }
}

/** A sub-panel's bar: back at left, its name. */
@Composable
private fun SubPanelBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BarButton(LcIcon.ChevronLeft, "Back", size = 40.dp, tint = Lc.text, onClick = onBack)
        Text(title, color = Lc.text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

/** A panel's 36 dp round button with just an icon. */
@Composable
fun PanelIconButton(icon: LcIcon, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, Lc.buttonBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        LcIcon(icon, null, tint = Lc.text, size = 20.dp)
    }
}

/** A panel's 36 dp pill, icon and label (selected: the tool highlight). */
@Composable
fun PanelButton(label: String, icon: LcIcon? = null, active: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) Lc.toolActive else Color.Transparent)
            .border(1.dp, if (active) Lc.thumb else Lc.buttonBorder, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LcIcon(icon, null, tint = Lc.text, size = 18.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(label, color = Lc.text, fontFamily = Inter, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        color = Lc.textDim,
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun Slider(vm: AppViewModel, ed: EditorState, row: SliderRow) {
    val c = ed.control(row.id) ?: return
    DevelopSlider(
        c,
        onChange = { vm.onControlChange(c, it) },
        onFinished = vm::onControlChangeFinished,
        onReset = { vm.resetControl(c) },
        label = row.label ?: c.label,
        enabled = row.enabled(ed),
    )
}

private fun LazyListScope.rows(vm: AppViewModel, ed: EditorState, rows: List<PanelRow>) {
    items(rows.filter { it !is SliderRow || it.shown(ed) }, key = { r -> if (r is SliderRow) r.id else "title:" + (r as TitleRow).text }) { r ->
        when (r) {
            is SliderRow -> Slider(vm, ed, r)
            is TitleRow -> SubTitle(r.text)
        }
    }
}

/** A panel's list: [header] items (they scroll with it, as the reference app's buttons do), then [rows]. */
@Composable
private fun PanelList(vm: AppViewModel, ed: EditorState, rows: List<PanelRow>, top: Dp = 0.dp, header: LazyListScope.() -> Unit = {}) {
    LazyColumn(Modifier.sheetList(), contentPadding = PaddingValues(top = top, bottom = 16.dp)) {
        header()
        rows(vm, ed, rows)
    }
}

/** Choices drawn as pills in a row that scrolls. */
@Composable
private fun Choices(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.first }) { (id, label) -> PanelButton(label, active = id == selected) { onSelect(id) } }
    }
}

/**
 * Text tabs, the open one bright and underlined: centred, 40 dp (Effects, Detail), or from the left,
 * 59 dp (Presets), as the reference app's.
 */
@Composable
fun Tabs(
    options: List<Pair<String, String>>,
    selected: String,
    centred: Boolean = true,
    minWidth: Dp = 86.6.dp,
    bottomPadding: Dp = if (centred) 16.dp else 0.dp,
    onSelect: (String) -> Unit,
) {
    val height = if (centred) 40.dp else 59.dp
    Row(
        Modifier.fillMaxWidth().padding(top = if (centred) 10.dp else 0.dp, bottom = bottomPadding).padding(horizontal = 4.dp).height(height),
        horizontalArrangement = if (centred) Arrangement.Center else Arrangement.Start,
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            Box(
                Modifier
                    .then(if (centred) Modifier.widthIn(min = minWidth) else Modifier)
                    .height(height)
                    // the open tab's underline, as wide as the tab (a child would widen the tab to the row)
                    .drawBehind {
                        if (on) drawRect(Lc.selected, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
                    }
                    .clickable(role = Role.Tab) { onSelect(id) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Lc.text else Lc.textDim,
                    fontFamily = Inter,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun ColorPanel(vm: AppViewModel, ed: EditorState) {
    val bw = ed.state?.blackAndWhite == true
    // rendered files have a relative Temp slider (`wb.tempRel`), raws one in Kelvin (`wb.temp`)
    val temp = if (ed.control("wb.temp") != null) "wb.temp" else "wb.tempRel"
    PanelList(vm, ed, listOf(SliderRow(temp), SliderRow("wb.tint"), SliderRow("color.vibrance"), SliderRow("color.saturation"))) {
        item(key = "buttons") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelButton("B&W", LcIcon.Invert, active = bw) { vm.toggleBlackAndWhite() }
                Spacer(Modifier.weight(1f))
                PanelButton("Grading", LcIcon.Presets) { ed.sub = "grading" }
                Spacer(Modifier.width(10.dp))
                PanelButton("Mix", LcIcon.Radial) { ed.sub = "mix" }
            }
        }
        item(key = "wb") { WhiteBalanceRow(vm, ed) }
    }
}

/** "White balance", the mode's menu and the eyedropper (tap it, then a neutral spot on the photo). */
@Composable
private fun WhiteBalanceRow(vm: AppViewModel, ed: EditorState) {
    var open by remember { mutableStateOf(false) }
    val mode = ed.state?.wbMode
    Row(Modifier.fillMaxWidth().height(19.9.dp).padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("White balance", color = Lc.text, fontFamily = Inter, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.DropdownList) { open = true }.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    vm.wbModes.firstOrNull { it.id == mode }?.label ?: if (mode == "custom") "Custom" else "As Shot",
                    color = Lc.textLabel,
                    fontFamily = Inter,
                    fontSize = 14.sp,
                )
                LcIcon(LcIcon.ChevronDown, null, tint = Lc.textLabel, size = 16.dp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Lc.bottomBar) {
                vm.wbModes.forEach { m ->
                    MenuItem(m.label) {
                        open = false
                        vm.setWhiteBalance(m.id)
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { ed.pickingWb = !ed.pickingWb }
                .semantics { contentDescription = "White balance selector" },
            contentAlignment = Alignment.Center,
        ) {
            LcIcon(LcIcon.Picker, null, tint = if (ed.pickingWb) Lc.selected else Lc.text, size = 20.dp)
        }
    }
}

@Composable
private fun MixPanel(vm: AppViewModel, ed: EditorState) {
    if (ed.state?.blackAndWhite == true) {
        PanelList(vm, ed, bandIds.map { SliderRow("bw.$it") })
        return
    }
    val band = bandIds.getOrElse(ed.mixerBand) { "red" }
    PanelList(vm, ed, listOf(SliderRow("mixer.$band.hue", "Hue"), SliderRow("mixer.$band.sat", "Saturation"), SliderRow("mixer.$band.lum", "Luminance"))) {
        // targeted adjustment: drag on the photo to move the hue, saturation or luminance of the colours there
        item(key = "target") {
            val on = ed.targeting in listOf("hue", "sat", "lum")
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TargetButton(on) { vm.setTargeting(if (on) null else "sat") }
                if (on) {
                    Choices(listOf("hue" to "Hue", "sat" to "Saturation", "lum" to "Luminance"), ed.targeting) { vm.setTargeting(it) }
                } else {
                    Text("Drag on the photo to adjust a colour", color = Lc.textDim, fontFamily = Inter, fontSize = 13.sp)
                }
            }
        }
        item(key = "bands") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Lc.bandColors.forEachIndexed { i, color ->
                    val selected = i == ed.mixerBand
                    val edited = listOf("hue", "sat", "lum").any { k -> ed.control("mixer.${bandIds[i]}.$k")?.isChanged == true }
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (selected) Lc.text else Color.Transparent, CircleShape)
                            .padding(4.dp)
                            .background(color, CircleShape)
                            .clickable(role = Role.RadioButton) { ed.mixerBand = i }
                            .semantics { contentDescription = bandIds[i] },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (edited) Box(Modifier.size(5.dp).background(Color.White, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun GradingPanel(vm: AppViewModel, ed: EditorState) {
    val range = ed.gradingRange
    val rows = listOf(SliderRow("grading.$range.lum", "Luminance"), SliderRow("grading.$range.hue", "Hue"), SliderRow("grading.$range.sat", "Saturation")) +
        TitleRow("Overall") + SliderRow("grading.blending") + SliderRow("grading.balance")
    PanelList(vm, ed, rows) {
        // the ranges stacked beside the open one's wheel: both fit the sheet's first height
        item(key = "wheel") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("shadows" to "Shadows", "midtones" to "Midtones", "highlights" to "Highlights", "global" to "Global").forEach { (id, label) ->
                        PanelButton(label, active = id == range) { ed.gradingRange = id }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GradingWheel(vm, range, ed.value("grading.$range.hue"), ed.value("grading.$range.sat"))
                }
            }
        }
    }
}

@Composable
private fun EffectsPanel(vm: AppViewModel, ed: EditorState) {
    when (ed.effectsTab) {
        "vignette" -> {
            val on = { e: EditorState -> e.value("vignette.amount") != 0.0 }
            val rows = listOf(SliderRow("vignette.amount", "Amount")) +
                listOf("vignette.midpoint", "vignette.feather", "vignette.roundness", "vignette.highlights").map { SliderRow(it, shown = on) }
            PanelList(vm, ed, rows) {
                item(key = "style") {
                    Choices(listOf("highlightPriority" to "Highlight priority", "colorPriority" to "Color priority", "paintOverlay" to "Paint overlay"), ed.state?.vignetteStyle) {
                        vm.setVignetteStyle(it)
                    }
                }
            }
        }
        "grain" -> {
            val on = { e: EditorState -> e.value("grain.amount") != 0.0 }
            PanelList(vm, ed, listOf(SliderRow("grain.amount", "Amount")) + listOf("grain.size", "grain.roughness").map { SliderRow(it, shown = on) })
        }
        else -> PanelList(vm, ed, listOf("effects.texture", "effects.clarity", "effects.dehaze").map { SliderRow(it) })
    }
}

@Composable
private fun DetailPanel(vm: AppViewModel, ed: EditorState) {
    // each tab's amount first; its details once the amount is set
    fun tab(amount: String, details: List<String>): List<PanelRow> {
        val set = { e: EditorState -> e.value(amount) > 0.0 }
        return listOf(SliderRow(amount, "Amount")) + details.map { SliderRow(it, shown = set) }
    }
    val rows = when (ed.detailTab) {
        "noise" -> tab("detail.nrLuminance", listOf("detail.nrDetail", "detail.nrContrast"))
        "color" -> tab("detail.nrColor", listOf("detail.nrColorDetail", "detail.nrColorSmoothness"))
        else -> tab("detail.sharpenAmount", listOf("detail.sharpenRadius", "detail.sharpenDetail", "detail.sharpenMasking"))
    }
    PanelList(vm, ed, rows)
}

@Composable
private fun OpticsPanel(vm: AppViewModel, ed: EditorState) {
    val st = ed.state
    val lens = st?.lensCorrections == true
    val hasLens = st?.hasLensProfile == true
    val profile = if (lens) {
        listOf(SliderRow("optics.profileDistortion", "Distortion", enabled = { hasLens }), SliderRow("optics.profileVignetting", "Vignetting", enabled = { hasLens }))
    } else {
        emptyList()
    }
    val rows = profile +
        TitleRow("Manual") +
        listOf(SliderRow("optics.distortion"), SliderRow("optics.vignetting"), SliderRow("optics.vignettingMidpoint", shown = { it.value("optics.vignetting") != 0.0 })) +
        TitleRow("Defringe") +
        SliderRow("optics.defringePurple") +
        listOf("optics.defringePurpleHueLo", "optics.defringePurpleHueHi").map { SliderRow(it, shown = { e -> e.value("optics.defringePurple") > 0.0 }) } +
        SliderRow("optics.defringeGreen") +
        listOf("optics.defringeGreenHueLo", "optics.defringeGreenHueHi").map { SliderRow(it, shown = { e -> e.value("optics.defringeGreen") > 0.0 }) } +
        TitleRow("Lateral chromatic aberration") +
        listOf(SliderRow("optics.caRed"), SliderRow("optics.caBlue"))
    PanelList(vm, ed, rows, top = 4.dp) {
        item(key = "switches") {
            Column {
                SwitchRow("Remove chromatic aberration", st?.removeCa == true) { vm.setOptics("remove_ca", it, "Remove CA") }
                SwitchRow("Enable lens corrections", lens) { vm.setOptics("lens_profile", it, "Lens Corrections") }
                if (lens && !hasLens) {
                    Text(
                        "No lens data embedded in this file (DNG lens opcodes only).",
                        color = Lc.textDim,
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

/** A 48 dp row: its label at left, a small switch at right. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Lc.text, fontFamily = Inter, fontSize = 14.sp, modifier = Modifier.weight(1f))
        LcSwitch(checked)
    }
}

/** The switch's ring when off. */
private val switchRing = Color(0xFFB0B0B0)

/**
 * A switch as the reference app draws its own (measured from its captures): an 18 dp ring the
 * colour of what's behind it on a 36 × 18 dp pill; on, both turn light blue and the ring slides to
 * the end. Only drawn: the row it sits in takes the taps (and says it's a switch).
 */
@Composable
fun LcSwitch(checked: Boolean, modifier: Modifier = Modifier, behind: Color = Lc.bar) {
    val on by animateFloatAsState(if (checked) 1f else 0f, tween(150), label = "switch")
    Canvas(modifier.size(36.dp, 18.dp)) {
        val r = size.height / 2
        drawRoundRect(lerp(Lc.divider, Lc.highlight, on), cornerRadius = CornerRadius(r))
        val centre = Offset(r + (size.width - size.height) * on, r)
        val ring = 2.dp.toPx()
        drawCircle(behind, r - ring / 2, centre)
        drawCircle(lerp(switchRing, Lc.highlight, on), r - ring / 2, centre, style = Stroke(ring))
    }
}

/** A synthetic 0–200 % slider for the strength of the last applied preset. */
private fun presetAmount(value: Float) = DevelopControl("preset.amount", "Amount", "", "", 0.0, 200.0, 100.0, 1.0, 0u, value.toDouble(), SliderTrack.Plain)

/** Presets: LightCraft's and yours, by group, each previewed on this photo; tap to apply. */
@Composable
fun PresetsSheet(vm: AppViewModel, ed: EditorState, modifier: Modifier) {
    val shown = remember(vm.presets, ed.presetTab) { vm.presets.filter { if (ed.presetTab == "yours") !it.builtin else it.builtin } }
    var amount by remember(ed.lastPreset) { mutableStateOf(ed.presetAmount) }
    Column(modifier) {
        Tabs(listOf("lightcraft" to "LightCraft", "yours" to "Yours"), ed.presetTab, centred = false) { ed.presetTab = it }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Lc.toolActive))
        ed.lastPreset?.let { p ->
            DevelopSlider(
                presetAmount(amount),
                onChange = { amount = it.toFloat() },
                onFinished = { vm.setPresetAmount(amount) },
                onReset = {
                    amount = 100f
                    vm.setPresetAmount(100f)
                },
                label = "${p.name} amount",
            )
        }
        if (shown.isEmpty()) {
            Text(
                if (ed.presetTab == "yours") "Presets you create (⋯ › Create preset) appear here." else "No presets.",
                color = Lc.textDim,
                fontFamily = Inter,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        LookGrid(Modifier.padding(top = 17.dp)) {
            shown.groupBy { it.group }.forEach { (group, presets) ->
                item(key = "group:$group", span = { GridItemSpan(maxLineSpan) }) {
                    Text(group, color = Lc.textDim, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                items(presets, key = { it.id }) { p -> PresetCell(vm, ed, p) }
            }
        }
    }
}

/** Preset and profile previews: a grid, 226 dp tall upright (the reference app's strip), scrolling inside. */
@Composable
private fun LookGrid(modifier: Modifier, content: LazyGridScope.() -> Unit) {
    val size = if (LocalSheetLimit.current == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(226.dp)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.then(size),
        content = content,
    )
}

@Composable
private fun PresetCell(vm: AppViewModel, ed: EditorState, p: PresetInfo) {
    val key = vm.presetKey(ed.info, p.id)
    val look by produceState(vm.peekLook(key), key) { vm.presetPreview(ed.info, p.id)?.let { value = it } }
    LookCell(p.name, look, selected = ed.lastPreset?.id == p.id) { vm.applyPreset(p) }
}

/** Profiles (a mode: Cancel goes back to the profile it started from): the group's name and menu, its looks. */
@Composable
fun ProfilesSheet(vm: AppViewModel, ed: EditorState, modifier: Modifier) {
    val groups = remember(vm.profiles) { vm.profiles.map { it.group }.distinct() }
    val current = ed.state?.profile
    var group by remember { mutableStateOf(vm.profiles.firstOrNull { it.id == current }?.group ?: groups.firstOrNull().orEmpty()) }
    var menu by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(group.ifEmpty { "Profiles" }, color = Lc.text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Box {
                BarButton(LcIcon.More, "Profile groups", size = 40.dp, tint = Lc.text) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Lc.bottomBar) {
                    groups.forEach { g ->
                        MenuItem(g) {
                            menu = false
                            group = g
                        }
                    }
                }
            }
        }
        if (current != null && current != "lc.color") ed.control("profile.amount")?.let { Slider(vm, ed, SliderRow("profile.amount")) }
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.profiles.filter { it.group == group }, key = { it.id }) { p ->
                Box(Modifier.width(80.dp)) { ProfileCell(vm, ed, p, selected = p.id == current) }
            }
        }
    }
}

@Composable
private fun ProfileCell(vm: AppViewModel, ed: EditorState, p: ProfileEntry, selected: Boolean) {
    val key = vm.profileKey(ed.info, p.id)
    val look by produceState(vm.peekLook(key), key) { vm.profilePreview(ed.info, p.id)?.let { value = it } }
    LookCell(p.name, look, selected) { vm.setProfile(p.id) }
}

/** A look's preview with its name under it; the one in use is outlined. */
@Composable
private fun LookCell(name: String, look: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    // its name is read once, from the text under it
    Column(Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Lc.cell)
                .border(2.dp, if (selected) Lc.selected else Color.Transparent, RoundedCornerShape(4.dp)),
        ) {
            look?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(
            name,
            color = if (selected) Lc.text else Lc.textLabel,
            fontFamily = Inter,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Rating, flags and what the photo is. */
@Composable
fun RateSheet(vm: AppViewModel, ed: EditorState, modifier: Modifier) {
    val info = ed.info
    val rating = info.rating.toInt()
    Column(modifier) {
        LazyColumn(Modifier.sheetList(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            item(key = "stars") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (stars in 1..5) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { vm.setRating(if (rating == stars) 0 else stars) },
                            contentAlignment = Alignment.Center,
                        ) {
                            LcIcon(
                                if (stars <= rating) LcIcon.StarFilled else LcIcon.Star,
                                if (stars == 1) "1 star" else "$stars stars",
                                tint = if (stars <= rating) Lc.star else Lc.icon,
                                size = 24.dp,
                            )
                        }
                    }
                }
            }
            item(key = "flags") {
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelButton("Pick", LcIcon.FlagPick, active = info.flag == PhotoFlag.PICK) {
                        vm.setFlag(if (info.flag == PhotoFlag.PICK) PhotoFlag.UNFLAGGED else PhotoFlag.PICK)
                    }
                    PanelButton("Reject", LcIcon.FlagReject, active = info.flag == PhotoFlag.REJECT) {
                        vm.setFlag(if (info.flag == PhotoFlag.REJECT) PhotoFlag.UNFLAGGED else PhotoFlag.REJECT)
                    }
                }
            }
            val facts = listOf(
                "File" to info.fileName,
                "Captured" to info.captured?.replace('T', ' '),
                "Camera" to info.camera,
                "Lens" to info.lens,
                "Size" to "${info.width} × ${info.height}",
                "Format" to (if (info.raw) "${info.format} (raw)" else info.format),
                "Title" to info.title,
            ).filter { !it.second.isNullOrBlank() }
            items(facts, key = { it.first }) { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(label, color = Lc.textDim, fontFamily = Inter, fontSize = 13.sp, modifier = Modifier.width(84.dp))
                    // a long file name breaks after a - _ or . (a zero-width space there), not mid-word
                    Text(value.orEmpty().replace(Regex("([-_.])"), "$1\u200B"), color = Lc.text, fontFamily = Inter, fontSize = 13.sp)
                }
            }
        }
    }
}
