package dev.shamar.adonis.ide

import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeLexerTest {
    @Test
    fun `splits html host text from edge constructs`() {
        assertEquals(
            listOf(
                EdgeTokens.TEMPLATE_DATA to "<div class=\"card\">",
                EdgeTokens.ECHO to "{{ name }}",
                EdgeTokens.TEMPLATE_DATA to "</div>",
            ),
            tokens("<div class=\"card\">{{ name }}</div>"),
        )
    }

    @Test
    fun `directive swallows its argument list`() {
        assertEquals(
            listOf(
                EdgeTokens.DIRECTIVE to "@if(count > 1 and label == \")\")",
                EdgeTokens.TEMPLATE_DATA to "x",
                EdgeTokens.DIRECTIVE to "@end",
            ),
            tokens("@if(count > 1 and label == \")\")x@end"),
        )
    }

    @Test
    fun `comments and raw echoes are distinct`() {
        assertEquals(
            listOf(
                EdgeTokens.COMMENT to "{{-- hidden --}}",
                EdgeTokens.RAW_ECHO to "{{{ body }}}",
                EdgeTokens.RAW_ECHO to "{!! legacy !!}",
            ),
            tokens("{{-- hidden --}}{{{ body }}}{!! legacy !!}"),
        )
    }

    @Test
    fun `bang component directive is recognized`() {
        assertEquals(
            listOf(EdgeTokens.DIRECTIVE to "@!component('alert')"),
            tokens("@!component('alert')"),
        )
    }

    @Test
    fun `email addresses and escaped at signs stay html`() {
        assertEquals(
            listOf(EdgeTokens.TEMPLATE_DATA to "hi@example.com @@notADirective"),
            tokens("hi@example.com @@notADirective"),
        )
    }

    @Test
    fun `unterminated constructs consume the rest without stalling`() {
        assertEquals(listOf(EdgeTokens.ECHO to "{{ oops"), tokens("{{ oops"))
        assertEquals(listOf(EdgeTokens.COMMENT to "{{-- oops"), tokens("{{-- oops"))
    }

    private fun tokens(text: String): List<Pair<IElementType, String>> {
        val lexer = EdgeLexer()
        lexer.start(text, 0, text.length, 0)
        assertEquals(text.length, lexer.bufferEnd)
        val out = mutableListOf<Pair<IElementType, String>>()
        while (true) {
            val type = lexer.tokenType ?: break
            out += type to text.substring(lexer.tokenStart, lexer.tokenEnd)
            check(lexer.tokenEnd > lexer.tokenStart) { "lexer did not advance at ${lexer.tokenStart}" }
            lexer.advance()
        }
        return out
    }

    @Test
    fun `unknown at sequences stay host text`() {
        // Hits directive scanner return -1 paths (not a known directive / email-like).
        assertEquals(
            listOf(EdgeTokens.TEMPLATE_DATA to "@notARealDirective"),
            tokens("@notARealDirective"),
        )
    }

    @Test
    fun `dotted layout tags are directives`() {
        assertEquals(
            listOf(EdgeTokens.DIRECTIVE to "@layouts.app({ title: 'Dashboard' })"),
            tokens("@layouts.app({ title: 'Dashboard' })"),
        )
    }

    @Test
    fun `page slot directive is recognized`() {
        assertEquals(
            listOf(EdgeTokens.DIRECTIVE to "@page()"),
            tokens("@page()"),
        )
    }
}
