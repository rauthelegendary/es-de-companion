package com.esde.companion.ui.contextmenu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.esde.companion.WidgetPage
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetMenuContent(
    uiState: MenuUiState,
    currentPage: WidgetPage,
    isSystemView: Boolean,
    actions: WidgetActions,
    currentPageIndex: Int
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var draftPage by remember(currentPage.id) { mutableStateOf(currentPage.copy()) }

    DisposableEffect(Unit) {
        onDispose {
            actions.onSavePageSettings(draftPage)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PageHeaderSection(uiState.locked, currentPageIndex, isSystemView, actions) {
                    showDeleteConfirmation = true
                }
            }

            //Globals
            item {
                MenuSection(title = "Global Layout") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuChip(
                            "Lock: ${if (uiState.locked) "ON" else "OFF"}",
                            !uiState.locked,
                            actions.onToggleLock
                        )
                        MenuChip("Snap", uiState.snap, actions.onToggleSnap)
                        MenuChip("Grid", uiState.showGrid, actions.onToggleGrid)
                        if(currentPageIndex != 0) {
                            MenuChip(
                                "Required content",
                                draftPage.isRequired,
                                { draftPage = draftPage.copy(isRequired = !draftPage.isRequired) })
                        }
                    }
                }
            }

            if (uiState.locked) {
                item {
                    Text(
                        text = "Press the unlock button to add/edit widgets or add/delete pages",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            } else {
                //Widgets
                item {
                    MenuSection(title = "Add Widget") {
                        WidgetGrid(isSystemView, actions.onAddWidget)
                    }
                }
            }

            //Background content
            item {
                MenuSection(title = "Background Content") {
                    // Type Selector (Custom implementation of a dropdown or chips)
                    BackgroundTypeSelector(draftPage.backgroundType) {
                        draftPage = draftPage.copy(backgroundType = it)
                    }

                    // Show slot selector if it's dynamic media
                    if (draftPage.backgroundType in listOf(
                            PageContentType.FANART,
                            PageContentType.SCREENSHOT,
                            PageContentType.VIDEO
                        )
                    ) {
                        MediaSlotScreen(draftPage.slot) {
                            draftPage = draftPage.copy(slot = it)
                        }
                    }

                    if (draftPage.backgroundType == PageContentType.VIDEO) {
                        MenuToggle("Mute Video", draftPage.isVideoMuted) {
                            draftPage = draftPage.copy(isVideoMuted = it)
                        }
                        MenuSlider("Start Delay", draftPage.videoDelay.toFloat(), 0f, 5f, "s") {
                            draftPage = draftPage.copy(videoDelay = it.toInt())
                        }
                    }

                    if (draftPage.backgroundType == PageContentType.SOLID_COLOR) {
                        ColorPickerSection(draftPage.solidColor) { selectedColor ->
                            draftPage = draftPage.copy(solidColor = selectedColor.toArgb())
                        }
                    }
                }
            }

            //Background visual settings
            item {
                MenuSection(title = "Visuals") {
                    MenuSlider("Opacity", draftPage.backgroundOpacity, 0f, 1f) {
                        draftPage = draftPage.copy(backgroundOpacity = it)
                    }

                    if (draftPage.backgroundType != PageContentType.VIDEO && draftPage.backgroundType != PageContentType.SOLID_COLOR) {
                        MenuSlider(
                            "Blur Intensity",
                            draftPage.blurRadius,
                            0f,
                            25f
                        ) { draftPage = draftPage.copy(blurRadius = it) }
                    }
                }
            }

            //Animations
            item {
                MenuSection(title = "Animations") {
                    MenuToggle(
                        "Transition Effect",
                        draftPage.swapAnimation,
                        { draftPage = draftPage.copy(swapAnimation = it) })
                    if (draftPage.swapAnimation) {
                        MenuSlider(
                            "Duration",
                            draftPage.animationDuration.toFloat(),
                            100f,
                            1000f,
                            "ms"
                        ) {
                            draftPage = draftPage.copy(animationDuration = it.toInt())
                        }
                    }
                    MenuToggle(
                        "Pan & Zoom (Ken Burns)", draftPage.panZoomAnimation,
                        { draftPage = draftPage.copy(panZoomAnimation = it) })
                }
            }
        }

        // Delete Confirmation Overlay (Keep your existing implementation here)
        if (showDeleteConfirmation) {
            DeleteConfirmationOverlay(
                onConfirm = { actions.onRemovePage(); showDeleteConfirmation = false },
                onDismiss = { showDeleteConfirmation = false }
            )
        }
    }
}

