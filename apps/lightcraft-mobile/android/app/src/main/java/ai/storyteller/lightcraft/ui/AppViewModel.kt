package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.LightcraftApplication
import ai.storyteller.lightcraft.core.AlbumInfo
import ai.storyteller.lightcraft.core.Choice
import ai.storyteller.lightcraft.core.CropFrame
import ai.storyteller.lightcraft.core.DevelopControl
import ai.storyteller.lightcraft.core.DevelopState
import ai.storyteller.lightcraft.core.EditHistory
import ai.storyteller.lightcraft.core.ImageHistogram
import ai.storyteller.lightcraft.core.ImageSize
import ai.storyteller.lightcraft.core.ImportReport
import ai.storyteller.lightcraft.core.LibraryOverview
import ai.storyteller.lightcraft.core.MaskInfo
import ai.storyteller.lightcraft.core.MobileSession
import ai.storyteller.lightcraft.core.PhotoFlag
import ai.storyteller.lightcraft.core.PhotoInfo
import ai.storyteller.lightcraft.core.PresetInfo
import ai.storyteller.lightcraft.core.ProfileEntry
import ai.storyteller.lightcraft.core.SpotInfo
import ai.storyteller.lightcraft.core.ToneCurveInfo
import ai.storyteller.lightcraft.core.ViewGeometry
import ai.storyteller.lightcraft.core.engineVersion
import ai.storyteller.lightcraft.core.whiteBalanceModes
import ai.storyteller.lightcraft.engine.DeviceAccess
import ai.storyteller.lightcraft.engine.DeviceMedia
import ai.storyteller.lightcraft.engine.DevicePhoto
import ai.storyteller.lightcraft.engine.Engine
import ai.storyteller.lightcraft.engine.PhotoFiles
import ai.storyteller.lightcraft.engine.toImageBitmap
import android.app.Application
import android.net.Uri
import android.os.StatFs
import android.util.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A snackbar message, with an optional action. */
class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/**
 * The editor's main tools along the bottom, the reference app's row. Labelled, a tool is as wide as
 * the reference's (its label and 17.5 dp, at least 53.5 dp), so they land on its bounds.
 */
enum class MainTool(val label: String, val icon: LcIcon, val labelledWidth: Float = 53.5f) {
    ACTIONS("Actions", LcIcon.Actions),
    PRESETS("Presets", LcIcon.Presets),
    CROP("Crop", LcIcon.Crop),
    EDIT("Edit", LcIcon.Sliders),
    MASKING("Masking", LcIcon.Radial, 55.9f),
    REMOVE("Remove", LcIcon.Heal, 55.4f),
}

/**
 * Actions' row, where Edit's sections go: one-tap edits. The reference's others (enhance, subject,
 * background) need AI models the phone doesn't have; Rate (stars, flags and the photo's facts) is
 * LightCraft's own, after a gap as the reference sets its second group apart.
 */
enum class QuickAction(val label: String, val icon: LcIcon, val width: Float = 53.5f) {
    AUTO("Auto", LcIcon.Auto),
    FIX_ANGLE("Fix angle", LcIcon.FixAngle, 57.4f),
    RATE("Rate", LcIcon.Star),
}

/** Edit's sections, the row above the tools when Edit is open. */
enum class EditSection(val label: String, val icon: LcIcon, val sections: List<String>) {
    AUTO("Auto", LcIcon.Auto, emptyList()),
    LIGHT("Light", LcIcon.Light, listOf("light", "curve")),
    COLOR("Color", LcIcon.Color, listOf("color", "mixer", "bwMix", "grading")),
    EFFECTS("Effects", LcIcon.Effects, listOf("effects", "vignette", "grain")),
    DETAIL("Detail", LcIcon.Detail, listOf("detail")),
    OPTICS("Optics", LcIcon.Optics, listOf("optics")),
    CALIBRATION("Calibration", LcIcon.Calibration, listOf("calibration")),
    PROFILES("Profiles", LcIcon.ProfileGrid, listOf("profile")),
}

/** A photo opened from the phone's gallery: which, and whether this visit added its copy to the library. */
class DeviceOrigin(val photo: DevicePhoto, val added: Boolean)

/** The editor, for the photo being edited ([device]: opened from the Device tab). */
@Stable
class EditorState(val photoId: ULong, info: PhotoInfo, val device: DeviceOrigin? = null) {
    var info by mutableStateOf(info)
    var controls by mutableStateOf<List<DevelopControl>>(emptyList())
    var state by mutableStateOf<DevelopState?>(null)

    /** The open main tool (none: just the photo and the tool row). */
    var tool by mutableStateOf<MainTool?>(null)

    /** Actions' open panel (Rate's), if any. */
    var action by mutableStateOf<QuickAction?>(null)

    /** Edit's open section (none: just its row of sections). */
    var section by mutableStateOf<EditSection?>(null)

    /** Profiles is a mode with Cancel and Apply: the profile it started from, to go back to. */
    var profileFrom by mutableStateOf<String?>(null)

    /** A panel's sub-panel: `curve` (Light), `mix` and `grading` (Color). */
    var sub by mutableStateOf<String?>(null)
    var detailTab by mutableStateOf("sharpening")

    /** Light › Curve: the channel shown (`parametric`, `master`, `red`, `green`, `blue`), the curves, the points while a finger moves one. */
    var curveChannel by mutableStateOf("parametric")
    var curve by mutableStateOf<ToneCurveInfo?>(null)
    var liveCurve by mutableStateOf<List<Pair<Double, Double>>?>(null)
    var effectsTab by mutableStateOf("effects")

    /** The white balance selector is armed: the next tap on the photo picks a neutral spot. */
    var pickingWb by mutableStateOf(false)

    /** Targeted adjustment is on: a vertical drag on the photo moves `curve` (Light › Curve) or the mixer's `hue`, `sat` or `lum` (Color › Mix) there. */
    var targeting by mutableStateOf<String?>(null)

    /** Crop mode: the crop as the engine has it and the panel's tab. */
    var crop by mutableStateOf<CropFrame?>(null)
    var cropTab by mutableStateOf("aspect")

    /** Where the history was as a mode (Crop, Profiles) opened: its Cancel takes back every step after it. */
    internal var modeMark: ULong? = null

    /** Remove: the spots, the one picked, the view ↔ photo map and the brush. */
    var spots by mutableStateOf<List<SpotInfo>>(emptyList())
    var selectedSpot by mutableStateOf<Int?>(null)
    var geometry by mutableStateOf<ViewGeometry?>(null)
    var removeMode by mutableStateOf("remove")
    var brushSize by mutableFloatStateOf(0.02f)
    var brushFeather by mutableFloatStateOf(50f)
    var brushOpacity by mutableFloatStateOf(100f)

    /** Masking: the masks, the one being edited, what its panel shows (`new`, `mask`, or a section: `light`…), the brush. */
    var masks by mutableStateOf<List<MaskInfo>>(emptyList())
    var selectedMask by mutableStateOf<UInt?>(null)

    /** The part of the edited mask its handles and settings edit (an index of its parts). */
    var maskPart by mutableIntStateOf(0)
    var maskSection by mutableStateOf<String?>(null)
    var maskErase by mutableStateOf(false)
    var maskBrushSize by mutableFloatStateOf(0.03f)
    var maskBrushFeather by mutableFloatStateOf(50f)
    var maskBrushFlow by mutableFloatStateOf(100f)

    /** The mask tinted over the photo (the top bar's toggle). */
    var showMaskOverlay by mutableStateOf(false)

    /** Masking's sheet over the editor: `new` (create a mask) or `list` (the photo's masks), if open. */
    var maskPicker by mutableStateOf<String?>(null)

    /** Values while a finger moves them (before the engine has them back): local sliders, a shape's numbers, a dragged shape. */
    var liveLocal by mutableStateOf<Map<String, Double>>(emptyMap())
    var liveShape by mutableStateOf<Map<String, Double>>(emptyMap())
    var liveShapeJson by mutableStateOf<String?>(null)

    /** The view shows the photo uncropped (the crop tool's): its frame is drawn over it. */
    var showingCropView by mutableStateOf(false)
    var presetTab by mutableStateOf("lightcraft")
    var mixerBand by mutableIntStateOf(0)
    var gradingRange by mutableStateOf("midtones")
    var lastPreset by mutableStateOf<PresetInfo?>(null)
    var presetAmount by mutableFloatStateOf(100f)
    var image by mutableStateOf<ImageBitmap?>(null)
    var histogram by mutableStateOf<ImageHistogram?>(null)
    var showHistogram by mutableStateOf(false)
    var showingBefore by mutableStateOf(false)
    var history by mutableStateOf(EditHistory(null, null))
    var exporting by mutableStateOf(false)
    var rendering by mutableStateOf(false)
    internal var viewport = IntSize.Zero
    internal var zoom = 1f

    /** The view has the photo's own render (not a stand-in). */
    internal var rendered = false

    /** The control being dragged: an engine interaction is open for it (one undo step). */
    internal var adjusting: String? = null

    fun control(id: String): DevelopControl? = controls.firstOrNull { it.id == id }
}

