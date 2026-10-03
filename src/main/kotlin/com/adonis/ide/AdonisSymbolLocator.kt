package com.adonis.ide

import com.intellij.openapi.util.TextRange

/**
 * Locate an Adonis symbol under the caret — quoted call-site strings or
 * bare identifiers inside `{{ … }}` / `{!! … !!}`.
 */
object AdonisSymbolLocator {
    data class Hit(
        val kind: SymbolKind,
        val name: String,
        /** Absolute range of the navigable token in the file. */
        val range: TextRange,
        val receiver: String? = null,
    )

    fun hitAt(text: String, offset: Int): Hit? {
        if (offset < 0 || offset > text.length) return null
        stringLiteralAt(text, offset)?.let { lit ->
            // CallSiteDetector expects text through the opening quote + content.
            val probe = text.substring(0, lit.contentStart) + lit.value
            val site = CallSiteDetector.detect(probe) ?: return@let
            // Full literal name for navigation (not the typed prefix).
            val name = when (site.kind) {
                SymbolKind.VALIDATION -> lit.value.substringBefore(":").substringBefore("|")
                else -> lit.value
            }
            if (name.isBlank()) return@let
            return Hit(site.kind, name, TextRange(lit.contentStart, lit.contentEnd), site.receiver)
        }
        templateVarAt(text, offset)?.let { tv ->
            return Hit(SymbolKind.TEMPLATE_VAR, tv.name, TextRange(tv.start, tv.end))
        }
        return null
    }

    private data class Literal(val contentStart: Int, val contentEnd: Int, val value: String)

    private data class TemplateVar(val start: Int, val end: Int, val name: String)

    /** Quoted string whose content range contains [offset]. */
    private fun stringLiteralAt(text: String, offset: Int): Literal? {
        if (offset <= 0 || offset > text.length) return null
        var i = offset - 1
        while (i >= 0 && text[i] != '\n') {
            val c = text[i]
            if (c == '"' || c == '\'') {
                val quote = c
                val open = i
                var j = i + 1
                while (j < text.length && text[j] != quote && text[j] != '\n') j++
                if (j < text.length && text[j] == quote && offset in (open + 1)..j) {
                    return Literal(open + 1, j, text.substring(open + 1, j))
                }
                return null
            }
            i--
        }
        return null
    }

    /**
     * Identifier inside `{{ name }}` / `{!! name !!}` (and optional `.attr` chain —
     * only the root segment is navigable).
     */
    private fun templateVarAt(text: String, offset: Int): TemplateVar? {
        // Find the echo open before caret.
        val before = text.substring(0, offset.coerceAtMost(text.length))
        val openEcho = before.lastIndexOf("{{").let { a ->
            val b = before.lastIndexOf("{!!")
            maxOf(a, b)
        }
        if (openEcho < 0) return null
        val afterOpen = before.substring(openEcho)
        if (!afterOpen.startsWith("{{") && !afterOpen.startsWith("{!!")) return null
        // Must still be inside the echo (no closing before offset).
        val closeFrom = openEcho + 2
        val close = text.indexOf("}}", closeFrom).let { c ->
            if (c < 0) Int.MAX_VALUE else c
        }
        if (offset > close) return null

        // Identifier under caret: [A-Za-z_][\w]* possibly starting a dotted chain.
        var start = offset
        while (start > openEcho + 2 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) {
            start--
        }
        var end = offset
        while (end < text.length && end < close && (text[end].isLetterOrDigit() || text[end] == '_')) {
            end++
        }
        if (start >= end) return null
        // Skip if caret is on a dotted attribute (foo.bar → only resolve when on foo),
        // but still allow navigation from the root when offset is on root or we take root.
        val tokenStart = run {
            var s = start
            // If we're on `.attr`, walk back to root
            if (s > openEcho + 2 && text.getOrNull(s - 1) == '.') {
                s--
                while (s > openEcho + 2 && (text[s - 1].isLetterOrDigit() || text[s - 1] == '_')) s--
            }
            // Expand left fully for root
            while (s > openEcho + 2 && (text[s - 1].isLetterOrDigit() || text[s - 1] == '_')) s--
            s
        }
        var tokenEnd = tokenStart
        while (tokenEnd < text.length && tokenEnd < close &&
            (text[tokenEnd].isLetterOrDigit() || text[tokenEnd] == '_')
        ) {
            tokenEnd++
        }
        if (tokenStart >= tokenEnd) return null
        // Only treat as template var when it looks like an expression start (not after |)
        val between = text.substring(openEcho, tokenStart)
        if (between.contains('|')) return null
        val name = text.substring(tokenStart, tokenEnd)
        if (name.isEmpty() || name[0].isDigit()) return null
        // Confirm CallSiteDetector agrees when probing at end of name
        val probe = text.substring(0, tokenEnd)
        val site = CallSiteDetector.detect(probe)
        if (site?.kind != SymbolKind.TEMPLATE_VAR && site?.kind != null) {
            // Something else won — still OK if we're clearly in echo
        }
        return TemplateVar(tokenStart, tokenEnd, name)
    }
}
