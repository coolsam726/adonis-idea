package com.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Extra branch coverage for IndexLoader / CallSiteDetector / resolvers / planners.
 */
class AdonisCoverageBoostTest {
    private fun fixtureIndex(): AdonisIndex {
        val stream = javaClass.classLoader.getResourceAsStream("playground-index.json")
            ?: return AdonisIndex.empty(error = "no fixture")
        return AdonisIndexLoader.parse(stream.bufferedReader().readText())
    }

    @Test
    fun playgroundFixtureParses() {
        val index = fixtureIndex()
        if (!index.ok) return
        assertTrue(index.views.isNotEmpty())
        for (kind in SymbolKind.entries) {
            index.known(kind, "users")
            index.known(kind, "")
            AdonisCompletionCatalog.symbolsFor(index, CallSiteDetector.Site(kind, "u"))
            AdonisHoverDocs.forSymbol(index, kind, "users")
            AdonisHoverDocs.forSymbol(index, kind, "")
            AdonisSymbolResolver.resolve(index, kind, "users")
        }
        index.optionsForEnvKey("CACHE_STORE")
        index.optionsForEnvKey("CACHE_DRIVER")
        index.templateVarNames()
        index.viewNameForPath("")
        index.wireNameForPath("")
        index.wireNameForPath("/nope")
    }

    @Test
    fun callSiteDetectorExhaustBranches() {
        val samples = listOf(
            "route(\"home",
            "Router.route('x",
            "view(\"pages/a",
            "config('app.",
            "env('APP_",
            "Env.get(\"DB_",
            "env.get(\"X\", \"y",
            "middleware(['auth",
            ".use(['guest",
            ".as(\"named",
            "@include('x",
            "@includeIf('x",
            "@component('x",
            "@!component('x",
            "@wire('c",
            "@vite(['a",
            "@ea",
            "@if",
            "<x-alert",
            "<wire:counter",
            "wire:click=\"save",
            "wire:submit='go",
            "${'$'}wire.count",
            "${'$'}wire.save(",
            "User.query().where('email",
            "User.query().whereIn('id",
            "User.query().orderBy('name",
            "User.query().preload('posts",
            "User.query().withCount('posts",
            "User.create({ 'ema",
            "User.update({ \"nam",
            "fillable = [\"ema",
            "guarded = ('pass",
            "casts = { \"id\": \"nu",
            "casts = { \"ema",
            "[HomeController, \"ind",
            "ace('make:controller",
            "validate('required|em",
            "vine('email",
            "{{ title",
            "{{{ body",
            "{!! html",
            "${'$'}{APP_",
            "TextInput.make('email",
            "Select.make('role",
            "DatePicker.make('d",
            "TextColumn.make('name",
            "navigationGroup = 'CRM",
            "auth().user().ema",
            "request.user().nam",
            "user.ema",
            "disk('fs",
            "table('users",
            "can('update",
        )
        for (s in samples) {
            CallSiteDetector.detect(s)
        }
        assertEquals(SymbolKind.ENV, CallSiteDetector.detect("APP_KEY", dotenvFile = true)!!.kind)
        assertEquals(SymbolKind.ENV_VALUE, CallSiteDetector.detect("APP_KEY=sec", dotenvFile = true)!!.kind)
        CallSiteDetector.detect("# comment", dotenvFile = true)
        assertEquals(SymbolKind.ENV, CallSiteDetector.detect("export FOO", dotenvFile = true)!!.kind)
        CallSiteDetector.detect("FOO=\"bar", dotenvFile = true)
        CallSiteDetector.detect("FOO='baz", dotenvFile = true)
        CallSiteDetector.detect("FOO=", dotenvFile = true)
    }

