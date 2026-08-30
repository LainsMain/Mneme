package com.lainsmain.mneme.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.lainsmain.mneme.data.JournalDefinition
import com.lainsmain.mneme.data.JournalIconKind

@Composable
fun JournalIconView(journal: JournalDefinition, modifier: Modifier = Modifier) {
    if (journal.icon.kind == JournalIconKind.Emoji) {
        Text(journal.icon.value, modifier = modifier, textAlign = TextAlign.Center)
    } else {
        Icon(journalMaterialIcon(journal.icon.value), contentDescription = null, modifier = modifier)
    }
}

fun journalMaterialIcon(key: String): ImageVector = when (key) {
    "dream" -> Icons.Rounded.Bedtime
    "fitness" -> Icons.Rounded.FitnessCenter
    "gratitude", "heart" -> Icons.Rounded.Favorite
    "travel" -> Icons.Rounded.Flight
    "food" -> Icons.Rounded.Restaurant
    "work" -> Icons.Rounded.Work
    "study" -> Icons.Rounded.School
    "health" -> Icons.Rounded.HealthAndSafety
    "nature" -> Icons.Rounded.Forest
    "music" -> Icons.Rounded.MusicNote
    "art" -> Icons.Rounded.Brush
    "family" -> Icons.Rounded.Groups
    "star" -> Icons.Rounded.Star
    "idea" -> Icons.Rounded.Lightbulb
    "camera" -> Icons.Rounded.CameraAlt
    else -> Icons.AutoMirrored.Rounded.MenuBook
}
