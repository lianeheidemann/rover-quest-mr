package io.github.lianeheidemann.roverquest.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Round icon button that turns purple while its option is on. */
@Composable
fun IconToggle(icon: Int, description: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** Small pill button, optionally with an icon, so the camera view stays clear. */
@Composable
fun CompactChip(label: String, selected: Boolean, icon: Int? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = icon?.let { res ->
            { Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(18.dp)) }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Black.copy(alpha = 0.35f),
            labelColor = Color.White,
            iconColor = Color.White,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
