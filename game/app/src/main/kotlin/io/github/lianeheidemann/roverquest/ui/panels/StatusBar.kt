package io.github.lianeheidemann.roverquest.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lianeheidemann.roverquest.R
import io.github.lianeheidemann.roverquest.ui.components.IconToggle

/** One compact line: tracking state on the left, icon switches on the right. */
@Composable
internal fun StatusBar(
    tracking: Boolean,
    trackingText: String,
    showGameTools: Boolean,
    diagnosticOn: Boolean,
    onToggleDiagnostic: () -> Unit,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    showMap: Boolean,
    onToggleMap: () -> Unit,
    canChooseNewArea: Boolean,
    onNewArea: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (tracking) Color(0xFF66BB6A) else Color(0xFFFFB300), CircleShape),
        )
        // Long warnings are cut short so the icons (above all the torch) never leave the screen.
        Text(
            trackingText,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 6.dp),
        )
        if (showGameTools) {
            IconToggle(R.drawable.ic_depth, "Profundidade", diagnosticOn, onToggleDiagnostic)
            IconToggle(R.drawable.ic_map, "Mapa de obstáculos", showMap, onToggleMap)
        }
        IconToggle(R.drawable.ic_torch, "Lanterna", torchOn, onToggleTorch)
        if (showGameTools && canChooseNewArea) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconToggle(R.drawable.ic_more, "Mais opções", selected = false) { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        // Starts the game over: new floor scan, rover, destination and map cleared.
                        text = { Text("Resetar jogo") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_reset_game), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onNewArea()
                        },
                    )
                }
            }
        }
    }
}
