package com.branlly.pocket

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.branlly.pocket.platform.android.RoutineWidgetIntents
import com.branlly.pocket.ui.BranllyPocketApp
import com.branlly.pocket.ui.theme.BranllyPocketTheme

class MainActivity : ComponentActivity() {
    private var sharedMediaLink by mutableStateOf<String?>(null)
    private var openCreateRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedMediaLink = extractSharedMediaLink(intent)
        openCreateRequest = if (intent.action == RoutineWidgetIntents.ACTION_OPEN_CREATE) 1 else 0
        enableEdgeToEdge()
        setContent {
            BranllyPocketTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BranllyPocketApp(
                        sharedMediaLink = sharedMediaLink,
                        openCreateRequest = openCreateRequest,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedMediaLink = extractSharedMediaLink(intent)
        if (intent.action == RoutineWidgetIntents.ACTION_OPEN_CREATE) openCreateRequest += 1
    }

    private fun extractSharedMediaLink(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        return intent
            .getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()
            ?.take(MAX_SHARED_LINK_LENGTH)
            ?.takeIf { it.startsWith("https://") }
    }

    private companion object {
        const val MAX_SHARED_LINK_LENGTH = 2_000
    }
}
