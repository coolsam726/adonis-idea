package com.adonis.ide

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lexer.LexerBase
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.tree.TokenSet

/**
 * Edge is a template language over HTML.
 *
 * Two things must be true for the editor to look right, and they are separate:
 *
 * 1. **PSI** — [EdgeFileViewProvider] gives the file an HTML root so HTML
 *    completion / inspections work ([EDGE_TEMPLATE_DATA] strips Edge syntax).
 * 2. **Colors** — [EdgeEditorHighlighterProvider] layers the HTML highlighter
 *    over [EdgeTokens.TEMPLATE_DATA] spans.
 */
object EdgeLanguage : Language("Edge"), TemplateLanguage {
    private fun readResolve(): Any = EdgeLanguage
}

object EdgeTokens {
    /** HTML host text; highlighted and parsed as HTML. */
    val TEMPLATE_DATA = IElementType("EDGE_TEMPLATE_DATA", EdgeLanguage)

    /** Holes punched into the HTML tree where Edge syntax was removed. */
    val OUTER = OuterLanguageElementType("EDGE_OUTER", EdgeLanguage)

    val COMMENT = IElementType("EDGE_COMMENT", EdgeLanguage)
    val ECHO = IElementType("EDGE_ECHO", EdgeLanguage)
    val RAW_ECHO = IElementType("EDGE_RAW_ECHO", EdgeLanguage)
    val DIRECTIVE = IElementType("EDGE_DIRECTIVE", EdgeLanguage)

    val EDGE_SYNTAX = TokenSet.create(COMMENT, ECHO, RAW_ECHO, DIRECTIVE)
    val ALL = TokenSet.create(TEMPLATE_DATA, OUTER, COMMENT, ECHO, RAW_ECHO, DIRECTIVE)
}

/** Feeds the HTML parser the buffer with Edge constructs replaced by outer holes. */
val EDGE_TEMPLATE_DATA = TemplateDataElementType(
    "EDGE_HTML",
    HTMLLanguage.INSTANCE,
    EdgeTokens.TEMPLATE_DATA,
    EdgeTokens.OUTER,
)

/**
 * Splits a template into Edge constructs and HTML host chunks.
 *
 * Every [advance] consumes at least one character, so the lexer can never
 * stall the editor.
 *
 * Echo forms (checked in this order):
 * - `{{-- … --}}` comments
 * - `{{{ … }}}` raw / unescaped echo
 * - `{!! … !!}` legacy raw echo (kept for compatibility)
 * - `{{ … }}` escaped echo
 *
 * Directives: `@name` or `@!name` (e.g. `@!component('alert')`).
 */
class EdgeLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.tokenType = null
        if (startOffset < endOffset) advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= endOffset) {
            tokenType = null
            return
        }

        val text = buffer
        val i = tokenStart

        if (match(text, i, "{{--")) {
            val close = indexOf(text, i + 4, "--}}")
            tokenEnd = if (close >= 0) close + 4 else endOffset
            tokenType = EdgeTokens.COMMENT
            return
        }
        // Must run before `{{` — `{{{` is a prefix of `{{`.
        if (match(text, i, "{{{")) {
            val close = indexOf(text, i + 3, "}}}")
            tokenEnd = if (close >= 0) close + 3 else endOffset
            tokenType = EdgeTokens.RAW_ECHO
            return
        }
        if (match(text, i, "{!!")) {
            val close = indexOf(text, i + 3, "!!}")
            tokenEnd = if (close >= 0) close + 3 else endOffset
            tokenType = EdgeTokens.RAW_ECHO
            return
        }
        if (match(text, i, "{{")) {
            val close = indexOf(text, i + 2, "}}")
            tokenEnd = if (close >= 0) close + 2 else endOffset
            tokenType = EdgeTokens.ECHO
            return
        }
        val directiveEnd = directiveEndAt(text, i)
        if (directiveEnd > 0) {
            tokenEnd = directiveEnd
            tokenType = EdgeTokens.DIRECTIVE
            return
        }

        var j = i + 1
        // `@@if` is an escaped literal: step over both so the second `@` cannot open one.
        if (text[i] == '@' && j < endOffset && text[j] == '@') j++
        while (j < endOffset && !startsEdge(text, j)) j++
        tokenEnd = j
        tokenType = EdgeTokens.TEMPLATE_DATA
    }

    private fun startsEdge(text: CharSequence, offset: Int): Boolean =
        match(text, offset, "{{") ||
            match(text, offset, "{!!") ||
            match(text, offset, "{{{") ||
            directiveEndAt(text, offset) > 0

    /** End offset of the directive at [offset], or -1 when there is none. */
    private fun directiveEndAt(text: CharSequence, offset: Int): Int {
        if (text[offset] != '@') return -1
        if (offset > 0 && text[offset - 1] == '@') return -1
        var j = offset + 1
        // `@!component` — optional bang after `@`.
        if (j < endOffset && text[j] == '!') j++
        val nameStart = j
        while (j < endOffset && isIdentPart(text[j])) j++
        if (j == nameStart) return -1
        // Only known directives, so `hi@example.com` stays HTML text.
        if (text.subSequence(nameStart, j).toString() !in EdgeDirectives.NAMES) return -1
        // Directives carry their argument list so `@if(a > b)` cannot break HTML.
        if (j < endOffset && text[j] == '(') {
            val close = matchingParen(text, j)
            j = if (close >= 0) close + 1 else endOffset
        }
        return j
    }

    private fun matchingParen(text: CharSequence, open: Int): Int {
        var depth = 0
        var quote: Char? = null
        var i = open
        while (i < endOffset) {
            val c = text[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun match(text: CharSequence, offset: Int, literal: String): Boolean {
        if (offset + literal.length > endOffset) return false
        for (k in literal.indices) {
            if (text[offset + k] != literal[k]) return false
        }
        return true
    }

    private fun indexOf(text: CharSequence, from: Int, literal: String): Int {
        val last = endOffset - literal.length
        var i = from
        while (i <= last) {
            if (match(text, i, literal)) return i
            i++
        }
        return -1
    }

    private fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()
}
