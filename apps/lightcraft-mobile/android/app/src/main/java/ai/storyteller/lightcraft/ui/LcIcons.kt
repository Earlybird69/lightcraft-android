package ai.storyteller.lightcraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** An icon of either set: LightCraft's own ([LcIcon]) or Google's Material Icons ([MdIcon]). */
sealed interface Glyph

/** [glyph] at [size] (by default 22 dp for LightCraft's, 24 dp for Material's, their own sizes). */
@Composable
fun GlyphIcon(glyph: Glyph, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = Lc.icon, size: Dp? = null) {
    when (glyph) {
        is LcIcon -> LcIcon(glyph, contentDescription, modifier, tint, size ?: 22.dp)
        is MdIcon -> MdIcon(glyph, contentDescription, modifier, tint, size ?: 24.dp)
    }
}

/**
 * LightCraft's own icons, drawn the way the desktop draws them (`crates/ui-egui/src/icons.rs`): in
 * code, on a 20×20 grid, with 1.5-unit strokes and round caps. Original work, not derived from any
 * icon set. The entries after [Sort] are drawn for the phone in the same style; a few are solid
 * shapes with parts cut out, as the reference's are where its icons are its own.
 */
enum class LcIcon : Glyph {
    Back, ChevronLeft, ChevronRight, ChevronDown, Share, More, Presets, Star, StarFilled, FlagPick, FlagReject, Curve, Radial,
    BeforeAfter, Info, Plus, Trash, Edited, Sliders, Check, Close, Crop, Photos, Folder, Picker, ProfileGrid, Invert, Versions,
    Filter, Sort,
    Undo, Redo, Auto, Light, Color, Effects, Detail, Optics, Geometry, Calibration, Menu, AddPhoto, Download,
    Pencil, PencilOff, Import, Search, Camera, Person, Tag, Film, Calendar, GridSquare, GridJustified, Segments, SelectBox,
    AtLeast, Exactly, AtMost, FlagNone, FlagPickSolid, FlagRejectSolid, Gear, Phone, Help, Az, RotateLeft, RotateRight, FlipH, FlipV, Heal,
    Actions, FixAngle, Clone, HealCross, Level, Vertical, FullUpright, Original, FreeAspect, Target, Play, Minus, PhotoStack,
    DevicePhotos, Library,
}

/** An icon; [weight] scales its strokes (thinner for the large ones, as an illustration). */
@Composable
fun LcIcon(icon: LcIcon, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = Lc.icon, size: Dp = 22.dp, weight: Float = 1f) {
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    // its own layer: cut-outs clear the icon's pixels, not what's under it
    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.then(described)) { Pen(this, tint, weight).draw(icon) }
}

private class Pen(private val scope: DrawScope, private val color: Color, weight: Float) {
    private val side = min(scope.size.width, scope.size.height)
    private val k = side / 20f
    private val ox = (scope.size.width - side) / 2f
    private val oy = (scope.size.height - side) / 2f
    private val stroke = Stroke(width = max(1.5f * k * weight, 1f), cap = StrokeCap.Round, join = StrokeJoin.Round)

    private fun pt(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)

