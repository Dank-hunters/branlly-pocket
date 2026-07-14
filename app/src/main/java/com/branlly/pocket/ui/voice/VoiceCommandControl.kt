package com.branlly.pocket.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.branlly.pocket.domain.voice.LocalVoiceCommand
import com.branlly.pocket.domain.voice.LocalVoiceCommandParser
import com.branlly.pocket.platform.android.OnDeviceVoiceRecognizer
import com.branlly.pocket.platform.android.VoiceRecognitionResult

@Composable
fun VoiceCommandControl(onCommand: (LocalVoiceCommand) -> Unit) {
    val context = LocalContext.current
    val recognizer = remember(context) { OnDeviceVoiceRecognizer(context.applicationContext) }
    var status by remember { mutableStateOf("Commandes exactes, traitées localement.") }

    fun startListening() {
        status = "Écoute en cours…"
        recognizer.start { result ->
            when (result) {
                is VoiceRecognitionResult.Transcripts -> {
                    val command = result.values.firstNotNullOfOrNull(LocalVoiceCommandParser::parse)
                    if (command == null) {
                        status = "Commande inconnue. Aucune action lancée."
                    } else {
                        status = "Commande reconnue."
                        onCommand(command)
                    }
                }
                VoiceRecognitionResult.NoMatch -> status = "Aucune commande exacte reconnue."
                VoiceRecognitionResult.Busy -> status = "Le microphone est déjà utilisé."
                VoiceRecognitionResult.Unavailable -> status = "Reconnaissance hors ligne indisponible sur ce téléphone."
                VoiceRecognitionResult.PermissionMissing -> status = "Autorisation du microphone requise."
                VoiceRecognitionResult.Failure -> status = "La reconnaissance locale a échoué."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening() else status = "Microphone refusé. Aucune écoute possible."
    }

    DisposableEffect(recognizer) {
        onDispose(recognizer::destroy)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Commande vocale locale", fontWeight = FontWeight.Bold)
        Text(
            "« Je vais partir » ou « Lance la musique »",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Button(
            onClick = {
                if (!recognizer.isAvailable()) {
                    status = "Reconnaissance hors ligne indisponible sur ce téléphone."
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListening()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Parler")
        }
        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}
