package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.core.PhotoFlag
import ai.storyteller.lightcraft.core.PhotoInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The screens' pure helpers: the grid's days and the white balance selector's point on the photo. */
class LayoutLogicTest {
    private fun photo(id: Int, captured: String?, aspect: Double = 1.5) = PhotoInfo(
        id.toULong(), "IMG_$id.jpg", "JPEG", false, 4000u, 3000u, captured, 0u, PhotoFlag.UNFLAGGED, false, 0u, "", "", "", aspect, "2026-10-01T10:00:00", null,
    )

    private val days = listOf(
        photo(1, "2025-09-10T18:00:00"),
        photo(2, "2025-09-10T09:30:00"),
        photo(3, "2025-09-09T12:00:00"),
        photo(4, "2024-12-31T23:00:00"),
    )

    @Test
    fun photosSegmentByTheUnitOfTheSortDate() {
        val byDay = segments(days, "captureDate", "day")
        assertEquals(listOf("2025-09-10", "2025-09-09", "2024-12-31"), byDay.map { it.key })
        assertEquals(listOf(2, 1, 1), byDay.map { it.photos.size })
        assertEquals(listOf(1, 2), byDay[0].photos.map { it.id.toInt() })
        assertEquals(listOf("2025-09", "2024-12"), segments(days, "captureDate", "month").map { it.key })
        assertEquals(listOf("2025", "2024"), segments(days, "captureDate", "year").map { it.key })
        assertEquals(4, segments(days, "captureDate", "hour").size)
        // every photo here was imported at the same hour
        assertEquals(1, segments(days, "importDate", "day").size)
    }

    @Test
    fun noSegmentsWithoutAUnitOrADateSort() {
        listOf(segments(days, "captureDate", "none"), segments(days, "fileName", "day")).forEach {
            assertEquals(1, it.size)
            assertEquals(null, it[0].label)
            assertEquals(4, it[0].photos.size)
        }
        assertEquals(0, segments(emptyList(), "captureDate", "day").size)
    }

    @Test
    fun photosWithoutAUsableDateShareOneHeading() {
        val runs = segments(listOf(photo(1, null), photo(2, "not a date"), photo(3, "")), "captureDate", "day")
        assertEquals(1, runs.size)
        assertEquals("", runs[0].key)
        assertEquals("Unknown date", runs[0].label)
        assertEquals(3, runs[0].photos.size)
    }

    @Test
    fun justifiedRowsFillTheWidthAtMostTheTargetTall() {
        // four 3:2 photos in a 400 wide grid, 120 tall at most, gaps of 4
        val rows = justify(listOf(1.5, 1.5, 1.5, 1.5, 0.75), 400f, 120f, 4f)
        // two 3:2 photos fill 400 at (400 - 4) / 3 = 132 > 120, three at (400 - 8) / 4.5 = 87.1
        assertEquals(GridRow(0, 3, (400f - 8f) / 4.5f), rows[0])
        // the rest keeps the target height, at the left
        assertEquals(GridRow(3, 5, 120f), rows[1])
        assertEquals(0, justify(listOf(1.0), 0f, 120f, 4f).size)
    }

    @Test
    fun squareRowsHoldColumnsEach() {
        assertEquals(listOf(GridRow(0, 4, 100f), GridRow(4, 8, 100f), GridRow(8, 9, 100f)), squareRows(9, 4, 100f))
        assertEquals(0, squareRows(3, 0, 100f).size)
    }

    @Test
    fun theViewsCentreIsThePhotosCentre() {
        // a 3:2 photo fitted to a tall view: letterboxed above and below
        val p = photoPoint(Offset(500f, 1000f), IntSize(1000, 2000), 3000, 2000, 1f, Offset.Zero)
        assertNotNull(p)
        assertEquals(0.5f, p!!.x, 1e-4f)
        assertEquals(0.5f, p.y, 1e-4f)
    }

    @Test
    fun theLetterboxIsOffThePhoto() {
        // the photo fills 1000 × 667 in the middle of the 2000 px tall view: y = 100 is above it
        assertNull(photoPoint(Offset(500f, 100f), IntSize(1000, 2000), 3000, 2000, 1f, Offset.Zero))
    }

