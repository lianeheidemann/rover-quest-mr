package io.github.lianeheidemann.roverquest.core.startup

import kotlin.test.Test
import kotlin.test.assertEquals

class StartupStateTest {

    @Test
    fun `ready when permission granted and AR supported`() {
        assertEquals(
            StartupState.Ready,
            resolveStartupState(CameraPermission.GRANTED, ArAvailability.SUPPORTED),
        )
    }

    @Test
    fun `checking while permission or availability is unknown`() {
        assertEquals(
            StartupState.Checking,
            resolveStartupState(CameraPermission.UNKNOWN, ArAvailability.SUPPORTED),
        )
        assertEquals(
            StartupState.Checking,
            resolveStartupState(CameraPermission.GRANTED, ArAvailability.CHECKING),
        )
    }

    @Test
    fun `unsupported device wins over denied permission`() {
        assertEquals(
            StartupState.ArUnsupported,
            resolveStartupState(CameraPermission.DENIED, ArAvailability.UNSUPPORTED),
        )
    }

    @Test
    fun `denied permission is reported even while checking availability`() {
        assertEquals(
            StartupState.CameraPermissionDenied,
            resolveStartupState(CameraPermission.DENIED, ArAvailability.CHECKING),
        )
    }

    @Test
    fun `session error is reported when everything else is fine`() {
        assertEquals(
            StartupState.SessionFailed("boom"),
            resolveStartupState(CameraPermission.GRANTED, ArAvailability.SUPPORTED, "boom"),
        )
    }
}
