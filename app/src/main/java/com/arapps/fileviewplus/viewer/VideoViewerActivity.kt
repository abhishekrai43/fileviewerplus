package com.arapps.fileviewplus.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.arapps.fileviewplus.model.FileNode
import java.io.File
import java.io.FileOutputStream

class VideoViewerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val file = resolveFileFromIntent(intent)

        if (file == null || !file.exists() || !file.canRead()) {
            Toast.makeText(this, "Cannot play video", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        playerView = PlayerView(this).apply {
            useController = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        setContentView(playerView)

        WindowInsetsControllerCompat(window, playerView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    private fun resolveFileFromIntent(intent: Intent): File? {
        // Internal path
        intent.getStringExtra("path")?.let { return File(it) }

        // External open-with
        val uri = intent.data ?: return null
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val ext = contentResolver.getType(uri)?.substringAfterLast("/") ?: "mp4"
            val tempFile = File(cacheDir, "external_vid_${System.currentTimeMillis()}.$ext")
            FileOutputStream(tempFile).use { output -> inputStream.copyTo(output) }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        fun launch(context: Context, fileNode: FileNode, fromVault: Boolean) {
            val intent = Intent(context, VideoViewerActivity::class.java).apply {
                putExtra("path", fileNode.path)
                putExtra("fromVault", fromVault)
            }
            context.startActivity(intent)
        }
    }
}
