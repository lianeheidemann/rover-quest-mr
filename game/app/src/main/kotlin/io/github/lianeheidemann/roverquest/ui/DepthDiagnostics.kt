package io.github.lianeheidemann.roverquest.ui

import android.graphics.Bitmap
import android.media.Image
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ar.core.Frame
import io.github.lianeheidemann.roverquest.core.depth.ColoredDepth
import io.github.lianeheidemann.roverquest.core.depth.DepthFrame
import io.github.lianeheidemann.roverquest.core.depth.DepthIssue
import io.github.lianeheidemann.roverquest.core.depth.DepthQuality
import io.github.lianeheidemann.roverquest.core.depth.DepthReport
import java.nio.ByteOrder
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * Copies the latest ARCore depth image (and the raw depth confidence, when available) into a
 * [DepthFrame]. Returns `null` when no depth image is available for this frame.
 * Must run on the thread that delivers ARCore frames (`onSessionUpdated`).
 */
fun readDepthFrame(frame: Frame): DepthFrame? {
    val depth = runCatching { frame.acquireDepthImage16Bits() }.getOrNull() ?: return null
    return depth.use { image ->
        val width = image.width
        val height = image.height
        val depthMm = readPlane(image, bytesPerPixel = 2)
        val confidence = runCatching { frame.acquireRawDepthConfidenceImage() }.getOrNull()?.use { conf ->
            if (conf.width == width && conf.height == height) readPlane(conf, bytesPerPixel = 1) else null
        }
        DepthFrame(width, height, depthMm, confidence)
    }
}

/** Reads a single-plane image of unsigned 8- or 16-bit (little-endian) pixels, row by row. */
private fun readPlane(image: Image, bytesPerPixel: Int): IntArray {
    val plane = image.planes[0]
    val buffer = plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    val out = IntArray(image.width * image.height)
    for (y in 0 until image.height) {
        val rowStart = y * plane.rowStride
        for (x in 0 until image.width) {
            val offset = rowStart + x * plane.pixelStride
            out[y * image.width + x] = if (bytesPerPixel == 2) {
                buffer.getShort(offset).toInt() and 0xFFFF
            } else {
                buffer.get(offset).toInt() and 0xFF
            }
        }
    }
    return out
}

/** False-color depth drawn over the camera feed: red is near, blue is far, no data is clear. */
@Composable
fun DepthOverlay(colored: ColoredDepth, modifier: Modifier = Modifier) {
    val bitmap = remember(colored) {
        Bitmap.createBitmap(colored.argb, colored.width, colored.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.55f,
        modifier = modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticPanel(
    report: DepthReport,
    supportText: String,
    recording: Boolean,
    recordingError: String?,
    playingBack: Boolean,
    canReplay: Boolean,
    onToggleRecording: () -> Unit,
    onToggleReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ok = report.quality == DepthQuality.GOOD
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(centerLabel(report), color = Color.White, style = MaterialTheme.typography.titleSmall)
        Text(
            qualityLabel(report),
            color = if (ok) Color(0xFFA5D6A7) else Color(0xFFFFD54F),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "vermelho = perto · azul = longe · $supportText",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
        if (playingBack) {
            Text(
                "Repetindo a gravação (sem câmera ao vivo)",
                color = Color(0xFF81D4FA),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (recordingError != null) {
            Text(
                "Falha na gravação: $recordingError",
                color = Color(0xFFFFD54F),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            if (!playingBack) {
                CompactChip(if (recording) "● Parar gravação" else "Gravar sessão", recording, onClick = onToggleRecording)
            }
            if (playingBack || (canReplay && !recording)) {
                CompactChip(if (playingBack) "Voltar ao vivo" else "Repetir gravação", playingBack, onClick = onToggleReplay)
            }
        }
    }
}

private fun centerLabel(report: DepthReport): String {
    val center = report.center
    val meters = center?.meters ?: return "Centro: sem leitura"
    val distance = String.format(PT_BR, "%.2f m", meters)
    val confidence = center.confidence?.let { " · confiança ${percent(it)}" }.orEmpty()
    return "Centro: $distance$confidence"
}

private fun qualityLabel(report: DepthReport): String = when (report.quality) {
    DepthQuality.NOT_SUPPORTED -> "Sem profundidade neste aparelho: use o obstáculo virtual."
    DepthQuality.WAITING -> "Aguardando: mova o celular devagar."
    DepthQuality.GOOD -> "Leitura confiável · cobertura ${percent(report.coverage)}"
    DepthQuality.UNCERTAIN -> when (report.issue) {
        DepthIssue.LOW_COVERAGE -> "Incerta: pouca área lida (${percent(report.coverage)})"
        DepthIssue.NO_DATA_AT_CENTER -> "Incerta: sem dado no centro"
        DepthIssue.LOW_CONFIDENCE -> "Incerta: baixa confiança no centro"
        DepthIssue.OUT_OF_RANGE -> "Incerta: fora de 0,3–5 m"
        null -> "Leitura incerta"
    }
}

private fun percent(value: Float): String = "${(value * 100).toInt()}%"