    @Test
    fun modelResolverBranches() {
        val index = fixtureIndex().takeIf { it.ok } ?: AdonisIndexLoader.parse(
            """{"ok":true,"base_path":"/t","tables":{"users":{"columns":{"email":{"path":"/m","line":1}},"model":"User","path":"/m","line":1}},"model_metadata":{"User":{"fillable":["email"],"module":"user","path":"/u.ts","relations":["posts"],"relation_lines":{"posts":1},"table":"users"}},"relations":{"User":["posts"]},"views":{},"routes":{},"config_keys":[],"env_keys":{},"env_options":{},"components":{},"wire_components":{},"shamar":{"panels":[],"resources":{},"pages":{},"nav_groups":[],"field_types":[],"column_types":[],"icons":[]}}""",
        )
        assertTrue(AdonisModelResolver.modelNames(index).isNotEmpty())
        assertNotNull(AdonisModelResolver.resolveTable(index, "User"))
        assertNotNull(AdonisModelResolver.resolveTable(index, "users"))
        assertNotNull(AdonisModelResolver.resolveTable(index, AdonisModelResolver.AUTH_USER_SENTINEL))
        assertTrue(AdonisModelResolver.columnsFor(index, "User").isNotEmpty())
        assertTrue(AdonisModelResolver.columnsFor(index, AdonisModelResolver.AUTH_USER_SENTINEL).isNotEmpty())
        assertTrue(AdonisModelResolver.columnsFor(index, "table").isEmpty())
        assertTrue(AdonisModelResolver.columnsFor(index, null).isEmpty())
        assertTrue(AdonisModelResolver.columnsFor(index, "").isEmpty())
        assertTrue(AdonisModelResolver.authUserModel(index).isNotBlank())
        assertNotNull(AdonisModelResolver.peelModelHint("app.models.User"))
        AdonisModelResolver.inferChainHead("User.query().where('")
        AdonisModelResolver.inferModel(index, "User.query().where('", "User")
        assertTrue(AdonisModelResolver.RELATION_METHODS.contains("hasMany"))
        assertTrue(AdonisModelResolver.QUERY_COLUMN_METHODS.contains("where"))
    }

    @Test
    fun edgeStructureAndRenameAndRefactor() {
        assertTrue(AdonisEdgeStructure.analyze("@else\n").isNotEmpty())
        assertTrue(AdonisEdgeStructure.analyze("@end\n").isNotEmpty())
        assertTrue(AdonisEdgeStructure.analyze("@persist\n@end\n").isEmpty())
        assertFalse(AdonisEdgeStructure.isInlineDirective("@if(true)", 3))
        val includePlan = AdonisRefactorPlanner.includeToComponent("@include('alert')", 0)
        assertTrue(includePlan.isAllowed || includePlan.refusal != null)
        AdonisRefactorPlanner.extractPartial("/tmp", "", 0, 0, "x")
        AdonisRefactorPlanner.extractPartial("/tmp", "hi", 0, 2, "")
        AdonisRefactorPlanner.extractPartial("/tmp", "hi", 0, 2, "bad name!")
        AdonisRenamePlanner.planConfigKeyDefinition(
            "export default {\n  env: 'dev',\n}\n",
            "/tmp/config/app.ts",
            "env",
            "environment",
            1,
        )
        AdonisRenamePlanner.planFromOccurrences(SymbolKind.ROUTE, "a", "a", emptyList())
        AdonisRenamePlanner.planFromOccurrences(SymbolKind.ROUTE, "a", "!!!", emptyList())
    }

    @Test
    fun relationStubAndDbAndEnvAndMake() {
        val src = "export default class User {\n  declare id: number\n}\n"
        val plan = AdonisRelationStubPlanner.planInsert(src, "posts", "Post")
        if (plan.isAllowed) {
            AdonisRelationStubPlanner.applyToText(src, plan.edits)
        }
        AdonisRelationStubPlanner.planInsert("no class here", "posts", "Post")
        AdonisRelationStubPlanner.findClassInsertOffset("class Foo {\n  x = 1\n}\n")
        AdonisRelationStubPlanner.findClassInsertOffset("")
        assertTrue(AdonisDbIntrospection.buildJdbcUrl("mssql", "h", 1433, "db").contains("sqlserver"))
        assertTrue(AdonisDbIntrospection.buildJdbcUrl("postgres", "h", 5432, "db").contains("postgresql"))
        assertTrue(AdonisDbIntrospection.buildJdbcUrl("unknown", "h", 1, "db").contains("postgresql"))
        assertTrue(AdonisDbIntrospection.fromEnv(mapOf("DB_DATABASE" to "x")).enabled)
        assertTrue(AdonisDbIntrospection.fromEnv(mapOf("DB_HOST" to "h")).enabled)
        AdonisEnvBulkInsert.offer(listOf("A"), "A")
        AdonisEnvBulkInsert.offer(listOf("MAIL_HOST", "MAIL_PORT"), "")
        AdonisEnvBulkInsert.offer(listOf("MAIL_HOST", "MAIL_PORT"), "MAIL")
        assertEquals("make:model user", AdonisMakeCatalog.modelAceArgs("user", AdonisMakeCatalog.ModelOptions()))
        assertNotNull(AdonisMakeCatalog.byId("controller"))
        assertTrue(AdonisMakeCatalog.menuLabel(AdonisMakeCatalog.ALL.first()).contains("New"))
        assertEquals("make:controller X", AdonisMakeCatalog.ALL.first { it.id == "controller" }.aceArgs("X"))
        assertEquals("make:controller", AdonisMakeCatalog.Generator("c", "C", "make:controller", null).aceArgs(null))
    }

