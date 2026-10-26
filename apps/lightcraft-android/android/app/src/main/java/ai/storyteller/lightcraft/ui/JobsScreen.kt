package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.core.AlbumInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val jobSides: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)

/**
 * The home screen: the jobs (one per property, each a plain album), newest first, and Import
 * photos, which picks a folder (an AutoHDR download, say) and makes it a job. All photos opens the
 * whole library, as the app used to open to.
 */
@Composable
fun JobsScreen(vm: AppViewModel, snackbar: SnackbarHostState, onImportFolder: () -> Unit) {
    var renaming by remember { mutableStateOf<AlbumInfo?>(null) }
    Box(Modifier.fillMaxSize().background(Lc.canvas)) {
        Column(Modifier.fillMaxSize()) {
            StatusStrip()
            Row(
                Modifier.fillMaxWidth().background(Lc.bar).windowInsetsPadding(jobSides).height(60.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Jobs",
                    color = Lc.text,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = vm.engine != null, role = Role.Button) { vm.openAllPhotos() }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MdIcon(MdIcon.GridView, null, tint = Lc.selected, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("All photos", color = Lc.selected, fontFamily = Inter, fontSize = 15.sp)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(jobSides)) {
                val error = vm.loadError
                when {
                    error != null -> Text(
                        "LightCraft couldn't start its engine:\n$error",
                        color = Lc.text,
                        fontFamily = Inter,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    vm.engine == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Lc.highlight, trackColor = Lc.divider)
                    else -> JobsGrid(vm, onImportFolder, onRename = { renaming = it })
                }
                if (vm.importing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Lc.selected, trackColor = Lc.bar)
            }
            NavStrip()
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp),
        ) { LcToast(it) }
    }
    renaming?.let { job ->
        NameDialog("Rename job", "Rename", initial = job.name, onDismiss = { renaming = null }) { name ->
            renaming = null
            vm.renameAlbum(job, name)
        }
    }
}

@Composable
private fun JobsGrid(vm: AppViewModel, onImportFolder: () -> Unit, onRename: (AlbumInfo) -> Unit) {
    val jobs = vm.jobs
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "import", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ImportButton(enabled = !vm.importing, onClick = onImportFolder)
            }
        }
        if (jobs.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No jobs yet.\nImport a folder of photos and it becomes a job, named after the folder.",
                    color = Lc.textDim,
                    fontFamily = Inter,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                )
            }
        } else {
            item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                Text("Recent jobs", color = Lc.textDim, fontFamily = Inter, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            items(jobs, key = { it.id.toString() }) { job ->
                JobCard(vm, job, onOpen = { vm.openJob(job.id, job.name) }, onRename = { onRename(job) }, onDelete = { vm.deleteAlbum(job) })
            }
        }
    }
}

/** The screen's one big action: pick a folder, it becomes a job. */
@Composable
private fun ImportButton(enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Lc.primary else Lc.cell)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MdIcon(MdIcon.Folder, null, tint = Color.White, size = 26.dp)
            Spacer(Modifier.width(12.dp))
            Text(if (enabled) "Import photos" else "Importing…", color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Choose a folder. Its photos become a job named after it.",
            color = Color.White.copy(alpha = 0.8f),
            fontFamily = Inter,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 38.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JobCard(vm: AppViewModel, job: AlbumInfo, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val cover = job.cover
    val image by produceState<ImageBitmap?>(null, cover, job.count) {
        if (cover != null) vm.photoInfo(cover)?.let { p -> vm.thumbnail(p)?.let { value = it } }
    }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(role = Role.Button, onLongClickLabel = "Job options", onLongClick = { menu = true }, onClick = onOpen),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(10.dp)).background(Lc.cell), contentAlignment = Alignment.Center) {
                val img = image
                if (img != null) {
                    Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    LcIcon(LcIcon.Photos, null, tint = Lc.icon, size = 28.dp)
                }
            }
            Text(
                job.name,
                color = Lc.text,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
            )
            Text(
                if (job.count == 1u) "1 photo" else "${job.count} photos",
                color = Lc.textDim,
                fontFamily = Inter,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Lc.bottomBar) {
            MenuItem("Open") {
                menu = false
                onOpen()
            }
            MenuItem("Rename") {
                menu = false
                onRename()
            }
            MenuItem("Delete job (photos stay in the library)") {
                menu = false
                onDelete()
            }
        }
    }
}
