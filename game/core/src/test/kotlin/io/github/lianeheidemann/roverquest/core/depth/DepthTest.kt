package io.github.lianeheidemann.roverquest.core.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepthTest {

    private fun frame(width: Int, height: Int, mm: Int, confidence: Int? = null) = DepthFrame(
        width,
        height,
        IntArray(width * height) { mm },
        confidence?.let { c -> IntArray(width * height) { c } },
    )

    @Test
    fun `center sample is the median of valid pixels`() {
        val f = frame(5, 5, 1000)
        f.depthMm[12] = 0 // center without data is ignored
        f.depthMm[6] = 9000 // outlier does not move the median
        val sample = f.sampleAt(0.5f, 0.5f, radius = 1)
        assertEquals(1.0f, sample.meters)
        assertNull(sample.confidence)
    }

    @Test
    fun `sample without data reports no depth`() {
        assertEquals(DepthSample(null, null), frame(4, 4, 0).sampleAt(0.5f, 0.5f))
    }

    @Test
    fun `confidence is averaged and normalized`() {
        val sample = frame(3, 3, 1500, confidence = 255).sampleAt(0.5f, 0.5f, radius = 1)
        assertEquals(1f, sample.confidence)
    }

    @Test
    fun `coverage counts pixels with data`() {
        val f = frame(2, 2, 1000)
        f.depthMm[0] = 0
        assertEquals(0.75f, f.coverage)
    }

    @Test
    fun `quality follows support and arrival`() {
        assertEquals(DepthQuality.NOT_SUPPORTED, analyzeDepth(supported = false, frame = frame(4, 4, 1000)).quality)
        assertEquals(DepthQuality.WAITING, analyzeDepth(supported = true, frame = null).quality)
        val good = analyzeDepth(supported = true, frame = frame(8, 8, 1200, confidence = 230))
        assertEquals(DepthQuality.GOOD, good.quality)
        assertEquals(1.2f, good.center?.meters)
    }

    @Test
    fun `uncertain readings say why`() {
        val sparse = frame(10, 10, 0).also { f -> for (i in 0 until 40) f.depthMm[i] = 1000 }
        assertEquals(DepthIssue.LOW_COVERAGE, analyzeDepth(true, sparse).issue)

        val holeInCenter = frame(10, 10, 1000).also { f ->
            for (y in 2..7) for (x in 2..7) f.depthMm[y * 10 + x] = 0
        }
        assertEquals(DepthIssue.NO_DATA_AT_CENTER, analyzeDepth(true, holeInCenter).issue)

        assertEquals(DepthIssue.LOW_CONFIDENCE, analyzeDepth(true, frame(8, 8, 1000, confidence = 40)).issue)
        assertEquals(DepthIssue.OUT_OF_RANGE, analyzeDepth(true, frame(8, 8, 7000)).issue)
        assertEquals(DepthIssue.OUT_OF_RANGE, analyzeDepth(true, frame(8, 8, 100)).issue)
        assertEquals(DepthQuality.UNCERTAIN, analyzeDepth(true, frame(8, 8, 7000)).quality)
    }

    @Test
    fun `colors go from warm near to cool far and skip missing data`() {
        assertEquals(0, depthColor(0))
        assertEquals(0xFFE53935.toInt(), depthColor(100))
        assertEquals(0xFF1E88E5.toInt(), depthColor(10_000))
        val mid = depthColor((COLOR_RAMP_NEAR_MM + COLOR_RAMP_FAR_MM) / 2)
        assertTrue(mid ushr 24 == 0xFF, "opaque")
    }

    @Test
    fun `colorize turns the image clockwise`() {
        // 3 wide × 2 tall; mark the top-left pixel as near (red), the rest far (blue).
        val f = frame(3, 2, 10_000)
        f.depthMm[0] = 100
        val red = 0xFFE53935.toInt()

        val same = colorize(f, 0)
        assertEquals(3, same.width)
        assertEquals(red, same.argb[0])

        val quarter = colorize(f, 1)
        assertEquals(2, quarter.width)
        assertEquals(3, quarter.height)
        // Top-left goes to top-right after a clockwise quarter turn.
        assertEquals(red, quarter.argb[quarter.width - 1])

        val half = colorize(f, 2)
        assertEquals(red, half.argb.last())

        val threeQuarters = colorize(f, 3)
        // Top-left goes to bottom-left after a counter-clockwise quarter turn.
        assertEquals(red, threeQuarters.argb[(threeQuarters.height - 1) * threeQuarters.width])
    }
}
