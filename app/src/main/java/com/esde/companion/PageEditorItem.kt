package com.esde.companion

data class PageEditorItem(
    val id: String, // Original UUID or ID
    val originalIndex: Int,
    var name: String,
    val isRequired: Boolean, // e.g. has required widgets
    val isLocked: Boolean = false // True for Index 0
)