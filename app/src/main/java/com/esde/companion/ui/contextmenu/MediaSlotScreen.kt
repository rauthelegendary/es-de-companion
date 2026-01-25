package com.esde.companion.ui.contextmenu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun MediaSlotScreen(
    selectedSlot: Int,
    onSlotSelected: (Int) -> Unit
) {
    Column {
        Text(
            "Which slot do you want to save to?",
            color = Color.Gray,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "The alt slots can be used for widgets in ES-DE Companion. Selecting ES-DE will replace the scraped image/video in ES-DE!",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val slots = listOf(0 to "ES-DE", 1 to "Alt 1", 2 to "Alt 2", 3  to "Alt 3")

            slots.forEach { (index, label) ->
                val isSelected = selectedSlot == index
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSlotSelected(index) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color.Cyan.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.4f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color.Cyan else Color.Transparent
                    )
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        color = if (isSelected) Color.Cyan else Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}