package dev.shamar.adonis.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import icons.AdonisIcons

/**
 * Verification matrix for Adonis EdgeJS guide fidelity.
 */
class EdgeGuideFidelityTest : BasePlatformTestCase() {

    fun `test edge file icon is adonis mark`() {
        val icon = EdgeFileType.INSTANCE.icon
        assertNotNull(icon)
        assertSame(AdonisIcons.File, icon)
        // SVG may report 1×1 until rasterized in headless tests; path must resolve.
        assertTrue(icon.iconWidth >= 1)
        assertTrue(icon.iconHeight >= 1)
    }

    fun `test form and bang button are directives`() {
        assertEquals(
            listOf(EdgeTokens.DIRECTIVE),
            tokenize("@form({ route: 'posts.store' })").map { it.first },
        )
        // Bare `@form` (while typing, before `(`) must stay an Edge directive — not HTML.
        assertEquals(
            listOf(EdgeTokens.DIRECTIVE),
            tokenize("@form").map { it.first },
        )
        assertEquals(
            listOf(EdgeTokens.DIRECTIVE),
            tokenize("@!button({ text: 'Go' })").map { it.first },
        )
        assertEquals(
            listOf(EdgeTokens.DIRECTIVE),
            tokenize("@field.root({ name: 'email' })").map { it.first },
        )
    }

    fun `test escaped mustache is not echo`() {
        assertEquals(
            listOf(EdgeTokens.TEMPLATE_DATA to "@{{ user }}"),
            tokenize("@{{ user }}"),
        )
    }

    fun `test trailing tilde is part of directive`() {
        assertEquals(
            listOf(EdgeTokens.DIRECTIVE to "@let(x = 1)~"),
            tokenize("@let(x = 1)~"),
        )
    }

    fun `test dump is void`() {
        assertTrue(AdonisEdgeStructure.analyze("@dump(posts)\n<p>x</p>").isEmpty())
        assertTrue(EdgeDirectives.NAMES.contains("dump"))
    }

    fun `test layout component block and classic inline`() {
        assertTrue(
            AdonisEdgeStructure.analyze("@layout()\n  <p>x</p>\n@end").isEmpty(),
        )
        assertTrue(
            AdonisEdgeStructure.analyze("@layout('master')\n<p>x</p>").isEmpty(),
        )
    }

    fun `test form is block opener`() {
        assertTrue(
            AdonisEdgeStructure.analyze(
                "@form({ route: 'posts.store' })\n  fields\n@end",
            ).isEmpty(),
        )
    }

    fun `test custom undotted tag call is block opener`() {
        // `@card(` is not in OPENERS — structure still treats argful tag components as blocks.
        assertTrue(
            AdonisEdgeStructure.analyze("@card({ title: 'Hi' })\n  body\n@end").isEmpty(),
        )
        assertTrue(
            AdonisEdgeStructure.analyze("@card({ title: 'Hi' })\n  body").isNotEmpty(),
        )
    }

    fun `test form route prop is named route site`() {
        val site = CallSiteDetector.detect("@form({ route: 'posts.")!!
        assertEquals(SymbolKind.ROUTE, site.kind)
        assertEquals("posts.", site.prefix)
    }

    fun `test link route prop is named route site`() {
        val site = CallSiteDetector.detect("@!link({ route: 'ho")!!
        assertEquals(SymbolKind.ROUTE, site.kind)
        assertEquals("ho", site.prefix)
    }

    fun `test form method prop offers http verbs`() {
        val site = CallSiteDetector.detect("@form({ method: 'PO")!!
        assertEquals(SymbolKind.EDGE_LITERAL, site.kind)
        assertEquals("method", site.receiver)
        val items = AdonisCompletionCatalog.symbolsFor(AdonisIndex(ok = true), site)
        assertTrue(items.any { it.first == "POST" })
    }

    fun `test form prop keys`() {
        val site = CallSiteDetector.detect("@form({ ")!!
        assertEquals(SymbolKind.EDGE_PROP_KEY, site.kind)
        assertEquals("form", site.receiver)
        val keys = AdonisCompletionCatalog.symbolsFor(AdonisIndex(ok = true), site).map { it.first }
        assertTrue(keys.contains("route"))
        assertTrue(keys.contains("method"))
    }

    fun `test includeIf second arg is view`() {
        val site = CallSiteDetector.detect("@includeIf(ok, 'partials/")!!
        assertEquals(SymbolKind.VIEW, site.kind)
        assertEquals("partials/", site.prefix)
    }

