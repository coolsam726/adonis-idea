package com.adonis.ide

/**
 * Edge block-structure checks: unmatched open/close directives.
 *
 * Adonis Edge closes blocks with a generic `@end` (not Blade `@endif` / `@endforeach`).
 * Mid-block tags (`@elseif`, `@else`) do not push or pop the stack.
 */
object AdonisEdgeStructure {
    data class Issue(
        val startOffset: Int,
        val endOffset: Int,
        val message: String,
    )

    /** Directives that open a block closed by `@end`. */
    val OPENERS: Set<String> = setOf(
        "if", "unless", "each", "component", "slot", "section", "layout", "wire", "persist",
    )

    /** Mid-block tags — ignored for stack balance. */
    val MID_BLOCK: Set<String> = setOf("elseif", "else")

    /** Generic Edge closer. */
    const val CLOSER: String = "end"

    /**
     * Legacy Blade-style closers some copied templates still use.
     * Mapped to the same stack pop as `@end`.
     */
    private val LEGACY_CLOSERS: Set<String> = setOf(
        "endif", "endunless", "endeach", "endcomponent", "endslot",
        "endsection", "endlayout", "endwire", "endpersist", "endforeach",
        "endfor", "endwhile", "endempty", "endisset", "show",
    )

    /** Openers that may be one-line / self-closing when args include a value. */
    private val INLINEABLE: Set<String> = setOf("section")

    private val DIRECTIVE = Regex("""@!?([A-Za-z_][\w]*)""")

    /**
     * True when `@section('name', 'value')` style inline (top-level comma in args).
     */
    fun isInlineDirective(text: String, atNameEnd: Int): Boolean {
        var i = atNameEnd
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '(') return false
        i++ // past '('
        var depth = 1
        var quote: Char? = null
        var sawTopLevelComma = false
        while (i < text.length && depth > 0) {
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
                ch == '(' -> {
                    depth++
                    i++
                }
                ch == ')' -> {
                    depth--
                    i++
                }
                else -> {
                    if (ch == ',' && depth == 1) sawTopLevelComma = true
                    i++
                }
            }
        }
        return sawTopLevelComma && depth == 0
    }

    fun analyze(text: String): List<Issue> {
        data class Frame(val name: String, val start: Int, val end: Int)
        val stack = ArrayDeque<Frame>()
        val issues = mutableListOf<Issue>()
        for (m in DIRECTIVE.findAll(text)) {
            val name = m.groupValues[1]
            val start = m.range.first
            val end = m.range.last + 1
            when {
                name in OPENERS -> {
                    if (name in INLINEABLE && isInlineDirective(text, end)) {
                        continue
                    }
                    stack.addLast(Frame(name, start, end))
                }
                name == CLOSER || name in LEGACY_CLOSERS -> {
                    if (stack.isEmpty()) {
                        issues.add(Issue(start, end, "Unexpected @$name (no matching open)"))
                        continue
                    }
                    stack.removeLast()
                }
                name in MID_BLOCK -> {
                    // elseif / else — require an open block but do not push/pop.
                    if (stack.isEmpty()) {
                        issues.add(Issue(start, end, "Unexpected @$name (no matching open)"))
                    }
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
}
