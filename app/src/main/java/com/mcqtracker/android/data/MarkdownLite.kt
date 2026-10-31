package com.mcqtracker.android.data

/**
 * Minimal Markdown-to-plain-text conversion for display.
 *
 * Deliberately not a renderer: no LaTeX/MathJax, no HTML, no tables. The goal is
 * readable question text, callouts stripped, and links reduced to their labels.
 */
object MarkdownLite {

    private val CALLOUT = Regex("""^\s*>\s*\[![^\]]*][-+]?\s*.*$""", RegexOption.IGNORE_CASE)
    private val HTML_TAG = Regex("""</?[A-Za-z][^>]*>""")
    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val HEADING = Regex("""^\s{0,3}#{1,6}\s*""")
    private val LIST_MARKER = Regex("""^\s*(?:[-*+]|\d+[.)])\s+""")
    private val BLOCKQUOTE = Regex("""^\s*>\s?""")
    private val HORIZONTAL_RULE = Regex("""^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""")
    private val BOLD_ITALIC = Regex("""(\*\*\*|___)(.+?)\1""")
    private val BOLD = Regex("""(\*\*|__)(.+?)\1""")
    private val ITALIC = Regex("""(\*|_)(.+?)\1""")
    private val INLINE_CODE = Regex("""`([^`]*)`""")
    private val MULTI_BLANK = Regex("""\n{3,}""")

    fun toPlainText(markdown: String): String {
        if (markdown.isBlank()) return ""
        val kept = mutableListOf<String>()
        for (rawLine in markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            val line = rawLine
            if (CALLOUT.matches(line)) continue
            if (HORIZONTAL_RULE.matches(line)) continue
            var out = line
            out = HEADING.replace(out, "")
            out = BLOCKQUOTE.replace(out, "")
            out = LIST_MARKER.replace(out, "")
            out = HTML_TAG.replace(out, "")
            out = IMAGE.replace(out, "$1")
            out = LINK.replace(out, "$1")
            out = INLINE_CODE.replace(out, "$1")
            out = BOLD_ITALIC.replace(out, "$2")
            out = BOLD.replace(out, "$2")
            out = ITALIC.replace(out, "$2")
            kept += out.trimEnd()
        }
        return kept.joinToString("\n")
            .trim()
            .replace(MULTI_BLANK, "\n\n")
    }

    /** Single-line variant for list rows and pills. */
    fun toSingleLine(markdown: String): String =
        toPlainText(markdown).split("\n").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}