    fun `test router on render is view not inertia`() {
        val site = CallSiteDetector.detect("""router.on('/').render('pages/ho""")!!
        assertEquals(SymbolKind.VIEW, site.kind)
        assertEquals("pages/ho", site.prefix)
    }

    fun `test bare render stays inertia`() {
        val site = CallSiteDetector.detect("""render('Dash""")!!
        assertEquals(SymbolKind.INERTIA, site.kind)
    }

    fun `test directive completions include dump and tag components`() {
        // Empty index: starter-kit tags / snippets must still appear (no HTML steal).
        val cold = AdonisCompletionCatalog.directiveCompletions(AdonisIndex.empty())
        assertTrue(cold.any { it.first == "form" })
        assertTrue(cold.any { it.first == "!button" })
        assertTrue(cold.any { it.first == "dump" })
        // Wire is detection-gated — absent without framework.wire / wire components.
        assertFalse(cold.any { it.first == "wire" })
        assertFalse(cold.any { it.first == "persist" })

        val index = AdonisIndex(
            ok = true,
            components = mapOf("form" to "/tmp/form.edge", "field.root" to "/tmp/fr.edge"),
        )
        val items = AdonisCompletionCatalog.directiveCompletions(index)
        assertTrue(items.any { it.first == "dump" })
        assertTrue(items.any { it.first == "form" })
        assertTrue(items.any { it.first == "field.root" })
        assertTrue(items.any { it.first == "includeIf" })
        assertFalse(items.any { it.first == "endif" })
        assertFalse(items.any { it.first == "wire" })
    }

    fun `test wire directives gated on detection`() {
        val off = AdonisCompletionCatalog.directiveCompletions(
            AdonisIndex(ok = true, framework = AdonisIndex.FrameworkEntry(wire = false)),
        )
        assertFalse(off.any { it.first == "wire" || it.first == "persist" })

        val onFlag = AdonisCompletionCatalog.directiveCompletions(
            AdonisIndex(ok = true, framework = AdonisIndex.FrameworkEntry(wire = true)),
        )
        assertTrue(onFlag.any { it.first == "wire" })
        assertTrue(onFlag.any { it.first == "persist" })

        val onComponents = AdonisCompletionCatalog.directiveCompletions(
            AdonisIndex(
                ok = true,
                wireComponents = mapOf("counter" to AdonisIndex.WireEntry(path = "/tmp/c.ts")),
            ),
        )
        assertTrue(onComponents.any { it.first == "wire" })
        assertTrue(onComponents.any { it.first == "persist" })
        assertTrue(AdonisIndex(framework = AdonisIndex.FrameworkEntry(wire = true)).wireEnabled)
        assertFalse(AdonisIndex.empty().wireEnabled)
    }

    fun `test dark theme icons are well formed svg`() {
        val loader = AdonisIcons::class.java.classLoader
        for (path in listOf(
            "icons/adonis.svg",
            "icons/adonis_dark.svg",
            "icons/edge.svg",
            "icons/edge_dark.svg",
        )) {
            val stream = loader.getResourceAsStream(path)
            assertNotNull("missing $path", stream)
            val bytes = stream!!.readBytes()
            assertTrue(
                "$path has control bytes",
                bytes.none { b ->
                    val u = b.toInt() and 0xFF
                    u < 32 && u !in setOf(9, 10, 13)
                },
            )
            assertTrue("$path empty", bytes.isNotEmpty())
            javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(java.io.ByteArrayInputStream(bytes))
        }
        assertSame(AdonisIcons.File, AdonisIcons.ToolWindow)
    }

    fun `test raw echo has distinct color key`() {
        assertNotSame(EdgeColors.ECHO, EdgeColors.RAW_ECHO)
        val keys = EdgeSyntaxHighlighter.getTokenHighlights(EdgeTokens.RAW_ECHO)
        assertTrue(keys.contains(EdgeColors.RAW_ECHO))
    }

    fun `test template var detects dollar slots`() {
        val site = CallSiteDetector.detect("{{ \$slots")!!
        assertEquals(SymbolKind.TEMPLATE_VAR, site.kind)
        assertEquals("\$slots", site.prefix)
    }

