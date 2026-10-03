package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AdonisCodeActionPlannerTest {
    private fun index(base: String = "/tmp/app", views: Map<String, String> = emptyMap()) =
        AdonisIndex(basePath = base, ok = true, views = views, components = emptyMap())

    @Test
    fun createViewAndAceForUnknownView() {
        val actions = AdonisCodeActionPlanner.forUnknownSymbol(
            index(),
            SymbolKind.VIEW,
            "auth.login",
        )
        assertEquals(2, actions.size)
        assertTrue(actions.any { it.id == "create-view-file" && it.createPath!!.endsWith("auth/login.edge") })
        assertTrue(actions.any { it.aceArgs == "make:view auth.login" })
    }

    @Test
    fun noActionsWhenKnown() {
        val idx = index(views = mapOf("welcome" to "/tmp/welcome.edge"))
        assertTrue(AdonisCodeActionPlanner.forUnknownSymbol(idx, SymbolKind.VIEW, "welcome").isEmpty())
        assertTrue(AdonisCodeActionPlanner.forUnknownSymbol(idx, SymbolKind.VIEW, "").isEmpty())
        assertTrue(AdonisCodeActionPlanner.forUnknownSymbol(idx, SymbolKind.ROUTE, "home").isEmpty())
        assertTrue(AdonisCodeActionPlanner.forUnknownSymbol(idx, SymbolKind.CONFIG, "app.env").isEmpty())
    }

    @Test
    fun componentAndMiddlewareActions() {
        val actions = AdonisCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.COMPONENT, "alert")
        assertTrue(actions.any { it.createPath?.contains("components/alert.edge") == true })
        assertTrue(actions.any { it.aceArgs?.startsWith("make:view components/") == true })

        val mw = AdonisCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.MIDDLEWARE, "throttle")
        assertEquals(listOf("make:middleware throttle"), mw.map { it.aceArgs })

        assertTrue(
            AdonisCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.CONTROLLER_ACTION, "Foo@index")
                .any { it.aceArgs!!.startsWith("make:controller") },
        )
        assertTrue(
            AdonisCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.MAILER, "Welcome")
                .any { it.aceArgs == "make:mail Welcome" },
        )
        assertTrue(AdonisCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.INERTIA, "Dash").isEmpty())
        assertTrue(AdonisCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.ENV, "X").isEmpty())
        assertTrue(AdonisCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.GATE, "edit").isEmpty())
    }

    @Test
    fun stubWriterCreatesOnce() {
        val dir = Files.createTempDirectory("adonis-stub")
        val path = dir.resolve("resources/views/x.edge")
        val action = AdonisCodeActionPlanner.Action(
            id = "create-view-file",
            title = "Create",
            kind = SymbolKind.VIEW,
            name = "x",
            createPath = path.toString(),
        )
        assertTrue(AdonisStubFileWriter.applyCreateAction(action))
        assertTrue(Files.exists(path))
        assertTrue(Files.readString(path).contains("x"))
        assertFalse(AdonisStubFileWriter.applyCreateAction(action))
        assertFalse(AdonisStubFileWriter.applyCreateAction(action.copy(createPath = null)))
    }

    @Test
    fun pathHelpers() {
        val p = AdonisCodeActionPlanner.viewPathForName("/app", "teams.show")
        assertTrue(p.toString().endsWith("resources/views/teams/show.edge"))
        val c = AdonisCodeActionPlanner.componentPathForName("", "nav.bar")
        assertTrue(c.toString().replace('\\', '/').endsWith("resources/views/components/nav/bar.edge"))
    }
}