/** Rendered images by key, at most [bytes] of them (grid thumbnails, preset and profile looks). */
class BitmapCache(bytes: Int) {
    private val cache = object : LruCache<String, ImageBitmap>(bytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(key: String, render: suspend () -> ImageBitmap?): ImageBitmap? = peek(key) ?: render()?.also { cache.put(key, it) }

    fun clear() = cache.evictAll()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val lightcraft = app as LightcraftApplication

    var engine by mutableStateOf<Engine?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var photos by mutableStateOf<List<PhotoInfo>>(emptyList())
        private set
    var importing by mutableStateOf(false)
        private set
    var editor by mutableStateOf<EditorState?>(null)
        private set
    var presets by mutableStateOf<List<PresetInfo>>(emptyList())
        private set
    var profiles by mutableStateOf<List<ProfileEntry>>(emptyList())
        private set
    val wbModes: List<Choice> = runCatching { whiteBalanceModes() }.getOrDefault(emptyList())

    private val prefs = app.getSharedPreferences("library", Application.MODE_PRIVATE)

    /** What the grid shows (the title's menu). */
    var view by mutableStateOf(LibraryView.ALL)
        private set

    /** The filter sheet's choices and the search words. */
    var filter by mutableStateOf(LibraryFilter())
        private set

    /** A search is being typed (its sent words are the filter's, or on the Device tab [deviceQuery]). */
    var searching by mutableStateOf(false)
    var deviceQuery by mutableStateOf("")

    /** The sort sheet's key (as the engine names it) and direction. */
    var sortKey by mutableStateOf("captureDate")
        private set
    var newestFirst by mutableStateOf(true)
        private set

    /** View options, kept between launches: a justified or a square grid, and its segments. */
    var justified by mutableStateOf(prefs.getBoolean("justified", true))
        private set
    var segmentBy by mutableStateOf(prefs.getString("segmentBy", "none") ?: "none")
        private set

    /** Ratings, flags and the edited mark on the grid's cells (off: clean cells, as the reference app). */
    var showBadges by mutableStateOf(prefs.getBoolean("showBadges", false))
        private set

    /** What Save a copy and Share write (the export sheet's choices). */
    var exportChoice by mutableStateOf(loadExportChoice())
        private set

    private fun loadExportChoice(): ExportChoice {
        val o = runCatching { JSONObject(prefs.getString("export", null) ?: "{}") }.getOrDefault(JSONObject())
        val d = ExportChoice()
        return ExportChoice(
            format = o.optString("format", d.format).takeIf { f -> exportFormats.any { it.first == f } } ?: d.format,
            longEdge = o.optInt("longEdge", d.longEdge).coerceAtLeast(0),
            quality = o.optInt("quality", d.quality).coerceIn(10, 100),
            metadata = o.optString("metadata", d.metadata).takeIf { m -> exportMetadata.any { it.first == m } } ?: d.metadata,
            removeLocation = o.optBoolean("removeLocation", d.removeLocation),
        )
    }

    /** Counts of the library's views, the albums and what the filter can pick from. */
    var overview by mutableStateOf<LibraryOverview?>(null)
        private set

    /** The home screen's tab: `device` (the phone's gallery) or `library` (LightCraft's), kept between launches. */
    var homeTab by mutableStateOf(prefs.getString("homeTab", "library") ?: "library")
        private set

    /** What the Device tab may see of the gallery, its photos, and the album (folder) it shows (null: all). */
    var deviceAccess by mutableStateOf(DeviceAccess.NONE)
        private set
    var devicePhotos by mutableStateOf<List<DevicePhoto>>(emptyList())
        private set
    var deviceAlbum by mutableStateOf<String?>(null)
        private set

    /** The Device tab's photos: its album's, newest first. */
    val shownDevicePhotos: List<DevicePhoto>
        get() = deviceAlbum?.let { a -> devicePhotos.filter { it.album == a } } ?: devicePhotos

    /** The gallery's thumbnails. */
    private val deviceThumbs = BitmapCache(32 shl 20)

    /** Select mode (batch edit): the photos picked, by id. */
    var selecting by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Set<ULong>>(emptySet())
        private set

    /** Photos (and looks, and sizes) already prepared for the editor. */
    private val prefetched = HashSet<String>()

    /** Grid thumbnails by photo and look (an edit is a new key). */
    val thumbnails = BitmapCache(48 shl 20)

    /** Preset and profile previews of the edited photo. */
    private val looks = BitmapCache(32 shl 20)
    val messages = Channel<UiMessage>(Channel.BUFFERED)

    private data class ViewRequest(val photo: ULong, val width: Int, val height: Int, val draft: Boolean, val before: Boolean, val crop: Boolean, val mask: UInt?, val seq: Long)

    /** The latest view the editor wants: a busy render loop skips straight to the newest. */
    private val viewRequests = MutableStateFlow<ViewRequest?>(null)
    private var requestSeq = 0L

    init {
        viewModelScope.launch {
            lightcraft.engine.await()
                .onSuccess { e ->
                    engine = e
                    e.problem?.let { say("The library couldn't be opened, so this is the demo (nothing is saved): $it") }
                    // the order the library was left in (the engine keeps it with the library)
                    runCatching { JSONObject(e.command { execute("library.state", "") }).optJSONObject("sort") }.getOrNull()?.let {
                        newestFirst = !it.optBoolean("ascending", false)
                        sortKey = it.optString("key", "captureDate").takeIf { k -> sortKeys.any { (id, _) -> id == k } } ?: "captureDate"
                    }
                    refreshPhotos()
                    runCatching { e.command { presets() to profiles() } }.onSuccess { (pr, pf) ->
                        presets = pr
                        profiles = pf
                    }
                    renderViews(e)
                }
                .onFailure { loadError = it.message ?: it.toString() }
        }
    }

    private fun say(text: String?, actionLabel: String? = null, action: (() -> Unit)? = null) {
        messages.trySend(UiMessage(text ?: "Something went wrong", actionLabel, action))
    }

    /**
     * [text] with an Undo for the step whose mark is [depth] (`historyDepth()` right after it): it
     * takes back that step, never a later one.
     */
    private fun sayUndoable(text: String, depth: ULong) {
        val e = engine ?: return
        say(text, "Undo") {
            viewModelScope.launch {
                val undone = runCatching { e.command { undoAt(depth) } }.getOrDefault(false)
                if (!undone) say("Can't undo that now: other changes came after it")
                refreshPhotos()
            }
        }
    }

    private suspend fun refreshPhotos() {
        val e = engine ?: return
        runCatching { e.command { photos() to libraryOverview() } }.onSuccess { (p, o) ->
            photos = p
            overview = o
            // photos that left the view leave the selection
            if (selected.isNotEmpty()) selected = selected.intersect(p.map { it.id }.toSet())
        }
    }

    /** The size [ed]'s photo exports at with [choice], its crop and size applied (null: unknown). */
    suspend fun exportSize(ed: EditorState, choice: ExportChoice): ImageSize? {
        val e = engine ?: return null
        return runCatching { e.command { exportSize(ed.photoId, choice.json()) } }.getOrNull()
    }

    /** A photo by id (an album's cover, say), when the library has it. */
    suspend fun photoInfo(id: ULong): PhotoInfo? {
        val e = engine ?: return null
        return runCatching { e.command { photo(id) } }.getOrNull()
    }

    fun thumbnailKey(p: PhotoInfo) = "${p.id}:${p.settingsHash}"

    suspend fun thumbnail(photo: PhotoInfo): ImageBitmap? {
        val e = engine ?: return null
        return thumbnails.load(thumbnailKey(photo)) {
            withContext(e.thumbnails) {
                runCatching { e.session.renderThumbnail(photo.id, THUMB_EDGE.toUInt()).toImageBitmap() }.getOrNull()
            }
        }
    }

    fun presetKey(p: PhotoInfo, preset: String) = "preset:${p.id}:${p.settingsHash}:$preset"

    fun profileKey(p: PhotoInfo, profile: String) = "profile:${p.id}:${p.settingsHash}:$profile"

    fun peekLook(key: String): ImageBitmap? = looks.peek(key)

    /** The edited photo with preset [preset] applied, as the preset browser shows it. */
    suspend fun presetPreview(photo: PhotoInfo, preset: String): ImageBitmap? {
        val e = engine ?: return null
        return looks.load(presetKey(photo, preset)) {
            withContext(e.thumbnails) {
                runCatching { e.session.renderPresetThumbnail(photo.id, preset, LOOK_EDGE.toUInt()).toImageBitmap() }.getOrNull()
            }
        }
    }

    /** The edited photo with profile [profile] instead of its own. */
    suspend fun profilePreview(photo: PhotoInfo, profile: String): ImageBitmap? {
        val e = engine ?: return null
        return looks.load(profileKey(photo, profile)) {
            withContext(e.thumbnails) {
                runCatching { e.session.renderProfileThumbnail(photo.id, profile, LOOK_EDGE.toUInt()).toImageBitmap() }.getOrNull()
            }
        }
    }

    // ------------------------------------------------------------------ library

    /** Import picked or shared documents: copied next to the library, then moved into it. */
    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importFiles { e -> PhotoFiles.stage(getApplication(), uris, e.stagingDir) }
    }

    /** A photo the camera app has just written to [file] (one of ours): moved into the library. */
    fun importShot(file: File) = importFiles { listOf(file) }

    /** Something to tell the person, from outside the view model (the camera couldn't open, say). */
    fun tell(text: String) = say(text)

    private fun importFiles(stage: (Engine) -> List<File>) {
        viewModelScope.launch {
            val e = lightcraft.engine.await().getOrNull() ?: return@launch
            importing = true
            val result = runCatching {
                val files = withContext(Dispatchers.IO) { stage(e) }
                if (files.isEmpty()) error("the selected files couldn't be read")
                try {
                    e.command { `import`(files.map { it.absolutePath }, true) }
                } finally {
                    // duplicates and failures stay where they were copied: tidy up
                    withContext(Dispatchers.IO) { files.forEach { it.delete() } }
                }
            }
            importing = false
            refreshPhotos()
            result.onSuccess { say(importSummary(it)) }.onFailure { say("Import failed: ${it.message}") }
        }
    }

