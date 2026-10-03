package com.adonis.ide

/**
 * Edge block-structure checks: unmatched open/close directives.
 *
 * Adonis Edge closes blocks with a generic `@end` (not Blade `@endif`).
 * Supports modern tag components (`@layouts.app({…})`, `@page()`), bang
 * self-closers (`@!component(…)`), and mid-block `@else` / `@elseif`.
 */
object AdonisEdgeStructure {
    data class Issue(
        val startOffset: Int,
        val endOffset: Int,
        val message: String,
    )

    /** Directives that open a block closed by `@end`. */
    val OPENERS: Set<String> = setOf(
        "if", "unless", "each", "component", "slot", "section", "layout",
        "wire", "persist", "page", "pushTo",
    )

    /** Mid-block tags — ignored for stack balance. */
    val MID_BLOCK: Set<String> = setOf("elseif", "else")

    /** Never open a block (self-closing even without `@!`). */
    val VOID: Set<String> = setOf(
        "include", "includeIf", "includeWhen", "includeUnless",
        "svg", "vite", "inject", "eval", "let", "assign",
        "debugger", "newError", "stack",
    )

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

    /** `@layouts.app`, `@!component`, `@page`, `@end`. */
    private val DIRECTIVE = Regex("""@(!?)([A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)""")

    /**
     * True when `@section('name', 'value')` style inline (top-level comma in args).
     * Commas inside `{…}` / `[…]` do not count.
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
                bang || root in VOID -> {
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
                isBlockOpener(name, root) -> {
                    if (root in INLINEABLE && isInlineDirective(text, end)) {
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

    /** Known openers, dotted tag components (`layouts.app`), or slot-like roots. */
    private fun isBlockOpener(name: String, root: String): Boolean =
        name in OPENERS ||
            root in OPENERS ||
            name.contains('.')
}
