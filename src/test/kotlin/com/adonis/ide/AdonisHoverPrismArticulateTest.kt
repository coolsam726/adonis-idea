package com.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdonisHoverDocsTest {
    private fun index() = AdonisIndex(
        ok = true,
        views = mapOf("welcome" to "/v.edge"),
        routes = mapOf("home" to AdonisIndex.RouteEntry("/", listOf("GET"), "/r.ts", 3)),
        configKeys = setOf("app.env"),
        configLocations = mapOf("app.env" to AdonisIndex.Located("/c.ts", 1)),
        envKeys = mapOf("APP_KEY" to AdonisIndex.EnvEntry(path = "/.env", line = 2, detail = "secret")),
        components = mapOf("alert" to "/a.edge"),
        relations = mapOf("User" to listOf("posts")),
        tables = mapOf(
            "users" to AdonisIndex.TableEntry(columns = mapOf("email" to AdonisIndex.Located())),
        ),
        viewHelpers = mapOf("auth" to AdonisIndex.ViewVarEntry()),
    )

    @Test
    fun symbolHovers() {
        val idx = index()
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.ROUTE, "home")!!.contains("GET"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.ROUTE, "nope")!!.contains("Unknown"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.VIEW, "welcome")!!.contains("/v"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.VIEW, "x")!!.contains("Not found"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.CONFIG, "app.env")!!.contains("indexed"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.ENV, "APP_KEY")!!.contains("secret"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.COMPONENT, "alert")!!.contains("/a"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.RELATION, "posts", "User")!!.contains("relation"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.COLUMN, "email", "users")!!.contains("column"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.TEMPLATE_VAR, "auth")!!.contains("template"))
        assertNull(AdonisHoverDocs.forSymbol(idx, SymbolKind.ROUTE, ""))
        assertNotNull(AdonisHoverDocs.directive("if"))
        assertNull(AdonisHoverDocs.directive("not-a-directive"))
    }
}

class AdonisEdgeStructureTest {
    @Test
    fun balancedIf() {
        assertTrue(AdonisEdgeStructure.analyze("@if(true)\nx\n@end").isEmpty())
    }

    @Test
    fun unclosedIf() {
        val issues = AdonisEdgeStructure.analyze("@if(true)\nx")
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("Unclosed"))
        assertTrue(issues[0].message.contains("@end"))
    }

    @Test
    fun unexpectedClose() {
        val issues = AdonisEdgeStructure.analyze("@end")
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("Unexpected"))
    }

    @Test
    fun mismatchedLegacyCloseStillPops() {
        // Legacy `@endif` is accepted as a closer equivalent to `@end`.
        assertTrue(AdonisEdgeStructure.analyze("@if(1)\n@endforeach").isEmpty())
    }

    @Test
    fun inlineSectionNeedsNoEnd() {
        assertTrue(AdonisEdgeStructure.analyze("@section('title', 'The title')").isEmpty())
        assertTrue(AdonisEdgeStructure.analyze("@section(\"title\", \"Hello, world\")").isEmpty())
    }

    @Test
    fun blockSectionStillNeedsEnd() {
        val issues = AdonisEdgeStructure.analyze("@section('title')\nHi")
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("Unclosed @section"))
        assertTrue(
            AdonisEdgeStructure.analyze("@section('title')\nHi\n@end").isEmpty(),
        )
    }

    @Test
    fun wireAndEachPairWithEnd() {
        assertTrue(
            AdonisEdgeStructure.analyze("@wire('counter')\n{{ count }}\n@end").isEmpty(),
        )
        assertTrue(
            AdonisEdgeStructure.analyze("@each(user in users)\n{{ user }}\n@end").isEmpty(),
        )
        assertTrue(
            AdonisEdgeStructure.analyze(
                "@layouts.app({ title: 'Dashboard' })\n@page()\nx\n@end\n@end",
            ).isEmpty(),
        )
        assertTrue(AdonisEdgeStructure.analyze("@!component('alert')").isEmpty())
    }

    @Test
    fun elseifElseDoNotOpen() {
        assertTrue(
            AdonisEdgeStructure.analyze("@if(a)\nx\n@elseif(b)\ny\n@else\nz\n@end").isEmpty(),
        )
    }
}

class AdonisLucidHelpersTest {
    @Test
    fun eagerLoadAndWhere() {
        val index = AdonisIndex(
            ok = true,
            relations = mapOf("User" to listOf("posts", "profile")),
            tables = mapOf(
                "users" to AdonisIndex.TableEntry(
                    columns = mapOf("email" to AdonisIndex.Located(), "name" to AdonisIndex.Located()),
                ),
            ),
        )
        val eager = AdonisLucidHelpers.eagerLoadSnippets(index, "User")
        assertTrue(eager.any { it.template.contains("preload('posts')") })
        assertTrue(eager.any { it.template.contains("withCount('profile')") })
        assertTrue(
            AdonisLucidHelpers.eagerLoadSnippets(index, null).isEmpty().not() ||
                AdonisLucidHelpers.eagerLoadSnippets(index, "Missing").isEmpty(),
        )

        val where = AdonisLucidHelpers.whereColumnSnippets(index, "users")
        assertTrue(where.any { it.template.contains("where('email'") })
        assertTrue(
            AdonisLucidHelpers.relationMethodStub("posts", "Post")
                .contains("@hasMany(() => Post)"),
        )
    }

    @Test
    fun emptyWhenNoRelations() {
        assertTrue(AdonisLucidHelpers.eagerLoadSnippets(AdonisIndex(ok = true), "User").isEmpty())
    }
}

class AdonisHoverEdgeTest {
    @Test
    fun hoverUnknownAndDirectiveBranches() {
        val idx = AdonisIndex(ok = true, gates = setOf("update"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.RELATION, "nope")!!.contains("Unknown"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.COLUMN, "nope")!!.contains("Unknown"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.TEMPLATE_VAR, "nope")!!.contains("Unknown"))
        assertNotNull(AdonisHoverDocs.forSymbol(idx, SymbolKind.DIRECTIVE, "if"))
        assertTrue(AdonisHoverDocs.forSymbol(idx, SymbolKind.GATE, "update")!!.contains("gate"))
        assertNull(AdonisHoverDocs.forSymbol(idx, SymbolKind.GATE, "missing"))
        assertEquals(
            AdonisLucidHelpers.relationMethodStub("posts"),
            AdonisLucidHelpers.relationMethodStub("posts", "Related"),
        )
    }
}
