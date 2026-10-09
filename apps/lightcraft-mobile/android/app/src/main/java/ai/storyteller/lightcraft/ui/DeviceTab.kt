package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.engine.DeviceAccess
import ai.storyteller.lightcraft.engine.DevicePhoto
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId

/** The bottom navigation's labels and icons when not chosen (the chosen tab's are blue). */
private val tabLabel = Color(0xFFC8C8C8)
private val tabIcon = Color(0xFFA2A2A2)

/**
 * The home screen's bottom navigation, as the reference's: 64.2 dp, a 24 dp icon 10.2 dp from its
 * top over a label, 175.1 dp a tab. Two tabs where its first two are: the phone's gallery and
 * LightCraft's library (its third, its community, is online).
 */
@Composable
fun HomeNav(tab: String, onTab: (String) -> Unit, modifier: Modifier = Modifier) {
    // sideways the reference's tabs are 168 dp
    val config = LocalConfiguration.current
    val width = if (config.screenWidthDp > config.screenHeightDp) 168.dp else 175.1.dp
    Row(modifier.fillMaxWidth().background(Lc.bar).height(64.2.dp), horizontalArrangement = Arrangement.Center) {
        HomeTab(LcIcon.DevicePhotos, "Device", tab == "device", width) { onTab("device") }
        HomeTab(LcIcon.Library, "LightCraft", tab == "library", width) { onTab("library") }
        // the reference's third tab (its online community) has no LightCraft counterpart: its
        // place stays empty so the two tabs sit where its first two do
        Spacer(Modifier.width(width))
    }
}

/**
 * Sideways the tabs stand in a rail at the left, as the reference's: 80.3 dp wide, a tab 59.3 dp
 * tall every 67.1 dp, in the middle of the height (its icon 7.8 dp from the tab's top).
 */
@Composable
fun HomeRail(tab: String, onTab: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.8.dp)) {
        HomeTab(LcIcon.DevicePhotos, "Device", tab == "device", 80.3.dp, Modifier.height(59.3.dp), iconTop = 7.8.dp) { onTab("device") }
        HomeTab(LcIcon.Library, "LightCraft", tab == "library", 80.3.dp, Modifier.height(59.3.dp), iconTop = 7.8.dp) { onTab("library") }
        // the empty place of the reference's third tab
        Spacer(Modifier.height(59.3.dp))
    }
}

@Composable
private fun HomeTab(icon: LcIcon, label: String, selected: Boolean, width: Dp, modifier: Modifier = Modifier.fillMaxHeight(), iconTop: Dp = 10.2.dp, onClick: () -> Unit) {
    Column(
        modifier.width(width).clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(iconTop))
        LcIcon(icon, null, tint = if (selected) Lc.highlight else tabIcon, size = 24.dp)
        // the label's letters as high and as tall as the reference's (Inter sits lower in its line)
        Text(label, color = if (selected) Lc.highlight else tabLabel, fontFamily = Inter, fontSize = 13.sp, maxLines = 1, modifier = Modifier.offset(y = (-0.4).dp))
    }
}

/** The Device tab's header: "Photos on device" (or the album shown) with a menu of the gallery's albums. */
@Composable
fun DeviceHeader(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val albums = remember(vm.devicePhotos) { vm.devicePhotos.groupingBy { it.album }.eachCount().toList().sortedByDescending { it.second } }
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.Bottom) {
        Box {
            Row(
                Modifier.height(48.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = vm.deviceAccess != DeviceAccess.NONE, role = Role.DropdownList) { open = true }
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    vm.deviceAlbum ?: "Photos on device",
                    color = Lc.text,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (vm.deviceAccess != DeviceAccess.NONE) {
                    Spacer(Modifier.width(6.dp))
                    MdIcon(MdIcon.ExpandMore, null, tint = Lc.text)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Lc.bottomBar) {
                MenuItem("All photos (${vm.devicePhotos.size})") {
                    open = false
                    vm.showDeviceAlbum(null)
                }
                albums.forEach { (album, count) ->
                    MenuItem("${album.ifBlank { "Other" }} ($count)") {
                        open = false
                        vm.showDeviceAlbum(album)
                    }
                }
            }
        }
    }
}

/** The gallery's photos under a day's heading. */
class DeviceDay(val key: String, val photos: List<DevicePhoto>)

/** [photos] (newest first) by the day they were taken, in the phone's time zone. */
fun deviceDays(photos: List<DevicePhoto>, zone: ZoneId = ZoneId.systemDefault()): List<DeviceDay> =
    byDay(photos, zone) { it.taken }.map { (day, run) -> DeviceDay(day, run) }