    private fun path(points: List<Pair<Float, Float>>, close: Boolean) = Path().apply {
        points.forEachIndexed { i, (x, y) ->
            val o = pt(x, y)
            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        if (close) close()
    }

    fun line(vararg p: Pair<Float, Float>) = scope.drawPath(path(p.toList(), false), color, style = stroke)

    fun closed(vararg p: Pair<Float, Float>) = scope.drawPath(path(p.toList(), true), color, style = stroke)

    fun fill(points: List<Pair<Float, Float>>) = scope.drawPath(path(points, true), color)

    fun circle(x: Float, y: Float, r: Float, c: Color = color) = scope.drawCircle(c, r * k, pt(x, y), style = stroke)

    fun dot(x: Float, y: Float, r: Float) = scope.drawCircle(color, r * k, pt(x, y))

    fun rect(x0: Float, y0: Float, x1: Float, y1: Float, round: Float) =
        scope.drawRoundRect(color, pt(x0, y0), Size((x1 - x0) * k, (y1 - y0) * k), CornerRadius(round * k), style = stroke)

    fun rectFill(x0: Float, y0: Float, x1: Float, y1: Float, round: Float) =
        scope.drawRoundRect(color, pt(x0, y0), Size((x1 - x0) * k, (y1 - y0) * k), CornerRadius(round * k))

    // cut-outs: clear what's drawn so far there (the icon's own layer)
    fun cutLine(vararg p: Pair<Float, Float>) = scope.drawPath(path(p.toList(), false), Color.Black, style = stroke, blendMode = BlendMode.Clear)

    fun cutRect(x0: Float, y0: Float, x1: Float, y1: Float, round: Float) =
        scope.drawRoundRect(Color.Black, pt(x0, y0), Size((x1 - x0) * k, (y1 - y0) * k), CornerRadius(round * k), blendMode = BlendMode.Clear)

    fun cutDot(x: Float, y: Float, r: Float) = scope.drawCircle(Color.Black, r * k, pt(x, y), blendMode = BlendMode.Clear)

    fun cutFill(points: List<Pair<Float, Float>>) = scope.drawPath(path(points, true), Color.Black, blendMode = BlendMode.Clear)

    /** Arc from angle [a0] to [a1] in degrees (y down, as egui), as 24 segments like the desktop. */
    fun arc(cx: Float, cy: Float, r: Float, a0: Float, a1: Float) = line(*arcPoints(cx, cy, r, a0, a1).toTypedArray())

    private fun arcPoints(cx: Float, cy: Float, r: Float, a0: Float, a1: Float) = (0..24).map { i ->
        val a = Math.toRadians((a0 + (a1 - a0) * i / 24f).toDouble())
        (cx + r * cos(a).toFloat()) to (cy + r * sin(a).toFloat())
    }

    private fun starPoints(cx: Float, cy: Float, r: Float) = (0 until 10).map { i ->
        val a = Math.toRadians(-90.0 + i * 36.0)
        val rr = if (i % 2 == 0) r else r * 0.45f
        (cx + rr * cos(a).toFloat()) to (cy + rr * sin(a).toFloat())
    }

    fun draw(icon: LcIcon) {
        when (icon) {
            LcIcon.Back -> {
                line(16f to 10f, 4f to 10f)
                line(9f to 5f, 4f to 10f, 9f to 15f)
            }
            LcIcon.ChevronLeft -> line(12.5f to 5f, 7.5f to 10f, 12.5f to 15f)
            LcIcon.ChevronRight -> line(7.5f to 5f, 12.5f to 10f, 7.5f to 15f)
            LcIcon.ChevronDown -> line(5f to 7.5f, 10f to 12.5f, 15f to 7.5f)
            LcIcon.Share -> {
                line(6f to 8f, 4f to 8f, 4f to 17f, 16f to 17f, 16f to 8f, 14f to 8f)
                line(10f to 12f, 10f to 2.5f)
                line(6.5f to 6f, 10f to 2.5f, 13.5f to 6f)
            }
            LcIcon.More -> listOf(5f, 10f, 15f).forEach { dot(it, 10f, 1.3f) }
            LcIcon.Presets -> {
                circle(8f, 8f, 5f)
                circle(12f, 12f, 5f)
            }
            LcIcon.Star -> closed(*starPoints(10f, 10.5f, 7.5f).toTypedArray())
            LcIcon.StarFilled -> fill(starPoints(10f, 10.5f, 7.5f))
            LcIcon.FlagPick -> {
                line(5f to 17f, 5f to 3.5f)
                closed(5f to 3.5f, 15f to 3.5f, 12.5f to 7f, 15f to 10.5f, 5f to 10.5f)
            }
            LcIcon.FlagReject -> {
                line(5f to 17f, 5f to 3.5f)
                closed(5f to 3.5f, 15f to 3.5f, 12.5f to 7f, 15f to 10.5f, 5f to 10.5f)
                line(8f to 5f, 12f to 9f)
                line(12f to 5f, 8f to 9f)
            }
            LcIcon.Curve -> {
                rect(3f, 3f, 17f, 17f, 1.5f)
                line(*(0..12).map { i ->
                    val t = i / 12f
                    val y = t * t * (3f - 2f * t)
                    (4.5f + 11f * t) to (15.5f - 11f * y)
                }.toTypedArray())
            }
            LcIcon.Radial -> {
                circle(10f, 10f, 7f)
                circle(10f, 10f, 3.5f, color.copy(alpha = color.alpha * 0.5f))
            }
            LcIcon.BeforeAfter -> {
                rect(3f, 4f, 17f, 16f, 1f)
                line(10f to 2.5f, 10f to 17.5f)
                rectFill(10f, 4f, 17f, 16f, 0.5f)
            }
            LcIcon.Info -> {
                circle(10f, 10f, 7.5f)
                line(10f to 9f, 10f to 14f)
                dot(10f, 6.3f, 0.9f)
            }
            LcIcon.Plus -> {
                line(10f to 4f, 10f to 16f)
                line(4f to 10f, 16f to 10f)
            }
            LcIcon.Trash -> {
                // a solid bin with three slits, its lid and handle
                rectFill(3f, 4.3f, 17f, 6f, 0.5f)
                rectFill(8f, 2.6f, 12f, 4.6f, 0.7f)
                fill(listOf(4.8f to 7f, 15.2f to 7f, 14.2f to 17.5f, 5.8f to 17.5f))
                cutLine(7.9f to 9.5f, 8.2f to 15f)
                cutLine(10f to 9.5f, 10f to 15f)
                cutLine(12.1f to 9.5f, 11.8f to 15f)
            }
            LcIcon.Edited -> {
                circle(10f, 10f, 7f)
                line(7f to 13f, 13f to 7f)
            }
            LcIcon.Sliders -> listOf(5f to 12f, 10f to 7f, 15f to 13f).forEach { (y, x) ->
                line(3f to y, (x - 2f) to y)
                line((x + 2f) to y, 17f to y)
                circle(x, y, 2f)
            }
            LcIcon.Check -> line(4f to 10.5f, 8.5f to 15f, 16f to 5.5f)
            LcIcon.Close -> {
                line(5f to 5f, 15f to 15f)
                line(15f to 5f, 5f to 15f)
            }
            LcIcon.Crop -> {
                line(6f to 2.5f, 6f to 14f, 17.5f to 14f)
                line(2.5f to 6f, 14f to 6f, 14f to 17.5f)
                line(15.5f to 2f, 17.5f to 4f, 15.5f to 6f)
            }
            LcIcon.Photos -> {
                rect(2.5f, 4f, 17.5f, 16f, 1.5f)
                line(2.5f to 13.5f, 7f to 9.5f, 11f to 13f, 13.5f to 11f, 17.5f to 14.5f)
                dot(13.5f, 7.5f, 1.3f)
            }
            LcIcon.Folder -> closed(2.5f to 5f, 8f to 5f, 9.5f to 7f, 17.5f to 7f, 17.5f to 15.5f, 2.5f to 15.5f)
            LcIcon.Picker -> {
                line(4f to 16f, 11.5f to 8.5f)
                closed(11f to 5f, 15f to 9f, 17f to 7f, 13f to 3f)
            }
            LcIcon.ProfileGrid -> {
                rectFill(3.5f, 3.5f, 9f, 9f, 0.5f)
                rectFill(11f, 3.5f, 16.5f, 9f, 0.5f)
                rectFill(3.5f, 11f, 9f, 16.5f, 0.5f)
                circle(14f, 14f, 2.5f)
                line(15.8f to 15.8f, 17.5f to 17.5f)
            }
            LcIcon.Invert -> {
                circle(10f, 10f, 7f)
                fill(arcPoints(10f, 10f, 7f, 90f, 270f))
            }
            LcIcon.Versions -> {
                rect(5f, 3f, 17f, 13f, 1.5f)
                line(3f to 6f, 3f to 16f, 14f to 16f)
            }
            LcIcon.Filter -> closed(3f to 4f, 17f to 4f, 11.5f to 10.5f, 11.5f to 16f, 8.5f to 14.5f, 8.5f to 10.5f)
            LcIcon.Sort -> {
                // shorter lines down, and the order's arrow beside them
                line(2.75f to 5.25f, 11f to 5.25f)
                line(2.75f to 10f, 9f to 10f)
                line(2.75f to 14.75f, 7f to 14.75f)
                line(14.75f to 5f, 14.75f to 14.9f)
                line(12.65f to 12.8f, 14.75f to 14.9f, 16.85f to 12.8f)
            }
            // --- drawn for the phone, in the same style
            LcIcon.Undo -> {
                line(7.5f to 2.5f, 4f to 6f, 7.5f to 9.5f)
                line(4f to 6f, 12f to 6f)
                arc(12f, 11f, 5f, 270f, 450f)
                line(12f to 16f, 6f to 16f)
            }
            LcIcon.Redo -> {
                line(12.5f to 2.5f, 16f to 6f, 12.5f to 9.5f)
                line(16f to 6f, 8f to 6f)
                arc(8f, 11f, 5f, 270f, 90f)
                line(8f to 16f, 14f to 16f)
            }
            LcIcon.Auto -> {
                line(4f to 16f, 12f to 8f)
                line(14.5f to 2.5f, 14.5f to 8.5f)
                line(11.5f to 5.5f, 17.5f to 5.5f)
                dot(16.5f, 11f, 0.9f)
                dot(9f, 3.5f, 0.9f)
            }
            LcIcon.Light -> {
                circle(10f, 10f, 3.5f)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val (c, s) = cos(a).toFloat() to sin(a).toFloat()
                    line((10f + 6f * c) to (10f + 6f * s), (10f + 8.5f * c) to (10f + 8.5f * s))
                }
            }
            LcIcon.Color -> {
                arc(10f, 12f, 5f, -30f, 210f)
                line(14.33f to 9.5f, 10f to 2.5f, 5.67f to 9.5f)
            }
            LcIcon.Effects -> {
                rect(2.5f, 4f, 17.5f, 16f, 1.5f)
                circle(10f, 10f, 3.5f)
            }
            LcIcon.Detail -> closed(10f to 3.5f, 17f to 16f, 3f to 16f)
            LcIcon.Optics -> {
                circle(10f, 10f, 7f)
                circle(10f, 10f, 3f)
                dot(12.6f, 7.4f, 0.9f)
            }
            LcIcon.Geometry -> {
                closed(5.5f to 4f, 14.5f to 4f, 17.5f to 16f, 2.5f to 16f)
                line(10f to 4f, 10f to 16f)
                line(4f to 10f, 16f to 10f)
            }
            LcIcon.Calibration -> {
                circle(10f, 10f, 6f)
                line(10f to 2f, 10f to 6f)
                line(10f to 14f, 10f to 18f)
                line(2f to 10f, 6f to 10f)
                line(14f to 10f, 18f to 10f)
                dot(10f, 10f, 1f)
            }
            LcIcon.Menu -> listOf(5f, 10f, 15f).forEach { y -> line(3f to y, 17f to y) }
            LcIcon.AddPhoto -> {
                // a photo (a solid hill, a sun), a round plus over its corner
                rect(1f, 1.7f, 14.6f, 13.6f, 1.2f)
                fill(listOf(1f to 13.6f, 1f to 10.6f, 4.7f to 6.9f, 8.4f to 10.6f, 10.2f to 8.9f, 12.6f to 11.3f, 12.6f to 13.6f))
                dot(11f, 5f, 1.4f)
                cutDot(15f, 15f, 5.7f)
                dot(15f, 15f, 4.6f)
                cutLine(15f to 12.5f, 15f to 17.5f)
                cutLine(12.5f to 15f, 17.5f to 15f)
            }
            LcIcon.Download -> {
                line(10f to 3f, 10f to 13f)
                line(6f to 9.5f, 10f to 13.5f, 14f to 9.5f)
                line(4f to 16.5f, 16f to 16.5f)
            }
            LcIcon.Pencil -> {
                // a solid body, an open tip
                fill(listOf(4.2f to 11.95f, 14.8f to 1.35f, 17.8f to 4.35f, 7.2f to 14.95f))
                closed(2.7f to 16.45f, 4.2f to 11.95f, 7.2f to 14.95f)
            }
            LcIcon.PencilOff -> {
                fill(listOf(3.8f to 12.3f, 14.4f to 1.7f, 17.4f to 4.7f, 6.8f to 15.3f))
                closed(2.3f to 16.8f, 3.8f to 12.3f, 6.8f to 15.3f)
                cutLine(4.2f to 0.9f, 19.2f to 15.9f)
                line(2.6f to 1.7f, 17.4f to 16.5f)
            }
            LcIcon.Import -> {
                line(8.6f to 5.6f, 8.6f to 2.8f, 17.6f to 2.8f, 17.6f to 17.2f, 8.6f to 17.2f, 8.6f to 14.4f)
                line(1.4f to 10f, 12.6f to 10f)
                line(9.2f to 6.6f, 12.6f to 10f, 9.2f to 13.4f)
            }
            LcIcon.Search -> {
                circle(8.5f, 8.5f, 5f)
                line(12.2f to 12.2f, 16.5f to 16.5f)
            }
            LcIcon.Camera -> {
                closed(2.5f to 6.5f, 6.5f to 6.5f, 8f to 4f, 12f to 4f, 13.5f to 6.5f, 17.5f to 6.5f, 17.5f to 16f, 2.5f to 16f)
                circle(10f, 11f, 3f)
            }
            LcIcon.Person -> {
                circle(10f, 7f, 3.2f)
                line(*arcPoints(10f, 17f, 6f, 180f, 360f).toTypedArray())
            }
            LcIcon.Tag -> {
                closed(3f to 3f, 10f to 3f, 17f to 10f, 10f to 17f, 3f to 10f)
                dot(6.8f, 6.8f, 1.1f)
            }
            LcIcon.Film -> {
                // a solid strip, its sprocket holes and frame cut out
                rectFill(2.5f, 2.1f, 17.5f, 17.9f, 1.2f)
                listOf(4.3f, 8.1f, 11.9f, 15.7f).forEach { y ->
                    cutRect(3.6f, y - 0.95f, 5.4f, y + 0.95f, 0.3f)
                    cutRect(14.6f, y - 0.95f, 16.4f, y + 0.95f, 0.3f)
                }
                cutRect(6.8f, 3.4f, 13.2f, 16.6f, 0.6f)
            }
            LcIcon.Calendar -> {
                rect(3f, 4.5f, 17f, 17f, 1.5f)
                line(7f to 2.5f, 7f to 6f)
                line(13f to 2.5f, 13f to 6f)
                // its days
                for (r in 0..2) for (c in 0..3) dot(5.8f + c * 2.8f, 9.4f + r * 2.6f, 0.75f)
            }
            LcIcon.GridSquare -> {
                rectFill(3f, 3f, 9f, 9f, 0.5f)
                rectFill(11f, 3f, 17f, 9f, 0.5f)
                rectFill(3f, 11f, 9f, 17f, 0.5f)
                rectFill(11f, 11f, 17f, 17f, 0.5f)
            }
            LcIcon.GridJustified -> {
                rectFill(2.1f, 2.1f, 11.6f, 9.2f, 0.5f)
                rectFill(13.2f, 2.1f, 17.9f, 9.2f, 0.5f)
                rectFill(2.1f, 10.8f, 6.8f, 17.9f, 0.5f)
                rectFill(8.4f, 10.8f, 17.9f, 17.9f, 0.5f)
            }
            LcIcon.Segments -> {
                // a segment's heading over its rows
                dot(2.2f, 4f, 1.6f)
                line(5.6f to 4f, 18.6f to 4f)
                rectFill(0.7f, 8.2f, 19.3f, 12f, 0.8f)
                rectFill(0.7f, 14.1f, 19.3f, 17.9f, 0.8f)
            }
            LcIcon.SelectBox -> {
                // two cards, the front one solid with its check cut out
                rectFill(1.3f, 1.3f, 13.4f, 13.4f, 2.2f)
                cutRect(4.5f, 4.5f, 20f, 20f, 3f)
                rectFill(5.7f, 5.7f, 18.7f, 18.7f, 2.2f)
                cutLine(8.6f to 12.3f, 11.2f to 14.9f, 15.8f to 10.1f)
            }
            LcIcon.AtLeast -> {
                line(5.75f to 4.5f, 14.25f to 7.5f, 5.75f to 10.5f)
                line(5.75f to 13.5f, 14.25f to 13.5f)
            }
            LcIcon.Exactly -> {
                line(5.75f to 6.75f, 14.25f to 6.75f)
                line(5.75f to 11.25f, 14.25f to 11.25f)
            }
            LcIcon.AtMost -> {
                line(14.25f to 4.5f, 5.75f to 7.5f, 14.25f to 10.5f)
                line(5.75f to 13.5f, 14.25f to 13.5f)
            }
            LcIcon.FlagNone -> {
                line(4.5f to 18f, 4.5f to 2.8f)
                closed(4.5f to 3.5f, 15.5f to 3.5f, 15.5f to 11f, 4.5f to 11f)
            }
            LcIcon.FlagPickSolid -> {
                // a solid flag, a check cut out of it
                line(4.5f to 18f, 4.5f to 2.8f)
                rectFill(4.5f, 2.8f, 16.3f, 11.7f, 0.4f)
                cutLine(7.6f to 7.3f, 9.6f to 9.3f, 13.4f to 5.3f)
            }
            LcIcon.FlagRejectSolid -> {
                line(4.5f to 18f, 4.5f to 2.8f)
                rectFill(4.5f, 2.8f, 16.3f, 11.7f, 0.4f)
                cutLine(8.3f to 5.1f, 12.5f to 9.3f)
                cutLine(12.5f to 5.1f, 8.3f to 9.3f)
            }
            LcIcon.RotateLeft -> {
                rect(7f, 8f, 17f, 17f, 1f)
                arc(7f, 8f, 5f, 180f, 270f)
                line(4.5f to 6f, 2f to 8f, 0.5f to 5.5f)
            }
            LcIcon.RotateRight -> {
                rect(3f, 8f, 13f, 17f, 1f)
                arc(13f, 8f, 5f, 270f, 360f)
                line(15.5f to 6f, 18f to 8f, 19.5f to 5.5f)
            }
            LcIcon.FlipH -> {
                line(10f to 2.5f, 10f to 17.5f)
                closed(8f to 5f, 8f to 15f, 2.5f to 15f)
                fill(listOf(12f to 5f, 12f to 15f, 17.5f to 15f))
            }
            LcIcon.FlipV -> {
                line(2.5f to 10f, 17.5f to 10f)
                closed(5f to 8f, 15f to 8f, 15f to 2.5f)
                fill(listOf(5f to 12f, 15f to 12f, 15f to 17.5f))
            }
            LcIcon.Heal -> {
                closed(2.5f to 12.5f, 12.5f to 2.5f, 17.5f to 7.5f, 7.5f to 17.5f)
                line(7.5f to 7.5f, 12.5f to 12.5f)
                dot(9f, 11f, 0.7f)
                dot(11f, 9f, 0.7f)
            }
            LcIcon.Gear -> {
                circle(10f, 10f, 2.5f)
                circle(10f, 10f, 5.5f)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val (c, s) = cos(a).toFloat() to sin(a).toFloat()
                    line((10f + 5.5f * c) to (10f + 5.5f * s), (10f + 7.5f * c) to (10f + 7.5f * s))
                }
            }
            LcIcon.Phone -> {
                rect(5.5f, 2.5f, 14.5f, 17.5f, 1.5f)
                line(8.5f to 15f, 11.5f to 15f)
            }
            LcIcon.Help -> {
                circle(10f, 10f, 7.5f)
                arc(10f, 8f, 2.5f, 180f, 405f)
                line(11.8f to 9.8f, 10f to 11f, 10f to 12f)
                dot(10f, 14.5f, 0.9f)
            }
            LcIcon.Az -> {
                line(2f to 11.2f, 5.6f to 2.2f, 9.2f to 11.2f)
                line(3.3f to 8f, 7.9f to 8f)
                line(10.6f to 10.4f, 17.6f to 10.4f, 10.6f to 17.6f, 17.6f to 17.6f)
            }
            LcIcon.Actions -> closed(11.5f to 2f, 4.5f to 11f, 9.5f to 11f, 8.5f to 18f, 15.5f to 9f, 10.5f to 9f)
            LcIcon.Play -> fill(listOf(3.5f to 3.3f, 16.5f to 10f, 3.5f to 16.7f))
            LcIcon.PhotoStack -> {
                // a photo (a solid hill, a sun) with another behind it
                line(3.5f to 17f, 18.2f to 17f, 18.2f to 4.2f)
                rect(0.9f, 1.6f, 15.6f, 14.1f, 1f)
                fill(listOf(0.9f to 14.1f, 0.9f to 11.2f, 4.9f to 7.2f, 8.8f to 11.2f, 10.8f to 9.4f, 15.6f to 13.4f, 15.6f to 14.1f))
                dot(11.6f, 5.4f, 1.4f)
            }
            LcIcon.Minus -> line(4.5f to 10f, 15.5f to 10f)
            LcIcon.DevicePhotos -> {
                // a phone, a photo (a solid hill, a sun) in front of it
                rect(1.65f, 1.65f, 11.5f, 18.35f, 1.4f)
                line(5.3f to 3.9f, 7.8f to 3.9f)
                cutRect(6.3f, 6.6f, 19.85f, 17.6f, 1f)
                rect(7.05f, 7.35f, 19.1f, 16.85f, 0.8f)
                fill(listOf(7.05f to 16.85f, 7.05f to 14.6f, 10.4f to 11.4f, 13.3f to 14.2f, 14.9f to 12.8f, 19.1f to 16.2f, 19.1f to 16.85f))
                dot(15.8f, 10.3f, 1.1f)
            }
            LcIcon.Library -> {
                // LightCraft's library: a solid tile, a photo's hill and sun cut out of it
                rectFill(1.8f, 1.8f, 18.2f, 18.2f, 2.5f)
                cutFill(listOf(4f to 15.6f, 8f to 10.2f, 11f to 13.4f, 12.8f to 11.6f, 16f to 15.6f))
                cutDot(12.8f, 6.6f, 1.6f)
            }
            LcIcon.Target -> {
                circle(8.5f, 10f, 5f)
                dot(8.5f, 10f, 1.1f)
                line(16.5f to 3f, 16.5f to 17f)
                line(14.5f to 5f, 16.5f to 3f, 18.5f to 5f)
                line(14.5f to 15f, 16.5f to 17f, 18.5f to 15f)
            }
            LcIcon.FixAngle -> {
                closed(4f to 7.5f, 14.5f to 4f, 16f to 12.5f, 5.5f to 16f)
                line(2f to 10f, 18f to 10f)
            }
            LcIcon.Clone -> {
                circle(10f, 5f, 2.5f)
                line(10f to 7.5f, 10f to 11f)
                rect(4f, 11f, 16f, 14.5f, 1f)
                line(4.5f to 17f, 15.5f to 17f)
            }
            LcIcon.HealCross -> {
                circle(10f, 10f, 7f)
                line(10f to 6.5f, 10f to 13.5f)
                line(6.5f to 10f, 13.5f to 10f)
            }
            LcIcon.Level -> {
                rect(2.5f, 4.5f, 17.5f, 15.5f, 1.5f)
                line(5.5f to 10f, 14.5f to 10f)
            }
            LcIcon.Vertical -> {
                rect(2.5f, 4.5f, 17.5f, 15.5f, 1.5f)
                line(7.5f to 7f, 7.5f to 13f)
                line(12.5f to 7f, 12.5f to 13f)
            }
            LcIcon.FullUpright -> {
                rect(2.5f, 4.5f, 17.5f, 15.5f, 1.5f)
                line(5.5f to 10f, 14.5f to 10f)
                line(10f to 7f, 10f to 13f)
            }
            LcIcon.Original -> {
                line(3f to 7f, 3f to 4f, 6f to 4f)
                line(14f to 4f, 17f to 4f, 17f to 7f)
                line(17f to 13f, 17f to 16f, 14f to 16f)
                line(6f to 16f, 3f to 16f, 3f to 13f)
                dot(10f, 10f, 1.2f)
            }
            LcIcon.FreeAspect -> {
                rect(3f, 4f, 17f, 16f, 1.5f)
                line(7f to 13f, 13f to 7f)
                line(9.5f to 7f, 13f to 7f, 13f to 10.5f)
                line(7f to 9.5f, 7f to 13f, 10.5f to 13f)
            }
        }
    }
}
