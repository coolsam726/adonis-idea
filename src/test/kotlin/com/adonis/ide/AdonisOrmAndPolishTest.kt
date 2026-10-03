package com.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdonisModelResolverTest {
    private fun index(): AdonisIndex {
        val users = AdonisIndex.TableEntry(
            columns = mapOf(
                "id" to AdonisIndex.Located("/m.py", 1),
                "email" to AdonisIndex.Located("/m.py", 2),
                "name" to AdonisIndex.Located("/m.py", 3),
                "password" to AdonisIndex.Located("/m.py", 4),
            ),
            model = "User",
            detail = "users",
        )
        return AdonisIndex(
            ok = true,
            tables = mapOf("users" to users),
            modelMetadata = mapOf(
                "User" to AdonisIndex.ModelEntry(
                    fillable = listOf("email", "name"),
                    guarded = listOf("password"),
                    hidden = listOf("remember_token"),
                    casts = mapOf("email_verified_at" to "datetime"),
                    module = "user",
                    path = "/user.py",
                ),
            ),
            relations = mapOf("User" to listOf("posts")),
            casts = setOf("datetime", "int", "bool"),
        )
    }

    @Test
    fun resolvesUserTableAndColumns() {
        val idx = index()
        assertEquals("users", AdonisModelResolver.resolveTable(idx, "User"))
        assertEquals("users", AdonisModelResolver.resolveTable(idx, "users"))
        assertTrue(AdonisModelResolver.columnsFor(idx, "User").containsAll(listOf("email", "name", "id")))
        assertEquals("User", AdonisModelResolver.authUserModel(idx))
        assertEquals("users", AdonisModelResolver.resolveTable(idx, AdonisModelResolver.AUTH_USER_SENTINEL))
    }

    @Test
    fun infersFromAssignmentAndAuthHeuristic() {
        val idx = index()
        assertEquals(
            "User",
            AdonisModelResolver.inferModel(idx, "author = User.find(1)\n", "author"),
        )
        assertEquals(
            "User",
            AdonisModelResolver.inferModel(idx, "def show(user: User):\n    ", "user"),
        )
        assertEquals(
            "User",
            AdonisModelResolver.inferModel(idx, "x = 1\n", "user"),
        )
        assertEquals(
            "User",
            AdonisModelResolver.inferModel(idx, "", AdonisModelResolver.AUTH_USER_SENTINEL),
        )
    }

    @Test
    fun pluralize() {
        assertEquals("users", AdonisModelResolver.pluralize("user"))
        assertEquals("companies", AdonisModelResolver.pluralize("company"))
        assertEquals("boxes", AdonisModelResolver.pluralize("box"))
    }
}

class AdonisOrmCompletionTest {
    private fun index() = AdonisIndex(
        ok = true,
        tables = mapOf(
            "users" to AdonisIndex.TableEntry(
                columns = mapOf(
                    "email" to AdonisIndex.Located(),
                    "name" to AdonisIndex.Located(),
                    "password" to AdonisIndex.Located(),
                ),
                model = "User",
            ),
        ),
        modelMetadata = mapOf(
            "User" to AdonisIndex.ModelEntry(
                fillable = listOf("email", "name"),
                guarded = listOf("password"),
                module = "user",
                path = "/u.py",
            ),
        ),
        casts = setOf("datetime", "int"),
        relations = mapOf("User" to listOf("posts")),
    )

    @Test
    fun ormWhereAndAuthUserAttrs() {
        val idx = index()
        val where = CallSiteDetector.detect("""User.where("ema""")!!
        assertEquals(SymbolKind.COLUMN, where.kind)
        assertEquals("User", where.receiver)
        val cols = AdonisCompletionCatalog.symbolsFor(idx, where, """User.where("ema""")
        assertTrue(cols.any { it.first == "email" })

        val auth = CallSiteDetector.detect("auth().user().ema")!!
        assertEquals(SymbolKind.ATTR, auth.kind)
        assertEquals(AdonisModelResolver.AUTH_USER_SENTINEL, auth.receiver)
        val attrs = AdonisCompletionCatalog.symbolsFor(idx, auth, "auth().user().ema")
        assertTrue(attrs.any { it.first == "email" })

        val req = CallSiteDetector.detect("request.user().na")!!
        assertEquals(SymbolKind.ATTR, req.kind)
        assertTrue(
            AdonisCompletionCatalog.symbolsFor(idx, req, "request.user().na")
                .any { it.first == "name" },
        )
    }

