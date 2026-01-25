package com.esde.companion.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.esde.companion.OverlayWidget
import com.esde.companion.ui.contextmenu.ActionButton
import com.esde.companion.ui.contextmenu.MediaSlotScreen

@Composable
fun WidgetSettingsOverlay(
    widget: OverlayWidget,
    onDismiss: () -> Unit,
    onUpdate: (OverlayWidget) -> Unit,
    onDelete: (OverlayWidget) -> Unit,
    onReorder: (OverlayWidget, Boolean) -> Unit
) {
    DisposableEffect(Unit) {
        onDispose {
            onDismiss()
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f).clickable(enabled = false) { },
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Edit ${widget.contentType.name}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("Current zIndex: ${widget.zIndex}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                Spacer(Modifier.height(20.dp))

                Text("Media Source", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                MediaSlotScreen(selectedSlot = widget.slotIndex) { newSlot ->
                    widget.slotIndex = newSlot
                    onUpdate(widget)
                }

                Divider(Modifier.padding(vertical = 16.dp), color = Color.DarkGray)

                if (widget.contentType != OverlayWidget.ContentType.GAME_DESCRIPTION) {
                    Text("Scale Type", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(OverlayWidget.ScaleType.FIT, OverlayWidget.ScaleType.CROP).forEach { type ->
                            val isSelected = widget.scaleType == type
                            Button(
                                onClick = {
                                    widget.scaleType = type
                                    onUpdate(widget)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF03DAC6) else Color(0xFF333333)
                                )
                            ) {
                                Text(type.name, color = if (isSelected) Color.Black else Color.White)
                            }
                        }
                    }
                }

                if (widget.contentType == OverlayWidget.ContentType.GAME_DESCRIPTION) {
                    OpacitySlider(currentOpacity = widget.backgroundOpacity) { newOpacity ->
                        widget.backgroundOpacity = newOpacity
                        onUpdate(widget)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Layering", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Backward", { onReorder(widget, false) })
                    ActionButton("Forward", { onReorder(widget, true) })
                }

                Spacer(Modifier.height(24.dp))

                // --- DELETE ---
                Button(
                    onClick = { onDelete(widget) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF442222))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove Widget", color = Color.Red)
                }
            }
        }
    }
}