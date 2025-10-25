package com.arapps.fileviewplus.core

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

object AppGlobals {
    lateinit var folderAccessLauncher: ActivityResultLauncher<Intent>
    var fileToDelete: File? = null

    // Navigation event stream: emit strings like "vault" or "vault_notes" to request navigation
    val navigateTo = MutableSharedFlow<String>(
        replay = 1, // ensure the latest navigation request reaches late subscribers
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Pending move-to-vault request (absolute path). If set when PIN is not configured,
    // we navigate to Vault for setup and resume the move automatically when ready.
    var pendingMoveToVaultPath: String? = null

    // One-shot signal emitted when Vault becomes ready (PIN set/unlocked)
    val vaultReady = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Deep link: request focusing a specific note id in Vault notes
    val openNote = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
}