    // ------------------------------------------------------------------ slideshow

    /** The slideshow's photo (an index into [photos]), or null when it isn't running; what it shows. */
    var slideshow by mutableStateOf<Int?>(null)
        private set
    var slideshowImage by mutableStateOf<ImageBitmap?>(null)
        private set
    private var slideSize = IntSize.Zero

    /** The grid's photos full screen, from the first (more › slideshow). */
    fun startSlideshow() {
        if (photos.isEmpty()) return
        endSelecting()
        slideshowImage = null
        slideshow = 0
        showSlide(0)
    }

    fun stopSlideshow() {
        slideshow = null
        slideshowImage = null
    }

    /** The next (+1) or previous (−1) photo, wrapping. */
    fun stepSlideshow(step: Int) {
        val i = slideshow ?: return
        val n = photos.size
        if (n == 0) return stopSlideshow()
        val next = ((i + step) % n + n) % n
        slideshow = next
        showSlide(next)
    }

    fun onSlideshowSize(size: IntSize) {
        if (size == slideSize) return
        slideSize = size
        slideshow?.let { showSlide(it) }
    }

    /** Photo [i]: its grid thumbnail at once, then its render at the screen's size; the next one rendered ahead. */
    private fun showSlide(i: Int) {
        val e = engine ?: return
        val p = photos.getOrNull(i) ?: return
        thumbnails.peek(thumbnailKey(p))?.let { slideshowImage = it }
        val size = slideSize.takeIf { it.width > 0 && it.height > 0 } ?: return
        viewModelScope.launch {
            val img = withContext(e.viewRenders) {
                runCatching { e.session.renderView(p.id, size.width.toUInt(), size.height.toUInt(), false, false, null).toImageBitmap() }.getOrNull()
            }
            if (slideshow == i && img != null) slideshowImage = img
            photos.getOrNull((i + 1) % photos.size)?.let { n ->
                launch(e.thumbnails) { runCatching { e.session.prefetchView(n.id, size.width.toUInt(), size.height.toUInt()) } }
            }
        }
    }

    // ------------------------------------------------------------------ the phone's gallery

    fun showHomeTab(tab: String) {
        homeTab = tab
        prefs.edit().putString("homeTab", tab).apply()
        endSelecting()
        if (tab == "device") refreshDevice()
    }

    fun showDeviceAlbum(album: String?) {
        deviceAlbum = album
    }

