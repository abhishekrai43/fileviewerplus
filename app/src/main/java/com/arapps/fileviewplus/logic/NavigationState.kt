import com.arapps.fileviewplus.model.FileNode
import java.io.File

data class NavigationState(
    val category: FileNode.Category? = null,
    val year: FileNode.Year? = null,
    val month: FileNode.Month? = null,
    val day: FileNode.Day? = null,
    val showFileTypeExplorer: Boolean = false,
    val showVault: Boolean = false,
    val vaultFolder: File? = null
) {
    fun goBack(): NavigationState {
        return when {
            day != null -> copy(day = null)
            month != null -> copy(month = null)
            year != null -> copy(year = null)
            category != null -> copy(category = null)
            showFileTypeExplorer -> copy(showFileTypeExplorer = false)
            vaultFolder != null -> copy(vaultFolder = null)
            showVault -> copy(showVault = false)
            else -> NavigationState()
        }
    }
}

