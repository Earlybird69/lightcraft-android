package ai.storyteller.lightcraft.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * The editor, laid out as the reference app lays it out on a phone (`plan/lightroom-mobile/observed-ui.md`):
 * the photo on black under a slim top bar, then the open tool's panel, Edit's row of sections and
 * the row of tools. Sideways, the top bar spans only the photo, and the panel, the sections and the
 * tools stand as columns at its right.
 */
@Composable
fun EditorScreen(vm: AppViewModel, ed: EditorState, snackbar: SnackbarHostState) {
    var namingPreset by remember { mutableStateOf(false) }
    val createPreset = { namingPreset = true }
    // the export sheet, for sharing (true) or saving (false)
    var exportFor by remember { mutableStateOf<Boolean?>(null) }
    val onExport = { share: Boolean -> exportFor = share }
    Scaffold(containerColor = Lc.canvas, contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar, Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth > maxHeight && maxWidth >= 600.dp) {
                Column(Modifier.fillMaxSize()) {
                    StatusStrip()
                    Row(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(horizontalInsets)) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            EditorTopBar(vm, ed, createPreset, onExport)
                            PhotoView(vm, ed, Modifier.weight(1f).fillMaxWidth())
                        }
                        SideArea(vm, ed)
                    }
                }
            } else {
                val density = LocalDensity.current
                val peek = with(density) { SheetPeek.toPx() }
                val full = with(density) { (maxHeight * 0.6f).toPx() }.coerceAtLeast(peek)
                // a new panel starts at the peek height
                val limit = remember(peek, full, ed.tool, ed.section, ed.sub) { SheetLimit(peek, full) }
                Column(Modifier.fillMaxSize()) {
                    StatusStrip()
                    EditorTopBar(vm, ed, createPreset, onExport, Modifier.windowInsetsPadding(horizontalInsets))
                    PhotoView(vm, ed, Modifier.weight(1f).fillMaxWidth())
                    CompositionLocalProvider(LocalSheetLimit provides limit) {
                        Column(Modifier.fillMaxWidth().background(Lc.bar).windowInsetsPadding(horizontalInsets).nestedScroll(limit.connection)) {
                            BottomArea(vm, ed)
                        }
                    }
                    NavStrip()
                }
            }
        }
    }
    OverlayHost(exportFor) { share -> ExportSheet(vm, ed, share) { exportFor = null } }
    OverlayHost(ed.maskPicker) { picker ->
        when (picker) {
            "new" -> NewMaskSheet(vm, null) { ed.maskPicker = null }
            "add", "subtract" -> NewMaskSheet(vm, picker) { ed.maskPicker = null }
            "list" -> MasksSheet(vm, ed) { ed.maskPicker = null }
        }
    }
    if (namingPreset) PresetNameDialog(onDismiss = { namingPreset = false }) { name ->
        namingPreset = false
        vm.createPreset(name)
    }
}

private val horizontalInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)

/** The strip behind the status bar. */
@Composable
fun StatusStrip() = Spacer(Modifier.fillMaxWidth().background(Lc.statusBar).windowInsetsTopHeight(WindowInsets.statusBars))

/** The strip behind the navigation bar (or the gesture handle). */
@Composable
fun NavStrip() = Spacer(Modifier.fillMaxWidth().background(Lc.navBar).windowInsetsBottomHeight(WindowInsets.navigationBars))