    /** Read the gallery again: when the tab shows, after the permission changes, back in the app. */
    fun refreshDevice() {
        val app = getApplication<Application>()
        val access = DeviceMedia.access(app)
        deviceAccess = access
        if (access == DeviceAccess.NONE) {
            devicePhotos = emptyList()
            return
        }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { DeviceMedia.load(app) }.getOrDefault(emptyList()) }
            devicePhotos = list
            if (deviceAlbum != null && list.none { it.album == deviceAlbum }) deviceAlbum = null
        }
    }

    fun peekDeviceThumbnail(p: DevicePhoto): ImageBitmap? = deviceThumbs.peek("${p.id}")

    suspend fun deviceThumbnail(p: DevicePhoto): ImageBitmap? =
        deviceThumbs.load("${p.id}") { withContext(Dispatchers.IO) { DeviceMedia.thumbnail(getApplication(), p, THUMB_EDGE)?.asImageBitmap() } }

    /**
     * Open a photo of the phone's gallery in the editor. LightCraft edits what's in its library, so
     * a copy goes in first (opened again, the same copy is found); left untouched, that copy is
     * taken out again, as the reference app keeps a gallery photo only once it's edited.
     */
    fun openDevicePhoto(p: DevicePhoto) {
        val e = engine ?: return
        if (importing) return
        viewModelScope.launch {
            importing = true
            val result = runCatching {
                val files = withContext(Dispatchers.IO) { PhotoFiles.stage(getApplication(), listOf(p.uri), e.stagingDir) }
                if (files.isEmpty()) error("it couldn't be read")
                try {
                    e.command { `import`(files.map { it.absolutePath }, true) }
                } finally {
                    withContext(Dispatchers.IO) { files.forEach { it.delete() } }
                }
            }
            importing = false
            val report = result.getOrElse {
                say("Can't open ${p.name}: ${it.message}")
                return@launch
            }
            val added = report.imported.firstOrNull()
            val id = added ?: report.existing.firstOrNull()
            if (id == null) {
                say("Can't open ${p.name}: ${report.failed.firstOrNull() ?: "LightCraft can't read it"}")
                return@launch
            }
            refreshPhotos()
            val info = runCatching { e.command { photo(id) } }.getOrNull() ?: return@launch
            openEditor(info, DeviceOrigin(p, added != null))
        }
    }

    /** Leaving [ed]: a gallery photo's copy that this visit added and that wasn't touched goes again. */
    private suspend fun discardUntouched(ed: EditorState) {
        val origin = ed.device ?: return
        if (!origin.added) return
        val e = engine ?: return
        val info = runCatching { e.command { photo(ed.photoId) } }.getOrNull() ?: return
        if (info.edited || info.rating > 0u || info.flag != PhotoFlag.UNFLAGGED) return
        runCatching { e.command { discardPhoto(ed.photoId) } }
        refreshPhotos()
    }

    /** Show [v] (a view of the library or an album); the filter stays. */
    fun showView(v: LibraryView) {
        val e = engine ?: return
        view = v
        endSelecting()
        val source = JSONObject().put(
            "kind",
            when (v.kind) {
                "recentlyAdded" -> "recentlyAdded"
                "deleted" -> "recentlyDeleted"
                "album" -> "album"
                else -> "all"
            },
        )
        v.albumId?.let { source.put("id", it.toLong()) }
        viewModelScope.launch {
            runCatching { e.command { execute("library.source", source.toString()) } }.onFailure { say(it.message) }
            applyFilter()
        }
    }

    fun updateFilter(f: LibraryFilter) {
        filter = f
        viewModelScope.launch { applyFilter() }
    }

    /** The filter sheet's choices, the search words and the view's own condition (edited or not), as one engine filter. */
    private suspend fun applyFilter() {
        val e = engine ?: return
        val f = filter
        val json = JSONObject()
        if (f.rating > 0) json.put("rating", f.rating).put("ratingOp", f.ratingOp)
        f.flag?.let { json.put("flag", it) }
        f.kind?.let { json.put("kind", it) }
        f.camera?.let { json.put("camera", it) }
        f.person?.let { json.put("person", it) }
        f.place?.let { json.put("place", it) }
        f.keyword?.let { json.put("keyword", it) }
        if (f.text.isNotBlank()) json.put("text", f.text.trim())
        val edited = f.edited ?: when (view.kind) {
            "edited" -> true
            "unedited" -> false
            else -> null
        }
        edited?.let { json.put("edited", it) }
        runCatching {
            e.command {
                execute("library.clearFilter", "{}")
                if (json.length() > 0) execute("library.filter", json.toString())
            }
        }.onFailure { say(it.message) }
        refreshPhotos()
    }

    /** Sort by [key] (the sort sheet's keys), keeping the direction. */
    fun sortBy(key: String) {
        sortKey = key
        applySort()
    }

    /** The sort sheet's Reverse order. */
    fun reverseSort() {
        newestFirst = !newestFirst
        applySort()
    }

    private fun applySort() {
        val e = engine ?: return
        val params = JSONObject().put("key", sortKey).put("ascending", !newestFirst).put("group", "none").toString()
        viewModelScope.launch {
            runCatching { e.command { execute("library.sort", params) } }.onFailure { say(it.message) }
            refreshPhotos()
        }
    }

    fun setJustifiedGrid(on: Boolean) {
        justified = on
        prefs.edit().putBoolean("justified", on).apply()
    }

    /** `none`, `year`, `month`, `day` or `hour`. */
    fun setSegments(unit: String) {
        segmentBy = unit
        prefs.edit().putString("segmentBy", unit).apply()
    }

    fun setBadges(on: Boolean) {
        showBadges = on
        prefs.edit().putBoolean("showBadges", on).apply()
    }

    /** A new album named [name], with the selected photos in it when selecting. */
    fun createAlbum(name: String) {
        val e = engine ?: return
        if (name.isBlank()) return
        val ids = selected.toList()
        viewModelScope.launch {
            runCatching {
                e.command {
                    val made = JSONObject(execute("album.create", JSONObject().put("name", name.trim()).toString()))
                    val id = made.optLong("id", -1L)
                    if (ids.isNotEmpty() && id >= 0) {
                        val list = JSONArray(ids.map { it.toLong() })
                        execute("library.select", JSONObject().put("ids", list).toString())
                        execute("album.addPhotos", JSONObject().put("id", id).put("ids", list).toString())
                    }
                }
            }.onSuccess {
                // made from the selection: done with it, as when adding to an album
                if (ids.isNotEmpty()) endSelecting()
                say("Album “${name.trim()}” created")
            }.onFailure { say(it.message) }
            refreshPhotos()
        }
    }

    /** Rename album [a]; the title follows when it's the album on show. */
    fun renameAlbum(a: AlbumInfo, name: String) {
        val e = engine ?: return
        val n = name.trim()
        if (n.isEmpty() || n == a.name) return
        viewModelScope.launch {
            runCatching { e.command { execute("album.rename", JSONObject().put("id", a.id.toLong()).put("name", n).toString()) } }
                .onSuccess { if (view.albumId == a.id) view = LibraryView.album(a.id, n) }
                .onFailure { say(it.message) }
            refreshPhotos()
        }
    }

    /** Delete album [a]: its photos stay in the library (Undo brings the album back). On show, the view goes to all photos. */
    fun deleteAlbum(a: AlbumInfo) {
        val e = engine ?: return
        viewModelScope.launch {
            runCatching {
                e.command {
                    execute("album.delete", JSONObject().put("id", a.id.toLong()).toString())
                    historyDepth()
                }
            }
                .onSuccess { depth ->
                    if (view.albumId == a.id) showView(LibraryView.ALL)
                    sayUndoable("Deleted “${a.name}”", depth)
                }
                .onFailure { say(it.message) }
            refreshPhotos()
        }
    }

    /** The photo in the editor becomes the cover of the album on show. */
    fun useAsAlbumCover() {
        val ed = editor ?: return
        val e = engine ?: return
        val album = view.albumId ?: return
        viewModelScope.launch {
            runCatching {
                e.command {
                    execute("album.setCover", JSONObject().put("id", album.toLong()).put("photo", ed.photoId.toLong()).toString())
                    history()
                }
            }
                .onSuccess {
                    ed.history = it
                    say("Album cover set")
                }
                .onFailure { say(it.message) }
            refreshPhotos()
        }
    }

    // select mode

    fun startSelecting(first: ULong? = null) {
        selecting = true
        selected = setOfNotNull(first)
    }

    fun endSelecting() {
        selecting = false
        selected = emptySet()
    }

    fun toggleSelected(id: ULong) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll() {
        selected = if (selected.size == photos.size) emptySet() else photos.map { it.id }.toSet()
    }

    /**
     * Run [command] with the selected photos' ids added to [params], then leave select mode if [done];
     * [after] gets the step's mark (for an Undo, see [sayUndoable]).
     */
    private fun onSelected(command: String, params: JSONObject = JSONObject(), done: Boolean = true, after: ((ULong) -> Unit)? = null) {
        val e = engine ?: return
        if (selected.isEmpty()) return
        val ids = JSONArray(selected.map { it.toLong() })
        params.put("ids", ids)
        viewModelScope.launch {
            runCatching {
                e.command {
                    // the engine's selection too: its photo commands need one
                    execute("library.select", JSONObject().put("ids", ids).toString())
                    execute(command, params.toString())
                    historyDepth()
                }
            }
                .onSuccess { depth ->
                    if (done) endSelecting()
                    after?.invoke(depth)
                }
                .onFailure { say(it.message) }
            refreshPhotos()
        }
    }

    fun rateSelected(stars: Int) = onSelected("photo.rate", JSONObject().put("rating", stars), done = false)

    fun flagSelected(flag: String) = onSelected("photo.flag", JSONObject().put("flag", flag), done = false)

    fun pasteToSelected() = onSelected("develop.paste") { say("Settings pasted") }

    fun addSelectedToAlbum(album: AlbumInfo) =
        onSelected("album.addPhotos", JSONObject().put("id", album.id.toLong())) { say("Added to ${album.name}") }

    /** Move the selected photos to Recently Deleted (Undo brings them back). */
    fun deleteSelected() {
        val n = selected.size
        onSelected("photo.delete") { depth -> sayUndoable(if (n == 1) "Removed 1 photo" else "Removed $n photos", depth) }
    }

    /** Select mode's Remove: out of the album on show (a smart album's follow its rules: to Deleted), elsewhere to Deleted. */
    fun removeSelected() {
        val album = view.albumId?.let { id -> overview?.albums?.find { it.id == id } }
        if (album != null && !album.smart) removeSelectedFromAlbum() else deleteSelected()
    }

    /** In an album: take the selected photos out of it (they stay in the library; Undo puts them back). */
    fun removeSelectedFromAlbum() {
        val album = view.albumId ?: return
        val name = view.name
        onSelected("album.removePhotos", JSONObject().put("id", album.toLong())) { depth -> sayUndoable("Removed from $name", depth) }
    }

    fun restoreSelected() = onSelected("photo.restore") { say("Restored") }

    fun deleteSelectedPermanently() = onSelected("photo.deletePermanently") { say("Deleted permanently") }

    private fun importSummary(r: ImportReport): String = buildString {
        val n = r.imported.size
        append(if (n == 1) "Imported 1 photo" else "Imported $n photos")
        if (r.duplicates > 0u) append(", ${r.duplicates} already in the library")
        if (r.failed.isNotEmpty()) append(". Couldn't import ${r.failed.joinToString("; ")}")
    }

    /** Whether the engine may render on the GPU (null while it loads). */
    suspend fun gpuEnabled(): Boolean? {
        val e = engine ?: return null
        return runCatching { JSONObject(e.command { execute("app.gpu", "") }).optBoolean("enabled", true) }.getOrNull()
    }

    fun setGpu(on: Boolean) {
        val e = engine ?: return
        viewModelScope.launch { runCatching { e.command { execute("app.gpu", JSONObject().put("enabled", on).toString()) } }.onFailure { say(it.message) } }
    }

    /** The phone's space and what the library takes, for Local storage. */
    suspend fun storageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val library = engine?.libraryDir
        val stat = runCatching { StatFs(getApplication<Application>().filesDir.path) }.getOrNull()
        StorageInfo(
            deviceTotal = stat?.totalBytes ?: 0L,
            deviceFree = stat?.availableBytes ?: 0L,
            libraryBytes = library?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L,
            photos = photos.size,
            libraryPath = library?.absolutePath ?: "The demo library (in memory)",
        )
    }

    /** Engine version, GPU, memory and licences, for About. */
    suspend fun about(): String {
        val e = engine ?: return loadError ?: "The engine is still loading."
        val gpu = runCatching { JSONObject(e.command { execute("app.gpu", "") }) }.getOrNull()
        val memory = runCatching { JSONObject(e.command { execute("library.memory", "") }) }.getOrNull()
        return buildString {
            appendLine("LightCraft ${engineVersion()}, the pure-Rust engine of the desktop app, running on this phone.")
            appendLine()
            if (gpu != null) {
                val adapter = gpu.optString("adapter").takeIf { it.isNotBlank() && it != "null" }
                val reason = gpu.optString("reason").takeIf { it.isNotBlank() && it != "null" }
                appendLine(if (gpu.optBoolean("available")) "GPU rendering: ${adapter ?: "on"}" else "GPU rendering: off (${reason ?: "no GPU"})")
            }
            memory?.let {
                val used = it.optLong("engineBytes", it.optLong("engine_bytes"))
                appendLine("Memory: ${used shr 20} MB cached of a ${it.optLong("budget") shr 20} MB budget")
            }
            appendLine("${photos.size} photos in the library")
            e.problem?.let { appendLine("Library problem: $it") }
            appendLine()
            append("Inter font: SIL Open Font License 1.1. UniFFI: MPL-2.0. JNA: Apache-2.0.")
        }.trim()
    }

    // ------------------------------------------------------------------ editor

    fun openEditor(p: PhotoInfo, device: DeviceOrigin? = null) {
        val e = engine ?: return
        val ed = EditorState(p.id, p, device)
        // stepping from photo to photo: same view, same tool and section
        editor?.let {
            if (it.photoId != p.id) viewModelScope.launch { discardUntouched(it) }
            ed.viewport = it.viewport
            ed.tool = it.tool.takeIf { t -> t != MainTool.CROP }
            ed.section = it.section.takeIf { s -> s != EditSection.PROFILES }
            ed.showHistogram = it.showHistogram
        }
        ed.image = thumbnails.peek(thumbnailKey(p)) // a stand-in until the view is rendered
        editor = ed
        // what the desktop loupe shows meanwhile: the last view render, a raw's embedded JPEG or a thumbnail
        viewModelScope.launch {
            val metrics = getApplication<Application>().resources.displayMetrics
            val edge = max(metrics.widthPixels, metrics.heightPixels).coerceAtMost(2560).toUInt()
            val quick = withContext(e.thumbnails) { runCatching { e.session.renderQuickView(p.id, edge) }.getOrNull()?.let { it to it.toImageBitmap() } }
            if (quick != null && editor === ed && !ed.rendered) {
                ed.image = quick.second
                ed.histogram = quick.first.histogram
            }
        }
        viewModelScope.launch {
            runCatching { e.command { openPhoto(p.id); Snapshot(developControls(), developState(), history(), photo(p.id)) } }
                .onSuccess { ed.update(it) }
                .onFailure { say("Can't open ${p.fileName}: ${it.message}") }
            requestView()
        }
    }

    /** What an edit can change, read back from the engine in one go. */
    private class Snapshot(val controls: List<DevelopControl>, val state: DevelopState?, val history: EditHistory, val info: PhotoInfo?)

    private fun MobileSession.snapshot(id: ULong) = Snapshot(developControls(), developState(), history(), photo(id))

    private fun EditorState.update(s: Snapshot) {
        controls = s.controls
        state = s.state
        history = s.history
        s.info?.let { info = it }
    }

    /** Back: out of Profiles (as Cancel), out of a sub-panel, off the white balance selector, then out of the editor. */
    fun back() {
        val ed = editor ?: return
        when {
            ed.tool == MainTool.CROP -> cancelCrop()
            ed.section == EditSection.PROFILES && ed.tool == MainTool.EDIT -> cancelProfiles()
            ed.targeting != null -> ed.targeting = null
            ed.sub != null -> ed.sub = null
            ed.pickingWb -> ed.pickingWb = false
            else -> closeEditor()
        }
    }

    fun closeEditor() {
        val ed = editor ?: return
        editor = null
        val e = engine ?: return
        viewModelScope.launch {
            runCatching {
                e.command {
                    // the history's floor and the view first: nothing after may leave them set
                    this.closeEditor()
                    releaseView()
                    if (ed.adjusting != null) endAdjustment()
                }
            }
            discardUntouched(ed)
            refreshPhotos()
        }
    }

    fun onViewport(size: IntSize) {
        val ed = editor ?: return
        if (size == ed.viewport) return
        ed.viewport = size
        requestView()
    }

    /** Pinch zoom settled at [zoom]: zoomed in, the view renders larger so it stays sharp. */
    fun onZoom(zoom: Float) {
        val ed = editor ?: return
        val z = zoom.coerceIn(1f, 4f)
        if (abs(z - ed.zoom) < 0.05f) return
        ed.zoom = z
        requestView()
    }

    fun showBefore(before: Boolean) {
        val ed = editor ?: return
        if (ed.showingBefore == before) return
        ed.showingBefore = before
        requestView()
    }

    private fun requestView(draft: Boolean = false) {
        val ed = editor ?: return
        val vp = ed.viewport
        if (vp.width <= 0 || vp.height <= 0) return
        // drafts (slider drags) at 0.6 of the size, like the desktop loupe; zoomed in, up to the
        // engine's 2560 px previews
        val scale = if (draft) 0.6f else ed.zoom.coerceAtMost(2560f / max(vp.width, vp.height)).coerceAtLeast(1f)
        val w = (vp.width * scale).roundToInt().coerceAtLeast(1)
        val h = (vp.height * scale).roundToInt().coerceAtLeast(1)
        val mask = ed.selectedMask.takeIf { ed.tool == MainTool.MASKING && ed.showMaskOverlay }
        viewRequests.value = ViewRequest(ed.photoId, w, h, draft, ed.showingBefore && ed.tool != MainTool.CROP, ed.tool == MainTool.CROP, mask, ++requestSeq)
    }

    private suspend fun renderViews(e: Engine) {
        viewRequests.filterNotNull().collect { req ->
            val ed = editor?.takeIf { it.photoId == req.photo } ?: return@collect
            ed.rendering = true
            val result = withContext(e.viewRenders) {
                runCatching {
                    val img = if (req.crop) {
                        e.session.renderCropView(req.photo, req.width.toUInt(), req.height.toUInt(), req.draft)
                    } else {
                        e.session.renderView(req.photo, req.width.toUInt(), req.height.toUInt(), req.draft, req.before, req.mask)
                    }
                    img.histogram to img.toImageBitmap()
                }
            }
            if (editor === ed) {
                result
                    .onSuccess { (histogram, bitmap) ->
                        ed.image = bitmap
                        ed.histogram = histogram
                        ed.rendered = true
                        ed.showingCropView = req.crop
                        if (!req.draft && !req.before && !req.crop && ed.adjusting == null) prefetchNeighbours(e, ed, req.width, req.height)
                    }
                    .onFailure { if (!req.draft) say("Can't show ${ed.info.fileName}: ${it.message}") }
                ed.rendering = false
            }
        }
    }

    /**
     * Once a photo is on screen, get its neighbours in grid order ready in the background (source
     * decoded, view rendered and cached), as the desktop does, so swiping to them is instant.
     */
    private fun prefetchNeighbours(e: Engine, ed: EditorState, width: Int, height: Int) {
        val i = photos.indexOfFirst { it.id == ed.photoId }
        if (i < 0) return
        for (n in listOfNotNull(photos.getOrNull(i + 1), photos.getOrNull(i - 1))) {
            if (!prefetched.add("${n.id}:${n.settingsHash}:${width}x$height")) continue
            viewModelScope.launch(e.thumbnails) { runCatching { e.session.prefetchView(n.id, width.toUInt(), height.toUInt()) } }
        }
    }

    /** Swipe in the editor: the next (+1) or previous (−1) photo of the grid (of the gallery's, for a gallery photo). */
    fun showNeighbour(step: Int) {
        val ed = editor ?: return
        val origin = ed.device
        if (origin != null) {
            val shown = shownDevicePhotos
            val i = shown.indexOfFirst { it.id == origin.photo.id }
            val next = shown.getOrNull(i + step) ?: return
            if (i < 0 || importing) return
            if (ed.adjusting != null) {
                ed.adjusting = null
                engine?.let { e -> viewModelScope.launch { runCatching { e.command { endAdjustment() } } } }
            }
            openDevicePhoto(next)
            return
        }
        val i = photos.indexOfFirst { it.id == ed.photoId }
        if (i < 0) return
        val next = photos.getOrNull(i + step) ?: return
        val e = engine ?: return
        if (ed.adjusting != null) {
            ed.adjusting = null
            viewModelScope.launch { runCatching { e.command { endAdjustment() } } }
        }
        openEditor(next)
    }

    /** A main tool: opens it, or closes it when it is open (back to just the photo and the row). Crop is a mode. */
    fun selectTool(tool: MainTool) {
        val ed = editor ?: return
        if (ed.section == EditSection.PROFILES || ed.tool == MainTool.CROP) return
        ed.sub = null
        ed.pickingWb = false
        ed.targeting = null
        if (tool == MainTool.CROP) {
            ed.tool = tool
            markMode(ed)
            ed.zoom = 1f
            refreshCrop()
            requestView()
            return
        }
        if (tool == MainTool.MASKING) {
            ed.tool = if (ed.tool == tool) null else tool
            ed.maskPicker = null
            if (ed.tool == tool) {
                ed.maskSection = null
                refreshMasks()
            }
            requestView()
            return
        }
        if (tool == MainTool.REMOVE) {
            ed.tool = if (ed.tool == tool) null else tool
            ed.selectedSpot = null
            if (ed.tool == tool) {
                ed.zoom = 1f
                refreshSpots()
            }
            requestView()
            return
        }
        ed.tool = if (ed.tool == tool) null else tool
    }

    private fun refreshCrop() {
        val ed = editor ?: return
        val e = engine ?: return
        viewModelScope.launch { runCatching { e.command { cropFrame(ed.photoId) } }.onSuccess { ed.crop = it } }
    }

    private fun refreshSpots() {
        val ed = editor ?: return
        val e = engine ?: return
        viewModelScope.launch {
            runCatching { e.command { Triple(spots(ed.photoId), selectedSpot(), viewGeometry(ed.photoId)) } }.onSuccess { (sp, sel, g) ->
                ed.spots = sp
                ed.selectedSpot = sel?.toInt()?.takeIf { it < sp.size }
                ed.geometry = g
            }
        }
    }

    /** Read the photo's masks again ([lastPart]: edit the edited mask's newest part). */
    private fun refreshMasks(lastPart: Boolean = false) {
        val ed = editor ?: return
        val e = engine ?: return
        viewModelScope.launch {
            runCatching { e.command { Triple(masks(ed.photoId), selectedMask(), viewGeometry(ed.photoId)) } }.onSuccess { (ms, sel, g) ->
                ed.masks = ms
                ed.selectedMask = sel?.takeIf { s -> ms.any { it.id == s } } ?: ms.firstOrNull()?.id
                ed.geometry = g
                if (ms.isEmpty() && ed.maskSection != null) ed.maskSection = null
                val parts = ms.firstOrNull { it.id == ed.selectedMask }?.components?.size ?: 0
                ed.maskPart = if (lastPart) (parts - 1).coerceAtLeast(0) else ed.maskPart.coerceIn(0, (parts - 1).coerceAtLeast(0))
            }
        }
    }

    /** Masking's round button: no masks yet, create one; else the photo's masks. */
    fun openMaskPicker() {
        val ed = editor ?: return
        ed.maskPicker = if (ed.masks.isEmpty()) "new" else "list"
    }

    /** A section of Masking's row: the edited mask's sliders of it (again: back to the mask's own); no mask yet, create one. */
    fun selectMaskSection(section: String) {
        val ed = editor ?: return
        if (ed.selectedMask == null) {
            ed.maskPicker = "new"
            return
        }
        ed.maskSection = if (ed.maskSection == section) "mask" else section
    }

    /** Edit mask [id]: its own settings in the panel. */
    fun selectMask(id: UInt) {
        val ed = editor ?: return
        val e = engine ?: return
        ed.selectedMask = id
        ed.maskPart = 0
        ed.maskSection = "mask"
        viewModelScope.launch {
            runCatching { e.command { execute("mask.select", JSONObject().put("id", id.toLong()).toString()) } }
            if (ed.showMaskOverlay) requestView()
        }
    }

    /** A shape of [kind] placed in the middle of the view (radial and linear; the others are painted or picked on the photo). */
    private fun shapeParams(ed: EditorState, kind: String): JSONObject {
        val toPhoto = ed.geometry?.let { Affine.of(it.toPhoto) }
        fun at(u: Double, v: Double): org.json.JSONArray {
            val (x, y) = toPhoto?.apply(u, v) ?: (u to v)
            return org.json.JSONArray().put(x).put(y)
        }
        val params = JSONObject().put("kind", kind)
        when (kind) {
            "radial" -> params.put("center", at(0.5, 0.5)).put("rx", 0.2).put("ry", 0.15)
            "linear" -> params.put("start", at(0.5, 0.25)).put("end", at(0.5, 0.6))
        }
        return params
    }

    /** A new mask of [kind] (brush, linear, radial, colorRange, luminanceRange), placed in the middle of the view. */
    fun addMask(kind: String) {
        val ed = editor ?: return
        val params = shapeParams(ed, kind)
        ed.maskPicker = null
        ed.maskPart = 0
        edit(after = {
            ed.maskSection = "mask"
            refreshMasks()
        }) { it.execute("mask.add", params.toString()) }
    }

    /** A part of [kind] added to the edited mask ([op] `add`) or taken from it (`subtract`); then it's the part edited. */
    fun addMaskPart(op: String, kind: String) {
        val ed = editor ?: return
        val mask = ed.selectedMask ?: return
        val params = shapeParams(ed, kind).put("id", mask.toLong()).put("op", op)
        ed.maskPicker = null
        edit(after = {
            ed.maskSection = "mask"
            refreshMasks(lastPart = true)
        }) { it.execute("mask.addComponent", params.toString()) }
    }

    /** A command on the edited mask (`mask.invert`, `mask.duplicate`, `mask.delete`). */
    fun maskCommand(id: String) {
        val ed = editor ?: return
        val mask = ed.selectedMask ?: return
        if (id == "mask.delete") {
            ed.selectedMask = null
            ed.maskSection = null
        }
        edit(after = { refreshMasks() }) { it.execute(id, JSONObject().put("id", mask.toLong()).toString()) }
    }

    private fun beginMaskChange() {
        val ed = editor ?: return
        val e = engine ?: return
        if (ed.adjusting == "mask") return
        ed.adjusting = "mask"
        viewModelScope.launch { runCatching { e.command { beginAdjustment("Mask") } } }
    }

    private fun endMaskChange(after: () -> Unit) {
        val ed = editor ?: return
        val e = engine ?: return
        if (ed.adjusting != "mask") return after()
        ed.adjusting = null
        viewModelScope.launch {
            runCatching { e.command { endAdjustment(); snapshot(ed.photoId) } }.onSuccess { ed.update(it) }
            refreshMasks()
            after()
            requestView()
        }
    }

    /** A local slider moving: [key] of the edited mask (`exposure`…) to [value]. */
    fun setLocal(key: String, value: Double) {
        val ed = editor ?: return
        val e = engine ?: return
        val mask = ed.selectedMask ?: return
        ed.liveLocal = ed.liveLocal + (key to value)
        beginMaskChange()
        val params = JSONObject().put("id", mask.toLong()).put("values", JSONObject().put(key, value)).toString()
        viewModelScope.launch {
            runCatching { e.command { execute("mask.adjust", params) } }.onSuccess { requestView(draft = true) }
        }
    }

    fun localDone() = endMaskChange { editor?.liveLocal = emptyMap() }

    /** The edited mask's shape with [key] (`feather`, `refine`, `lo`, `hi`) at [value]. */
    fun setShapeValue(key: String, value: Double) {
        val ed = editor ?: return
        val mask = ed.masks.firstOrNull { it.id == ed.selectedMask } ?: return
        ed.liveShape = ed.liveShape + (key to value)
        val shape = partShape(mask, ed.maskPart)
        ed.liveShape.forEach { (k, v) -> shape.put(k, v) }
        beginMaskChange()
        sendShape(shape)
    }

    fun shapeDone() = endMaskChange { editor?.liveShape = emptyMap() }

    fun beginShapeDrag() = beginMaskChange()

    /** A radial's or linear's handle moving: the whole new shape. */
    fun dragShape(shape: JSONObject) {
        val ed = editor ?: return
        ed.liveShapeJson = shape.toString()
        sendShape(shape)
    }

    fun endShapeDrag() = endMaskChange { editor?.liveShapeJson = null }

    private fun sendShape(shape: JSONObject) {
        val ed = editor ?: return
        val e = engine ?: return
        val mask = ed.selectedMask ?: return
        val params = JSONObject().put("id", mask.toLong()).put("component", ed.maskPart).put("shape", shape).toString()
        viewModelScope.launch {
            runCatching { e.command { execute("mask.update", params) } }.onSuccess { requestView(draft = true) }
        }
    }

    /** A brush stroke over [points] (the photo's 0–1): painting into the mask, or erasing. */
    fun brushStroke(points: List<Pair<Double, Double>>) {
        val ed = editor ?: return
        val mask = ed.selectedMask ?: return
        if (points.isEmpty()) return
        val params = JSONObject()
            .put("id", mask.toLong())
            .put("points", org.json.JSONArray(points.map { (x, y) -> org.json.JSONArray().put(x).put(y) }))
            .put("size", ed.maskBrushSize.toDouble())
            .put("feather", ed.maskBrushFeather.toDouble())
            .put("flow", ed.maskBrushFlow.toDouble())
            .put("erase", ed.maskErase)
        edit(after = { refreshMasks() }) { it.execute("mask.brushStroke", params.toString()) }
    }

    /** A colour range's sample at [x], [y] of the photo (added to the ones it has). */
    fun sampleColor(x: Double, y: Double) {
        val ed = editor ?: return
        val mask = ed.masks.firstOrNull { it.id == ed.selectedMask } ?: return
        val any = (partShape(mask, ed.maskPart).optJSONArray("samples")?.length() ?: 0) > 0
        val params = JSONObject().put("id", mask.id.toLong()).put("component", ed.maskPart).put("x", x).put("y", y).put("add", any)
        edit(after = { refreshMasks() }) { it.execute("mask.sampleColor", params.toString()) }
    }

    /** The mask tinted over the photo, or not. */
    fun toggleMaskOverlay() {
        val ed = editor ?: return
        ed.showMaskOverlay = !ed.showMaskOverlay
        requestView()
    }

    // ------------------------------------------------------------------ targeted adjustment

    /** Targeted adjustment on or off ([target]: `curve`, `hue`, `sat`, `lum`; null: off). */
    fun setTargeting(target: String?) {
        val ed = editor ?: return
        ed.targeting = target
        // the view ↔ photo map, for where the finger is
        if (target != null) engine?.let { e -> viewModelScope.launch { runCatching { e.command { viewGeometry(ed.photoId) } }.onSuccess { ed.geometry = it } } }
    }

    /** A finger on the photo for targeted adjustment: what it does until it lifts is one step. */
    fun beginTargeted() {
        val ed = editor ?: return
        val e = engine ?: return
        ed.adjusting = "targeted"
        viewModelScope.launch { runCatching { e.command { beginAdjustment("Targeted Adjustment") } } }
    }

    /** [delta] slider units (point curves: output levels) at [x], [y] of the view (0–1). */
    fun targeted(x: Float, y: Float, delta: Double) {
        val ed = editor ?: return
        val e = engine ?: return
        val target = ed.targeting ?: return
        val (px, py) = ed.geometry?.let { g -> Affine.of(g.toPhoto)?.apply(x.toDouble(), y.toDouble()) } ?: (x.toDouble() to y.toDouble())
        val params = JSONObject().put("target", target).put("x", px).put("y", py).put("delta", delta)
        if (target == "curve" && ed.curveChannel != "parametric") params.put("channel", ed.curveChannel)
        viewModelScope.launch {
            runCatching { e.command { execute("develop.targeted", params.toString()) } }.onSuccess { r ->
                // the sliders follow
                runCatching { JSONObject(r) }.getOrNull()?.let { changed ->
                    ed.controls = ed.controls.map { c -> if (changed.has(c.id)) c.copy(value = changed.optDouble(c.id, c.value)) else c }
                    // Mix shows the colour the finger moves most
                    val band = changed.keys().asSequence().filter { it.startsWith("mixer.") }.maxByOrNull { abs(changed.optDouble(it, 0.0)) }?.split('.')?.getOrNull(1)
                    bandIds.indexOf(band).takeIf { it >= 0 }?.let { ed.mixerBand = it }
                }
                if (target == "curve") refreshCurve()
                requestView(draft = true)
            }
        }
    }

    fun endTargeted() {
        val ed = editor ?: return
        val e = engine ?: return
        if (ed.adjusting != "targeted") return
        ed.adjusting = null
        viewModelScope.launch {
            runCatching { e.command { endAdjustment(); snapshot(ed.photoId) } }.onSuccess { ed.update(it) }
            if (ed.targeting == "curve") refreshCurve()
            requestView()
        }
    }

    // ------------------------------------------------------------------ grading wheels

    /** A finger on a grading wheel: what it does until it lifts is one step. */
    fun beginWheel(range: String) {
        val ed = editor ?: return
        val e = engine ?: return
        ed.adjusting = "grading"
        val label = range.replaceFirstChar { it.uppercase() } + " Grading"
        viewModelScope.launch { runCatching { e.command { beginAdjustment(label) } } }
    }

    /** A grading wheel's point: [range]'s hue (degrees) and saturation (0–100) together. */
    fun dragWheel(range: String, hue: Double, sat: Double) {
        val ed = editor ?: return
        val e = engine ?: return
        val values = mapOf("grading.$range.hue" to hue, "grading.$range.sat" to sat)
        ed.controls = ed.controls.map { c -> values[c.id]?.let { c.copy(value = it) } ?: c }
        val params = JSONObject().put("values", JSONObject(values)).toString()
        viewModelScope.launch { runCatching { e.command { execute("develop.set", params) } }.onSuccess { requestView(draft = true) } }
    }

    fun endWheel() {
        val ed = editor ?: return
        val e = engine ?: return
        ed.adjusting = null
        viewModelScope.launch {
            runCatching { e.command { endAdjustment(); snapshot(ed.photoId) } }.onSuccess { ed.update(it) }
            requestView()
        }
    }

    // ------------------------------------------------------------------ curve

    /** Light › Curve: the curves over the photo, their channels and presets in the panel. */
    fun openCurve() {
        val ed = editor ?: return
        ed.sub = "curve"
        refreshCurve()
    }

    private fun refreshCurve() {
        val ed = editor ?: return
        val e = engine ?: return
        viewModelScope.launch { runCatching { e.command { toneCurve(ed.photoId) } }.onSuccess { ed.curve = it } }
    }

    fun setCurveChannel(channel: String) {
        editor?.curveChannel = channel
    }

    /** A finger takes the curve: what it does until it lifts is one step. */
    fun beginCurveDrag() {
        val ed = editor ?: return
        val e = engine ?: return
        ed.adjusting = "curve"
        viewModelScope.launch { runCatching { e.command { beginAdjustment("Tone Curve") } } }
    }

    /** The open channel's points while a finger moves one. */
    fun dragCurve(points: List<Pair<Double, Double>>) {
        val ed = editor ?: return
        val e = engine ?: return
        ed.liveCurve = points
        val params = curveParams(ed.curveChannel, points)
        viewModelScope.launch { runCatching { e.command { execute("develop.curve", params) } }.onSuccess { requestView(draft = true) } }
    }

    fun endCurveDrag() {
        val ed = editor ?: return
        val e = engine ?: return
        ed.adjusting = null
        viewModelScope.launch {
            runCatching { e.command { endAdjustment(); snapshot(ed.photoId) to toneCurve(ed.photoId) } }.onSuccess { (snap, curve) ->
                ed.update(snap)
                ed.curve = curve
            }
            ed.liveCurve = null
            requestView()
        }
    }

    /** The open channel's points set at once (a point added or taken away): one step. */
    fun setCurvePoints(points: List<Pair<Double, Double>>) {
        val ed = editor ?: return
        val params = curveParams(ed.curveChannel, points)
        edit(after = ::refreshCurve) { it.execute("develop.curve", params) }
    }

    /** A point-curve preset on all four point curves (the parametric curve stays). */
    fun applyCurvePreset(name: String) = edit(after = ::refreshCurve) { it.execute("curve.applyPreset", JSONObject().put("name", name).toString()) }

    /** The open channel back to a straight line (the parametric one: its regions and splits back to theirs). */
    fun resetCurveChannel() {
        val ed = editor ?: return
        val channel = ed.curveChannel
        edit(after = ::refreshCurve) { it.execute("curve.reset", JSONObject().put("channel", channel).toString()) }
    }

    private fun curveParams(channel: String, points: List<Pair<Double, Double>>) =
        JSONObject().put("channel", channel).put("points", JSONArray(points.map { (x, y) -> JSONArray().put(x).put(y) })).toString()

    // ------------------------------------------------------------------ actions

    /** An action of Actions' row: Auto and Fix angle apply at once (one step each), Rate opens its panel (again: closes it). */
    fun runAction(a: QuickAction) {
        val ed = editor ?: return
        when (a) {
            QuickAction.AUTO -> edit(after = { say("Auto settings applied") }) { it.execute("develop.auto", "{}") }
            // Upright's automatic correction, cropped to leave no blank corners: one step
            QuickAction.FIX_ANGLE -> edit(after = { say("Angle fixed") }) { s ->
                s.beginAdjustment("Fix Angle")
                try {
                    s.execute("geometry.upright", JSONObject().put("mode", "auto").toString())
                    s.execute("develop.merge", JSONObject().put("settings", JSONObject().put("geometry", JSONObject().put("constrain_crop", true))).toString())
                } finally {
                    s.endAdjustment()
                }
            }
            QuickAction.RATE -> ed.action = if (ed.action == a) null else a
        }
    }

    /** Crop › Geometry's switch: crop the corners an Upright or transform leaves blank (one step Cancel takes back). */
    fun setConstrainCrop(on: Boolean) =
        cropCommand("develop.merge", JSONObject().put("settings", JSONObject().put("geometry", JSONObject().put("constrain_crop", on))).put("label", "Constrain Crop"))

    /** A spot over [points] (the photo's 0–1): one point a round spot, more a brushed path. */
    fun addSpot(points: List<Pair<Double, Double>>) {
        val ed = editor ?: return
        if (points.isEmpty()) return
        val params = JSONObject()
            .put("mode", ed.removeMode)
            .put("points", JSONArray(points.map { (x, y) -> JSONArray().put(x).put(y) }))
            .put("size", ed.brushSize.toDouble())
            .put("feather", ed.brushFeather.toDouble())
            .put("opacity", ed.brushOpacity.toDouble())
        edit(after = { refreshSpots() }) { it.execute("spot.add", params.toString()) }
    }

    /** Pick spot [index]: the panel's sliders become its settings. */
    fun selectSpot(index: Int) {
        val ed = editor ?: return
        val e = engine ?: return
        ed.selectedSpot = index
        ed.spots.getOrNull(index)?.let {
            ed.brushSize = it.size.toFloat()
            ed.brushFeather = it.feather.toFloat()
            ed.brushOpacity = it.opacity.toFloat()
        }
        viewModelScope.launch { runCatching { e.command { execute("spot.select", JSONObject().put("index", index).toString()) } } }
    }

    fun deleteSpot() {
        val ed = editor ?: return
        val i = ed.selectedSpot ?: return
        ed.selectedSpot = null
        edit(after = { refreshSpots() }) { it.execute("spot.delete", JSONObject().put("index", i).toString()) }
    }

    /** Remove, heal or clone: for the picked spot, else for the next ones. */
    fun setRemoveMode(mode: String) {
        val ed = editor ?: return
        val i = ed.selectedSpot
        if (i == null) {
            ed.removeMode = mode
            return
        }
        edit(after = { refreshSpots() }) { it.execute("spot.update", JSONObject().put("index", i).put("mode", mode).toString()) }
    }

    /** The brush's size (of the long edge), feather or opacity while a slider moves. */
    fun setBrush(size: Float? = null, feather: Float? = null, opacity: Float? = null) {
        val ed = editor ?: return
        size?.let { ed.brushSize = it.coerceIn(0.002f, 0.1f) }
        feather?.let { ed.brushFeather = it }
        opacity?.let { ed.brushOpacity = it }
    }

    /** A brush slider let go: the picked spot takes its settings (one step). */
    fun brushDone() {
        val ed = editor ?: return
        val i = ed.selectedSpot ?: return
        val params = JSONObject().put("index", i).put("size", ed.brushSize.toDouble()).put("feather", ed.brushFeather.toDouble()).put("opacity", ed.brushOpacity.toDouble())
        edit(after = { refreshSpots() }) { it.execute("spot.update", params.toString()) }
    }

    /** Spots on the dust the engine finds. */
    fun findDust() {
        val ed = editor ?: return
        edit(after = {
            refreshSpots()
            say("Dust spots added")
        }) { it.execute("spot.findDust", "{}") }
    }

    /** Leave Crop keeping what it did. */
    fun applyCrop() {
        val ed = editor ?: return
        ed.tool = null
        ed.crop = null
        ed.modeMark = null
        requestView()
    }

    /** Leave Crop taking back everything done in it. */
    fun cancelCrop() {
        val ed = editor ?: return
        ed.tool = null
        ed.crop = null
        // taken back for good: Redo doesn't bring a cancelled crop back
        discardMode(ed)
    }

    /** A crop command (`crop.reset`, `photo.rotateLeft`…) on the photo: one step Cancel can take back. */
    fun cropCommand(id: String, params: JSONObject = JSONObject(), photo: Boolean = false) {
        val ed = editor ?: return
        if (photo) params.put("ids", JSONArray().put(ed.photoId.toLong()))
        edit(after = { refreshCrop() }) { it.execute(id, params.toString()) }
    }

    fun setCropAspect(aspect: String) = cropCommand("crop.aspect", JSONObject().put("aspect", aspect))

    /** A finger takes a crop handle: what it does until it lifts is one step. */
    fun beginCropDrag() {
        val ed = editor ?: return
        val e = engine ?: return
        ed.adjusting = "crop"
        viewModelScope.launch { runCatching { e.command { beginAdjustment("Crop") } } }
    }

    /** The crop while a handle moves ([rect]: `x0, y0, x1, y1` in the straightened frame). */
    fun dragCrop(rect: DoubleArray) {
        val e = engine ?: return
        val params = JSONObject().put("rect", JSONArray(rect.toList())).toString()
        viewModelScope.launch { runCatching { e.command { execute("crop.set", params) } } }
    }

    fun endCropDrag() {
        val ed = editor ?: return
        val e = engine ?: return
        ed.adjusting = null
        viewModelScope.launch {
            runCatching { e.command { endAdjustment(); snapshot(ed.photoId) to cropFrame(ed.photoId) } }.onSuccess { (snap, frame) ->
                ed.update(snap)
                ed.crop = frame
            }
        }
    }

    /** An Edit section: Auto applies at once, Profiles opens its mode, the others their panel (again: closes it). */
    fun selectSection(section: EditSection) {
        val ed = editor ?: return
        ed.sub = null
        ed.pickingWb = false
        ed.targeting = null
        when (section) {
            EditSection.AUTO -> edit(after = { say("Auto settings applied") }) { it.execute("develop.auto", "{}") }
            EditSection.PROFILES -> {
                ed.profileFrom = ed.state?.profile ?: "lc.color"
                ed.section = section
                markMode(ed)
            }
            else -> ed.section = if (ed.section == section) null else section
        }
    }

    /** Leave Profiles keeping the profile chosen. */
    fun applyProfiles() {
        val ed = editor ?: return
        ed.profileFrom = null
        ed.section = null
        ed.modeMark = null
    }

    /** Leave Profiles going back to the profile it started with (the profiles tried are taken back for good). */
    fun cancelProfiles() {
        val ed = editor ?: return
        ed.profileFrom = null
        ed.section = null
        discardMode(ed)
    }

    /** Note where the history is as a mode (Crop, Profiles) opens, for its Cancel. */
    private fun markMode(ed: EditorState) {
        val e = engine ?: return
        ed.modeMark = null
        // queued before any of the mode's own commands, so it is read first
        viewModelScope.launch { runCatching { e.command { historyDepth() } }.onSuccess { ed.modeMark = it } }
    }

    /** A mode's Cancel: every step since it opened, taken back for good (not left on Redo). */
    private fun discardMode(ed: EditorState) {
        val mark = ed.modeMark
        ed.modeMark = null
        if (mark == null) {
            requestView()
            return
        }
        edit(after = ::refreshToolState) { s -> s.discardTo(mark) }
    }

    fun toggleBlackAndWhite() = edit { it.execute("develop.treatment", "{}") }

    fun copySettings() = edit(render = false, after = { say("Settings copied") }) { it.execute("develop.copy", "{}") }

    fun pasteSettings() = edit { it.execute("develop.paste", "{}") }

    fun applyFromPrevious() = edit { it.execute("develop.pastePrevious", "{}") }

    /** Save the photo's settings as a preset of the user's. */
    fun createPreset(name: String) {
        val e = engine ?: return
        if (name.isBlank()) return
        edit(render = false, after = {
            viewModelScope.launch { runCatching { e.command { presets() } }.onSuccess { presets = it } }
            say("Preset “${name.trim()}” created")
        }) { it.execute("preset.create", JSONObject().put("name", name.trim()).toString()) }
    }

    fun onControlChange(control: DevelopControl, value: Double) {
        val ed = editor ?: return
        val e = engine ?: return
        ed.controls = ed.controls.map { if (it.id == control.id) it.copy(value = value) else it }
        val begin = ed.adjusting != control.id
        ed.adjusting = control.id
        val straighten = control.id == "crop.angle" && ed.tool == MainTool.CROP
        viewModelScope.launch {
            runCatching {
                e.command {
                    if (begin) beginAdjustment(control.label)
                    if (straighten) {
                        execute("crop.straighten", JSONObject().put("angle", value).toString())
                        cropFrame(ed.photoId)
                    } else {
                        setControl(control.id, value)
                        null
                    }
                }
            }
                .onSuccess { frame ->
                    if (frame != null) ed.crop = frame
                    // the crop view doesn't change with the crop's angle
                    if (!straighten) requestView(draft = true)
                    if (ed.sub == "curve" && control.id.startsWith("curve.")) refreshCurve()
                }
                .onFailure { say(it.message) }
        }
    }

    fun onControlChangeFinished() {
        val ed = editor ?: return
        val e = engine ?: return
        if (ed.adjusting == null) return
        ed.adjusting = null
        val cropping = ed.tool == MainTool.CROP
        viewModelScope.launch {
            runCatching { e.command { endAdjustment(); snapshot(ed.photoId) to (if (cropping) cropFrame(ed.photoId) else null) } }.onSuccess { (snap, frame) ->
                ed.update(snap)
                if (frame != null) ed.crop = frame
            }
            requestView()
        }
    }

    /** Run [block] on the engine, then read back what an edit changes (and render, if [render]). */
    private fun edit(render: Boolean = true, after: (() -> Unit)? = null, block: (MobileSession) -> Unit) {
        val ed = editor ?: return
        val e = engine ?: return
        viewModelScope.launch {
            runCatching {
                e.command {
                    block(this)
                    snapshot(ed.photoId)
                }
            }
                .onSuccess {
                    ed.update(it)
                    if (render) requestView()
                    after?.invoke()
                }
                .onFailure { say(it.message) }
        }
    }

    fun resetControl(control: DevelopControl) {
        edit(after = { refreshToolState() }) { it.resetControl(control.id) }
    }

    fun undo() = edit(after = ::refreshToolState) { it.undo() }

    fun redo() = edit(after = ::refreshToolState) { it.redo() }

    /** What the open tool shows of the photo (its masks, spots or crop), read again after an undo or redo. */
    private fun refreshToolState() {
        if (editor?.sub == "curve") refreshCurve()
        when (editor?.tool) {
            MainTool.MASKING -> refreshMasks()
            MainTool.REMOVE -> refreshSpots()
            MainTool.CROP -> refreshCrop()
            else -> {}
        }
    }

    fun resetAll() = edit { it.execute("develop.reset", "{}") }

    fun setWhiteBalance(mode: String) = edit { it.execute("develop.wb", JSONObject().put("mode", mode).toString()) }

    /** White balance from a neutral spot of the photo, at [x], [y] across and down it (0–1). */
    fun pickWhiteBalance(x: Float, y: Float) {
        val ed = editor ?: return
        ed.pickingWb = false
        edit { s ->
            val (px, py) = s.viewGeometry(ed.photoId)?.let { g -> Affine.of(g.toPhoto)?.apply(x.toDouble(), y.toDouble()) } ?: (x.toDouble() to y.toDouble())
            s.execute("develop.wbPick", JSONObject().put("x", px).put("y", py).toString())
        }
    }

    fun setVignetteStyle(style: String) = edit {
        it.execute("develop.merge", JSONObject().put("settings", JSONObject().put("vignette", JSONObject().put("style", style))).put("label", "Vignette Style").toString())
    }

    /** An Optics switch: `remove_ca` or `lens_profile`. */
    fun setOptics(key: String, on: Boolean, label: String) = edit {
        it.execute("develop.merge", JSONObject().put("settings", JSONObject().put("optics", JSONObject().put(key, on))).put("label", label).toString())
    }

    fun setProfile(profile: String) = edit { it.execute("develop.profile", JSONObject().put("id", profile).toString()) }

    fun applyPreset(preset: PresetInfo) {
        val ed = editor ?: return
        ed.lastPreset = preset
        ed.presetAmount = 100f
        edit(after = { say("Preset: ${preset.name}") }) { it.applyPreset(preset.id, 100.0) }
    }

    /** The last applied preset at [amount] percent: taken back and applied again, as the desktop does. */
    fun setPresetAmount(amount: Float) {
        val ed = editor ?: return
        val preset = ed.lastPreset ?: return
        ed.presetAmount = amount
        edit { s ->
            if (s.history().undo == "Preset: ${preset.name}") s.undo()
            s.applyPreset(preset.id, amount.toDouble())
        }
    }

    private fun ids(): JSONArray = JSONArray().put(editor?.photoId?.toLong() ?: 0L)

    fun setRating(stars: Int) = edit(render = false) {
        it.execute("photo.rate", JSONObject().put("ids", ids()).put("rating", stars).toString())
    }

    fun setFlag(flag: PhotoFlag) = edit(render = false) {
        val name = when (flag) {
            PhotoFlag.PICK -> "pick"
            PhotoFlag.REJECT -> "reject"
            PhotoFlag.UNFLAGGED -> "none"
        }
        it.execute("photo.flag", JSONObject().put("flag", name).put("ids", ids()).toString())
    }

    /** Move the photo to Recently Deleted (its file stays where it is); Undo brings it back. */
    fun removePhoto() {
        val ed = editor ?: return
        val e = engine ?: return
        val params = JSONObject().put("ids", ids()).toString()
        editor = null
        viewModelScope.launch {
            val result = runCatching {
                e.command {
                    releaseView()
                    this.closeEditor()
                    execute("photo.delete", params)
                    historyDepth()
                }
            }
            refreshPhotos()
            result
                .onSuccess { depth ->
                    sayUndoable("Removed ${ed.info.fileName}", depth)
                }
                .onFailure { say(it.message) }
        }
    }

    /**
     * Render the photo with its edits as [choice] says (the choice is kept for next time) into
     * Pictures/LightCraft; with [share], to the system's share sheet instead, leaving no copy in
     * the phone's photos.
     */
    fun export(choice: ExportChoice, share: Boolean = false) {
        val ed = editor ?: return
        val e = engine ?: return
        if (ed.exporting) return
        ed.exporting = true
        exportChoice = choice
        prefs.edit().putString("export", JSONObject(choice.json()).put("longEdge", choice.longEdge).toString()).apply()
        val options = choice.json()
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = e.session.export(ed.photoId, options)
                    val app = getApplication<Application>()
                    (if (share) PhotoFiles.shareable(app, file) else PhotoFiles.saveToPictures(app, file)) to file
                }
            }
            ed.exporting = false
            result
                .onSuccess { (uri, file) ->
                    if (share) {
                        PhotoFiles.share(getApplication(), uri, file.mimeType)
                    } else {
                        say("Saved ${file.fileName} (${file.width}×${file.height}) to Pictures/LightCraft", "Share") {
                            PhotoFiles.share(getApplication(), uri, file.mimeType)
                        }
                    }
                }
                .onFailure { say("Export failed: ${it.message}") }
        }
    }

    companion object {
        /** Long edge the grid asks for (the engine caches thumbnails at 128, 256, 384 and 512 px). */
        const val THUMB_EDGE = 384

        /** Long edge of preset and profile previews. */
        const val LOOK_EDGE = 256
    }
}
