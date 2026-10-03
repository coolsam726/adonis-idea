package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdonisMakeCatalogTest {
    @Test
    fun coversCoreMakeCommands() {
        val ids = AdonisMakeCatalog.ALL.map { it.id }.toSet()
        assertTrue(
            ids.containsAll(
                listOf(
                    "controller", "model", "migration", "view", "test",
                    "seeder", "factory", "middleware", "validator", "service",
                ),
            ),
        )
        assertTrue(ids.contains("wire"))
        assertTrue(ids.contains("panel"))
        assertTrue(AdonisMakeCatalog.ALL.size >= 16)
    }

    @Test
    fun formatsAceArgs() {
        val controller = AdonisMakeCatalog.byId("controller")
        assertNotNull(controller)
        assertEquals("make:controller PostController", controller!!.aceArgs("PostController"))
        assertEquals("make:controller", controller.aceArgs("  "))
        assertEquals("New Controller…", AdonisMakeCatalog.menuLabel(controller))
    }

    @Test
    fun modelAceArgsMatchScaffolderFlags() {
        assertEquals(
            "make:model Post",
            AdonisMakeCatalog.modelAceArgs("Post", AdonisMakeCatalog.ModelOptions()),
        )
        assertEquals(
            "make:model Post --migration --factory --controller",
            AdonisMakeCatalog.modelAceArgs("Post", AdonisMakeCatalog.ModelOptions(all = true)),
        )
        assertEquals(
            "make:model Post --migration --factory",
            AdonisMakeCatalog.modelAceArgs(
                "Post",
                AdonisMakeCatalog.ModelOptions(migration = true, factory = true),
            ),
        )
        assertTrue(AdonisMakeCatalog.byId("model")!!.interactive)
    }

    @Test
    fun commandsAreMakePrefixed() {
        assertTrue(AdonisMakeCatalog.ALL.all { it.command.startsWith("make:") })
        assertTrue(AdonisMakeCatalog.ALL.all { it.id.isNotBlank() && it.label.isNotBlank() })
    }
}