/** Upright: the open tool's panel (sized to its content, see [SheetLimit]), then the rows. */
@Composable
private fun BottomArea(vm: AppViewModel, ed: EditorState) {
    val sheet = Modifier.fillMaxWidth()
    if (ed.tool == MainTool.CROP) {
        CropSheet(vm, ed, sheet)
        ModeBar("Crop", onCancel = vm::cancelCrop, onApply = vm::applyCrop)
        return
    }
    if (ed.section == EditSection.PROFILES && ed.tool == MainTool.EDIT) {
        ProfilesSheet(vm, ed, sheet)
        ModeBar("Profiles", onCancel = vm::cancelProfiles, onApply = vm::applyProfiles)
        return
    }
    val section = ed.section
    if (ed.tool == MainTool.MASKING) {
        if (ed.maskSection != null && ed.selectedMask != null) MaskSheet(vm, ed, sheet)
        MaskSectionRow(vm, ed)
        ToolRow(vm, ed)
        return
    }
    when (ed.tool) {
        MainTool.EDIT -> if (section != null) SectionSheet(vm, ed, section, sheet)
        MainTool.PRESETS -> PresetsSheet(vm, ed, sheet)
        MainTool.ACTIONS -> if (ed.action == QuickAction.RATE) RateSheet(vm, ed, sheet)
        MainTool.REMOVE -> RemoveSheet(vm, ed, sheet)
        MainTool.CROP, MainTool.MASKING, null -> {}
    }
    if (ed.tool == MainTool.EDIT) SectionRow(vm, ed)
    if (ed.tool == MainTool.ACTIONS) ActionRow(vm, ed)
    ToolRow(vm, ed)
}

/** Sideways: the open tool's 320 dp panel, Edit's sections and the tools, as columns down the right. */
@Composable
private fun SideArea(vm: AppViewModel, ed: EditorState) {
    val profiles = ed.section == EditSection.PROFILES && ed.tool == MainTool.EDIT
    val mode = profiles || ed.tool == MainTool.CROP
    val section = ed.section
    Row(Modifier.fillMaxHeight().background(Lc.bar)) {
        val panel = Modifier.width(320.dp).fillMaxHeight()
        when {
            ed.tool == MainTool.CROP -> Column(panel) {
                CropSheet(vm, ed, Modifier.fillMaxWidth().weight(1f))
                ModeBar("Crop", onCancel = vm::cancelCrop, onApply = vm::applyCrop)
                NavStrip()
            }
            ed.tool == MainTool.REMOVE -> RemoveSheet(vm, ed, panel)
            profiles -> Column(panel) {
                ProfilesSheet(vm, ed, Modifier.fillMaxWidth().weight(1f))
                ModeBar("Profiles", onCancel = vm::cancelProfiles, onApply = vm::applyProfiles)
                NavStrip()
            }
            ed.tool == MainTool.EDIT && section != null -> SectionSheet(vm, ed, section, panel)
            ed.tool == MainTool.MASKING -> MaskSheet(vm, ed, panel)
            ed.tool == MainTool.PRESETS -> PresetsSheet(vm, ed, panel)
            ed.tool == MainTool.ACTIONS && ed.action == QuickAction.RATE -> RateSheet(vm, ed, panel)
        }
        if (mode) return@Row
        if (ed.tool == MainTool.MASKING) {
            Column(Modifier.padding(start = 2.dp).width(53.5.dp).fillMaxHeight()) {
                MaskSection.entries.forEach { s ->
                    ToolButton(s.icon, s.label, showLabel = true, selected = ed.maskSection == s.name.lowercase(), modifier = Modifier.size(width = 53.5.dp, height = 68.1.dp)) {
                        vm.selectMaskSection(s.name.lowercase())
                    }
                }
            }
        }
        if (ed.tool == MainTool.ACTIONS) {
            Column(Modifier.padding(start = 2.dp).width(57.4.dp).fillMaxHeight()) {
                QuickAction.entries.forEach { a ->
                    if (a == QuickAction.RATE) Box(Modifier.padding(vertical = 3.dp).padding(horizontal = 12.dp).fillMaxWidth().height(0.8.dp).background(Lc.toolActive))
                    ToolButton(a.icon, a.label, showLabel = true, selected = ed.action == a, modifier = Modifier.size(width = 57.4.dp, height = 68.1.dp)) { vm.runAction(a) }
                }
            }
        }
        if (ed.tool == MainTool.EDIT) {
            LazyColumn(Modifier.padding(start = 2.dp).width(53.5.dp).fillMaxHeight()) {
                items(EditSection.entries, key = { it.name }) { s ->
                    if (s == EditSection.AUTO) {
                        Column {
                            SectionButton(vm, ed, s, Modifier.size(width = 53.5.dp, height = 56.4.dp))
                            Box(Modifier.padding(vertical = 3.dp).padding(horizontal = 12.dp).fillMaxWidth().height(0.8.dp).background(Lc.toolActive))
                        }
                    } else {
                        SectionButton(vm, ed, s, Modifier.size(width = 53.5.dp, height = 68.1.dp))
                    }
                }
            }
        }
        Column(Modifier.width(53.5.dp).fillMaxHeight()) {
            val labels = ed.tool == null
            MainTool.entries.forEach { t ->
                ToolButton(t.icon, t.label, showLabel = labels, selected = ed.tool == t, modifier = Modifier.size(width = 53.5.dp, height = if (labels) 68.1.dp else 53.5.dp)) {
                    vm.selectTool(t)
                }
            }
        }
    }
}