@Composable
fun MenuToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan)
        )
    }
}

@Composable
fun MenuSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Surface(
            color = Color(0xFF2A2A2A),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun MenuSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    unit: String = "",
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text("${value.toInt()}$unit", color = Color.Cyan, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BackgroundTypeSelector(currentType: PageContentType, onTypeSelected: (PageContentType) -> Unit) {
    val types = PageContentType.values()
    Column {
        Text("Type", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            types.forEach { type ->
                FilterChip(
                    selected = currentType == type,
                    onClick = { onTypeSelected(type) },
                    label = { Text(type.name.replace("_", " ").lowercase().capitalize()) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetGrid(isSystemView: Boolean, onAddWidget: (ContentType) -> Unit) {
    val options = if (isSystemView) {
        listOf("System Logo" to ContentType.SYSTEM_LOGO)
    } else {
        listOf(
            "Marquee" to ContentType.MARQUEE,
            "2D Box" to ContentType.BOX_2D,
            "3D Box" to ContentType.BOX_3D,
            "Mix Image" to ContentType.MIX_IMAGE,
            "Screenshot" to ContentType.SCREENSHOT,
            "Video" to ContentType.VIDEO,
            "Description" to ContentType.GAME_DESCRIPTION
        )
    }

    // Using FlowRow instead of LazyColumn so it doesn't nest scrollable areas
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, type) ->
            Box(modifier = Modifier.weight(1f)) {
                WidgetOptionCard(label) { onAddWidget(type) }
            }
        }
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

@Composable
fun DeleteConfirmationOverlay(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clickable(enabled = false) { }, // Prevent clicks from closing when touching the card
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Delete Page?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This will permanently remove this page and all its widgets.",
                    color = Color.LightGray, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.Gray) }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("DELETE") }
                }
            }
        }
    }
}

@Composable
fun ColorPickerSection(
    currentColor: Int?,
    onColorChanged: (Color) -> Unit
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Text("Background Color", color = Color.White, style = MaterialTheme.typography.bodyLarge)

        // The Preview and Hex Text
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (currentColor != null) Color(currentColor) else Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (currentColor != null) "#${Integer.toHexString(currentColor).uppercase().takeLast(6)}" else "Black",
                color = Color.Cyan
            )
        }

        // The Hue Slider (The "Simple Wheel")
        val hueValues = 0f..360f
        var currentHue by remember { mutableStateOf(0f) }

        Slider(
            value = currentHue,
            onValueChange = {
                currentHue = it
                // Convert Hue to a Compose Color and send it back
                onColorChanged(Color.hsv(it, 1f, 1f))
            },
            valueRange = hueValues,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent, // We'll see the gradient behind it
                inactiveTrackColor = Color.Transparent
            )
        )

        // Drawing the spectrum bar behind the slider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Red, Color.Yellow, Color.Green,
                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                        )
                    )
                )
        )
    }
}

@Composable
fun PageHeaderSection(
    locked: Boolean,
    currentPageIndex: Int,
    isSystemView: Boolean,
    actions: WidgetActions,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Page ${currentPageIndex + 1}",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            Surface(
                color = if (isSystemView) Color(0xFF5C6BC0) else Color(0xFF66BB6A),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = if (isSystemView) "SYSTEM VIEW" else "GAME VIEW",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // Page Management Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if(!locked) {
                // Reusing your ActionButton from the previous snippet
                ActionButton(label = "+ Page", onClick = actions.onAddPage)

                // Delete button - only show if there's more than one page to delete?
                // Or just keep it simple for now
                ActionButton(
                    label = "Delete",
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                )
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