package ai.storyteller.lightcraft.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import kotlinx.coroutines.delay

/** How long a photo stays in the slideshow (the desktop's 4 s). */
const val SLIDE_MS = 4000L

/**
 * The grid's photos full screen, one every 4 s, wrapping (the desktop's slideshow): tap to pause
 * or go on, swipe for the next or previous one, back to end.
 */
@Composable
fun Slideshow(vm: AppViewModel) {
    val index = vm.slideshow ?: return
    var paused by remember { mutableStateOf(false) }
    BackHandler { vm.stopSlideshow() }
    // full screen: the system bars step aside (a swipe from an edge shows them for a moment)
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(index, paused) {
        if (paused) return@LaunchedEffect
        delay(SLIDE_MS)
        vm.stepSlideshow(1)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { vm.onSlideshowSize(it) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dx = 0f
                    var moved = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dx = change.position.x - down.position.x
                        if (abs(dx) > viewConfiguration.touchSlop) moved = true
                    }
                    when {
                        moved && abs(dx) > 72.dp.toPx() -> vm.stepSlideshow(if (dx < 0) 1 else -1)
                        !moved -> paused = !paused
                    }
                }
            },
    ) {
        vm.slideshowImage?.let { Image(it, contentDescription = vm.photos.getOrNull(index)?.fileName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        if (paused) {
            Text(
                "Paused · ${index + 1} of ${vm.photos.size}",
                color = Lc.text,
                fontFamily = Inter,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