    @Test
    fun fillableGuardedCastsKeysAndValues() {
        val idx = index()
        val modelSrc = """
            class User(Model):
                fillable = ["ema
        """.trimIndent()
        val fill = CallSiteDetector.detect(modelSrc)!!
        assertEquals(SymbolKind.MODEL_ATTR, fill.kind)
        assertTrue(
            AdonisCompletionCatalog.symbolsFor(idx, fill, modelSrc).any { it.first == "email" },
        )

        val guardedSrc = """
            class User(Model):
                guarded = ["pass
        """.trimIndent()
        val g = CallSiteDetector.detect(guardedSrc)!!
        assertEquals(SymbolKind.MODEL_ATTR, g.kind)
        assertTrue(
            AdonisCompletionCatalog.symbolsFor(idx, g, guardedSrc).any { it.first == "password" },
        )

        val castKey = CallSiteDetector.detect("""casts = {"ema""")!!
        assertEquals(SymbolKind.MODEL_ATTR, castKey.kind)

        val castVal = CallSiteDetector.detect("""casts = {"email": "dat""")!!
        assertEquals(SymbolKind.CAST, castVal.kind)
        assertTrue(
            AdonisCompletionCatalog.symbolsFor(idx, castVal).any { it.first == "datetime" },
        )
    }

    @Test
    fun moreColumnMethods() {
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.pluck("ema""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.order_by("na""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.only("ema""")!!.kind)
        assertEquals(SymbolKind.RELATION, CallSiteDetector.detect("""User.query().preload('pos""")!!.kind)
    }
}

class AdonisRefactorPlannerTest {
    @Test
    fun extractPartial() {
        val plan = AdonisRefactorPlanner.extractPartial(
            "/app",
            "<div>card</div>",
            10,
            25,
            "posts._card",
        )
        assertTrue(plan.isAllowed)
        assertTrue(plan.createPath!!.endsWith("posts/_card.edge"))
        assertEquals("@include('posts._card')", plan.edits.single().newText)
        val out = AdonisRefactorPlanner.applyToText(
            "BEFORE<div>card</div>AFTER",
            listOf(AdonisRefactorPlanner.Edit(6, 21, "@include('posts._card')")),
        )
        assertTrue(out.contains("@include"))
    }

