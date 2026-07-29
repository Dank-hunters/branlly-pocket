@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.branlly.pocket.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.branlly.pocket.domain.model.ShortcutAccentColor
import com.branlly.pocket.ui.hud.HudColors
import com.branlly.pocket.ui.hud.HudCutCornerShape
import com.branlly.pocket.ui.hud.HudPanel
import com.branlly.pocket.ui.hud.HudSectionHeader

private data class IconChoice(
    val key: String,
    val glyph: String,
    val label: String,
    val category: String,
)

private val iconChoices =
    listOf(
        IconChoice("bolt", "ϟ", "Général", "Général"),
        IconChoice("route", "↗", "Itinéraire", "Navigation"),
        IconChoice("car", "▰", "Voiture", "Navigation"),
        IconChoice("home", "⌂", "Maison", "Maison"),
        IconChoice("music", "♪", "Musique", "Médias"),
        IconChoice("camera", "◉", "Appareil photo", "Applications"),
        IconChoice("phone", "☎", "Appeler", "Communication"),
        IconChoice("message", "✉", "Message", "Communication"),
        IconChoice("work", "▣", "Travail", "Productivité"),
        IconChoice("calendar", "□", "Calendrier", "Productivité"),
        IconChoice("fitness", "♥", "Sport", "Santé"),
        IconChoice("settings", "⚙", "Réglages", "Téléphone"),
        IconChoice("bluetooth", "ᛒ", "Bluetooth", "Téléphone"),
        IconChoice("moon", "☾", "Silence", "Téléphone"),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationPickerSheet(
    iconKey: String,
    accentColor: ShortcutAccentColor,
    onChange: (String, ShortcutAccentColor) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tous") }
    val filtered =
        iconChoices.filter {
            (category == "Tous" || it.category == category) &&
                (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true))
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HudColors.BackgroundRaised,
        contentColor = HudColors.TextPrimary,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HudPanel {
                HudSectionHeader("Personnaliser", "Présentation")
                Text("Icône et couleur du raccourci", color = HudColors.TextSecondary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Rechercher une icône") },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (listOf("Tous") + iconChoices.map(IconChoice::category).distinct()).forEach { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                filtered.forEach { choice ->
                    Surface(
                        modifier = Modifier.size(width = 104.dp, height = 82.dp).clickable { onChange(choice.key, accentColor) },
                        shape = HudCutCornerShape,
                        color = if (choice.key == iconKey) HudColors.Cyan.copy(alpha = 0.16f) else HudColors.Card,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(choice.glyph, style = MaterialTheme.typography.titleLarge)
                            Text(choice.label, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            HudSectionHeader("Couleur")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShortcutAccentColor.entries.forEach { color ->
                    val selected = color == accentColor
                    Box(
                        modifier =
                            Modifier
                                .size(if (selected) 42.dp else 34.dp)
                                .background(color.toComposeColor(), CircleShape)
                                .semantics { contentDescription = color.name }
                                .clickable { onChange(iconKey, color) },
                    )
                }
            }
        }
    }
}

fun ShortcutAccentColor.toComposeColor(): Color =
    when (this) {
        ShortcutAccentColor.BLUE -> Color(0xFF82AFFF)
        ShortcutAccentColor.CYAN -> Color(0xFF75E3F5)
        ShortcutAccentColor.VIOLET -> Color(0xFFB99CFF)
        ShortcutAccentColor.PINK -> Color(0xFFFFA3D2)
        ShortcutAccentColor.RED -> Color(0xFFFF9C9C)
        ShortcutAccentColor.ORANGE -> Color(0xFFFFB77A)
        ShortcutAccentColor.YELLOW -> Color(0xFFFFE082)
        ShortcutAccentColor.GREEN -> Color(0xFF9BE49D)
        ShortcutAccentColor.MINT -> Color(0xFF87E8C3)
        ShortcutAccentColor.WHITE -> Color(0xFFF1F1F7)
        ShortcutAccentColor.GRAY -> Color(0xFFC3C6D0)
    }
