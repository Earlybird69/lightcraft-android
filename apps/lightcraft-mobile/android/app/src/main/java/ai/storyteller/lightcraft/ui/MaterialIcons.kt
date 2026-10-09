package ai.storyteller.lightcraft.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Google's Material Icons (Apache-2.0, the classic set as Android Studio bundles it), their paths
 * unchanged: the ones the reference app shows on its library screens (grid view, filter, close,
 * star…), each in the style that matches it best, a few drawn a little smaller or larger to its
 * sizes. Its other icons are its own, and LightCraft draws its own for those ([LcIcon]).
 * Source: github.com/google/material-design-icons.
 */
enum class MdIcon(private val paths: List<String>, private val scale: Float = 1f) : Glyph {
    /** `menu` (filled). */
    Menu(listOf("M3,18h18v-2L3,16v2zM3,13h18v-2L3,11v2zM3,6v2h18L21,6L3,6z")),
    /** `search` (filled), a little larger. */
    Search(listOf("M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"), 1.08f),
    /** `more_vert` (filled). */
    MoreVert(listOf("M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")),
    /** `expand_more` (round). */
    ExpandMore(listOf("M15.88,9.29L12,13.17 8.12,9.29c-0.39,-0.39 -1.02,-0.39 -1.41,0 -0.39,0.39 -0.39,1.02 0,1.41l4.59,4.59c0.39,0.39 1.02,0.39 1.41,0l4.59,-4.59c0.39,-0.39 0.39,-1.02 0,-1.41 -0.39,-0.38 -1.03,-0.39 -1.42,0z")),
    /** `filter_alt` (filled). */
    FilterAlt(listOf("M4.25,5.61C6.27,8.2 10,13 10,13v6c0,0.55 0.45,1 1,1h2c0.55,0 1,-0.45 1,-1v-6c0,0 3.72,-4.8 5.74,-7.39C20.25,4.95 19.78,4 18.95,4H5.04C4.21,4 3.74,4.95 4.25,5.61z")),
    /** `star` (round): the filter's stars. */
    Star(listOf("M12,17.27l4.15,2.51c0.76,0.46 1.69,-0.22 1.49,-1.08l-1.1,-4.72l3.67,-3.18c0.67,-0.58 0.31,-1.68 -0.57,-1.75l-4.83,-0.41l-1.89,-4.46c-0.34,-0.81 -1.5,-0.81 -1.84,0L9.19,8.63L4.36,9.04c-0.88,0.07 -1.24,1.17 -0.57,1.75l3.67,3.18l-1.1,4.72c-0.2,0.86 0.73,1.54 1.49,1.08L12,17.27z")),
    /** `sell` (round). */
    Sell(listOf("M21.41,11.41l-8.83,-8.83C12.21,2.21 11.7,2 11.17,2H4C2.9,2 2,2.9 2,4v7.17c0,0.53 0.21,1.04 0.59,1.41l8.83,8.83c0.78,0.78 2.05,0.78 2.83,0l7.17,-7.17C22.2,13.46 22.2,12.2 21.41,11.41zM6.5,8C5.67,8 5,7.33 5,6.5S5.67,5 6.5,5S8,5.67 8,6.5S7.33,8 6.5,8z"), 0.92f),
    /** `place` (filled). */
    Place(listOf("M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z")),
    /** `photo_camera` (filled). */
    PhotoCamera(listOf("M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0", "M9,2L7.17,4L4,4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2L9,2zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z")),
    /** `grid_view` (sharp). */
    GridView(listOf("M3,3h8v8h-8z", "M3,13h8v8h-8z", "M13,3h8v8h-8z", "M13,13h8v8h-8z"), 1.05f),
    /** `arrow_back` (filled). */
    ArrowBack(listOf("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")),
    /** `close` (filled). */
    Close(listOf("M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")),
    /** `add` (filled). */
    Add(listOf("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")),
    /** `star` (filled), a little larger: the sort sheet's. */
    StarSharp(listOf("M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"), 1.05f),
    /** `check` (round). */
    Check(listOf("M9,16.17L5.53,12.7c-0.39,-0.39 -1.02,-0.39 -1.41,0 -0.39,0.39 -0.39,1.02 0,1.41l4.18,4.18c0.39,0.39 1.02,0.39 1.41,0L20.29,7.71c0.39,-0.39 0.39,-1.02 0,-1.41 -0.39,-0.39 -1.02,-0.39 -1.41,0L9,16.17z")),
    /** `phone_iphone` (sharp). */
    PhoneIphone(listOf("M18,1L5,1v22h13L18,1zM11.5,22c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5zM16,18L7,18L7,4h9v14z")),
    /** `settings` (filled). */
    Settings(listOf("M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"), 0.95f),
    /** `help` (filled). */
    Help(listOf("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,19h-2v-2h2v2zM15.07,11.25l-0.9,0.92C13.45,12.9 13,13.5 13,15h-2v-0.5c0,-1.1 0.45,-2.1 1.17,-2.83l1.24,-1.26c0.37,-0.36 0.59,-0.86 0.59,-1.41 0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2L8,9c0,-2.21 1.79,-4 4,-4s4,1.79 4,4c0,0.88 -0.36,1.68 -0.93,2.25z"), 0.8f),
    /** `account_circle` (filled). */
    AccountCircle(listOf("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,5c1.66,0 3,1.34 3,3s-1.34,3 -3,3 -3,-1.34 -3,-3 1.34,-3 3,-3zM12,19.2c-2.5,0 -4.71,-1.28 -6,-3.22 0.03,-1.99 4,-3.08 6,-3.08 1.99,0 5.97,1.09 6,3.08 -1.29,1.94 -3.5,3.22 -6,3.22z"), 0.9f),
    /** `chevron_right` (filled). */
    ChevronRight(listOf("M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z"), 1.08f),
    /** `chevron_left` (filled): a collapsed list's heading. */
    ChevronLeft(listOf("M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z"), 1.08f),
    /** `info` (filled). */
    Info(listOf("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2L11,7h2v2z"), 0.8f),
    /** `folder` (filled). */
    Folder(listOf("M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"));

    /** The icon as a vector, built once: its paths as they are, scaled about the centre by [scale]. */
    val vector: ImageVector by lazy {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addGroup(pivotX = 12f, pivotY = 12f, scaleX = scale, scaleY = scale)
            paths.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
            clearGroup()
        }.build()
    }
}

@Composable
fun MdIcon(icon: MdIcon, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = Lc.icon, size: Dp = 24.dp) {
    Icon(icon.vector, contentDescription, modifier.size(size), tint = tint)
}