    @Test
    fun zoomAndPanAreTakenBack() {
        // zoomed 2× about the centre and moved 200 px right: the view's centre shows the point
        // 100 px left of the photo's centre, a tenth of the photo's 1000 px width
        val p = photoPoint(Offset(500f, 1000f), IntSize(1000, 2000), 3000, 2000, 2f, Offset(200f, 0f))
        assertNotNull(p)
        assertEquals(0.4f, p!!.x, 1e-4f)
        assertEquals(0.5f, p.y, 1e-4f)
    }

    @Test
    fun degenerateSizesAreNoPoint() {
        assertNull(photoPoint(Offset(1f, 1f), IntSize(0, 0), 3000, 2000, 1f, Offset.Zero))
        assertNull(photoPoint(Offset(1f, 1f), IntSize(100, 100), 0, 2000, 1f, Offset.Zero))
        assertNull(photoPoint(Offset(1f, 1f), IntSize(100, 100), 3000, 2000, 0f, Offset.Zero))
    }

    private fun assertPoint(expected: Pair<Double, Double>, actual: Pair<Double, Double>) {
        assertEquals(expected.first, actual.first, 1e-9)
        assertEquals(expected.second, actual.second, 1e-9)
    }

    @Test
    fun curvePointsMoveBetweenTheirNeighbours() {
        val line = curvePoints(emptyList())
        assertEquals(listOf(0.0 to 0.0, 1.0 to 1.0), line)
        val (three, i) = withPoint(line, 0.5, 0.5)!!
        assertEquals(1, i)
        // an inner point keeps 1 % from its neighbours; y stays in 0..1
        assertPoint(0.99 to 1.0, movedPoint(three, 1, 1.4, 1.2)[1])
        assertPoint(0.01 to 0.0, movedPoint(three, 1, -0.3, -0.1)[1])
        // the ends keep to their side
        assertPoint(0.0 to 0.2, movedPoint(three, 0, -0.5, 0.2)[0])
        assertPoint(0.49 to 0.3, movedPoint(three, 0, 0.8, 0.3)[0])
        assertPoint(0.51 to 0.9, movedPoint(three, 2, 0.1, 0.9)[2])
        // no second point at the same input
        assertNull(withPoint(three, 0.505, 0.2))
        assertEquals(listOf(0.0, 0.0, 0.5, 0.5, 1.0, 1.0), three.flat())
        assertEquals(three, movedPoint(three, 7, 0.5, 0.5))
    }

    @Test
    fun aSampledCurveIsReadBetweenItsSamples() {
        assertEquals(0.25, sampledAt(listOf(0.0, 0.5, 1.0), 0.25), 1e-9)
        assertEquals(1.0, sampledAt(listOf(0.0, 0.5, 1.0), 2.0), 1e-9)
        assertEquals(0.3, sampledAt(emptyList(), 0.3), 1e-9)
        assertEquals("curve.shadows", curveRegion(0.1, 25.0, 50.0, 75.0))
        assertEquals("curve.darks", curveRegion(0.3, 25.0, 50.0, 75.0))
        assertEquals("curve.lights", curveRegion(0.6, 25.0, 50.0, 75.0))
        assertEquals("curve.highlights", curveRegion(0.9, 25.0, 50.0, 75.0))
    }

    @Test
    fun aWheelsPointIsAHueAroundItAndASaturationOut() {
        assertPoint(0.0 to 100.0, wheelValue(1.0, 0.0, 1.0))
        // up the screen is 90°, as on the desktop's wheels
        assertPoint(90.0 to 100.0, wheelValue(0.0, -1.0, 1.0))
        assertPoint(180.0 to 50.0, wheelValue(-0.5, 0.0, 1.0))
        assertPoint(270.0 to 100.0, wheelValue(0.0, 3.0, 1.0))
        assertPoint(0.0 to 0.0, wheelValue(0.0, 0.0, 0.0))
    }

    @Test
    fun theGallerysPhotosRunByDayInThePhonesZone() {
        val utc = java.time.ZoneId.of("UTC")
        val day = 86_400_000L
        // newest first: two on the 2nd of January 1970, one on the 1st
        val runs = byDay(listOf(day + 5_000L, day + 1_000L, 2_000L), utc) { it }
        assertEquals(listOf("1970-01-02", "1970-01-01"), runs.map { it.first })
        assertEquals(listOf(2, 1), runs.map { it.second.size })
        // the same moment is the 1st in New York
        assertEquals("1970-01-01", byDay(listOf(day + 1_000L), java.time.ZoneId.of("America/New_York")) { it }.single().first)
        assertEquals(0, byDay(emptyList<Long>(), utc) { it }.size)
    }
}
