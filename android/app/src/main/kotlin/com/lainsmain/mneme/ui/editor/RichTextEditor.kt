package com.lainsmain.mneme.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material.icons.rounded.Title
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lainsmain.mneme.model.InlineStyle
import com.lainsmain.mneme.model.RichTextDocument

@Stable
class RichTextEditorState internal constructor(initialText: String) {
    var fieldValue by mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
    /** Null inherits the cursor's surrounding styles; an empty set explicitly types plain text. */
    var pendingStyles by mutableStateOf<Set<InlineStyle>?>(null)
    var isFocused by mutableStateOf(false)
}

@Composable
fun rememberRichTextEditorState(documentKey: String, document: RichTextDocument): RichTextEditorState =
    remember(documentKey) { RichTextEditorState(document.text) }

@Composable
fun RichTextEditor(
    document: RichTextDocument,
    documentKey: String,
    state: RichTextEditorState,
    onDocumentChange: (RichTextDocument) -> Unit,
    placeholder: String = "Give today a title, or simply start writing…",
    modifier: Modifier = Modifier,
) {
    val bringIntoViewRequester = remember(documentKey) { BringIntoViewRequester() }
    var textLayoutResult by remember(documentKey) { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val cursorClearance = with(density) { 18.dp.toPx() }

    LaunchedEffect(documentKey, document.text) {
        if (state.fieldValue.text != document.text) {
            state.fieldValue = state.fieldValue.copy(
                text = document.text,
                selection = TextRange(document.text.length),
            )
        }
    }

    LaunchedEffect(
        state.fieldValue.text,
        state.fieldValue.selection,
        state.isFocused,
        imeBottom,
        textLayoutResult,
    ) {
        if (!state.isFocused) return@LaunchedEffect
        withFrameNanos { }
        val layout = textLayoutResult ?: return@LaunchedEffect
        val layoutTextLength = layout.layoutInput.text.length
        val offset = state.fieldValue.selection.end.coerceIn(0, layoutTextLength)
        val cursor = layout.getCursorRect(offset)
        bringIntoViewRequester.bringIntoView(
            Rect(
                left = cursor.left,
                top = cursor.top,
                right = cursor.right.coerceAtLeast(cursor.left + 1f),
                bottom = cursor.bottom + cursorClearance,
            ),
        )
    }

    Column(
        modifier = modifier,
    ) {
        BasicTextField(
            value = state.fieldValue,
            onValueChange = { newValue ->
                if (newValue.text != state.fieldValue.text) {
                    val inheritedStyles = RichTextEditing.typingStyles(
                        document = document,
                        cursor = state.fieldValue.selection.start,
                        pendingStyles = state.pendingStyles,
                    )
                    val updated = RichTextEditing.replaceText(document, newValue.text, inheritedStyles)
                    state.fieldValue = newValue
                    onDocumentChange(updated)
                } else {
                    val selectionChanged = newValue.selection != state.fieldValue.selection
                    state.fieldValue = newValue
                    if (selectionChanged) state.pendingStyles = null
                }
            },
            modifier = Modifier
                .bringIntoViewRequester(bringIntoViewRequester)
                .fillMaxWidth()
                .heightIn(min = if (state.fieldValue.text.isEmpty()) 180.dp else 72.dp)
                .onFocusChanged { state.isFocused = it.isFocused },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = RichTextVisualTransformation(document),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
                keyboardType = KeyboardType.Text,
            ),
            onTextLayout = { textLayoutResult = it },
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth()) {
                    if (state.fieldValue.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                    innerTextField()
                }
            },
        )

    }
}

@Composable
fun RichTextFormattingBar(
    document: RichTextDocument,
    state: RichTextEditorState,
    onDocumentChange: (RichTextDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    FormattingBar(
        document = document,
        selection = state.fieldValue.selection,
        pendingStyles = state.pendingStyles,
        onToggle = { style ->
            if (state.fieldValue.selection.collapsed) {
                state.pendingStyles = RichTextEditing.toggleTypingStyle(
                    document = document,
                    cursor = state.fieldValue.selection.start,
                    pendingStyles = state.pendingStyles,
                    style = style,
                )
            } else {
                state.pendingStyles = null
                onDocumentChange(RichTextEditing.toggleStyle(document, state.fieldValue.selection, style))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun FormattingBar(
    document: RichTextDocument,
    selection: TextRange,
    pendingStyles: Set<InlineStyle>?,
    onToggle: (InlineStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.wrapContentWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorStyle.entries.forEach { item ->
                val selected = if (selection.collapsed) {
                    item.style in RichTextEditing.typingStyles(
                        document = document,
                        cursor = selection.start,
                        pendingStyles = pendingStyles,
                    )
                } else {
                    RichTextEditing.selectionHasStyle(document, selection, item.style)
                }
                Surface(
                    onClick = { onToggle(item.style) },
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.description,
                            modifier = Modifier.size(18.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private enum class EditorStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val style: InlineStyle,
) {
    Bold(Icons.Rounded.FormatBold, "Bold", InlineStyle.Bold),
    Italic(Icons.Rounded.FormatItalic, "Italic", InlineStyle.Italic),
    Underline(Icons.Rounded.FormatUnderlined, "Underline", InlineStyle.Underline),
    Heading(Icons.Rounded.Title, "Heading", InlineStyle.Heading),
    Strike(Icons.Rounded.StrikethroughS, "Strikethrough", InlineStyle.StrikeThrough),
}
