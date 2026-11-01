package io.github.lianeheidemann.roverquest.core.startup

enum class CameraPermission { UNKNOWN, GRANTED, DENIED }

enum class ArAvailability { CHECKING, SUPPORTED, UNSUPPORTED }

/** What the app should show before (or instead of) the AR session. */
sealed interface StartupState {
    data object Checking : StartupState
    data object ArUnsupported : StartupState
    data object CameraPermissionDenied : StartupState
    data class SessionFailed(val reason: String) : StartupState
    data object Ready : StartupState
}

/**
 * Decides the startup state. Permanent problems win over transient ones, so the
 * user always sees the message they can act on.
 */
fun resolveStartupState(
    permission: CameraPermission,
    availability: ArAvailability,
    sessionError: String? = null,
): StartupState = when {
    availability == ArAvailability.UNSUPPORTED -> StartupState.ArUnsupported
    permission == CameraPermission.DENIED -> StartupState.CameraPermissionDenied
    sessionError != null -> StartupState.SessionFailed(sessionError)
    permission == CameraPermission.UNKNOWN || availability == ArAvailability.CHECKING -> StartupState.Checking
    else -> StartupState.Ready
}
