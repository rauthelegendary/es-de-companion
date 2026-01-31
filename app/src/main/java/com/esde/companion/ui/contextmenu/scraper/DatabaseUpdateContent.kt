package com.esde.companion.ui.contextmenu.scraper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.esde.companion.art.LaunchBox.LaunchBoxDao
import com.esde.companion.art.LaunchBox.LaunchBoxFileParser.importLaunchBoxMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DatabaseUpdateContent(
    onComplete: () -> Unit,
    onBack: () -> Unit,
    dao: LaunchBoxDao
) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready to import...") }
    var isImporting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("LaunchBox Metadata Sync", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "This will process Metadata.xml and update your local search index.",
            textAlign = TextAlign.Center,
            color = Color.Gray
        )

        Spacer(Modifier.height(32.dp))

        if (isImporting) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text(statusText, color = Color.Cyan, style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(
                onClick = {
                    scope.launch {
                        isImporting = true
                        statusText = "Locating Metadata.zip..."
                        val testFile = File("/storage/emulated/0/Download/Metadata.zip")

                        if (testFile.exists()) {
                            importLaunchBoxMetadata(testFile, dao) { currentProgress, message ->
                                progress = currentProgress
                                statusText = message
                            }
                            statusText = "Import Complete!"
                            delay(1000)
                            onComplete()
                        } else {
                            statusText = "Error: Metadata.zip not found in Downloads"
                            isImporting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("START IMPORT")
            }

            TextButton(onClick = onBack) {
                Text("CANCEL", color = Color.LightGray)
            }
        }
    }
}