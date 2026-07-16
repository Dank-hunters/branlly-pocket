package com.branlly.pocket.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.branlly.pocket.domain.voice.LocalVoiceCommand
import com.branlly.pocket.domain.voice.LocalVoiceCommandParser
import com.branlly.pocket.platform.android.OnDeviceVoiceRecognizer
import com.branlly.pocket.platform.android.VoiceRecognitionResult

@Composable
fun VoiceCommandControl(onCommand: (LocalVoiceCommand) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recognizer = remember(context) { OnDeviceVoiceRecognizer(context.applicationContext) }
    var status by remember { mutableStateOf("") }

    fun startListening() {
        status = "Écoute…"
        recognizer.start { result ->
            when (result) {
                is VoiceRecognitionResult.Transcripts -> {
                    result.values.firstNotNullOfOrNull(LocalVoiceCommandParser::parse)?.let(onCommand)
                        ?: run { status = "Commande inconnue" }
                }

                VoiceRecognitionResult.NoMatch -> {
                    status = "Aucune commande reconnue"
                }

                VoiceRecognitionResult.Busy -> {
                    status = "Microphone occupé"
                }

                VoiceRecognitionResult.Unavailable -> {
                    status = "Reconnaissance locale indisponible"
                }

                VoiceRecognitionResult.PermissionMissing -> {
                    status = "Microphone requis"
                }

                VoiceRecognitionResult.Failure -> {
                    status = "Échec de l’écoute"
                }
            }
        }
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                status = "Microphone refusé"
            }
        }
    DisposableEffect(recognizer) { onDispose(recognizer::destroy) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primary,
            onClick = {
                if (!recognizer.isAvailable()) {
                    status = "Reconnaissance locale indisponible"
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    startListening()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text(
                    "⌁",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (status.isNotBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
