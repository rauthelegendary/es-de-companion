package com.esde.companion.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.esde.companion.data.Widget
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.PageContentType.FontType
import com.esde.companion.ui.ScaleType
import com.esde.companion.ui.TextAlignment
import com.esde.companion.ui.contextmenu.ActionButton
import com.esde.companion.ui.contextmenu.MediaSlotScreen
import com.esde.companion.ui.contextmenu.MenuChip
import com.esde.companion.ui.contextmenu.MenuSlider
import com.esde.companion.ui.contextmenu.MenuToggle
import com.esde.companion.ui.contextmenu.SimpleColorPickerDialog

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsOverlay(
    widget: Widget,
    currentPageIndex: Int,
    onDismiss: () -> Unit,
    onUpdate: (Widget) -> Unit,
    onDelete: (Widget) -> Unit,
    inSystemView: Boolean
) {
    var liveWidget by remember(widget.id) { mutableStateOf(widget) }
    var showPageColorPicker by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            onDismiss()
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth(0.85f).background(Color.Black.copy(alpha = 0.6f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f).clickable(false) { },
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text("Edit ${liveWidget.contentType.name}", style = MaterialTheme.typography.headlineSmall, color = Color.White)

                Spacer(Modifier.height(12.dp))
                if (liveWidget.contentType.hasAltSlots() && !inSystemView) {
                    MediaSlotScreen(currentSlot = liveWidget.slot) { newSlot ->
                        liveWidget = liveWidget.copy(slot = newSlot)
                        onUpdate(liveWidget)
                    }
                }

                if(liveWidget.contentType == ContentType.CUSTOM_FOLDER) {
                    val fallbackTypes = ContentType.entries.let { entries ->
                        if (inSystemView) {
                            entries.filter { it != ContentType.VIDEO && it != ContentType.CUSTOM_IMAGE && it != ContentType.CUSTOM_FOLDER && it != ContentType.COLOR_BACKGROUND && !it.isTextWidget() }
                                .toTypedArray()
                        } else {
                            entries.filter { it != ContentType.CUSTOM_IMAGE && it != ContentType.CUSTOM_FOLDER && it != ContentType.COLOR_BACKGROUND && !it.isTextWidget() }
                                .toTypedArray()
                        }
                    }
                    Column {
                        Text(
                            "Fallback type (when no custom media is found for given system/game)",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            fallbackTypes.forEach { type ->
                                FilterChip(
                                    selected = liveWidget.contentFallbackType == type,
                                    onClick = {
                                        liveWidget = liveWidget.copy(contentFallbackType = type)
                                        onUpdate(liveWidget)
                                    },
                                    label = {
                                        Text(
                                            type.name.replace("_", " ").lowercase().capitalize()
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                if (liveWidget.contentType == ContentType.COLOR_BACKGROUND) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { showPageColorPicker = true }
                    ) {
                        // Preview Circle
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (liveWidget.solidColor != null) Color(liveWidget.solidColor!!) else Color.Black,
                                    RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("Select Background Color", color = Color.White)
                    }
                }

                if (showPageColorPicker) {
                    SimpleColorPickerDialog(
                        onDismiss = { showPageColorPicker = false },
                        onColorSelected = { hexString ->
                            liveWidget = liveWidget.copy(solidColor = android.graphics.Color.parseColor(hexString))
                            showPageColorPicker = false
                            onUpdate(liveWidget)
                        }
                    )
                }

                Spacer(Modifier.height(2.dp))

                if(currentPageIndex != 0 && liveWidget.contentType.canBeRequired()) {
                    MenuChip("Required content", liveWidget.isRequired, {
                        liveWidget = liveWidget.copy(isRequired = !liveWidget.isRequired)
                        onUpdate(liveWidget)
                    })
                }

                if (liveWidget.contentType == ContentType.MARQUEE) {
                    MenuChip("Use glint animation", liveWidget.glint, {
                        liveWidget = liveWidget.copy(glint = !liveWidget.glint)
                        onUpdate(liveWidget)
                    })
                }

                if (!inSystemView
                    && !liveWidget.contentType.isTextWidget()
                    && liveWidget.contentType != ContentType.VIDEO
                    && liveWidget.contentType.hasAltSlots()
                    ) {
                    MenuChip("Cycle image slot on touch", liveWidget.cycle, {
                        liveWidget = liveWidget.copy(cycle = !liveWidget.cycle)
                        onUpdate(liveWidget)
                    })
                }

                    Divider(Modifier.padding(vertical = 16.dp), color = Color.DarkGray)

                if (!liveWidget.contentType.isTextWidget()
                    && liveWidget.contentType != ContentType.VIDEO
                    && liveWidget.contentType != ContentType.COLOR_BACKGROUND
                ) {
                    Text("Scale Type", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ScaleType.FIT, ScaleType.CROP).forEach { type ->
                            val isSelected = liveWidget.scaleType == type
                            Button(
                                onClick = {
                                    liveWidget = liveWidget.copy(scaleType = type)
                                    onUpdate(liveWidget)
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

                if (liveWidget.contentType.isTextWidget()) {
                    Divider(Modifier.padding(vertical = 16.dp), color = Color.DarkGray)

                    OpacitySlider(currentOpacity = liveWidget.backgroundOpacity) { newOpacity ->
                        liveWidget = liveWidget.copy(backgroundOpacity = newOpacity)
                        onUpdate(liveWidget)
                    }
                    MenuSlider(
                        "Font size",
                        liveWidget.fontSize,
                        16f,
                        80f,
                        ""
                    ) {
                            size -> liveWidget = liveWidget.copy(fontSize = size)
                        onUpdate(liveWidget)
                    }
                    MenuSlider(
                        "Text padding",
                        liveWidget.textPadding.toFloat(),
                        0f,
                        150f,
                        ""
                    ) {
                            padding -> liveWidget = liveWidget.copy(textPadding = padding.toInt())
                        onUpdate(liveWidget)
                    }
                    MenuSlider(
                        "Text shadow radius",
                        liveWidget.shadowRadius,
                        0f,
                        50f,
                        ""
                    ) {
                            shadowRadius -> liveWidget = liveWidget.copy(shadowRadius = shadowRadius)
                        onUpdate(liveWidget)
                    }
                    Text("Font type", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FontType.entries.forEach { type ->
                            val isSelected = liveWidget.fontType == type
                            Button(
                                onClick = {
                                    liveWidget = liveWidget.copy(fontType = type)
                                    onUpdate(liveWidget)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF03DAC6) else Color(0xFF333333)
                                )
                            ) {
                                Text(type.toDisplayName(), color = if (isSelected) Color.Black else Color.White)
                            }
                        }
                    }
                    Text("Text Alignment", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextAlignment.entries.forEach { alignment ->
                            val isSelected = liveWidget.textAlignment == alignment
                            Button(
                                onClick = {
                                    liveWidget = liveWidget.copy(textAlignment = alignment)
                                    onUpdate(liveWidget)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF03DAC6) else Color(0xFF333333)
                                )
                            ) {
                                Text(alignment.toDisplayName(), color = if (isSelected) Color.Black else Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MenuChip("Allow scrolling", liveWidget.scrollText, {
                            liveWidget = liveWidget.copy(scrollText = !liveWidget.scrollText)
                            onUpdate(liveWidget)
                        })
                        MenuChip("Bold font", liveWidget.isBold, {
                            liveWidget = liveWidget.copy(isBold = !liveWidget.isBold)
                            onUpdate(liveWidget)
                        })
                        MenuChip("Italic font", liveWidget.isItalic, {
                            liveWidget = liveWidget.copy(isItalic = !liveWidget.isItalic)
                            onUpdate(liveWidget)
                        })
                    }

                }

                if ((liveWidget.contentType.hasAltSlots() && !inSystemView) || liveWidget.contentType == ContentType.CUSTOM_FOLDER) {
                    MenuToggle("Use default when alt slot is empty", !liveWidget.ignoreFallback) { ignoreFallback ->
                        liveWidget = liveWidget.copy(ignoreFallback = !ignoreFallback)
                        onUpdate(liveWidget)
                    }
                    MenuToggle("Mute video", !liveWidget.playAudio) { muted ->
                        liveWidget = liveWidget.copy(playAudio = !muted)
                        onUpdate(liveWidget)
                    }
                    MenuSlider(
                        "Video volume",
                        liveWidget.videoVolume * 100,
                        0f,
                        100f,
                        ""
                    ) {
                        volume -> liveWidget = liveWidget.copy(videoVolume = (volume / 100))
                        onUpdate(liveWidget)
                    }
                }

                Spacer(Modifier.height(16.dp))

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