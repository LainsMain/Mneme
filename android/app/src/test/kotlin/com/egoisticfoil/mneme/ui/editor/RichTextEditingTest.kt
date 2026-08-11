package com.egoisticfoil.mneme.ui.editor

import androidx.compose.ui.text.TextRange
import com.egoisticfoil.mneme.model.InlineStyle
import com.egoisticfoil.mneme.model.RichTextDocument
import com.egoisticfoil.mneme.model.TextMark
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
}
