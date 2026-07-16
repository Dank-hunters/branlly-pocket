package com.branlly.pocket.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Personnaliser", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Icône et couleur du raccourci", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Rechercher une icône") },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Tous") + iconChoices.map(IconChoice::category).distinct(), key = { it }) { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = IconChoice::key) { choice ->
                    Surface(
                        modifier = Modifier.size(width = 84.dp, height = 74.dp).clickable { onChange(choice.key, accentColor) },
                        shape = RoundedCornerShape(14.dp),
                        color =
                            if (choice.key ==
                                iconKey
                            ) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(choice.glyph, style = MaterialTheme.typography.titleLarge)
                            Text(choice.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Text("Couleur", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ShortcutAccentColor.entries, key = { it.name }) { color ->
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
