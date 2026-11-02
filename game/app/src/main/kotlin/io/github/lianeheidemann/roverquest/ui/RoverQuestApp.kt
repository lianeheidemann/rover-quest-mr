package io.github.lianeheidemann.roverquest.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import io.github.lianeheidemann.roverquest.core.startup.ArAvailability
import io.github.lianeheidemann.roverquest.core.startup.CameraPermission
import io.github.lianeheidemann.roverquest.core.startup.StartupState
import io.github.lianeheidemann.roverquest.core.startup.resolveStartupState

@Composable
fun RoverQuestApp() {
    val context = LocalContext.current

    var permission by remember {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        mutableStateOf(if (granted) CameraPermission.GRANTED else CameraPermission.UNKNOWN)
    }
    var availability by remember { mutableStateOf(ArAvailability.CHECKING) }
    var sessionError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permission = if (granted) CameraPermission.GRANTED else CameraPermission.DENIED
    }

    LaunchedEffect(Unit) {
        if (permission == CameraPermission.UNKNOWN) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        ArCoreApk.getInstance().checkAvailabilityAsync(context) { result ->
            availability = if (result.isUnsupported) ArAvailability.UNSUPPORTED else ArAvailability.SUPPORTED
        }
    }

    MaterialTheme {
        when (val state = resolveStartupState(permission, availability, sessionError)) {
            StartupState.Ready -> ArScreen(onSessionFailed = { sessionError = it })
            StartupState.Checking -> Message("Preparando a realidade aumentada…")
            StartupState.ArUnsupported -> Message(
                "Este aparelho não é compatível com o ARCore, necessário para o RoverQuest.",
            )
            StartupState.CameraPermissionDenied -> Message(
                "O RoverQuest precisa da câmera para mostrar o rover no seu ambiente.",
                actionLabel = "Permitir câmera",
                onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
            is StartupState.SessionFailed -> Message(
                "Não foi possível iniciar a sessão de realidade aumentada.\n${state.reason}",
                actionLabel = "Tentar de novo",
                onAction = { sessionError = null },
            )
        }
    }
}

@Composable
private fun Message(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    // Dark like the camera screen that follows, so the app does not flash white on start.
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212), contentColor = Color.White) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(32.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                if (actionLabel != null) {
                    Button(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}
