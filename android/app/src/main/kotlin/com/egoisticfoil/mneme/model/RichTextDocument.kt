package com.egoisticfoil.mneme.model

import kotlinx.serialization.Serializable

@Serializable
data class RichTextDocument(
    val text: String = "",
    val marks: List<TextMark> = emptyList(),
) {
    fun stylesAt(offset: Int): Set<InlineStyle> {
        if (text.isEmpty()) return emptySet()
        val safeOffset = offset.coerceIn(0, text.lastIndex)
        return marks
            .asSequence()
            .filter { safeOffset in it.start until it.end }
            .map { it.style }
            .toSet()
    }
}
@Serializable
data class TextMark(
    val start: Int,
    val end: Int,
    val style: InlineStyle,
)

@Serializable
enum class InlineStyle {
    Bold,
    Italic,
    Underline,
    Heading,
    StrikeThrough,
}
