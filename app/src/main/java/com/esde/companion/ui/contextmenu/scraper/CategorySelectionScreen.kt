package com.esde.companion.ui.contextmenu.scraper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.esde.companion.art.MediaCategory

@Composable
fun CategorySelectionStep(
    categories: List<MediaCategory>,
    isLoading: Boolean,
    onCategorySelected: (String) -> Unit
) {
    if (isLoading) {
        CircularProgressIndicator()
    } else {
        Column(Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categories.forEach { category ->
                Button(
                    onClick = { onCategorySelected(category.key) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(category.label)
                }
            }
        }
    }
}