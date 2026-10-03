package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class EdgeEnvPolishTest {
    @Test
    fun layoutsAppAndPageDoNotFlagUnexpectedEnd() {
        val text =
            """
            @layouts.app({ title: 'Dashboard' })
              @page()
                <div>ready</div>
              @end
            @end
            """.trimIndent()
        assertTrue(AdonisEdgeStructure.analyze(text).isEmpty())
    }

    @Test
    fun bangComponentIsSelfClosing() {
        assertTrue(
            AdonisEdgeStructure.analyze("@!component('alert')\n<p>x</p>").isEmpty(),
        )
    }

    @Test
    fun pushToIsABlockOpener() {
        assertTrue(
            AdonisEdgeStructure.analyze("@pushTo('scripts')\njs\n@end").isEmpty(),
        )
    }

    @Test
    fun objectArgsDoNotMakeLayoutsInline() {
        // Comma inside `{ title: 'A', subtitle: 'B' }` must not skip the opener.
        val text = "@layouts.app({ title: 'A', subtitle: 'B' })\n@end"
        assertTrue(AdonisEdgeStructure.analyze(text).isEmpty())
    }

    @Test
    fun lexerRecognizesDottedTagComponents() {
        val text = "@layouts.app({ title: 'Dashboard' })\n@page()\n@end\n@end"
        val types = tokenize(text).map { it.first }
        assertTrue(types.contains(EdgeTokens.DIRECTIVE))
        assertEquals(
            listOf(
                EdgeTokens.DIRECTIVE,
                EdgeTokens.TEMPLATE_DATA,
                EdgeTokens.DIRECTIVE,
                EdgeTokens.TEMPLATE_DATA,
                EdgeTokens.DIRECTIVE,
                EdgeTokens.TEMPLATE_DATA,
                EdgeTokens.DIRECTIVE,
            ),
            types,
        )
    }

    @Test
    fun lexerStillIgnoresEmails() {
        assertEquals(
            listOf(EdgeTokens.TEMPLATE_DATA to "hi@example.com"),
            tokenize("hi@example.com"),
        )
    }

    @Test
    fun eachIsOfferedAsLoopDirective() {
        val index = AdonisIndex(ok = true, directives = setOf("if", "end"))
        val items = AdonisCompletionCatalog.directiveCompletions(index)
        assertTrue(items.any { it.first == "each" && it.second == "loop" })
        assertTrue(items.any { it.first == "loop" })
        assertTrue(items.any { it.first == "for" })
        assertTrue(items.any { it.first == "page" })
    }

    @Test
    fun envResolverPrefersDotenvThenUsedBy() {
        val index = AdonisIndex(
            ok = true,
            basePath = "/tmp/app",
            envKeys = mapOf(
                "DB_HOST" to AdonisIndex.EnvEntry(
                    path = "/tmp/app/.env",
                    line = 3,
                    kind = "env",
                    detail = ".env",
                    usedBy = listOf("config/database.ts:12"),
                ),
                "DB_PORT" to AdonisIndex.EnvEntry(
                    path = null,
                    line = 0,
                    kind = "schema",
                    detail = "start/env",
                    usedBy = listOf("config/database.ts:14"),
                ),
            ),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/.env", 3),
            AdonisSymbolResolver.resolve(index, SymbolKind.ENV, "DB_HOST"),
        )
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.ENV, "DB_PORT"))
    }

    @Test
    fun indexerCollectsSchemaAndUsages() {
        val root = Files.createTempDirectory("adonis-env-idx")
        try {
            Files.writeString(root.resolve("adonisrc.ts"), "export default {}")
            Files.writeString(
                root.resolve(".env"),
                "APP_KEY=secret\nDB_HOST=127.0.0.1\n",
            )
            Files.createDirectories(root.resolve("start"))
            Files.writeString(
                root.resolve("start/env.ts"),
                """
                import { Env } from '@adonisjs/core/env'
                export default await Env.create(new URL('../', import.meta.url), {
                  APP_KEY: Env.schema.secret(),
                  DB_HOST: Env.schema.string(),
                  DB_PORT: Env.schema.number(),
                })
                """.trimIndent(),
            )
            Files.createDirectories(root.resolve("config"))
            Files.writeString(
                root.resolve("config/database.ts"),
                "const host = env.get('DB_HOST')\nconst port = env.get('DB_PORT')\n",
            )
            val script = java.nio.file.Path.of("indexer/index.mjs").toAbsolutePath()
            val proc = ProcessBuilder(
                "node",
                script.toString(),
                "--path",
                root.toString(),
                "--json",
            ).redirectErrorStream(true).start()
            val json = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())
            val index = AdonisIndexLoader.parse(json)
            assertTrue(index.envKeys.containsKey("DB_HOST"))
            assertTrue(index.envKeys.containsKey("DB_PORT"))
            assertTrue(index.envKeys["DB_HOST"]!!.usedBy.any { it.contains("config/database.ts") })
            assertTrue(index.envKeys["DB_PORT"]!!.usedBy.isNotEmpty())
            assertEquals(".env", index.envKeys["DB_HOST"]!!.detail)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun atDirectiveDetectsEachAndAliasesPrefix() {
        assertEquals(SymbolKind.DIRECTIVE, CallSiteDetector.detect("@ea")!!.kind)
        assertEquals("ea", CallSiteDetector.detect("@ea")!!.prefix)
        assertEquals("loop", CallSiteDetector.detect("@loop")!!.prefix)
        assertEquals(SymbolKind.DIRECTIVE, CallSiteDetector.detect("@")!!.kind)
    }

    @Test
    fun inlineDirectiveTracksBracesBracketsAndEscapes() {
        // Top-level comma still wins; braces/brackets must be walked.
        assertTrue(
            AdonisEdgeStructure.isInlineDirective(
                "@section('n', { a: 1, b: 2 })",
                "@section".length,
            ),
        )
        assertTrue(
            AdonisEdgeStructure.isInlineDirective(
                "@section('n', [1, 2])",
                "@section".length,
            ),
        )
        assertTrue(
            AdonisEdgeStructure.isInlineDirective(
                """@section('n', "a,\"b")""",
                "@section".length,
            ),
        )
        // Nested object args on a layout tag stay a block opener.
        assertTrue(
            AdonisEdgeStructure.analyze(
                "@layouts.app({ meta: { a: 1, b: 2 } })\n@end",
            ).isEmpty(),
        )
    }

    @Test
    fun lexerHandlesEscapesInsideDirectiveArgs() {
        assertEquals(
            listOf(
                EdgeTokens.DIRECTIVE to """@if("a\"b")""",
                EdgeTokens.TEMPLATE_DATA to "x",
                EdgeTokens.DIRECTIVE to "@end",
            ),
            tokenize("""@if("a\"b")x@end"""),
        )
    }

    private fun tokenize(text: String): List<Pair<com.intellij.psi.tree.IElementType, String>> {
        val lexer = EdgeLexer()
        lexer.start(text, 0, text.length, 0)
        val out = mutableListOf<Pair<com.intellij.psi.tree.IElementType, String>>()
        while (true) {
            val type = lexer.tokenType ?: break
            out += type to text.substring(lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
        }
        return out
    }
}
