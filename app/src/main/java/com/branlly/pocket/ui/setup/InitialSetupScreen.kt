package com.branlly.pocket.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.branlly.pocket.platform.android.TestNotificationManager
import com.branlly.pocket.platform.android.openNotificationSettingsSafely
import com.branlly.pocket.platform.android.setup.CapabilityRequestTarget
import com.branlly.pocket.platform.android.setup.InitialSetupDecision
import com.branlly.pocket.platform.android.setup.PermissionSettingsIntents
import com.branlly.pocket.platform.android.setup.SetupCapability
import com.branlly.pocket.platform.android.setup.SetupCapabilityPolicy
import com.branlly.pocket.platform.android.setup.SetupCapabilityStatus
import com.branlly.pocket.platform.android.setup.SetupSnapshot
import com.branlly.pocket.platform.android.setup.SetupStateStore
import com.branlly.pocket.ui.hud.HudColors
import com.branlly.pocket.ui.hud.HudPanel
import com.branlly.pocket.ui.hud.HudPrimaryButton
import com.branlly.pocket.ui.hud.HudSecondaryButton
import com.branlly.pocket.ui.hud.HudStatusBadge

@Composable
fun InitialSetupScreen(
    snapshot: SetupSnapshot,
    store: SetupStateStore,
    openedFromSettings: Boolean,
    onRefresh: () -> Unit,
    onComplete: () -> Unit,
    onContinueLimited: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val testNotifications = remember(context) { TestNotificationManager(context.applicationContext) }
    var confirmLimited by remember { mutableStateOf(false) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    val bluetoothPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    val activeCapability = InitialSetupDecision.nextMissingCapability(snapshot)

    fun request(status: SetupCapabilityStatus) {
        val runtimePermission = SetupCapabilityPolicy.runtimePermission(status.capability, Build.VERSION.SDK_INT)
        val target =
            SetupCapabilityPolicy.requestTarget(
                capability = status.capability,
                sdkInt = Build.VERSION.SDK_INT,
                granted = status.granted,
                runtimeRequestAlreadyMade = runtimePermission?.let(store::wasRuntimePermissionRequested) == true,
            )
        when (target) {
            CapabilityRequestTarget.NONE -> {
                Unit
            }

            CapabilityRequestTarget.RUNTIME_PERMISSION -> {
                checkNotNull(runtimePermission)
                store.markRuntimePermissionRequested(runtimePermission)
                when (status.capability) {
                    SetupCapability.NOTIFICATIONS -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    SetupCapability.NEARBY_DEVICES -> bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    SetupCapability.MEDIA_CONTROL -> Unit
                }
            }

            CapabilityRequestTarget.SETTINGS -> {
                when (status.capability) {
                    SetupCapability.NOTIFICATIONS -> {
                        PermissionSettingsIntents.open(context, PermissionSettingsIntents.appNotifications(context))
                    }

                    SetupCapability.MEDIA_CONTROL -> {
                        PermissionSettingsIntents.open(
                            context,
                            PermissionSettingsIntents.notificationListener(context),
                            PermissionSettingsIntents.notificationListenerList(),
                        )
                    }

                    SetupCapability.NEARBY_DEVICES -> {
                        PermissionSettingsIntents.open(context, PermissionSettingsIntents.appDetails(context))
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(HudColors.Background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (openedFromSettings) "CONFIGURATION ET AUTORISATIONS" else "CONFIGURATION INITIALE",
                    color = HudColors.CyanBright,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Branlly vérifie chaque accès depuis Android. Les demandes sont présentées une par une.",
                    color = HudColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(snapshot.statuses.size) { index ->
            val status = snapshot.statuses[index]
            CapabilityCard(
                status = status,
                isCurrentStep = status.capability == activeCapability,
                mediaListenerOperational = snapshot.mediaListenerOperational,
                requestTarget =
                    SetupCapabilityPolicy.requestTarget(
                        capability = status.capability,
                        sdkInt = Build.VERSION.SDK_INT,
                        granted = status.granted,
                        runtimeRequestAlreadyMade =
                            SetupCapabilityPolicy
                                .runtimePermission(status.capability, Build.VERSION.SDK_INT)
                                ?.let(store::wasRuntimePermissionRequested) == true,
                    ),
                onRequest = { request(status) },
            )
        }
        item {
            HudPanel(modifier = Modifier.fillMaxWidth(), borderColor = HudColors.Cyan) {
                Text(
                    "TEST DE NOTIFICATION",
                    color = HudColors.CyanBright,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Affiche une continuation factice. Continuer et Annuler ferment seulement le test.",
                    color = HudColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                HudSecondaryButton(
                    text = "Tester la notification",
                    onClick = testNotifications::show,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = snapshot.status(SetupCapability.NOTIFICATIONS)?.granted == true,
                )
                HudSecondaryButton(
                    text = "Ouvrir les réglages de cette notification",
                    onClick = { context.openNotificationSettingsSafely(testNotifications.channelSettingsIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            HudPrimaryButton(
                text = "Terminer la configuration",
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                enabled = snapshot.allRequiredGranted,
                showLeadingGlyph = false,
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (openedFromSettings) {
                    TextButton(onClick = onClose) { Text("Retour", color = HudColors.TextSecondary) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmLimited = true }) {
                    Text("Continuer avec des fonctions limitées", color = HudColors.TextSecondary)
                }
            }
        }
    }

    if (confirmLimited) {
        val limitations = snapshot.missing.joinToString(separator = "\n") { "• ${it.capability.limitation()}" }
        AlertDialog(
            onDismissRequest = { confirmLimited = false },
            title = { Text("Continuer avec des fonctions limitées ?") },
            text = {
                Text(
                    if (limitations.isBlank()) {
                        "Toutes les capacités sont disponibles. Vous pouvez terminer la configuration."
                    } else {
                        "Ces fonctions resteront indisponibles :\n$limitations\n\nL’assistant sera reproposé au prochain lancement."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLimited = false
                        onContinueLimited()
                    },
                ) { Text("Continuer") }
            },
            dismissButton = { TextButton(onClick = { confirmLimited = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun CapabilityCard(
    status: SetupCapabilityStatus,
    isCurrentStep: Boolean,
    mediaListenerOperational: Boolean,
    requestTarget: CapabilityRequestTarget,
    onRequest: () -> Unit,
) {
    HudPanel(
        modifier = Modifier.fillMaxWidth(),
        glow = isCurrentStep,
        borderColor = if (status.granted) HudColors.Success else HudColors.CyanMuted,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    status.capability.title(),
                    color = HudColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    status.capability.explanation(),
                    color = HudColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HudStatusBadge(
                text = if (status.granted) "Autorisé" else "À autoriser",
                color = if (status.granted) HudColors.Success else HudColors.Warning,
            )
        }
        if (status.capability == SetupCapability.MEDIA_CONTROL && status.granted && !mediaListenerOperational) {
            Text(
                "Accès autorisé. Le service de contrôle se reconnectera lorsque Android le démarrera.",
                color = HudColors.Warning,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!status.granted) {
            HudSecondaryButton(
                text = if (requestTarget == CapabilityRequestTarget.RUNTIME_PERMISSION) "Autoriser" else "Ouvrir les réglages",
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                enabled = isCurrentStep,
            )
            if (!isCurrentStep) {
                Text("Terminez d’abord l’étape précédente.", color = HudColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

fun SetupCapability.title(): String =
    when (this) {
        SetupCapability.NOTIFICATIONS -> "Notifications Branlly Pocket"
        SetupCapability.MEDIA_CONTROL -> "Contrôle de lecture"
        SetupCapability.NEARBY_DEVICES -> "Bluetooth / Appareils à proximité"
    }

private fun SetupCapability.explanation(): String =
    when (this) {
        SetupCapability.NOTIFICATIONS -> "Affiche l’exécution, les continuations et les demandes d’action."
        SetupCapability.MEDIA_CONTROL -> "Détecte le démarrage réel de la musique via le service de notifications Android."
        SetupCapability.NEARBY_DEVICES -> "Active Bluetooth et lit uniquement les appareils déjà jumelés. Aucun scan ni localisation."
    }

private fun SetupCapability.limitation(): String =
    when (this) {
        SetupCapability.NOTIFICATIONS -> "notifications de continuation, d’exécution et de test"
        SetupCapability.MEDIA_CONTROL -> "confirmation automatique du démarrage réel de PLAY_MEDIA"
        SetupCapability.NEARBY_DEVICES -> "activation Bluetooth et sélection des appareils jumelés"
    }
