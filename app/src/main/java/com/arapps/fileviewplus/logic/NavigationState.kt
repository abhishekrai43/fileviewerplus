package com.arapps.fileviewplus.logic

import com.arapps.fileviewplus.model.FileNode

data class NavigationState(
    val category: FileNode.Category? = null,
    val month: FileNode.Month? = null,
    val day: FileNode.Day? = null
) {
    fun goBack(): NavigationState {
        return when {
            day != null -> copy(day = null)
            month != null -> copy(month = null)
            category != null -> copy(category = null)
            else -> this
        }
    }
}
