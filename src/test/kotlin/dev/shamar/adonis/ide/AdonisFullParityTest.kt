package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Drives coverage for Wire / Shamar / Lucid / Edge / Node / DB surfaces.
 */
class AdonisFullParityTest {
    private fun richIndex(): AdonisIndex {
        val json = """
            {
              "base_path": "/tmp/adonis-app",
              "ok": true,
              "framework": {"adonis": true, "shamar": true, "wire": true, "orm": "mongoose"},
              "views": {
                "pages/home": "/tmp/adonis-app/resources/views/pages/home.edge",
                "wire.counter": "/tmp/adonis-app/resources/views/wire/counter.edge",
                "shamar::partials/shell": "/tmp/nm/shell.edge",
                "components.alert": "/tmp/adonis-app/resources/views/components/alert.edge"
              },
              "routes": {
                "home": {"uri": "/", "methods": ["GET"], "path": "/tmp/adonis-app/start/routes.ts", "line": 10},
                "shamar.admin.resources.users.index": {"uri": "/users", "methods": ["GET"], "path": null, "line": 0},
                "__path:/health": {"uri": "/health", "methods": ["GET"], "path": "/tmp/adonis-app/start/routes.ts", "line": 20}
              },
              "config_keys": ["app.appKey", "database.connection"],
              "config_files": {"app": "/tmp/adonis-app/config/app.ts"},
              "config_locations": {
                "app.appKey": {"path": "/tmp/adonis-app/config/app.ts", "line": 5}
              },
              "translation_keys": [],
              "middleware_aliases": ["auth", "guest"],
              "env_keys": {
                "APP_KEY": {"path": "/tmp/adonis-app/.env", "line": 1, "kind": "env", "detail": "", "used_by": []},
                "DB_HOST": {"path": "/tmp/adonis-app/.env", "line": 2, "kind": "env", "detail": "host", "used_by": []},
                "DB_CONNECTION": {"path": null, "line": 0, "kind": "config", "detail": "", "used_by": ["config/database.ts"]},
                "DB_PORT": {"path": null, "line": 0, "kind": "env", "detail": "", "used_by": []},
                "DB_USER": {"path": null, "line": 0, "kind": "env", "detail": "", "used_by": []},
                "DB_PASSWORD": {"path": null, "line": 0, "kind": "env", "detail": "", "used_by": []},
                "DB_DATABASE": {"path": null, "line": 0, "kind": "env", "detail": "", "used_by": []}
              },
              "env_options": {
                "DB_CONNECTION": ["pg", "mysql", "sqlite"],
                "CACHE_STORE": ["memory", "redis"]
              },
              "tables": {
                "users": {
                  "path": "/tmp/mig.ts", "line": 1, "detail": "lucid", "model": "User",
                  "columns": {
                    "id": {"path": "/tmp/mig.ts", "line": 2},
                    "email": {"path": "/tmp/mig.ts", "line": 3}
                  }
                }
              },
              "model_metadata": {
                "User": {
                  "module": "user", "path": "/tmp/user.ts",
                  "fillable": ["email", "name"], "guarded": ["id"], "hidden": ["password"],
                  "casts": {"id": "number"},
                  "relations": ["posts"], "relation_lines": {"posts": 20},
                  "table": "users"
                }
              },
              "relations": {"User": ["posts", "profile"]},
              "casts": ["string", "number", "boolean", "date", "json"],
              "components": {"alert": "/tmp/alert.edge"},
              "gates": [],
              "disks": ["fs", "s3"],
              "queues": ["redis"],
              "caches": ["memory", "redis"],
              "mailers": ["smtp"],
              "inertia_pages": [],
              "ace_commands": ["make:controller", "make:wire", "serve"],
              "validation_rules": ["required", "email", "minLength"],
              "directives": ["if", "each", "wire", "persist", "end"],
              "view_helpers": [{"name": "auth", "path": "/tmp/h.ts", "line": 1, "kind": "helper"}],
              "view_shared": {"flashMessages": {"path": null, "line": 0, "kind": "shared"}},
              "view_data": {
                "pages/home": {"title": {"path": "/tmp/c.ts", "line": 5, "kind": "data"}}
              },
              "vite_entries": {"resources/js/app.js": "/tmp/app.js"},
              "controller_actions": {"HomeController": ["index", "store"]},
              "wire_components": {
                "counter": {
                  "path": "/tmp/app/wire/counter.ts",
                  "view": "/tmp/views/wire/counter.edge",
                  "props": ["count"],
                  "methods": ["increment", "decrement"]
                }
              },
              "shamar": {
                "panels": ["admin", "app"],
                "resources": {
                  "users": {
                    "class": "UserResource", "panel": "admin", "slug": "users",
                    "label": "Users", "navigationGroup": "CRM", "icon": "users",
                    "model": "User", "path": "/tmp/user_resource.ts"
                  }
                },
                "pages": {
                  "settings": {
                    "class": "SettingsPage", "panel": "admin", "slug": "settings",
                    "label": "Settings", "navigationGroup": "System", "icon": "cog",
                    "path": "/tmp/settings_page.ts"
                  }
                },
                "widgets": {
                  "ProductStatsWidget": {
                    "class": "ProductStatsWidget", "panel": "admin", "kind": "stats",
                    "path": "/tmp/app/widgets/admin/product_stats_widget.ts"
                  }
                },
                "nav_groups": ["CRM", "System"],
                "field_types": ["TextInput", "Select", "Toggle", "RelationTable"],
                "column_types": ["TextColumn"],
                "widget_types": ["StatsOverviewWidget", "Stat"],
                "icons": ["users", "cog"]
              }
            }
        """.trimIndent()
        return AdonisIndexLoader.parse(json)
    }