/** Back at left; share, save and more at right (none in a mode such as Profiles). */
@Composable
private fun EditorTopBar(vm: AppViewModel, ed: EditorState, onCreatePreset: () -> Unit, onExport: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var more by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val help = { runCatching { uri.openUri(HELP_URL) } }
    Column(Modifier.fillMaxWidth().background(Lc.bar).then(modifier)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            // a mode (Profiles, Crop) keeps only help, at the right, as the reference's
            if (ed.tool == MainTool.CROP || (ed.section == EditSection.PROFILES && ed.tool == MainTool.EDIT)) {
                Spacer(Modifier.weight(1f))
                BarButton(MdIcon.Help, "Help", size = 40.dp, tint = Lc.text) { help() }
                return@Row
            }
            BarButton(LcIcon.Back, "Back", size = 40.dp, tint = Lc.text, onClick = vm::closeEditor)
            Spacer(Modifier.weight(1f))
            if (ed.history.undo != null || ed.history.redo != null) {
                BarButton(LcIcon.Undo, listOfNotNull("Undo", ed.history.undo).joinToString(" "), size = 40.dp, enabled = ed.history.undo != null, tint = Lc.text, onClick = vm::undo)
                BarButton(LcIcon.Redo, listOfNotNull("Redo", ed.history.redo).joinToString(" "), size = 40.dp, enabled = ed.history.redo != null, tint = Lc.text, onClick = vm::redo)
                Spacer(Modifier.width(8.dp))
            }
            if (ed.exporting) {
                Box(Modifier.size(width = 87.7.dp, height = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Lc.text, strokeWidth = 2.dp)
                }
            } else {
                BarButton(LcIcon.Share, "Share", size = 40.dp, tint = Lc.text) { onExport(true) }
                Spacer(Modifier.width(7.7.dp))
                // Masking: show the mask over the photo (between share and save, as in the reference)
                if (ed.tool == MainTool.MASKING) {
                    BarButton(LcIcon.Radial, if (ed.showMaskOverlay) "Hide mask" else "Show mask", size = 40.dp, tint = if (ed.showMaskOverlay) Lc.selected else Lc.text, onClick = vm::toggleMaskOverlay)
                    Spacer(Modifier.width(7.7.dp))
                }
                BarButton(LcIcon.Download, "Save copy to device", size = 40.dp, tint = Lc.text) { onExport(false) }
            }
            Spacer(Modifier.width(3.9.dp))
            Box {
                BarButton(MdIcon.MoreVert, "More options", size = 40.dp, tint = Lc.text) { more = true }
                // as the reference's: 201.4 dp wide at the right edge, its rows' words 38 dp in
                DropdownMenu(expanded = more, onDismissRequest = { more = false }, containerColor = Lc.bottomBar, modifier = Modifier.width(201.4.dp)) {
                    MenuItem(start = 38.dp, text = if (ed.showHistogram) "Hide histogram" else "Show histogram") {
                        more = false
                        ed.showHistogram = !ed.showHistogram
                    }
                    MenuItem(start = 38.dp, text = "Create preset…") {
                        more = false
                        onCreatePreset()
                    }
                    MenuItem(start = 38.dp, text = "Copy settings") {
                        more = false
                        vm.copySettings()
                    }
                    MenuItem(start = 38.dp, text = "Paste settings") {
                        more = false
                        vm.pasteSettings()
                    }
                    MenuItem(start = 38.dp, text = "Apply from previous") {
                        more = false
                        vm.applyFromPrevious()
                    }
                    if (vm.view.kind == "album" && ed.device == null && vm.photos.any { it.id == ed.photoId }) {
                        MenuItem(start = 38.dp, text = "Use as album cover") {
                            more = false
                            vm.useAsAlbumCover()
                        }
                    }
                    MenuItem(start = 38.dp, text = "Help") {
                        more = false
                        help()
                    }
                    HorizontalDivider(color = Lc.toolActive)
                    MenuItem(start = 38.dp, text = "Reset all edits") {
                        more = false
                        vm.resetAll()
                    }
                    MenuItem(start = 38.dp, text = "Remove from library") {
                        more = false
                        vm.removePhoto()
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetNameDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lc.bottomBar,
        title = { Text("Create preset", fontFamily = Inter, color = Lc.text) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name", fontFamily = Inter) })
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create", fontFamily = Inter, color = Lc.selected) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Inter, color = Lc.text) } },
    )
}

@Composable
fun MenuItem(text: String, start: Dp = 12.dp, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, fontFamily = Inter, fontSize = 15.sp, color = Lc.text) }, onClick = onClick, contentPadding = PaddingValues(start = start, end = 12.dp))
}

/**
 * An icon button: [icon] (its own size, or [iconSize]) in a [size] square that presses as a circle,
 * as the reference's; [active] while its card is open (a light disc, the icon dark).
 */
@Composable
fun BarButton(icon: Glyph, description: String, enabled: Boolean = true, size: Dp = 44.dp, iconSize: Dp? = null, tint: Color = Lc.icon, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) Lc.text else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(icon, null, tint = if (active) Lc.bar else if (enabled) tint else Lc.textDisabled, size = iconSize)
    }
}

