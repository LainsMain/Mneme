package com.lainsmain.mneme.ui.editor

import androidx.compose.ui.text.TextRange
import com.lainsmain.mneme.model.InlineStyle
import com.lainsmain.mneme.model.RichTextDocument
import com.lainsmain.mneme.model.TextMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextEditingTest {
    @Test
    fun toggleStyle_formatsAndUnformatsSelection() {
        val original = RichTextDocument("A quiet day")
        val bold = RichTextEditing.toggleStyle(original, TextRange(2, 7), InlineStyle.Bold)

        assertEquals(listOf(TextMark(2, 7, InlineStyle.Bold)), bold.marks)
        assertTrue(RichTextEditing.selectionHasStyle(bold, TextRange(2, 7), InlineStyle.Bold))

        val plain = RichTextEditing.toggleStyle(bold, TextRange(2, 7), InlineStyle.Bold)
        assertTrue(plain.marks.isEmpty())
    }

    @Test
    fun replaceText_preservesSurroundingMarksAndStylesInsertedText() {
        val original = RichTextDocument(
            text = "hello world",
            marks = listOf(TextMark(0, 5, InlineStyle.Bold)),
        )

        val edited = RichTextEditing.replaceText(
            document = original,
            newText = "hello calm world",
            insertedStyles = setOf(InlineStyle.Italic),
        )

        assertEquals("hello calm world", edited.text)
        assertTrue(InlineStyle.Bold in edited.stylesAt(1))
        assertTrue(InlineStyle.Italic in edited.stylesAt(7))
        assertFalse(InlineStyle.Italic in edited.stylesAt(13))
    }

    @Test
    fun collapsedCursor_canTurnInheritedStyleOffAndBackOn() {
        val bold = RichTextDocument(
            text = "hello",
            marks = listOf(TextMark(0, 5, InlineStyle.Bold)),
        )

        val plainTyping = RichTextEditing.toggleTypingStyle(
            document = bold,
            cursor = 5,
            pendingStyles = null,
            style = InlineStyle.Bold,
        )
        assertTrue(plainTyping.isEmpty())
        assertTrue(RichTextEditing.typingStyles(bold, 5, plainTyping).isEmpty())

        val boldTyping = RichTextEditing.toggleTypingStyle(
            document = bold,
            cursor = 5,
            pendingStyles = plainTyping,
            style = InlineStyle.Bold,
        )
        assertEquals(setOf(InlineStyle.Bold), boldTyping)
    }

    @Test
    fun movingCursorClearsOverrideAndInheritsThatPosition() {
        val document = RichTextDocument(
            text = "bold plain",
            marks = listOf(TextMark(0, 4, InlineStyle.Bold)),
        )

        assertEquals(
            setOf(InlineStyle.Bold),
            RichTextEditing.typingStyles(document, cursor = 4, pendingStyles = null),
        )
        assertTrue(RichTextEditing.typingStyles(document, cursor = 10, pendingStyles = null).isEmpty())
    }
}
