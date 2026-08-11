package com.egoisticfoil.mneme.ui.recap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.egoisticfoil.mneme.model.RichTextDocument
import com.egoisticfoil.mneme.ui.editor.RichTextEditor
import com.egoisticfoil.mneme.ui.editor.RichTextFormattingBar
import com.egoisticfoil.mneme.ui.editor.rememberRichTextEditorState
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun MonthlyRecapScreen(
    month: YearMonth,
    document: RichTextDocument,
    isSaving: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDocumentChange: (RichTextDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorState = rememberRichTextEditorState("recap-$month", document)
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())

    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecapArrow("Previous month", onPreviousMonth) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "MONTHLY RECAP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (isSaving) "Saving…" else "Saved",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RecapArrow("Next month", onNextMonth) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Looking back", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "What stayed with you? What changed? What do you want to remember?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RichTextEditor(
                document = document,
                documentKey = "recap-$month",
                state = editorState,
                onDocumentChange = onDocumentChange,
                placeholder = "Write the story of this month…",
                modifier = Modifier.padding(top = 24.dp),
            )
            Spacer(Modifier.height(if (editorState.isFocused) 18.dp else 48.dp))
        }
        AnimatedVisibility(
            visible = editorState.isFocused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                RichTextFormattingBar(document, editorState, onDocumentChange)
            }
        }
    }
}

@Composable
private fun RecapArrow(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { content() }
    }
}
