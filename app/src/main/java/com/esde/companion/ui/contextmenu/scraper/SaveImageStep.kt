package com.esde.companion.ui.contextmenu.scraper

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.esde.companion.OverlayWidget
import com.esde.companion.art.ImageSearchResult

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SaveImageStep(
    image: ImageSearchResult,
    onConfirm: (type: OverlayWidget.ContentType, slot: Int) -> Unit
) {
    val selectableMediaTypes = listOf<OverlayWidget.ContentType>(
        OverlayWidget.ContentType.BOX_2D,
        OverlayWidget.ContentType.BOX_3D,
        OverlayWidget.ContentType.MARQUEE,
        OverlayWidget.ContentType.FANART,
        OverlayWidget.ContentType.SCREENSHOT,
        OverlayWidget.ContentType.MIX_IMAGE,
        OverlayWidget.ContentType.TITLE_SCREEN,
        OverlayWidget.ContentType.BACK_COVER,
        OverlayWidget.ContentType.PHYSICAL_MEDIA
    )

    var selectedType by remember { mutableStateOf(OverlayWidget.ContentType.BOX_2D) }
    var selectedSlot by remember { mutableIntStateOf(1) }

    //display the chosen image, full image instead of thumbnail
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.fillMaxWidth().height(350.dp).background(Color.DarkGray, RoundedCornerShape(8.dp))) {
            AsyncImage(
                model = image.url,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) {
                        println("Image failed to load: ${state.result.throwable}")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        //save selection for content type and slot
        Text("SAVE AS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selectableMediaTypes.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.toDisplayName()) }
                )
            }
        }

        Text("TARGET SLOT", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { slot ->
                FilterChip(
                    selected = selectedSlot == slot,
                    onClick = { selectedSlot = slot },
                    label = { Text("Alt $slot")}
                )
            }
        }

        //a small summary to explain what we're doing
        val isEsDe = selectedSlot == 0
        val typeName = selectedType.toDisplayName()
        //TODO: no longer required but I'll leave it in for now in case I change my mind, doesn't affect anything anyway
        val summaryText = if (isEsDe) {
            "Image will overwrite the existing \"$typeName\" within ES-DE. This will affect both ES-DE and the companion app. Are you sure?"
        } else {
            "Image will be saved as \"$typeName\" in the \"Alt $selectedSlot\" slot. Any existing image in that slot will be overwritten."
        }

        Text(
            text = summaryText,
            color = if (isEsDe) Color(0xFFEF5350) else Color.White, // Red for ES-DE warning
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Button(
            onClick = { onConfirm(selectedType, selectedSlot) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isEsDe) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text("CONFIRM AND SAVE")
        }
    }
}