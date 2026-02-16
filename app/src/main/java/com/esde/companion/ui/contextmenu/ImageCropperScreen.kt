package com.esde.companion.ui.contextmenu

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageView


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropperScreen(
    imageUri: Uri,
    onCropSuccess: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var cropImageView by remember { mutableStateOf<CropImageView?>(null) }
    var is169 by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                CropImageView(context).apply {
                    guidelines = CropImageView.Guidelines.ON
                    setFixedAspectRatio(true)
                    setAspectRatio(16, 9)
                    setImageUriAsync(imageUri)

                    setOnCropImageCompleteListener { _, result ->
                        if (result.isSuccessful) {
                            result.bitmap?.let { onCropSuccess(it) }
                        }
                    }
                    cropImageView = this
                }
            },
            update = { view ->
                if(is169) {
                    view.setAspectRatio(16, 9)
                } else {
                    view.setAspectRatio(31, 27)
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onCancel) { Text("Cancel") }

            Row {
                FilterChip(
                    selected = is169,
                    onClick = { is169 = true },
                    label = { Text("16:9") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !is169,
                    onClick = { is169 = false },
                    label = { Text("31:27 (Ayn Thor Bottom screen)") }
                )
            }

            Button(onClick = {
                val width = if (is169) 1920 else 1080
                val height = if (is169) 1080 else 1240
                cropImageView?.croppedImageAsync(reqWidth = width, reqHeight = height, options = CropImageView.RequestSizeOptions.RESIZE_INSIDE)
            }) {
                Icon(Icons.Filled.Save, "")
            }
        }
    }
}