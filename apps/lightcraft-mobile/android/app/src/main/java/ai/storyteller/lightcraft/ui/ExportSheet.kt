package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.core.DevelopControl
import ai.storyteller.lightcraft.core.ImageSize
import ai.storyteller.lightcraft.core.SliderTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** What a saved or shared copy is (the desktop's export options, the ones a phone needs); kept between launches. */
data class ExportChoice(
    /** `jpeg`, `png`, `tiff`, `avif` or `dng` (a raw's data, raws only), as `app.export` names them. */
    val format: String = "jpeg",
    /** The long edge in pixels; 0: full size. */
    val longEdge: Int = 0,
    /** 1–100 (JPEG, AVIF). */
    val quality: Int = 90,
    /** `all`, `allExceptCamera`, `copyright` or `none`. */
    val metadata: String = "all",
    val removeLocation: Boolean = false,
) {
    /** The options as the engine's export takes them. */
    fun json(): String {
        val o = JSONObject().put("format", format).put("quality", quality).put("metadata", metadata).put("removeLocation", removeLocation)
        if (longEdge > 0) o.put("longEdge", longEdge)
        return o.toString()
    }

    /** Has a quality setting. */
    val lossy: Boolean get() = format == "jpeg" || format == "avif"
}

val exportFormats = listOf("jpeg" to "JPEG", "png" to "PNG", "tiff" to "TIFF", "avif" to "AVIF", "dng" to "DNG")
val exportSizes = listOf(2048 to "Small (2048 px)", 0 to "Full size")
val exportMetadata = listOf("all" to "All", "allExceptCamera" to "All but camera info", "copyright" to "Copyright only", "none" to "None")

private fun qualityControl(value: Int) = DevelopControl("export.quality", "Quality", "", "", 10.0, 100.0, 90.0, 1.0, 0u, value.toDouble(), SliderTrack.Plain)

/**
 * Save a copy (or share one): the file type, its size, the quality and what metadata it carries,
 * then save (to Pictures/LightCraft) or share. DNG is offered for raws only; it ignores the size.
 */
@Composable
fun ExportSheet(vm: AppViewModel, ed: EditorState, share: Boolean, onDismiss: () -> Unit) {
    var c by remember { mutableStateOf(vm.exportChoice.let { if (it.format == "dng" && !ed.info.raw) it.copy(format = "jpeg") else it }) }
    // full size as the photo is cropped (not the original's size)
    val full by produceState<ImageSize?>(null, ed.info.settingsHash) { value = vm.exportSize(ed, ExportChoice()) }
    LcSheet(onDismiss) {
        SheetHeading("File type")
        exportFormats.filter { (id, _) -> id != "dng" || ed.info.raw }.forEach { (id, label) ->
            RadioRow(label, c.format == id, detail = if (id == "dng") "the raw data, with the edits" else null) { c = c.copy(format = id) }
        }
        if (c.format != "dng") {
            SheetDivider()
            SheetHeading("Size")
            exportSizes.forEach { (edge, label) ->
                val detail = if (edge == 0) full?.let { "${it.width} × ${it.height}" } ?: "${ed.info.width} × ${ed.info.height}" else null
                RadioRow(label, c.longEdge == edge, detail = detail) { c = c.copy(longEdge = edge) }
            }
        }
        if (c.lossy) {
            Spacer(Modifier.height(4.dp))
            DevelopSlider(
                qualityControl(c.quality),
                onChange = { c = c.copy(quality = it.toInt().coerceIn(10, 100)) },
                onFinished = {},
                onReset = { c = c.copy(quality = 90) },
            )
        }
        SheetDivider()
        SheetHeading("Metadata")
        exportMetadata.forEach { (id, label) -> RadioRow(label, c.metadata == id) { c = c.copy(metadata = id) } }
        SwitchRow("Remove location", c.removeLocation) { c = c.copy(removeLocation = it) }
        Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Lc.primary)
                .clickable(role = Role.Button) {
                    vm.export(c, share)
                    onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (share) "Share" else "Save to device", color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }
    }
}
