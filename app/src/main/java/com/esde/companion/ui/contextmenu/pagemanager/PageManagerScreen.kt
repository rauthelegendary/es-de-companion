package com.esde.companion.ui.contextmenu.pagemanager

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esde.companion.PageEditorItem
import com.esde.companion.WidgetPage
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerScreen(
    currentPages: List<WidgetPage>, // Your generic Page object
    onSave: (List<PageEditorItem>) -> Unit,
    onCancel: () -> Unit
) {
    // 1. Convert real pages to our Editor Items
    // We use a mutableStateList so the UI updates instantly when we drag
    val editablePages = remember {
        mutableStateListOf<PageEditorItem>().apply {
            addAll(currentPages.mapIndexed { index, page ->
                PageEditorItem(
                    id = page.id,
                    originalIndex = index,
                    name = page.name ?: "Page ${index + 1}",
                    isRequired = page.isRequired || page.widgets.any { it.isRequired },
                    isLocked = index == 0
                )
            })
        }
    }

    // 2. Setup the Reorderable State
    val state = rememberReorderableLazyListState(
        onMove = { from, to ->
            // PREVENT moving item 0, or moving anything ABOVE item 0
            if (from.index == 0 || to.index == 0) return@rememberReorderableLazyListState

            editablePages.apply {
                add(to.index, removeAt(from.index))
            }
        }
    )

    var pageToRename by remember { mutableStateOf<PageEditorItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Pages") },
                actions = {
                    TextButton(onClick = { onSave(editablePages) }) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            // Drag and Drop List
            LazyColumn(
                state = state.listState,
                modifier = Modifier
                    .fillMaxSize()
                    .reorderable(state)
            ) {
                items(editablePages, key = { it.id }) { page ->
                    ReorderableItem(state, key = page.id) { isDragging ->
                        val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp)

                        Surface(
                            modifier = Modifier.fillMaxWidth().shadow(elevation.value),
                            color = if (page.isLocked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (page.isLocked) {
                                    Icon(Icons.Default.Lock, null, tint = Color.Gray)
                                } else {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Reorder",
                                        modifier = Modifier.detectReorder(state)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // 2. Page Name & Status
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = page.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (page.isRequired) {
                                        Text(
                                            text = "Contains required widgets",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                IconButton(onClick = { pageToRename = page }) {
                                    Icon(Icons.Default.Edit, "Rename")
                                }
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    // Simple Rename Dialog
    if (pageToRename != null) {
        RenamePageDialog(
            initialName = pageToRename!!.name,
            onDismiss = { pageToRename = null },
            onConfirm = { newName ->
                val index = editablePages.indexOf(pageToRename)
                if (index != -1) {
                    editablePages[index] = editablePages[index].copy(name = newName)
                }
                pageToRename = null
            }
        )
    }
}

@Composable
fun RenamePageDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Page") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }) },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}