    @Test
    fun parsesFrameworkWireAndShamar() {
        val index = richIndex()
        assertTrue(index.ok)
        assertTrue(index.framework.adonis)
        assertTrue(index.framework.shamar)
        assertEquals("mongoose", index.framework.orm)
        assertEquals(listOf("count"), index.wireComponents["counter"]!!.props)
        assertEquals(listOf("admin", "app"), index.shamar.panels)
        assertEquals("UserResource", index.shamar.resources["users"]!!.className)
        assertTrue(index.known(SymbolKind.WIRE, "counter"))
        assertTrue(index.known(SymbolKind.SHAMAR_RESOURCE, "users"))
        assertTrue(index.known(SymbolKind.SHAMAR_PAGE, "settings"))
        assertTrue(index.known(SymbolKind.SHAMAR_FIELD, "TextInput"))
        assertTrue(index.known(SymbolKind.SHAMAR_COLUMN, "TextColumn"))
        assertTrue(index.known(SymbolKind.SHAMAR_NAV, "CRM"))
        assertTrue(index.known(SymbolKind.SHAMAR_WIDGET, "ProductStatsWidget"))
        assertTrue(index.known(SymbolKind.SHAMAR_WIDGET, "StatsOverviewWidget"))
        assertTrue(index.known(SymbolKind.SHAMAR_LITERAL, "sidebar"))
        assertFalse(index.known(SymbolKind.WIRE, "missing"))
        assertEquals("counter", index.wireNameForPath("/tmp/app/wire/counter.ts"))
        assertEquals("pages/home", index.viewNameForPath("/tmp/adonis-app/resources/views/pages/home.edge"))
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/widgets/admin/product_stats_widget.ts", 0),
            AdonisSymbolResolver.resolve(index, SymbolKind.SHAMAR_WIDGET, "ProductStatsWidget"),
        )
    }

    @Test
    fun completionCatalogCoversNewKinds() {
        val index = richIndex()
        fun names(kind: SymbolKind, receiver: String? = null) =
            AdonisCompletionCatalog.symbolsFor(
                index,
                CallSiteDetector.Site(kind, "", receiver),
            ).map { it.first }.toSet()

        assertTrue("home" in names(SymbolKind.ROUTE))
        assertFalse(names(SymbolKind.ROUTE).any { it.startsWith("__path:") })
        assertTrue("pages/home" in names(SymbolKind.VIEW))
        assertTrue("auth" in names(SymbolKind.MIDDLEWARE))
        assertTrue("APP_KEY" in names(SymbolKind.ENV))
        assertTrue("pg" in names(SymbolKind.ENV_VALUE, "DB_CONNECTION"))
        assertTrue("counter" in names(SymbolKind.WIRE))
        assertTrue("count" in names(SymbolKind.WIRE_PROP, "counter"))
        assertTrue("increment" in names(SymbolKind.WIRE_METHOD, "counter"))
        assertTrue("users" in names(SymbolKind.SHAMAR_RESOURCE))
        assertTrue("settings" in names(SymbolKind.SHAMAR_PAGE))
        assertTrue("TextInput" in names(SymbolKind.SHAMAR_FIELD))
        assertTrue("TextColumn" in names(SymbolKind.SHAMAR_COLUMN))
        assertTrue("CRM" in names(SymbolKind.SHAMAR_NAV))
        assertTrue("ProductStatsWidget" in names(SymbolKind.SHAMAR_WIDGET))
        assertTrue("StatsOverviewWidget" in names(SymbolKind.SHAMAR_WIDGET))
        assertTrue("sidebar" in names(SymbolKind.SHAMAR_LITERAL, ShamarLiterals.PRESENTATION))
        assertTrue("embed" in names(SymbolKind.SHAMAR_LITERAL, ShamarLiterals.OPEN_IN))
        assertTrue("fullscreen" in names(SymbolKind.SHAMAR_LITERAL, ShamarLiterals.PAGE_MODE))
        assertTrue("success" in names(SymbolKind.SHAMAR_LITERAL, ShamarLiterals.STAT_COLOR))
        assertTrue("make:controller" in names(SymbolKind.ACE))
        assertTrue("if" in names(SymbolKind.DIRECTIVE))
        assertTrue("email" in names(SymbolKind.COLUMN, "User"))
        assertTrue("posts" in names(SymbolKind.RELATION, "User"))
        assertTrue(names(SymbolKind.WIRE_PROP).isNotEmpty())
        assertTrue(names(SymbolKind.WIRE_METHOD).isNotEmpty())
        assertTrue(AdonisCompletionCatalog.columnsFor(index, "User").contains("email"))
        assertTrue(AdonisCompletionCatalog.relationsFor(index, "User").contains("posts"))
    }

    @Test
    fun callSitesAdonisEdgeWireShamar() {
        val cases = listOf(
            """route('ho""" to SymbolKind.ROUTE,
            """view('pages/ho""" to SymbolKind.VIEW,
            """view.render('pages/auth/sign""" to SymbolKind.VIEW,
            """env.get('APP_""" to SymbolKind.ENV,
            """env.get('DB_CONNECTION', 'p""" to SymbolKind.ENV_VALUE,
            """@include('pages/ho""" to SymbolKind.VIEW,
            """@!component('alert""" to SymbolKind.COMPONENT,
            """@wire('coun""" to SymbolKind.WIRE,
            """<wire:coun""" to SymbolKind.WIRE,
            """wire:model="coun""" to SymbolKind.WIRE_PROP,
            """${'$'}wire.incre(""" to SymbolKind.WIRE_METHOD,
            """${'$'}wire.coun""" to SymbolKind.WIRE_PROP,
            """User.query().where('ema""" to SymbolKind.COLUMN,
            """User.query().preload('po""" to SymbolKind.RELATION,
            """middleware(['au""" to SymbolKind.MIDDLEWARE,
            """TextInput.make('""" to SymbolKind.SHAMAR_FIELD,
            """RelationTable.make('""" to SymbolKind.SHAMAR_FIELD,
            """TextColumn.make('""" to SymbolKind.SHAMAR_COLUMN,
            """static navigationGroup = 'CR""" to SymbolKind.SHAMAR_NAV,
            """.openIn('mod""" to SymbolKind.SHAMAR_LITERAL,
            """.presentation('side""" to SymbolKind.SHAMAR_LITERAL,
            """static createMode = 'full""" to SymbolKind.SHAMAR_LITERAL,
            """static editMode: 'side""" to SymbolKind.SHAMAR_LITERAL,
            """.color('suc""" to SymbolKind.SHAMAR_LITERAL,
            """.descriptionColor('warn""" to SymbolKind.SHAMAR_LITERAL,
            """.as('home""" to SymbolKind.ROUTE,
            """@ea""" to SymbolKind.DIRECTIVE,
            """{{ tit""" to SymbolKind.TEMPLATE_VAR,
            """@vite(['resources/js/a""" to SymbolKind.VITE,
        )
        for ((before, expected) in cases) {
            val site = CallSiteDetector.detect(before)
            assertNotNull("null site for: $before", site)
            assertEquals("kind for: $before", expected, site!!.kind)
        }
    }

    @Test
    fun edgeLexerAndStructure() {
        val lexer = EdgeLexer()
        val text = """<div>{{{ raw }}} @!component('x') {{-- c --}} {{ n }}</div>"""
        lexer.start(text)
        val types = mutableListOf<String>()
        while (lexer.tokenType != null) {
            types.add(lexer.tokenType!!.toString())
            lexer.advance()
        }
        assertTrue(types.any { it.contains("ECHO") || it.contains("RAW") })
        assertTrue(types.any { it.contains("DIRECTIVE") })
        assertTrue("wire" in EdgeDirectives.NAMES)
        assertTrue("end" in EdgeDirectives.NAMES)

        assertTrue(
            AdonisEdgeStructure.analyze(
                """
                @if(true)
                  @each(item in items)
                    {{ item }}
                  @end
                @end
                """.trimIndent(),
            ).isEmpty(),
        )
        assertTrue(AdonisEdgeStructure.analyze("@if(true)\n").isNotEmpty())
        assertTrue(AdonisEdgeStructure.isInlineDirective("@section('n', 'v')", "@section".length))
    }

    @Test
    fun makeCatalogAndTemplates() {
        val ids = AdonisMakeCatalog.ALL.map { it.id }.toSet()
        assertTrue("controller" in ids)
        assertTrue("wire" in ids)
        assertTrue("panel" in ids)
        assertFalse("channel" in ids)
        assertTrue(AdonisMakeCatalog.modelAceArgs("user", AdonisMakeCatalog.ModelOptions(migration = true)).contains("--migration"))
        assertNotNull(AdonisFileTemplates.resolve("controller", "Home"))
        assertTrue(AdonisFileTemplates.controller("Home").contents.contains("HomeController"))
        assertTrue(AdonisFileTemplates.wire("counter").relativePath.contains("wire"))
        assertTrue(AdonisFileTemplates.panel("admin").relativePath.contains("panels"))
        assertTrue(AdonisFileTemplates.model("User").contents.contains("BaseModel"))
        assertTrue(AdonisFileTemplates.view("pages/home").relativePath.endsWith(".edge"))
        assertNotNull(AdonisFileTemplates.resolve("middleware", "Auth"))
        assertNotNull(AdonisFileTemplates.resolve("validator", "create_user"))
        assertNotNull(AdonisFileTemplates.resolve("exception", "Bad"))
        assertNotNull(AdonisFileTemplates.resolve("listener", "User"))
        assertNotNull(AdonisFileTemplates.resolve("provider", "App"))
        assertNotNull(AdonisFileTemplates.resolve("command", "Greet"))
        assertNotNull(AdonisFileTemplates.resolve("test", "users"))
        assertNotNull(AdonisFileTemplates.resolve("migration", "create_users"))
        assertNotNull(AdonisFileTemplates.resolve("seeder", "User"))
        assertNotNull(AdonisFileTemplates.resolve("factory", "User"))
        assertNotNull(AdonisFileTemplates.resolve("event", "UserCreated"))
        assertNotNull(AdonisFileTemplates.resolve("policy", "User"))
        assertNotNull(AdonisFileTemplates.resolve("service", "Billing"))
        assertEquals("user_profile", AdonisFileTemplates.snake("UserProfile"))
        assertEquals("UserProfile", AdonisFileTemplates.className("user_profile"))
    }

    @Test
    fun modelResolverAndLucidHelpers() {
        val index = richIndex()
        assertTrue("email" in AdonisModelResolver.columnsFor(index, "User"))
        assertNotNull(AdonisModelResolver.inferModel(index, "User.query().where('", "User"))
        assertTrue(AdonisLucidHelpers.eagerLoadSnippet("posts").contains("preload"))
        assertTrue(AdonisLucidHelpers.whereSnippet("email").contains("email"))
        assertTrue(AdonisLucidHelpers.relationMethodNames().contains("hasMany"))
        assertTrue(AdonisLucidHelpers.eagerLoadSnippets(index, "User").isNotEmpty())
        assertTrue(AdonisLucidHelpers.whereColumnSnippets(index, "User").isNotEmpty())
        assertTrue(AdonisLucidHelpers.relationMethodStub("posts", "Post").contains("hasMany"))
    }

    @Test
    fun hoverDocsSymbolLocatorResolverToolWindow() {
        val index = richIndex()
        assertNotNull(AdonisHoverDocs.forSymbol(index, SymbolKind.ROUTE, "home"))
        assertNotNull(AdonisHoverDocs.forSymbol(index, SymbolKind.WIRE, "counter"))
        assertNotNull(AdonisHoverDocs.forSymbol(index, SymbolKind.VIEW, "pages/home"))
        assertNotNull(AdonisHoverDocs.forSymbol(index, SymbolKind.CONFIG, "app.appKey"))
        assertNotNull(AdonisHoverDocs.forSymbol(index, SymbolKind.ENV, "APP_KEY"))
        assertNotNull(AdonisHoverDocs.directive("if"))
        val hit = AdonisSymbolLocator.hitAt("""route('home')""", 8)
        assertNotNull(hit)
        assertEquals(SymbolKind.ROUTE, hit!!.kind)
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.ROUTE, "home"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.VIEW, "pages/home"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.CONFIG, "app.appKey"))
        val summary = AdonisToolWindowModel.summary(index)
        assertTrue(summary.ok)
        assertTrue(AdonisToolWindowModel.statusLine(summary).contains("OK"))
        assertTrue(AdonisToolWindowModel.symbolRows(index, "").isNotEmpty())
        assertTrue(AdonisToolWindowModel.symbolRows(index, "home").isNotEmpty())
    }

    @Test
    fun plannersEnvBulkDbAndSearch() {
        val index = richIndex()
        val offer = AdonisEnvBulkInsert.offer(index.envKeys.keys, "DB_")
        assertNotNull(offer)
        assertTrue(AdonisEnvBulkInsert.matchingKeys(index.envKeys.keys, "DB_").size > 1)
        val stub = AdonisRelationStubPlanner.planInsert(
            "export default class User {\n}\n",
            "posts",
            "Post",
        )
        assertTrue(stub.isAllowed || stub.refusal != null)
        val actions = AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.VIEW, "missing.page")
        assertTrue(actions.isNotEmpty())
        val extract = AdonisRefactorPlanner.extractPartial(
            "/tmp/adonis-app",
            "{{ x }}",
            0,
            7,
            "partials/x",
        )
        assertTrue(extract.isAllowed)
        val cfg = AdonisDbIntrospection.fromEnv(
            mapOf(
                "DB_HOST" to "127.0.0.1",
                "DB_PORT" to "5432",
                "DB_USER" to "adonis",
                "DB_PASSWORD" to "secret",
                "DB_DATABASE" to "app",
                "DB_CONNECTION" to "pg",
            ),
        )
        assertTrue(cfg.enabled)
        assertTrue(cfg.jdbcUrl.contains("postgresql"))
        assertTrue(AdonisDbIntrospection.fromEnv(mapOf("DB_CONNECTION" to "mysql", "DB_DATABASE" to "a")).jdbcUrl.contains("mysql"))
        assertTrue(AdonisDbIntrospection.fromEnv(mapOf("DB_CONNECTION" to "sqlite", "DB_DATABASE" to "/tmp/a.db")).jdbcUrl.contains("sqlite"))
        assertFalse(AdonisDbIntrospection.fromEnv(emptyMap()).enabled)
        assertEquals(
            setOf("a", "b"),
            AdonisDbIntrospection.mergeColumns(setOf("a"), setOf("b")),
        )

        val hits = AdonisCallSiteSearcher.findInText("""route('home')""", SymbolKind.ROUTE, "home")
        assertTrue(hits.isNotEmpty())
        val dir = Files.createTempDirectory("adonis-search")
        try {
            Files.createDirectories(dir.resolve("start"))
            Files.writeString(dir.resolve("start/routes.ts"), """route('home')""")
            assertTrue(AdonisCallSiteSearcher.findUsages(dir, SymbolKind.ROUTE, "home").isNotEmpty())
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun nodeResolverAndIndexCommandAndEmpty() {
        val root = Files.createTempDirectory("adonis-idea-node")
        try {
            val bin = AdonisNode.resolveBinary(root)
            assertTrue(bin.contains("node"))
            val script = root.resolve("indexer.mjs")
            Files.writeString(script, "console.log('{}')")
            val cmd = AdonisIndexLoader.buildIndexCommand(root, script)
            assertTrue(cmd.parametersList.parameters.contains("--json"))
        } finally {
            root.toFile().deleteRecursively()
        }
        assertNotNull(AdonisNode.resolveBinary(null))
        val empty = AdonisIndex.empty(error = "boom")
        assertFalse(empty.ok)
        assertEquals("boom", empty.error)
    }

    @Test
    fun renamePlannerOccurrences() {
        assertTrue(SymbolKind.ROUTE in AdonisRenamePlanner.RENAMABLE)
        val occ = AdonisCallSiteSearcher.findInText("""route('home')""", SymbolKind.ROUTE, "home")
        val plan = AdonisRenamePlanner.planFromOccurrences(
            SymbolKind.ROUTE,
            "home",
            "homepage",
            occ,
        )
        assertTrue(plan.edits.isNotEmpty() || plan.refusal != null)
        val move = AdonisRenamePlanner.planViewFileMove(
            SymbolKind.VIEW,
            "pages/home",
            "pages/welcome",
            "/tmp/adonis-app/resources/views/pages/home.edge",
            "/tmp/adonis-app",
        )
        assertNotNull(move)
    }
}