/** A tool: a 36 dp block with its icon over its label (or just the icon), blue when open, a dot when it holds edits. */
@Composable
private fun ToolButton(
    icon: LcIcon,
    label: String,
    showLabel: Boolean,
    selected: Boolean,
    edited: Boolean = false,
    modifier: Modifier = Modifier.width(53.5.dp).fillMaxHeight(),
    onClick: () -> Unit,
) {
    Column(
        // with its label showing, the label is what's read
        modifier.selectable(selected, role = Role.Tab, onClick = onClick).then(if (showLabel) Modifier else Modifier.semantics { contentDescription = label }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (showLabel) Arrangement.Top else Arrangement.Center,
    ) {
        if (showLabel) Spacer(Modifier.height(4.9.dp))
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            LcIcon(icon, null, tint = if (selected) Lc.selected else Lc.icon, size = 24.dp)
            if (edited) Box(Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp).size(5.dp).background(Lc.selected, CircleShape))
        }
        if (showLabel) {
            Text(
                label,
                color = if (selected) Lc.selected else Lc.textLabel,
                fontFamily = Inter,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.wrapContentWidth(unbounded = true).padding(horizontal = 3.dp),
            )
        }
    }
}

@Composable
private fun SectionButton(vm: AppViewModel, ed: EditorState, s: EditSection, modifier: Modifier) {
    val edited = s.sections.isNotEmpty() && ed.controls.any { it.section in s.sections && it.isChanged }
    ToolButton(s.icon, s.label, showLabel = true, selected = ed.section == s, edited = edited, modifier = modifier) { vm.selectSection(s) }
}

