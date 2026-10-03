package dev.shamar.adonis.ide

/**
 * Edge block-structure checks: unmatched open/close directives.
 *
 * Adonis Edge closes blocks with a generic `@end` (not Blade `@endif`).
 * Supports tag components (`@form({…})`, `@layouts.app({…})`, `@page()`), bang
 * self-closers (`@!button(…)`), and mid-block `@else` / `@elseif`.
 */
object AdonisEdgeStructure {
    data class Issue(
        val startOffset: Int,
        val endOffset: Int,
        val message: String,
    )

    val OPENERS: Set<String> = EdgeTagRegistry.OPENERS
    val MID_BLOCK: Set<String> = EdgeTagRegistry.MID_BLOCK
    val VOID: Set<String> = EdgeTagRegistry.VOID
    const val CLOSER: String = "end"

    private val LEGACY_CLOSERS: Set<String> = EdgeTagRegistry.LEGACY_CLOSERS
    private val INLINEABLE: Set<String> = EdgeTagRegistry.INLINEABLE

    private val DIRECTIVE = Regex("""@(!?)([A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)""")

    /**
     * True when `@section('name', 'value')` / classic `@layout('master')` style
     * inline (top-level comma in args, or single-string layout).
     */
    fun isInlineDirective(text: String, atNameEnd: Int): Boolean {
        var i = atNameEnd
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '(') return false
        i++ // past '('
        var paren = 1
        var brace = 0
        var bracket = 0
        var quote: Char? = null
        var sawTopLevelComma = false
        while (i < text.length && paren > 0) {
            val ch = text[i]
            when {
                quote != null -> {
                    if (ch == '\\' && i + 1 < text.length) {
                        i += 2
                        continue
                    }
                    if (ch == quote) quote = null
                    i++
                }
                ch == '\'' || ch == '"' -> {
                    quote = ch
                    i++
                }
                ch == '{' -> {
                    brace++
                    i++
                }
                ch == '}' -> {
                    brace--
                    i++
                }
                ch == '[' -> {
                    bracket++
                    i++
                }
                ch == ']' -> {
                    bracket--
                    i++
                }
                ch == '(' -> {
                    paren++
                    i++
                }
                ch == ')' -> {
                    paren--
                    i++
                }
                else -> {
                    if (ch == ',' && paren == 1 && brace == 0 && bracket == 0) {
                        sawTopLevelComma = true
                    }
                    i++
                }
            }
        }
        return sawTopLevelComma && paren == 0
    }

    fun analyze(text: String): List<Issue> {
        data class Frame(val name: String, val start: Int, val end: Int)
        val stack = ArrayDeque<Frame>()
        val issues = mutableListOf<Issue>()
        for (m in DIRECTIVE.findAll(text)) {
            val bang = m.groupValues[1] == "!"
            val name = m.groupValues[2]
            val root = name.substringBefore('.')
            val start = m.range.first
            val end = m.range.last + 1
            when {
                bang || root in VOID || name in VOID -> {
                    // Self-closing / void — never push.
                }
                name == CLOSER || name in LEGACY_CLOSERS -> {
                    if (stack.isEmpty()) {
                        issues.add(Issue(start, end, "Unexpected @$name (no matching open)"))
                        continue
                    }
                    stack.removeLast()
                }
                name in MID_BLOCK || root in MID_BLOCK -> {
                    if (stack.isEmpty()) {
                        issues.add(Issue(start, end, "Unexpected @$name (no matching open)"))
                    }
                }
                isBlockOpener(name, root, text, end) -> {
                    if (root in INLINEABLE && isInlineDirective(text, end)) {
                        continue
                    }
                    // Classic `@layout('master')` with a single string — no @end.
                    if (root == "layout" && !name.contains('.') && isSingleStringArg(text, end)) {
                        continue
                    }
                    stack.addLast(Frame(name, start, end))
                }
            }
        }
        for (frame in stack) {
            issues.add(
                Issue(
                    frame.start,
                    frame.end,
                    "Unclosed @${frame.name} (expected @$CLOSER)",
                ),
            )
        }
        return issues
    }

    private fun isSingleStringArg(text: String, atNameEnd: Int): Boolean {
        var i = atNameEnd
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '(') return false
        i++
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || (text[i] != '\'' && text[i] != '"')) return false
        val q = text[i++]
        while (i < text.length && text[i] != q) {
            if (text[i] == '\\') i++
            i++
        }
        if (i >= text.length) return false
        i++ // closing quote
        while (i < text.length && text[i].isWhitespace()) i++
        return i < text.length && text[i] == ')'
    }

    /**
     * Known openers, dotted tag components with args/known roots, or custom
     * undotted tags that look like calls (`@form(`).
     */
    private fun isBlockOpener(name: String, root: String, text: String, nameEnd: Int): Boolean {
        if (name in OPENERS || root in OPENERS) return true
        var i = nameEnd
        while (i < text.length && text[i].isWhitespace()) i++
        val hasArgs = i < text.length && text[i] == '('
        if (name.contains('.')) {
            return hasArgs || root in EdgeTagRegistry.TAG_COMPONENT_ROOTS
        }
        // `@form(` / `@card(` — file-based tag components.
        return hasArgs && root !in VOID && root !in MID_BLOCK &&
            root != CLOSER && root !in LEGACY_CLOSERS
    }
}