    @Test
    fun coverageMopUpBranches() {
        // ModelResolver: metadata-only columns, auth fallbacks, module match
        val metaOnly = AdonisIndex(
            ok = true,
            modelMetadata = mapOf(
                "Author" to AdonisIndex.ModelEntry(
                    fillable = listOf("bio"),
                    guarded = listOf("secret"),
                    hidden = listOf("token"),
                    casts = mapOf("born_at" to "date"),
                    module = "author",
                ),
            ),
        )
        assertTrue(AdonisModelResolver.columnsFor(metaOnly, "Author").contains("bio"))
        assertTrue(AdonisModelResolver.columnsFor(metaOnly, "author").contains("secret"))
        assertEquals("User", AdonisModelResolver.authUserModel(metaOnly))
        assertTrue(AdonisModelResolver.columnsFor(metaOnly, null).isEmpty())
        // Unknown hint → empty (never dump every DB column)
        assertTrue(AdonisModelResolver.columnsFor(AdonisIndex(ok = true), "Ghost").isEmpty())
        // Blueprint `table.` must not offer schema columns
        assertTrue(AdonisModelResolver.columnsFor(metaOnly, "table").isEmpty())
        val tableAttr = CallSiteDetector.detect("table.ema")
        assertEquals(SymbolKind.ATTR, tableAttr!!.kind)
        assertTrue(
            AdonisCompletionCatalog.symbolsFor(
                AdonisIndex(
                    ok = true,
                    tables = mapOf(
                        "users" to AdonisIndex.TableEntry(
                            columns = mapOf("email" to AdonisIndex.Located()),
                        ),
                    ),
                ),
                tableAttr,
            ).isEmpty(),
        )
        // user heuristic → authUserModel
        assertEquals(
            "User",
            AdonisModelResolver.inferModel(
                AdonisIndex(ok = true, modelMetadata = mapOf("User" to AdonisIndex.ModelEntry())),
                "",
                "user",
            ),
        )
        // table.model match via metadata class
        val people = AdonisIndex(
            ok = true,
            tables = mapOf(
                "people" to AdonisIndex.TableEntry(
                    model = "Author",
                    columns = mapOf("bio" to AdonisIndex.Located()),
                ),
            ),
            modelMetadata = mapOf("Author" to AdonisIndex.ModelEntry(module = "author")),
        )
        assertEquals("people", AdonisModelResolver.resolveTable(people, "Author"))
        assertTrue(AdonisModelResolver.columnsFor(people, "Author").contains("bio"))

        assertEquals(
            "<x-nav.bar />",
            AdonisRefactorPlanner.includeToComponent("@include('nav.bar')", 3)
                .edits.single().newText,
        )
        assertTrue(AdonisRefactorPlanner.includeToComponent("@extends('x')", 0).refusal != null)

        val userMeta = AdonisIndex(
            ok = true,
            modelMetadata = mapOf("user" to AdonisIndex.ModelEntry(module = "user")),
        )
        assertEquals("user", AdonisModelResolver.authUserModel(userMeta))

        val tagged = AdonisIndex(
            ok = true,
            tables = mapOf(
                "people" to AdonisIndex.TableEntry(model = "Author", columns = mapOf("id" to AdonisIndex.Located())),
            ),
            modelMetadata = mapOf("Author" to AdonisIndex.ModelEntry(module = "author")),
        )
        assertEquals("people", AdonisModelResolver.resolveTable(tagged, "author"))

        // CompletionCatalog relation via module + auth sentinel
        val withRel = AdonisIndex(
            ok = true,
            tables = mapOf("users" to AdonisIndex.TableEntry(model = "User", columns = mapOf("email" to AdonisIndex.Located()))),
            modelMetadata = mapOf("User" to AdonisIndex.ModelEntry(relations = listOf("posts"), module = "user")),
            relations = mapOf("User" to listOf("posts")),
        )
        assertTrue(
            AdonisCompletionCatalog.relationsFor(withRel, AdonisModelResolver.AUTH_USER_SENTINEL)
                .contains("posts"),
        )
        assertTrue(AdonisCompletionCatalog.relationsFor(withRel, "user").contains("posts"))
        val colSite = CallSiteDetector.Site(SymbolKind.COLUMN, "", receiver = "users")
        assertTrue(
            AdonisCompletionCatalog.symbolsFor(withRel, colSite).any {
                it.second.contains("column · users")
            },
        )

        // Refactor refusals
        assertTrue(
            AdonisRefactorPlanner.extractPartial("/a", "x", 0, 1, "bad name")
                .refusal!!.contains("Invalid"),
        )
        assertFalse(AdonisRefactorPlanner.includeToComponent("@include('')", 2).isAllowed)
        assertEquals(
            "Missing name",
            AdonisRefactorPlanner.includeToComponent("@include('')", 2).refusal,
        )

        val withUserClass = AdonisIndex(
            ok = true,
            modelMetadata = mapOf("User" to AdonisIndex.ModelEntry()),
        )
        assertEquals("User", AdonisModelResolver.authUserModel(withUserClass))

        // Chained redirect().route + ATTR simple recv
        assertEquals(SymbolKind.ROUTE, CallSiteDetector.detect("""redirect().route("hom""")!!.kind)
        assertEquals(SymbolKind.ATTR, CallSiteDetector.detect("user.ema")!!.kind)
        assertEquals(SymbolKind.VIEW, CallSiteDetector.detect("""@each('par""")!!.kind)
        assertNull(CallSiteDetector.detect("route.something"))

        // SymbolResolver relation via module path + view helper target
        val idx = AdonisIndex(
            ok = true,
            basePath = "/app",
            modelMetadata = mapOf(
                "User" to AdonisIndex.ModelEntry(
                    relations = listOf("posts"),
                    relationLines = mapOf("posts" to 9),
                    module = "user",
                    path = "/user.py",
                ),
            ),
            viewHelpers = mapOf("auth" to AdonisIndex.ViewVarEntry(path = "/h.py", line = 2)),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/user.py", 9),
            AdonisSymbolResolver.resolve(idx, SymbolKind.RELATION, "posts", receiver = "user"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/h.py", 2),
            AdonisSymbolResolver.resolve(idx, SymbolKind.TEMPLATE_VAR, "auth"),
        )
        assertEquals(0, AdonisSymbolResolver.locateNestedKeyLine("/nope", listOf("a", "b")))
    }
}