/** The tools, centred; labelled (68 dp) until one is open (53.5 dp), as the reference app's. */
@Composable
private fun ToolRow(vm: AppViewModel, ed: EditorState) {
    val labels = ed.tool == null
    // the bar's 1.9 dp top margin sits above the sections when they show
    val top = if (ed.tool == MainTool.EDIT || ed.tool == MainTool.ACTIONS || ed.tool == MainTool.MASKING) 0.dp else 1.9.dp
    Row(Modifier.fillMaxWidth().padding(top = top, bottom = 7.8.dp).height(if (labels) 68.1.dp else 53.5.dp), horizontalArrangement = Arrangement.Center) {
        MainTool.entries.forEach { t ->
            val width = if (labels) t.labelledWidth.dp else 53.5.dp
            ToolButton(t.icon, t.label, showLabel = labels, selected = ed.tool == t, modifier = Modifier.width(width).fillMaxHeight()) { vm.selectTool(t) }
        }
    }
}

/**
 * Actions' row (where Edit's sections go): Auto and Fix angle, then, set apart by a 6.8 dp gap as
 * the reference's second group, Rate. Each is as wide as the reference's (its label and 17.5 dp).
 */
@Composable
private fun ActionRow(vm: AppViewModel, ed: EditorState) {
    Row(Modifier.fillMaxWidth().padding(top = 1.9.dp).height(68.1.dp), horizontalArrangement = Arrangement.Center) {
        QuickAction.entries.forEach { a ->
            if (a == QuickAction.RATE) Spacer(Modifier.width(6.8.dp))
            ToolButton(a.icon, a.label, showLabel = true, selected = ed.action == a, modifier = Modifier.width(a.width.dp).fillMaxHeight()) { vm.runAction(a) }
        }
    }
}

/** Masking's sections (the edited mask's local adjustments), icon over label; shown before there's a mask, as the reference's. */
@Composable
private fun MaskSectionRow(vm: AppViewModel, ed: EditorState) {
    Row(Modifier.fillMaxWidth().padding(top = 1.9.dp).height(68.1.dp), horizontalArrangement = Arrangement.Center) {
        MaskSection.entries.forEach { s ->
            ToolButton(s.icon, s.label, showLabel = true, selected = ed.maskSection == s.name.lowercase()) { vm.selectMaskSection(s.name.lowercase()) }
        }
    }
}

/** Edit's sections, icon over label, Auto set apart from the rest by a hairline. */
@Composable
private fun SectionRow(vm: AppViewModel, ed: EditorState) {
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 1.9.dp).height(68.1.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(EditSection.entries, key = { it.name }) { s ->
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                SectionButton(vm, ed, s, Modifier.widthIn(min = 53.5.dp).fillMaxHeight())
                if (s == EditSection.AUTO) Box(Modifier.padding(horizontal = 3.dp).size(width = 1.dp, height = 28.dp).background(Lc.toolActive))
            }
        }
    }
}

