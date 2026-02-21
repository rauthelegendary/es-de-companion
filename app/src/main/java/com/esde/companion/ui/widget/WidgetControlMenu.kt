package com.esde.companion.ui.widget

import android.R.attr.mode
import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esde.companion.WidgetMode
import com.esde.companion.ui.contextmenu.WidgetUiState

@SuppressLint("ReturnFromAwaitPointerEventScope")
@Composable
fun WidgetControlMenu(
    uiState: WidgetUiState,
    onAction: (WidgetMode) -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onReorder: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    if (!uiState.isDragging) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = uiState.isVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(72.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Press) onInteractionChanged(
                                        true
                                    )
                                    if (event.type == PointerEventType.Release) onInteractionChanged(
                                        false
                                    )
                                }
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures { } },
                    color = Color(0xFF2A2A2A),
                    shape = RoundedCornerShape(36.dp),
                    border = BorderStroke(1.dp, Color(0xFF444444))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if ((uiState.mode == WidgetMode.MOVING || uiState.mode == WidgetMode.RESIZING)) {
                            Text(
                                text = uiState.mode.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Box(Modifier.width(1.dp).height(24.dp).background(Color(0xFF444444)))
                            IconButton(
                                onClick = { onAction(WidgetMode.SELECTED) },
                                modifier = Modifier.background(Color(0xFF4CAF50), CircleShape)
                            ) {
                                Icon(Icons.Default.Check, "Done", tint = Color.White)
                            }
                        } else {
                            MenuButton(
                                Icons.Default.OpenWith,
                                "Move",
                                onClick = { onAction(WidgetMode.MOVING) })
                            MenuButton(
                                Icons.Default.AspectRatio,
                                "Resize",
                                onClick = { onAction(WidgetMode.RESIZING) })

                            Box(Modifier.width(1.dp).height(24.dp).background(Color(0xFF444444)))

                            MenuButton(Icons.Default.Edit, "Edit", onEdit)
                            MenuButton(Icons.Default.Close, "Deselect", onDone, color = Color.White)

                            Box(Modifier.width(1.dp).height(24.dp).background(Color(0xFF444444)))

                            MenuButton(Icons.Default.FlipToFront, "Forward", { onReorder(true) })
                            MenuButton(Icons.Default.FlipToBack, "Back", { onReorder(false) })

                            Box(Modifier.width(1.dp).height(24.dp).background(Color(0xFF444444)))

                            MenuButton(Icons.Default.Lock, "Save & Close", { onClose() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuButton(icon: ImageVector, label: String, onClick: () -> Unit, color: Color = Color.White) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color)
        Text(label, fontSize = 12.sp, color = color)
    }
}