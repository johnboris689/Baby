package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RichMarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    // 1. Check for Collapsible Reasoning Blocks <thinking>...</thinking> or [thinking]...[/thinking]
    val thinkingPattern = Regex("(?s)<thinking>(.*?)</thinking>")
    val thinkingMatch = thinkingPattern.find(content)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (thinkingMatch != null) {
            val thinkingContent = thinkingMatch.groupValues[1].trim()
            val remainingContent = content.replace(thinkingMatch.value, "").trim()

            CollapsibleReasoningBlock(reasoning = thinkingContent)

            if (remainingContent.isNotEmpty()) {
                RenderMarkdownBlocks(content = remainingContent, textColor = textColor)
            }
        } else if (content.startsWith("Thinking:") || content.contains("\nThinking:")) {
            // Check if there is a block of thinking
            val lines = content.split("\n")
            val thinkingLines = lines.filter { it.trim().startsWith("Thinking:") || it.trim().startsWith("Thought:") }
            if (thinkingLines.isNotEmpty()) {
                val thinkingText = thinkingLines.joinToString("\n") { it.replaceFirst(Regex("^(Thinking|Thought):"), "").trim() }
                val remainingText = lines.filterNot { it.trim().startsWith("Thinking:") || it.trim().startsWith("Thought:") }.joinToString("\n").trim()

                CollapsibleReasoningBlock(reasoning = thinkingText)

                if (remainingText.isNotEmpty()) {
                    RenderMarkdownBlocks(content = remainingText, textColor = textColor)
                }
            } else {
                RenderMarkdownBlocks(content = content, textColor = textColor)
            }
        } else {
            RenderMarkdownBlocks(content = content, textColor = textColor)
        }
    }
}

@Composable
fun CollapsibleReasoningBlock(reasoning: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.03f)
        ),
        border = BoxDefaults.borderStroke()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = "Reasoning",
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Baby's Thought Process",
                        color = Color(0xFFC084FC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 8.dp))
                    Text(
                        text = reasoning,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RenderMarkdownBlocks(content: String, textColor: Color) {
    // Split into blocks: code blocks (```), tables, and regular text paragraphs
    val blocks = parseMarkdownBlocks(content)

    blocks.forEach { block ->
        when (block.type) {
            BlockType.CODE -> {
                PremiumSyntaxHighlightCodeBlock(
                    language = block.meta ?: "code",
                    code = block.rawContent
                )
            }
            BlockType.TABLE -> {
                MarkdownTableRenderer(rawTable = block.rawContent)
            }
            BlockType.MATH_BLOCK -> {
                MathFormulaRenderer(formula = block.rawContent, isBlock = true)
            }
            BlockType.TEXT -> {
                RenderParagraphText(text = block.rawContent, textColor = textColor)
            }
        }
    }
}

enum class BlockType { TEXT, CODE, TABLE, MATH_BLOCK }
data class MarkdownBlock(val type: BlockType, val rawContent: String, val meta: String? = null)

fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.split("\n")
    var index = 0

    while (index < lines.size) {
        val line = lines[index]

        // 1. Code Block detection
        if (line.trim().startsWith("```")) {
            val language = line.trim().substring(3).trim()
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                codeLines.add(lines[index])
                index++
            }
            blocks.add(MarkdownBlock(BlockType.CODE, codeLines.joinToString("\n"), language))
            index++
            continue
        }

        // 2. Math Block detection $$...$$
        if (line.trim().startsWith("$$")) {
            val mathLines = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trim().endsWith("$$") && !lines[index].trim().startsWith("$$")) {
                mathLines.add(lines[index])
                index++
            }
            if (index < lines.size && lines[index].trim().endsWith("$$") && lines[index].trim() != "$$") {
                mathLines.add(lines[index].trim().removeSuffix("$$"))
            }
            blocks.add(MarkdownBlock(BlockType.MATH_BLOCK, mathLines.joinToString("\n")))
            index++
            continue
        }

        // 3. Table detection
        if (line.trim().startsWith("|") && index + 1 < lines.size && lines[index + 1].trim().startsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (index < lines.size && lines[index].trim().startsWith("|")) {
                tableLines.add(lines[index])
                index++
            }
            blocks.add(MarkdownBlock(BlockType.TABLE, tableLines.joinToString("\n")))
            continue
        }

        // 4. Regular paragraph text block
        val textLines = mutableListOf<String>()
        while (index < lines.size &&
            !lines[index].trim().startsWith("```") &&
            !lines[index].trim().startsWith("$$") &&
            !(lines[index].trim().startsWith("|") && index + 1 < lines.size && lines[index + 1].trim().startsWith("|"))
        ) {
            textLines.add(lines[index])
            index++
        }
        if (textLines.isNotEmpty()) {
            blocks.add(MarkdownBlock(BlockType.TEXT, textLines.joinToString("\n")))
        }
    }

    return blocks
}