/** A mode's bar: cancel at left, its name in the middle, apply at right. */
@Composable
private fun ModeBar(title: String, onCancel: () -> Unit, onApply: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(63.2.dp).padding(start = 6.8.dp, end = 6.8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BarButton(MdIcon.Close, "Cancel", size = 40.dp, tint = Lc.text, onClick = onCancel)
        Text(title, color = Lc.text, fontFamily = Inter, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        BarButton(MdIcon.Check, "Apply", size = 40.dp, tint = Lc.text, onClick = onApply)
    }
}

/**
 * The photo. Pinch to zoom (and drag when zoomed in), double-tap to zoom in or out, swipe sideways
 * for the next or previous photo, and hold a finger still on it to see it before its edits.
 */
@Composable
private fun PhotoView(vm: AppViewModel, ed: EditorState, modifier: Modifier) {
    var zoom by remember(ed.photoId) { mutableFloatStateOf(1f) }
    var offset by remember(ed.photoId) { mutableStateOf(Offset.Zero) }
    var swipe by remember(ed.photoId) { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    // the crop tool shows the whole photo, unzoomed, and takes the gestures for its frame
    val curving = ed.tool == MainTool.EDIT && ed.section == EditSection.LIGHT && ed.sub == "curve"
    val target = ed.targeting
    val targeting = target != null && ed.tool == MainTool.EDIT &&
        ((target == "curve" && curving) || (target != "curve" && ed.section == EditSection.COLOR && ed.sub == "mix"))
    val cropping = ed.tool == MainTool.CROP || ed.tool == MainTool.REMOVE || (ed.tool == MainTool.MASKING && ed.selectedMask != null) || curving || targeting
    LaunchedEffect(cropping) {
        if (cropping) {
            zoom = 1f
            offset = Offset.Zero
        }
    }
    // once a pinch settles, render the view at the new size
    LaunchedEffect(ed.photoId, zoom) {
        delay(250)
        vm.onZoom(zoom)
    }
    Box(
        modifier
            .background(Lc.canvas)
            .clipToBounds()
            .onSizeChanged { vm.onViewport(it) }
            .pointerInput(ed.photoId, cropping) {
                if (cropping) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var dx = 0f
                    var travel = 0f
                    // true: lifted (a tap), false: moved or a second finger, null: held still
                    val lifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var lifted: Boolean? = null
                        while (lifted == null) {
                            val ev = awaitPointerEvent()
                            when {
                                ev.changes.none { it.pressed } -> lifted = true
                                ev.changes.count { it.pressed } > 1 -> lifted = false
                                else -> {
                                    val pan = ev.calculatePan()
                                    dx += pan.x
                                    travel += pan.getDistance()
                                    if (travel > slop) lifted = false
                                }
                            }
                        }
                        lifted
                    }
                    if (lifted == null) {
                        vm.showBefore(true)
                        do {
                            val ev = awaitPointerEvent()
                        } while (ev.changes.any { it.pressed })
                        vm.showBefore(false)
                        return@awaitEachGesture
                    }
                    if (lifted) {
                        val image = ed.image
                        if (ed.pickingWb && image != null) {
                            photoPoint(down.position, size, image.width, image.height, zoom, offset)?.let { vm.pickWhiteBalance(it.x, it.y) }
                        }
                        return@awaitEachGesture
                    }
                    var pinched = false
                    do {
                        val ev = awaitPointerEvent()
                        val fingers = ev.changes.count { it.pressed }
                        if (fingers >= 2 || zoom > 1f) {
                            pinched = pinched || fingers >= 2
                            zoom = (zoom * ev.calculateZoom()).coerceIn(1f, 4f)
                            val pan = ev.calculatePan()
                            val maxX = size.width * (zoom - 1f) / 2f
                            val maxY = size.height * (zoom - 1f) / 2f
                            offset = if (zoom <= 1f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (!pinched) {
                            dx += ev.calculatePan().x
                            swipe = dx
                        }
                    } while (ev.changes.any { it.pressed })
                    swipe = 0f
                    if (!pinched && zoom <= 1f && abs(dx) > threshold) vm.showNeighbour(if (dx < 0) 1 else -1)
                }
            }
            .pointerInput(ed.photoId, cropping) {
                if (cropping) return@pointerInput
                detectTapGestures(onDoubleTap = {
                    zoom = if (zoom > 1f) 1f else 2.5f
                    offset = Offset.Zero
                })
            },
    ) {
        val image = ed.image
        if (image != null) {
            Image(
                image,
                contentDescription = ed.info.fileName,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x + swipe * 0.35f
                    translationY = offset.y
                },
            )
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Lc.textLabel)
        }
        if (ed.showHistogram) ed.histogram?.let { HistogramView(it, Modifier.align(Alignment.TopEnd).padding(10.dp).size(width = 120.dp, height = 54.dp)) }
        val frame = ed.crop
        if (ed.tool == MainTool.CROP && ed.showingCropView && frame != null && image != null) {
            CropOverlay(vm, frame, IntSize(image.width, image.height), Modifier.fillMaxSize())
        }
        val geometry = ed.geometry
        val mask = ed.masks.firstOrNull { it.id == ed.selectedMask }
        if (ed.tool == MainTool.MASKING && ed.maskSection != "new" && mask != null && geometry != null && image != null && !ed.showingCropView) {
            MaskOverlay(vm, ed, mask, geometry, IntSize(image.width, image.height), Modifier.fillMaxSize())
        }
        if (ed.tool == MainTool.REMOVE && !ed.showingCropView && geometry != null && image != null) {
            RemoveOverlay(vm, ed, geometry, IntSize(image.width, image.height), Modifier.fillMaxSize())
        }
        val curve = ed.curve
        // targeting, the photo is in the clear: the curve's square steps aside
        if (curving && curve != null && !targeting) CurveOverlay(vm, ed, curve, Modifier.fillMaxSize())
        if (targeting && image != null) TargetedOverlay(vm, IntSize(image.width, image.height), Modifier.fillMaxSize())
        // as the reference's: 16 dp from the right, its foot 51.6 dp above the photo's
        if (ed.tool == MainTool.MASKING) MaskButton(vm, ed, Modifier.align(Alignment.BottomEnd).padding(end = 16.1.dp, bottom = 51.6.dp))
        if (ed.pickingWb) {
            Text(
                "Tap a spot that should be neutral grey or white",
                color = Lc.text,
                fontFamily = Inter,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        if (ed.showingBefore) {
            Text(
                "Before",
                color = Lc.text,
                fontFamily = Inter,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        ed.state?.previewOnly?.let { why ->
            Text(
                "Shown from the camera's preview: $why",
                color = Lc.caution,
                fontFamily = Inter,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Targeted adjustment over the photo: press on what to adjust, then drag up to raise it or down to
 * lower it (half a slider unit a dp, as the desktop's); one undo step a drag.
 */
@Composable
private fun TargetedOverlay(vm: AppViewModel, imageSize: IntSize, modifier: Modifier) {
    // read when a finger goes down: the drag's draft renders change the image's size
    val currentSize by rememberUpdatedState(imageSize)
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val image = currentSize
                val at = photoPoint(down.position, size, image.width, image.height, 1f, Offset.Zero) ?: return@awaitEachGesture
                down.consume()
                vm.beginTargeted()
                var acc = 0.0
                var last = down.position.y
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        acc -= (change.position.y - last) / density * 0.5
                        last = change.position.y
                        val step = acc.toInt()
                        if (step != 0) {
                            acc -= step
                            vm.targeted(at.x, at.y, step.toDouble())
                        }
                    }
                } finally {
                    vm.endTargeted()
                }
            }
        },
    ) {
        Text(
            "Drag up or down on what to adjust",
            color = Lc.text,
            fontFamily = Inter,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * Where [p], a point of the view, falls on the photo: 0–1 across and down (null off the photo).
 * The photo is fitted to the view, then zoomed by [zoom] about the view's centre and moved by [pan].
 * The view shows the whole photo (the phone has no crop yet), so this is a point of the photo itself.
 */
internal fun photoPoint(p: Offset, view: IntSize, imageWidth: Int, imageHeight: Int, zoom: Float, pan: Offset): Offset? {
    if (view.width <= 0 || view.height <= 0 || imageWidth <= 0 || imageHeight <= 0 || zoom <= 0f) return null
    val c = Offset(view.width / 2f, view.height / 2f)
    val q = (p - c - pan) / zoom + c
    val fit = min(view.width / imageWidth.toFloat(), view.height / imageHeight.toFloat())
    val w = imageWidth * fit
    val h = imageHeight * fit
    val x = (q.x - (view.width - w) / 2f) / w
    val y = (q.y - (view.height - h) / 2f) / h
    return if (x in 0f..1f && y in 0f..1f) Offset(x, y) else null
}
