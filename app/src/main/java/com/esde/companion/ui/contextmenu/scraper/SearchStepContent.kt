package com.esde.companion.ui.contextmenu.scraper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.esde.companion.art.GameSearchResult

@Composable
fun SearchStepContent(
    query: String,
    onQueryChange: (String) -> Unit,
    isLoading: Boolean,
    onSearchExecute: () -> Unit,
    results: List<GameSearchResult>,
    onGameSelected: (String) -> Unit
) {
    Column(modifier = Modifier.padding(8.dp)) {
        // The Search Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                label = { Text("Search Game Title") },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSearchExecute, enabled = !isLoading) {
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (results.isEmpty() && !isLoading) {
            Text("No results yet. Enter a title and press Search.", color = Color.Gray)
        }

        // The Results
        LazyColumn {
            items(results) { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onGameSelected(result.gameId.toString()) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF333333))
                ) {
                    Text(result.title, modifier = Modifier.padding(16.dp), color = Color.White)
                }
            }
        }
    }
}