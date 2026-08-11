package com.lainsmain.mneme.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.em
import com.lainsmain.mneme.model.InlineStyle
import com.lainsmain.mneme.model.RichTextDocument
import com.lainsmain.mneme.model.TextMark

object RichTextEditing {
    fun typingStyles(
        document: RichTextDocument,
        cursor: Int,
        pendingStyles: Set<InlineStyle>?,
    ): Set<InlineStyle> = pendingStyles
        ?: document.stylesAt((cursor - 1).coerceIn(0, document.text.lastIndex.coerceAtLeast(0)))

    fun toggleTypingStyle(
        document: RichTextDocument,
        cursor: Int,
        pendingStyles: Set<InlineStyle>?,
        style: InlineStyle,
    ): Set<InlineStyle> {
        val current = typingStyles(document, cursor, pendingStyles)
        return if (style in current) current - style else current + style
    }

    fun replaceText(
        document: RichTextDocument,
        newText: String,
        insertedStyles: Set<InlineStyle>,
    ): RichTextDocument {
        if (newText == document.text) return document

        val prefix = commonPrefixLength(document.text, newText)
        val suffix = commonSuffixLength(document.text, newText, prefix)
        val oldReplaceEnd = document.text.length - suffix
        val newReplaceEnd = newText.length - suffix

        val oldStyles = stylesByCharacter(document)
        val newStyles = buildList {
            addAll(oldStyles.take(prefix))
            repeat(newReplaceEnd - prefix) { add(insertedStyles) }
            addAll(oldStyles.drop(oldReplaceEnd))
        }

        return RichTextDocument(newText, marksFromCharacterStyles(newStyles))
    }

    fun toggleStyle(
        document: RichTextDocument,
        selection: TextRange,
        style: InlineStyle,
    ): RichTextDocument {
        val start = selection.min.coerceIn(0, document.text.length)
        val end = selection.max.coerceIn(0, document.text.length)
        if (start == end) return document

        val styles = stylesByCharacter(document).toMutableList()
        val shouldRemove = (start until end).all { style in styles[it] }
        for (index in start until end) {
            styles[index] = if (shouldRemove) styles[index] - style else styles[index] + style
        }
        return document.copy(marks = marksFromCharacterStyles(styles))
    }

    fun selectionHasStyle(
        document: RichTextDocument,
        selection: TextRange,
        style: InlineStyle,
    ): Boolean {
        if (document.text.isEmpty()) return false
        if (selection.collapsed) {
            val lookup = (selection.start - 1).coerceAtLeast(0)
            return style in document.stylesAt(lookup)
        }
        return (selection.min until selection.max).all { index ->
            document.marks.any { it.style == style && index in it.start until it.end }
        }
    }

    private fun stylesByCharacter(document: RichTextDocument): List<Set<InlineStyle>> =
        List(document.text.length) { index ->
            document.marks
                .asSequence()
                .filter { index in it.start until it.end }
                .map { it.style }
                .toSet()
        }

    private fun marksFromCharacterStyles(styles: List<Set<InlineStyle>>): List<TextMark> = buildList {
        InlineStyle.entries.forEach { style ->
            var start: Int? = null
            for (index in 0..styles.size) {
                val enabled = index < styles.size && style in styles[index]
                if (enabled && start == null) start = index
                if (!enabled && start != null) {
                    add(TextMark(start = start, end = index, style = style))
                    start = null
                }
            }
        }
    }.sortedWith(compareBy(TextMark::start, TextMark::end, TextMark::style))

    private fun commonPrefixLength(old: String, new: String): Int {
        val limit = minOf(old.length, new.length)
        var index = 0
        while (index < limit && old[index] == new[index]) index++
        return index
    }

    private fun commonSuffixLength(old: String, new: String, prefix: Int): Int {
        val limit = minOf(old.length, new.length) - prefix
        var count = 0
        while (count < limit && old[old.lastIndex - count] == new[new.lastIndex - count]) count++
        return count
    }
}
class RichTextVisualTransformation(
    private val document: RichTextDocument,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        document.marks.forEach { mark ->
            if (mark.start < mark.end && mark.start < text.length) {
                builder.addStyle(
                    style = mark.style.toSpanStyle(),
                    start = mark.start.coerceAtLeast(0),
                    end = mark.end.coerceAtMost(text.length),
                )
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun InlineStyle.toSpanStyle(): SpanStyle = when (this) {
        InlineStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
        InlineStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
        InlineStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
        InlineStyle.StrikeThrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        InlineStyle.Heading -> SpanStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 1.28.em,
            textGeometricTransform = TextGeometricTransform(scaleX = 1.02f),
        )
    }
}
