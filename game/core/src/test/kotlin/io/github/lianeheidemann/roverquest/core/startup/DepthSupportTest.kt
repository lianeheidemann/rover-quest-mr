package io.github.lianeheidemann.roverquest.core.startup

import kotlin.test.Test
import kotlin.test.assertEquals

class DepthSupportTest {

    @Test
    fun `maps ARCore depth flags`() {
        assertEquals(DepthSupport.AUTOMATIC_AND_RAW, DepthSupport.from(automatic = true, raw = true))
        assertEquals(DepthSupport.AUTOMATIC_ONLY, DepthSupport.from(automatic = true, raw = false))
        assertEquals(DepthSupport.NONE, DepthSupport.from(automatic = false, raw = false))
        assertEquals(DepthSupport.NONE, DepthSupport.from(automatic = false, raw = true))
    }
}