    fun `test snippets cover guide tags`() {
        assertNotNull(EdgeDirectiveSnippets.preview("dump"))
        assertNotNull(EdgeDirectiveSnippets.preview("form"))
        assertNotNull(EdgeDirectiveSnippets.preview("includeIf"))
        assertEquals("layout() … @end", EdgeDirectiveSnippets.preview("layout"))
    }

    fun `test tagComponentNames from layouts and partials`() {
        val index = AdonisIndex(
            ok = true,
            views = mapOf(
                "layouts/main" to "/tmp/layouts/main.edge",
                "partials/card" to "/tmp/partials/card.edge",
                "components/button/primary" to "/tmp/c.edge",
            ),
            components = mapOf("alert.root" to "/tmp/a.edge"),
        )
        val tags = AdonisCompletionCatalog.tagComponentNames(index)
        assertTrue(tags.contains("layouts.main"))
        assertTrue(tags.contains("partials.card"))
        assertTrue(tags.contains("button.primary") || tags.contains("components.button.primary") || tags.any { it.contains("button") })
        assertTrue(tags.contains("alert.root"))
    }

    fun `test prop key fallbacks and literals`() {
        assertEquals(listOf("name", "id"), EdgeTagRegistry.propKeysForTag("field.root"))
        assertTrue(EdgeTagRegistry.propKeysForTag("unknown.tag").isEmpty())
        assertEquals(SymbolKind.ROUTE, EdgeTagRegistry.propValueKind("signedRoute"))
        assertNull(EdgeTagRegistry.propValueKind("href"))
        assertTrue(EdgeTagRegistry.literalsForProp("variant").contains("destructive"))
        assertTrue(EdgeTagRegistry.literalsForProp("nope").isEmpty())
    }

    fun `test escaped raw mustache forms`() {
        assertEquals(
            listOf(EdgeTokens.TEMPLATE_DATA),
            tokenize("@{{{ html }}}").map { it.first },
        )
        assertEquals(
            listOf(EdgeTokens.TEMPLATE_DATA),
            tokenize("@{!! html !!}").map { it.first },
        )
    }

    fun `test dollar slots completion site`() {
        val site = CallSiteDetector.detect("{{{ \$slots")!!
        assertEquals(SymbolKind.TEMPLATE_VAR, site.kind)
        assertTrue(site.prefix.contains("slots") || site.prefix.startsWith("\$"))
    }

    fun `test alert variant prop`() {
        val site = CallSiteDetector.detect("@alert.root({ variant: 'des")!!
        assertEquals(SymbolKind.EDGE_LITERAL, site.kind)
        assertEquals("variant", site.receiver)
    }

    fun `test prop key suffix match and escaped mustache in host`() {
        assertEquals(listOf("text"), EdgeTagRegistry.propKeysForTag("field.label"))
        assertTrue(EdgeTagRegistry.propKeysForTag("x").isEmpty())
        val mixed = tokenize("hi @{{ x }}")
        assertTrue(mixed.any { it.first == EdgeTokens.TEMPLATE_DATA && it.second.contains("@{{") })
    }

    fun `test includeWhen and includeUnless lex`() {
        assertTrue(tokenize("@includeWhen(a, 'p')").any { it.first == EdgeTokens.DIRECTIVE })
        assertTrue(tokenize("@includeUnless(a, 'p')").any { it.first == EdgeTokens.DIRECTIVE })
    }

    fun `test dotted render without on is not forced to view`() {
        // `.render('…'` with empty head before `.render` stays Inertia (not router.on).
        val site = CallSiteDetector.detect(".render('pages/x")
        assertTrue(site == null || site.kind == SymbolKind.INERTIA)
    }

    fun `test unclosed escaped mustache`() {
        assertTrue(tokenize("@{{ unterminated").any { it.first == EdgeTokens.TEMPLATE_DATA })
    }

    fun `test locator on dollar slots`() {
        val text = "{{ \$slots.main }}"
        val atDollar = AdonisSymbolLocator.hitAt(text, text.indexOf('$'))
        val atSlots = AdonisSymbolLocator.hitAt(text, text.indexOf('s'))
        assertTrue(atDollar != null || atSlots != null)
    }

    private fun tokenize(text: String): List<Pair<com.intellij.psi.tree.IElementType, String>> {
        val lexer = EdgeLexer()
        lexer.start(text)
        val out = mutableListOf<Pair<com.intellij.psi.tree.IElementType, String>>()
        while (lexer.tokenType != null) {
            out.add(lexer.tokenType!! to text.substring(lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
        return out
    }
}