@Composable
fun RenderParagraphText(text: String, textColor: Color) {
    if (text.trim().isEmpty()) return

    val lines = text.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || trimmedLine.startsWith("• ")) {
                // Bullet list item
                Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                    Text(text = "• ", color = Color(0xFF60A5FA), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = parseInlineMarkdown(trimmedLine.substring(2)),
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
            } else if (trimmedLine.startsWith("# ")) {
                Text(
                    text = parseInlineMarkdown(trimmedLine.substring(2)),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else if (trimmedLine.startsWith("## ")) {
                Text(
                    text = parseInlineMarkdown(trimmedLine.substring(3)),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            } else if (trimmedLine.startsWith("### ")) {
                Text(
                    text = parseInlineMarkdown(trimmedLine.substring(4)),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                // Standard text line
                Text(
                    text = parseInlineMarkdown(line),
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0

        while (cursor < text.length) {
            // 1. Math block inline $...$
            val mathIndex = text.indexOf('$', cursor)
            if (mathIndex != -1 && mathIndex + 1 < text.length) {
                val nextMath = text.indexOf('$', mathIndex + 1)
                if (nextMath != -1) {
                    append(text.substring(cursor, mathIndex))
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Italic, color = Color(0xFF818CF8)))
                    append(text.substring(mathIndex + 1, nextMath))
                    pop()
                    cursor = nextMath + 1
                    continue
                }
            }

            // 2. Bold **...**
            val boldIndex = text.indexOf("**", cursor)
            if (boldIndex != -1 && boldIndex + 2 < text.length) {
                val nextBold = text.indexOf("**", boldIndex + 2)
                if (nextBold != -1) {
                    append(text.substring(cursor, boldIndex))
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                    append(text.substring(boldIndex + 2, nextBold))
                    pop()
                    cursor = nextBold + 2
                    continue
                }
            }

            // 3. Italics *...*
            val italicIndex = text.indexOf('*', cursor)
            if (italicIndex != -1 && italicIndex + 1 < text.length) {
                val nextItalic = text.indexOf('*', italicIndex + 1)
                if (nextItalic != -1) {
                    append(text.substring(cursor, italicIndex))
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(italicIndex + 1, nextItalic))
                    pop()
                    cursor = nextItalic + 1
                    continue
                }
            }

            // 4. Inline Code `...`
            val codeIndex = text.indexOf('`', cursor)
            if (codeIndex != -1 && codeIndex + 1 < text.length) {
                val nextCode = text.indexOf('`', codeIndex + 1)
                if (nextCode != -1) {
                    append(text.substring(cursor, codeIndex))
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFEC4899),
                            background = Color.White.copy(alpha = 0.08f)
                        )
                    )
                    append(text.substring(codeIndex + 1, nextCode))
                    pop()
                    cursor = nextCode + 1
                    continue
                }
            }

            // Just normal text remaining
            append(text.substring(cursor))
            break
        }
    }
}

@Composable
fun PremiumSyntaxHighlightCodeBlock(language: String, code: String) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF020617))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifEmpty { "code" }.uppercase(),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(code)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy code",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = highlightSyntax(code),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
    }
}

fun highlightSyntax(code: String): AnnotatedString {
    val keywords = setOf(
        "val", "var", "fun", "class", "interface", "import", "package", "return", "if", "else", "when", "for", "while", "do",
        "private", "public", "protected", "override", "suspend", "null", "true", "false", "this", "super", "try", "catch", "throw",
        "import", "from", "def", "let", "const", "function", "async", "await", "import", "export", "switch", "case", "break", "continue"
    )

    return buildAnnotatedString {
        val words = code.split(Regex("(?<=\\b)|(?=\\b)|(?<=[^\\w])|(?=[^\\w])"))
        var inString = false
        var stringChar = ' '

        words.forEach { word ->
            when {
                word == "\"" || word == "'" -> {
                    if (inString && word[0] == stringChar) {
                        pushStyle(SpanStyle(color = Color(0xFFA5F3FC))) // String color (Light Cyan)
                        append(word)
                        pop()
                        inString = false
                    } else if (!inString) {
                        inString = true
                        stringChar = word[0]
                        pushStyle(SpanStyle(color = Color(0xFFA5F3FC)))
                        append(word)
                        pop()
                    } else {
                        append(word)
                    }
                }
                inString -> {
                    pushStyle(SpanStyle(color = Color(0xFFA5F3FC)))
                    append(word)
                    pop()
                }
                keywords.contains(word) -> {
                    pushStyle(SpanStyle(color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)) // Keyword color (Rose/Red)
                    append(word)
                    pop()
                }
                word.matches(Regex("\\d+")) -> {
                    pushStyle(SpanStyle(color = Color(0xFFF59E0B))) // Numbers (Amber)
                    append(word)
                    pop()
                }
                word.startsWith("//") || word.startsWith("#") -> {
                    pushStyle(SpanStyle(color = Color(0xFF64748B), fontStyle = FontStyle.Italic)) // Comments (Slate)
                    append(word)
                    pop()
                }
                else -> {
                    append(word)
                }
            }
        }
    }
}

@Composable
fun MarkdownTableRenderer(rawTable: String) {
    val lines = rawTable.trim().split("\n")
    if (lines.size < 2) return

    val headers = parseTableRow(lines[0])
    // Skip separator line (index 1)
    val rows = lines.drop(2).map { parseTableRow(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.05f))
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            headers.forEach { header ->
                Text(
                    text = header,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.widthIn(min = 80.dp, max = 150.dp)
                )
            }
        }

        Divider(color = Color.White.copy(alpha = 0.1f))

        // Table Rows
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .background(if (index % 2 == 0) Color.Transparent else Color.White.copy(alpha = 0.01f))
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.widthIn(min = 80.dp, max = 150.dp)
                    )
                }
            }
            if (index < rows.size - 1) {
                Divider(color = Color.White.copy(alpha = 0.04f))
            }
        }
    }
}

fun parseTableRow(line: String): List<String> {
    return line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
}

@Composable
fun MathFormulaRenderer(formula: String, isBlock: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isBlock) 8.dp else 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isBlock) Color(0xFF1E293B) else Color.White.copy(alpha = 0.04f))
            .padding(if (isBlock) 12.dp else 4.dp),
        contentAlignment = if (isBlock) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            text = formula,
            color = Color(0xFF818CF8), // Purple Indigo accent for formulas
            fontSize = if (isBlock) 15.sp else 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic
        )
    }
}

object BoxDefaults {
    @Composable
    fun borderStroke() = androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.08f)
    )
}
