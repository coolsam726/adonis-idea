package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdonisToolWindowModelTest {
    private fun index() = AdonisIndex(
        ok = true,
        views = mapOf("welcome" to "/v"),
        routes = mapOf("home" to AdonisIndex.RouteEntry("/", listOf("GET"))),
        configKeys = setOf("app.env"),
        components = mapOf("alert" to "/c"),
        envKeys = mapOf("APP_KEY" to AdonisIndex.EnvEntry()),
        tables = mapOf(
            "users" to AdonisIndex.TableEntry(
                columns = mapOf("id" to AdonisIndex.Located()),
            ),
        ),
        gates = setOf("update"),
    )

    @Test
    fun summaryAndStatus() {
        val s = AdonisToolWindowModel.summary(index())
        assertTrue(s.ok)
        assertEquals(1, s.views)
        assertEquals(1, s.routes)
        assertTrue(AdonisToolWindowModel.statusLine(s).startsWith("OK —"))
        assertTrue(
            AdonisToolWindowModel.statusLine(AdonisToolWindowModel.Summary(false, "boom", 0, 0, 0, 0, 0, 0, 0, 0))
                .contains("boom"),
        )
        assertEquals(
            "Index not ready",
            AdonisToolWindowModel.statusLine(
                AdonisToolWindowModel.Summary(false, null, 0, 0, 0, 0, 0, 0, 0, 0),
            ),
        )
    }

    @Test
    fun filtersSymbols() {
        val rows = AdonisToolWindowModel.symbolRows(index(), "ho")
        assertTrue(rows.any { it.name == "home" })
        assertTrue(rows.none { it.name == "welcome" })
        val all = AdonisToolWindowModel.symbolRows(index())
        assertTrue(all.size >= 6)
        assertTrue(all.first().kind <= all.last().kind)
    }
}
