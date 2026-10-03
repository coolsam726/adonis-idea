package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdonisOptionalDepthTest {
    @Test
    fun fileTemplatesResolveCommonGenerators() {
        val controller = AdonisFileTemplates.resolve("controller", "PostController")!!
        assertTrue(controller.relativePath.contains("controllers/"))
        assertTrue(controller.contents.contains("class PostController"))

        val model = AdonisFileTemplates.resolve("model", "Author")!!
        assertEquals("app/models/author.ts", model.relativePath.replace('\\', '/'))
        assertTrue(model.contents.contains("class Author"))
        assertTrue(model.contents.contains("BaseModel"))

        val view = AdonisFileTemplates.resolve("view", "posts.index")!!
        assertTrue(view.relativePath.contains("posts/index.edge"))

        val command = AdonisFileTemplates.resolve("command", "SendDigest")!!
        assertTrue(command.relativePath.contains("commands/"))
        assertTrue(command.contents.contains("commandName"))

        val middleware = AdonisFileTemplates.resolve("middleware", "EnsureToken")!!
        assertTrue(middleware.contents.contains("EnsureTokenMiddleware"))

        assertNotNull(AdonisFileTemplates.resolve("wire", "counter"))
        assertNotNull(AdonisFileTemplates.resolve("panel", "admin"))
        assertNullish(AdonisFileTemplates.resolve("job", "ProcessPodcast"))
    }

    private fun assertNullish(v: Any?) {
        assertTrue(v == null)
    }

    @Test
    fun relationStubInsertsDeclare() {
        val src = """
            export default class User {
              @column({ isPrimary: true })
              declare id: number
            }
        """.trimIndent()
        val plan = AdonisRelationStubPlanner.planInsert(src, "posts", "Post")
        assertTrue(plan.isAllowed)
        assertFalse(AdonisRelationStubPlanner.planInsert(src, "", "X").isAllowed)
    }

    @Test
    fun makeCatalogModelFlags() {
        val args = AdonisMakeCatalog.modelAceArgs(
            "user",
            AdonisMakeCatalog.ModelOptions(all = true),
        )
        assertTrue(args.contains("make:model"))
        assertTrue(args.contains("--migration"))
    }

    @Test
    fun codeActionUnknownView() {
        val index = AdonisIndex.empty()
        val actions = AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.VIEW, "auth.login")
        assertTrue(actions.isNotEmpty())
    }
}
