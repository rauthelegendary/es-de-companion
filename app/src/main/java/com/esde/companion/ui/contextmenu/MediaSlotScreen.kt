package com.esde.companion.ui.contextmenu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.esde.companion.OverlayWidget.MediaSlot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaSlotScreen(
    currentSlot: MediaSlot,
    onSlotSelected: (MediaSlot) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Media source",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "Select \"Default\" to use ES-DE as image source. Alternatives can be set through the scraper!",
            color = Color.Gray.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            MediaSlot.entries.forEach { slot ->
                val isSelected = currentSlot == slot

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSlotSelected(slot) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color.Cyan.copy(alpha = 0.2f) else Color(0xFF2D2D2D),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color.Cyan else Color.Transparent
                    )
                ) {
                    Text(
                        text = if (slot == MediaSlot.Default) "Default" else "Slot ${slot.index}",
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        color = if (isSelected) Color.Cyan else Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}