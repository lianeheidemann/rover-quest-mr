package io.github.lianeheidemann.roverquest.core.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DepthProjectionTest {

    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** Camera 1 m above the floor looking straight down (camera -Z → floor -Y). */
    private val lookingDown = floatArrayOf(
        // column 0: camera X → floor X
        1f, 0f, 0f, 0f,
        // column 1: camera Y → floor -Z
        0f, 0f, -1f, 0f,
        // column 2: camera Z → floor Y
        0f, 1f, 0f, 0f,
        // column 3: translation (0, 1, 0)
        0f, 1f, 0f, 1f,
    )

    private val intrinsics = CameraIntrinsics(fx = 100f, fy = 100f, cx = 2f, cy = 2f)

    private fun frame(mm: Int, confidence: Int? = null) = DepthFrame(
        5, 5, IntArray(25) { mm }, confidence?.let { c -> IntArray(25) { c } },
    )

    @Test
    fun `center pixel lands straight ahead of the camera`() {
        val samples = projectToFloor(frame(2000), intrinsics, identity, stride = 1)
        val center = samples[12]
        assertEquals(0f, center.x, 1e-5f)
        assertEquals(0f, center.height, 1e-5f)
        assertEquals(-2f, center.z, 1e-5f)
    }

    @Test
    fun `off-center pixels follow the pinhole model`() {
        // Pixel (4, 2): 2 px right of center at 1 m → x = 2 * 1 / 100.
        val samples = projectToFloor(frame(1000), intrinsics, identity, stride = 1)
        assertEquals(0.02f, samples[14].x, 1e-5f)
        // Pixel (2, 0): above center in the image → +Y.
        assertEquals(0.02f, samples[2].height, 1e-5f)
    }

    @Test
    fun `floor seen from above has zero height and a box stands above it`() {
        val floor = projectToFloor(frame(1000), intrinsics, lookingDown, stride = 1)
        assertTrue(floor.all { kotlin.math.abs(it.height) < 1e-4f })
        val box = projectToFloor(frame(800), intrinsics, lookingDown, stride = 1)
        assertTrue(box.all { kotlin.math.abs(it.height - 0.2f) < 1e-4f })
    }

    @Test
    fun `unreliable pixels are skipped`() {
        assertEquals(0, projectToFloor(frame(0), intrinsics, identity).size)
        assertEquals(0, projectToFloor(frame(5000), intrinsics, identity).size)
        assertEquals(0, projectToFloor(frame(1000, confidence = 20), intrinsics, identity).size)
        assertEquals(9, projectToFloor(frame(1000, confidence = 250), intrinsics, identity, stride = 2).size)
    }
}
