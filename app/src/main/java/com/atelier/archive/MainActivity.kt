package com.atelier.archive

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.atelier.archive.network.ArchiveDispatcher
import com.atelier.archive.ui.MainScreen
import com.atelier.archive.ui.MainViewModel
import com.atelier.archive.ui.theme.AtelierArchiveTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle direct share intent if launched via Android Share Sheet (from RED or Douyin)
        handleIncomingIntent(intent)

        setContent {
            AtelierArchiveTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForMediaLinks()
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return

        if (incomingIntent.action == Intent.ACTION_SEND && incomingIntent.type == "text/plain") {
            val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.updateInput(sharedText)
                viewModel.parseUrl(sharedText)
            }
        }
    }

    private fun checkClipboardForMediaLinks() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (ArchiveDispatcher.detectPlatform(text) != null) {
                    viewModel.onClipboardDetected(text)
                }
            }
        } catch (_: Exception) {
            // Ignore clipboard access issues on restricted devices
        }
    }
}
