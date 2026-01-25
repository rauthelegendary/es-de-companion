package com.esde.companion.ui.contextmenu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.esde.companion.OverlayWidget

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetMenuContent(
    uiState: MenuUiState,
    isSystemView: Boolean,
    actions: WidgetActions
) {
    fun getWidgetOptions(isSystemView: Boolean): List<Pair<String, OverlayWidget.ContentType>> {
        return if (isSystemView) {
            listOf("System Logo" to OverlayWidget.ContentType.SYSTEM_LOGO)
        } else {
            listOf(
                "Marquee" to OverlayWidget.ContentType.MARQUEE,
                "2D Box" to OverlayWidget.ContentType.BOX_2D,
                "3D Box" to OverlayWidget.ContentType.BOX_3D,
                "Mix Image" to OverlayWidget.ContentType.MIX_IMAGE,
                "Screenshot" to OverlayWidget.ContentType.SCREENSHOT,
                "Video" to OverlayWidget.ContentType.VIDEO,
                "Game Description" to OverlayWidget.ContentType.GAME_DESCRIPTION
            )
        }
    }

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuChip(
                    "Edit Mode: ${if (uiState.locked) "OFF" else "ON"}",
                    !uiState.locked,
                    actions.onToggleLock
                )
                MenuChip(
                    "Snap: ${if (uiState.snap) "ON" else "OFF"}",
                    uiState.snap,
                    actions.onToggleSnap
                )
                MenuChip(
                    "Grid: ${if (uiState.showGrid) "ON" else "OFF"}",
                    uiState.showGrid,
                    actions.onToggleGrid
                )
            }

            Divider(
                color = Color.Gray.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (!uiState.locked) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Help", actions.onHelp)
                    ActionButton("+ Page", actions.onAddPage)
                    // Triggers the popup state
                    ActionButton(
                        label = "- Page",
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    )
                }

                Divider(
                    color = Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    "Add New Widget",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelMedium
                )

                val options = getWidgetOptions(isSystemView)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(options) { (label, type) ->
                        WidgetOptionCard(label) { actions.onAddWidget(type) }
                    }
                }
            }
        }

        // 2. THE POPUP OVERLAY
        if (showDeleteConfirmation) {
            // Dimmed background that also catches clicks to prevent interacting with menu below
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = true, onClick = { showDeleteConfirmation = false }),
                contentAlignment = Alignment.Center
            ) {
                // The "Popup" Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.8f) // Smaller width than the main menu for contrast
                        .clickable(
                            enabled = true,
                            onClick = { /* Consumes click so popup doesn't close */ }),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF252525),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Delete Page?",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Are you sure? This will remove all widgets on this page.",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showDeleteConfirmation = false }) {
                                Text("CANCEL", color = Color.Gray)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    actions.onRemovePage()
                                    showDeleteConfirmation = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("DELETE")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        colors = colors,
        border = BorderStroke(1.dp, Color.Gray)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetOptionCard(label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D3D3D)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(16.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}