/** [items] in runs by the day (ISO, in [zone]) of their time [millis], in their order. */
fun <T> byDay(items: List<T>, zone: ZoneId, millis: (T) -> Long): List<Pair<String, List<T>>> {
    val out = mutableListOf<Pair<String, List<T>>>()
    var key: String? = null
    var run = mutableListOf<T>()
    for (item in items) {
        val k = Instant.ofEpochMilli(millis(item)).atZone(zone).toLocalDate().toString()
        if (k != key) {
            key?.let { out += it to run }
            key = k
            run = mutableListOf()
        }
        run += item
    }
    key?.let { out += it to run }
    return out
}

/**
 * The Device tab: the phone's photos by day in the library's grid; a tap opens one in the editor.
 * Without access, why it's needed and a button to allow it.
 */
@Composable
fun DeviceGallery(vm: AppViewModel, query: String, denied: Boolean, onAllow: () -> Unit, onSettings: () -> Unit) {
    if (vm.deviceAccess == DeviceAccess.NONE) {
        DevicePrompt(denied, onAllow, onSettings)
        return
    }
    val shown = vm.shownDevicePhotos.let { all -> if (query.isBlank()) all else all.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize()) {
        if (vm.deviceAccess == DeviceAccess.PARTIAL) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LightCraft sees only the photos you picked.", color = Lc.textDim, fontFamily = Inter, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    "Pick more",
                    color = Lc.selected,
                    fontFamily = Inter,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onAllow).padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "No photos on this phone yet." else "No photos match “${query.trim()}”.",
                    color = Lc.text,
                    fontFamily = Inter,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            DeviceGrid(vm, shown)
        }
    }
}

/** The gallery's grid: justified rows under each day's heading, as the library's. */
@Composable
private fun DeviceGrid(vm: AppViewModel, photos: List<DevicePhoto>) {
    val days = remember(photos) { deviceDays(photos) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideways = maxWidth > maxHeight
        val inner = (maxWidth - 11.7.dp).value
        val target = if (sideways) 110f else 125.5f
        val rows = remember(days, inner) { days.map { d -> justify(d.photos.map { it.aspect }, inner, target = target, gap = 3.9f) } }
        LazyColumn(
            contentPadding = PaddingValues(start = 3.9.dp, top = 3.9.dp, end = 7.8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.9.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            days.forEachIndexed { di, day ->
                item(key = "day:${day.key}", contentType = "heading") { DayHeading(segmentLabel(day.key), day.photos.size) }
                items(rows[di], key = { r -> "row:${day.photos[r.start].id}" }, contentType = { "row" }) { r ->
                    val full = r.height < target - 0.01f
                    Row(Modifier.fillMaxWidth().height(r.height.dp), horizontalArrangement = Arrangement.spacedBy(3.9.dp)) {
                        for (i in r.start until r.end) {
                            val p = day.photos[i]
                            val aspect = p.aspect.coerceIn(0.25, 4.0).toFloat()
                            val size = if (full) Modifier.weight(aspect) else Modifier.width((aspect * r.height).dp)
                            DeviceCell(vm, p, size.fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeading(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 1.5.dp).height(36.5.dp).padding(start = 12.2.dp, end = 8.3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Lc.text, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.weight(1f).semantics { heading() })
        Text("$count", color = Lc.textDim, fontFamily = Inter, fontSize = 15.sp)
    }
}

@Composable
private fun DeviceCell(vm: AppViewModel, p: DevicePhoto, modifier: Modifier) {
    val thumb by produceState(vm.peekDeviceThumbnail(p), p.id) { vm.deviceThumbnail(p)?.let { value = it } }
    Box(modifier.background(Lc.cell).clickable(role = Role.Button) { vm.openDevicePhoto(p) }) {
        thumb?.let { Image(it, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** No access to the gallery yet: what the tab is for, and a button to allow it (or, refused, the app's settings). */
@Composable
private fun DevicePrompt(denied: Boolean, onAllow: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LcIcon(LcIcon.Phone, null, tint = Lc.text, size = 100.dp, weight = 0.3f)
            Spacer(Modifier.height(16.dp))
            Text("Your phone's photos, here", color = Lc.text, fontFamily = Inter, fontSize = 17.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                "Allow LightCraft to see the photos on this phone. Open one to edit it: LightCraft adds a copy to its library, and takes it out again if you leave it untouched.",
                color = Lc.text,
                fontFamily = Inter,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Lc.primary).clickable(role = Role.Button, onClick = if (denied) onSettings else onAllow)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (denied) "Open settings" else "Allow access", color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
        }
    }
}
