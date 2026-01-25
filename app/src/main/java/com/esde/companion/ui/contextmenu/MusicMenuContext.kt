package com.esde.companion.ui.contextmenu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun MusicMenuContent(
    initialQuery: String,
    results: List<StreamInfoItem>,
    isLoading: Boolean,
    onSearch: (String) -> Unit,
    onVideoSelected: (StreamInfoItem) -> Unit
) {
    // We "remember" the query in the text field so it doesn't vanish if the user types
    var searchQuery by remember { mutableStateOf(initialQuery) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search YouTube...", color = Color.Gray) },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSearch(searchQuery) }) {
                Text("🔍")
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results) { item ->
                // This Row represents your song entry
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        // The Fix: Wrap the call in a lambda inside clickable
                        .clickable {
                            onVideoSelected(item)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Your Song UI (Icon, Title, Artist, etc.)
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Cyan.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.duration} - ${item.uploaderName}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}