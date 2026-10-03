package com.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class AdonisRenamePlannerTest {
    @Test
    fun rewritesRouteCallSites() {
        val text = """
            return redirect().route("home")
            if route_is("home"):
                pass
            other = route("dashboard")
        """.trimIndent()
        val occ = AdonisCallSiteSearcher.findInText(text, SymbolKind.ROUTE, "home")
        val plan = AdonisRenamePlanner.planFromOccurrences(
            SymbolKind.ROUTE, "home", "welcome", occ,
        )
        assertTrue(plan.isAllowed)
        assertEquals(2, plan.edits.size)
        val out = AdonisRenamePlanner.applyToText(text, plan.edits)
        assertTrue(out.contains("""route("welcome")"""))
        assertTrue(out.contains("""route_is("welcome")"""))
        assertTrue(out.contains("""route("dashboard")"""))
        assertFalse(out.contains("""route("home")"""))
    }

    @Test
    fun rewritesViewAndComponent() {
        val text = """
            return view("auth.login", {})
            @include('auth.login')
            <x-alert type="error"/>
            @component('alert')
        """.trimIndent()
        val viewPlan = AdonisRenamePlanner.planFromOccurrences(
            SymbolKind.VIEW,
            "auth.login",
            "auth.signin",
            AdonisCallSiteSearcher.findInText(text, SymbolKind.VIEW, "auth.login"),
        )
        assertTrue(viewPlan.isAllowed)
        val afterView = AdonisRenamePlanner.applyToText(text, viewPlan.edits)
        assertTrue(afterView.contains("""view("auth.signin""""))
        assertTrue(afterView.contains("""@include('auth.signin')"""))

        val compPlan = AdonisRenamePlanner.planFromOccurrences(
            SymbolKind.COMPONENT,
            "alert",
            "notice",
            AdonisCallSiteSearcher.findInText(afterView, SymbolKind.COMPONENT, "alert"),
        )
        assertTrue(compPlan.isAllowed)
        val afterComp = AdonisRenamePlanner.applyToText(afterView, compPlan.edits)
        assertTrue(afterComp.contains("<x-notice"))
        assertTrue(afterComp.contains("@component('notice')"))
    }

    @Test
    fun rewritesConfigAndEnv() {
        val py = """
            env = config("app.env")
            key = env("APP_KEY")
        """.trimIndent()
        val cfg = AdonisRenamePlanner.planFromOccurrences(
            SymbolKind.CONFIG,
            "app.env",
            "app.environment",
            AdonisCallSiteSearcher.findInText(py, SymbolKind.CONFIG, "app.env"),
        )
        assertTrue(cfg.isAllowed)
        assertTrue(
            AdonisRenamePlanner.applyToText(py, cfg.edits)
                .contains("""config("app.environment")"""),
        )

        val dotenv = "APP_KEY=secret\nTITLE=\${APP_KEY}\n"
        val envPlan = AdonisRenamePlanner.planFromOccurrences(
            SymbolKind.ENV,
            "APP_KEY",
            "APP_SECRET",
            AdonisCallSiteSearcher.findInText(dotenv, SymbolKind.ENV, "APP_KEY", dotenvFile = true),
        )
        assertTrue(envPlan.isAllowed)
        val out = AdonisRenamePlanner.applyToText(dotenv, envPlan.edits)
        assertTrue(out.contains("APP_SECRET=secret"))
        assertTrue(out.contains("\${APP_SECRET}"))
    }

    @Test
    fun validatesNames() {
        assertNull(AdonisRenamePlanner.validateNewName(SymbolKind.ROUTE, "teams.show"))
        assertEquals(
            "Name cannot be empty",
            AdonisRenamePlanner.validateNewName(SymbolKind.ROUTE, ""),
        )
        assertTrue(
            AdonisRenamePlanner.validateNewName(SymbolKind.ENV, "app_key") != null,
        )
        assertTrue(
            AdonisRenamePlanner.validateNewName(SymbolKind.COLUMN, "id") != null,
        )
    }

    @Test
    fun refusesUnsupportedOrEmpty() {
        val empty = AdonisRenamePlanner.planFromOccurrences(
            SymbolKind.ROUTE, "home", "welcome", emptyList(),
        )
        assertFalse(empty.isAllowed)
        assertEquals("No usages found", empty.refusal)

        val bad = AdonisRenamePlanner.planFromOccurrences(
            SymbolKind.ROUTE,
            "home",
            "bad name",
            listOf(
                AdonisCallSiteSearcher.Occurrence(
                    Path.of("x.py"),
                    com.intellij.openapi.util.TextRange(0, 4),
                    SymbolKind.ROUTE,
                    "home",
                ),
            ),
        )
        assertFalse(bad.isAllowed)
    }

    @Test
    fun rewriteTextConvenience() {
        val text = """return route("home")"""
        val plan = AdonisRenamePlanner.rewriteText(text, SymbolKind.ROUTE, "home", "dash")
        assertTrue(plan.isAllowed)
        assertEquals(1, plan.edits.size)
        assertEquals("""return route("dash")""", plan.edits.single().newText)
    }
}
