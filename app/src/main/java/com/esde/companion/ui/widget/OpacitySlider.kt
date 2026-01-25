package com.esde.companion.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun OpacitySlider(
    currentOpacity: Float,
    onOpacityChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Background Opacity", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            // Show percentage (e.g., 85%)
            Text("${(currentOpacity * 100).toInt()}%", color = Color.Cyan, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = currentOpacity,
            onValueChange = onOpacityChange,
            valueRange = 0f..1f,
            steps = 19, // This gives us 5% increments (20 steps total)
            colors = SliderDefaults.colors(
                thumbColor = Color.Cyan,
                activeTrackColor = Color.Cyan,
                inactiveTrackColor = Color.DarkGray
            )
        )
    }
}