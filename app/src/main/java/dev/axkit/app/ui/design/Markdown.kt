package dev.axkit.app.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Inline markdown (bold, italic, code, links) for chat bubbles. Links are shown, never opened automatically. */
object InlineMarkdown {
    private val pattern = Regex("""\*\*(.+?)\*\*|__(.+?)__|\*(.+?)\*|_(.+?)_|`(.+?)`|\[(.+?)]\((.+?)\)""")

    fun parse(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in pattern.findAll(text)) {
            append(text.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]) }
                g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[2]) }
                g[3].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[3]) }
                g[4].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[4]) }
                g[5].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(g[5]) }
                g[6].isNotEmpty() -> withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(g[6]) }
            }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

/** Parsed once per text and link colour (the chat redraws often; parsing every time made scrolling stutter). */
private object MarkdownCache {
    private val map = HashMap<Pair<String, Color>, AnnotatedString>()
    @Synchronized fun get(text: String, link: Color): AnnotatedString {
        val key = text to link
        map[key]?.let { return it }
        val result = InlineMarkdown.parse(text, link)
        if (map.size > 3000) map.clear()
        map[key] = result
        return result
    }
}

@Composable
fun MarkdownText(
    source: String, style: TextStyle = AxType.body, color: Color = Theme.ax.label, modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE, onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null,
) {
    val link = Theme.ax.accent
    val text = androidx.compose.runtime.remember(source, link) { MarkdownCache.get(source, link) }
    Text(
        text, style = style, color = color, modifier = modifier, maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) androidx.compose.ui.text.style.TextOverflow.Clip else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = onTextLayout ?: {},
    )
}

private val headingLine = Regex("^(#{1,4})\\s+(.*)$")
private val bulletLine = Regex("^[-*•]\\s+(.*)$")
private val numberedLine = Regex("^(\\d+)[.)]\\s+(.*)$")
private val ruleLine = Regex("^([-*_]\\s*){3,}$")
private val quoteLine = Regex("^>\\s?(.*)$")

/** Block markdown for documents: headings, bullet and numbered lists, paragraphs. */
@Composable
fun MarkdownDocument(source: String, modifier: Modifier = Modifier) {
    val blocks = androidx.compose.runtime.remember(source) { buildList {
        val paragraph = mutableListOf<String>()
        fun flush() { if (paragraph.isNotEmpty()) { add("p" to paragraph.joinToString(" ")); paragraph.clear() } }
        for (raw in source.split("\n")) {
            var line = raw.trim()
            // Quotes and fences are drawn as plain text; a rule line is dropped.
            if (line.startsWith("```") || ruleLine.matches(line)) continue
            quoteLine.find(line)?.let { line = it.groupValues[1] }
            if (line.isEmpty()) { flush(); continue }
            val heading = headingLine.find(line)
            val bullet = bulletLine.find(line)
            val numbered = numberedLine.find(line)
            when {
                heading != null -> { flush(); add("h${heading.groupValues[1].length}" to heading.groupValues[2]) }
                bullet != null -> { flush(); add("b" to bullet.groupValues[1]) }
                numbered != null -> { flush(); add("n${numbered.groupValues[1]}" to numbered.groupValues[2]) }
                else -> paragraph.add(line)
            }
        }
        flush()
    } }
    SelectionContainer {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((kind, text) in blocks) {
                when {
                    kind.startsWith("h") -> {
                        val level = kind.drop(1).toInt()
                        MarkdownText(text, style = if (level <= 1) AxType.title2 else if (level == 2) AxType.title3.copy(fontWeight = FontWeight.Bold) else AxType.headline, modifier = Modifier.padding(top = 6.dp))
                    }
                    kind == "b" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("•", style = AxType.body, color = Theme.ax.label); MarkdownText(text) }
                    kind.startsWith("n") -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("${kind.drop(1)}.", style = AxType.body, color = Theme.ax.label); MarkdownText(text) }
                    else -> MarkdownText(text)
                }
            }
        }
    }
}