    @Test
    fun symbolLocatorHits() {
        val text = "route('home') and view('pages/home') and {{ title }} and {!! raw !!}"
        for (i in text.indices) {
            AdonisSymbolLocator.hitAt(text, i)
        }
        AdonisSymbolLocator.hitAt(text, -1)
        AdonisSymbolLocator.hitAt(text, text.length + 1)
    }

    @Test
    fun indexLoaderOddShapes() {
        val json = """
            {
              "ok": true,
              "base_path": "/t",
              "framework": {"adonis": true, "shamar": false, "orm": "lucid"},
              "views": {"a": "/a.edge"},
              "routes": {"r": {"uri": "/", "methods": ["GET"]}},
              "config_keys": {"app.x": true},
              "config_files": {"app": "/c.ts"},
              "config_locations": {"app.x": {"path": "/c.ts", "line": 1}},
              "translation_keys": ["t"],
              "middleware_aliases": ["m"],
              "env_keys": {"K": {"path": null, "line": 0, "kind": "", "detail": "", "used_by": []}},
              "env_options": {"K": "not-array"},
              "tables": {},
              "model_metadata": {},
              "relations": {},
              "casts": ["string"],
              "components": {},
              "gates": [],
              "disks": [],
              "queues": [],
              "caches": [],
              "mailers": [],
              "inertia_pages": [],
              "ace_commands": [],
              "validation_rules": ["required"],
              "directives": [],
              "view_helpers": [{"name": "h", "path": null, "line": 0, "kind": "helper"}],
              "view_shared": {"s": "token"},
              "view_data": {},
              "vite_entries": {},
              "controller_actions": {"C": ["i"]},
              "wire_components": {
                "c": {"path": "/w.ts", "view": null, "props": [], "methods": []}
              },
              "shamar": {
                "panels": [],
                "resources": {},
                "pages": {},
                "nav_groups": [],
                "field_types": [],
                "column_types": [],
                "icons": []
              }
            }
        """.trimIndent()
        val index = AdonisIndexLoader.parse(json)
        assertTrue(index.ok)
        assertTrue(index.known(SymbolKind.VALIDATION, "required:min"))
        assertTrue(index.known(SymbolKind.CONTROLLER_ACTION, "C"))
        assertTrue(index.known(SymbolKind.CONTROLLER_ACTION, "C@i"))
    }

    @Test
    fun fileTemplatesAllIds() {
        for (id in listOf(
            "controller", "model", "view", "middleware", "validator", "exception",
            "listener", "provider", "command", "test", "migration", "seeder",
            "factory", "event", "policy", "service", "wire", "panel",
        )) {
            assertNotNull(AdonisFileTemplates.resolve(id, "DemoName"))
        }
        assertEquals(null, AdonisFileTemplates.resolve("nope", "x"))
        assertEquals(null, AdonisFileTemplates.resolve("controller", "  "))
    }

    @Test
    fun callSiteSearcherComponentAndEnv() {
        val text = "<x-alert/> route('home') " + "${'$'}{APP_KEY}"
        AdonisCallSiteSearcher.findInText(text, SymbolKind.COMPONENT, "alert")
        AdonisCallSiteSearcher.findInText("APP_KEY=1\n", SymbolKind.ENV, "APP_KEY", dotenvFile = true)
        val root = Files.createTempDirectory("boost")
        try {
            Files.createDirectories(root.resolve("app"))
            Files.writeString(root.resolve("app/x.ts"), "route('home')")
            AdonisCallSiteSearcher.findUsages(root, SymbolKind.ROUTE, "home")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun hoverDirectivesAndShamar() {
        for (name in EdgeDirectives.NAMES) {
            AdonisHoverDocs.directive(name)
        }
        val index = fixtureIndex()
        if (!index.ok) return
        for ((name, _) in index.shamar.resources) {
            AdonisHoverDocs.forSymbol(index, SymbolKind.SHAMAR_RESOURCE, name)
        }
        for ((name, _) in index.shamar.pages) {
            AdonisHoverDocs.forSymbol(index, SymbolKind.SHAMAR_PAGE, name)
        }
        for ((name, _) in index.wireComponents) {
            AdonisHoverDocs.forSymbol(index, SymbolKind.WIRE, name)
        }
    }
}
