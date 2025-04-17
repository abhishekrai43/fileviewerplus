package com.arapps.fileviewplus.model

import java.io.File

enum class FilterMode {
    NONE, OLD, LARGE
}

data class NavigationState(
    val category: FileNode.Category? = null,
    val year: FileNode.Year? = null,
    val month: FileNode.Month? = null,
    val day: FileNode.Day? = null,

    val showFileTypeExplorer: Boolean = false,
    val showVault: Boolean = false,
    val vaultFolder: File? = null,

    val viewerFile: FileNode? = null,
    val viewerIsVault: Boolean = false,

    val showFilteredList: Boolean = false,
    val filteredFiles: List<FileNode> = emptyList(),

    val filteredTitle: String = "",
    val filterMode: FilterMode = FilterMode.NONE
) {
    fun goBack(): NavigationState {
        return when {
            viewerFile != null -> copy(viewerFile = null, viewerIsVault = false)
            showFilteredList -> copy(showFilteredList = false, filteredFiles = emptyList(), filteredTitle = "", filterMode = FilterMode.NONE)
            vaultFolder != null -> copy(vaultFolder = null)
            showVault -> copy(showVault = false)
            showFileTypeExplorer -> copy(showFileTypeExplorer = false)
            day != null -> copy(day = null)
            month != null -> copy(month = null)
            year != null -> copy(year = null)
            category != null -> copy(category = null)
            else -> NavigationState() // full reset
        }
    }
}
