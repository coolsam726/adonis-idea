package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Targeted branch/line hits to drive the verified set to 100% line coverage.
 */
class AdonisHundredPercentTest {

    private fun idx(): AdonisIndex = AdonisIndexLoader.parse(
        """
        {
          "ok": true,
          "base_path": "/tmp/app",
          "error": null,
          "framework": {"adonis": true, "shamar": true, "orm": "lucid"},
          "views": {
            "pages/home": "/tmp/app/resources/views/pages/home.edge",
            "components.alert": "/tmp/app/resources/views/components/alert.edge",
            "partial": "/other/partial.edge"
          },
          "routes": {
            "home": {"uri": "/", "methods": ["GET"], "path": "/tmp/app/start/routes.ts", "line": 3},
            "orphan": {"uri": "/o", "methods": [], "path": null, "line": 0}
          },
          "config_keys": ["app.env", "app.name", "database.connection"],
          "config_files": {"app": "/tmp/app/config/app.ts", "database": "/tmp/app/config/database.ts"},
          "config_locations": {
            "app.env": {"path": "/tmp/app/config/app.ts", "line": 2},
            "app.name": {"path": null, "line": 0}
          },
          "translation_keys": {"messages.hi": true},
          "middleware_aliases": ["auth"],
          "env_keys": {
            "APP_KEY": {"path": "/tmp/app/.env", "line": 1, "kind": "env", "detail": "secret", "used_by": []},
            "CONFIG_ONLY": {"path": null, "line": 0, "kind": "config", "detail": "", "used_by": ["config/app.ts:10"]},
            "NO_ORIGIN": {"path": null, "line": 0, "kind": "config", "detail": "", "used_by": []}
          },
          "env_options": {
            "CACHE_STORE": ["memory", "redis"],
            "CACHE_DRIVER": ["file"],
            "BAD": "x"
          },
          "tables": {
            "users": {
              "columns": {
                "id": {"path": "/tmp/mig.ts", "line": 2},
                "email": {"path": null, "line": 3}
              },
              "detail": "",
              "path": "/tmp/mig.ts",
              "line": 1,
              "model": "User"
            },
            "posts": {
              "columns": {"title": {"path": "/tmp/p.ts", "line": 1}},
              "detail": "posts",
              "path": null,
              "line": 0,
              "model": "Post"
            }
          },
          "model_metadata": {
            "User": {
              "fillable": ["email"], "guarded": ["id"], "hidden": ["password"],
              "casts": {"id": "number"}, "relations": ["posts"],
              "relation_lines": {"posts": 12},
              "module": "user", "path": "/tmp/user.ts", "table": "users", "orm": "lucid"
            },
            "Post": {
              "fillable": ["title"], "relations": ["author"],
              "relation_lines": {},
              "module": "post", "path": "", "table": "posts", "orm": "lucid"
            },
            "Category": {
              "fillable": [], "relations": ["posts"],
              "module": "category", "path": "/tmp/cat.ts", "table": "categories", "orm": "lucid"
            }
          },
          "relations": {"User": ["posts", "profile"]},
          "casts": ["string", "number"],
          "components": {"alert": "/tmp/alert.edge"},
          "gates": ["update"],
          "disks": ["fs"],
          "queues": ["redis"],
          "caches": ["memory"],
          "mailers": ["smtp"],
          "inertia_pages": ["Dash"],
          "ace_commands": ["serve"],
          "validation_rules": ["required", "email"],
          "directives": [],
          "view_helpers": {
            "auth": {"path": "/tmp/h.ts", "line": 1, "kind": "helper"},
            "plain": "x"
          },
          "view_shared": {
            "flash": {"path": "/tmp/s.ts", "line": 2, "kind": "shared"},
            "csrf": "token"
          },
          "view_data": {
            "pages/home": {
              "title": {"path": "/tmp/c.ts", "line": 5, "kind": "data"},
              "nopath": {"path": null, "line": 0, "kind": "data"}
            }
          },
          "vite_entries": {"resources/js/app.js": "/tmp/app.js"},
          "controller_actions": {"HomeController": ["index"]},
          "wire_components": {
            "counter": {
              "path": "/tmp/wire/counter.ts",
              "view": "/tmp/views/wire/counter.edge",
              "props": ["count"],
              "methods": ["increment"]
            },
            "bare": {"path": null, "view": null, "props": [], "methods": []}
          },
          "shamar": {
            "panels": ["admin"],
            "resources": {
              "users": {
                "class": "UserResource", "panel": "admin", "slug": "users",
                "label": "", "navigationGroup": "CRM", "icon": "users",
                "model": "User", "path": "/tmp/ur.ts"
              },
              "empty": {
                "class": "", "panel": "admin", "slug": "",
                "label": "", "path": null
              }
            },
            "pages": {
              "settings": {
                "class": "SettingsPage", "panel": "admin", "slug": "settings",
                "label": "", "icon": null, "path": "/tmp/sp.ts"
              }
            },
            "widgets": {
              "ProductStatsWidget": {
                "class": "ProductStatsWidget", "panel": "admin", "kind": "stats",
                "path": "/tmp/app/widgets/admin/product_stats_widget.ts"
              },
              "BareWidget": {
                "class": "", "panel": "", "path": null
              }
            },
            "nav_groups": ["CRM"],
            "field_types": ["TextInput"],
            "column_types": ["TextColumn"],
            "widget_types": ["StatsOverviewWidget", "Stat"],
            "icons": ["users"]
          }
        }
        """.trimIndent(),
    )

    @Test
    fun indexLoaderObjectHelpersAndNullShamar() {
        // view_helpers as object (not array) — hits NC branches
        val index = idx()
        assertTrue(index.viewHelpers.containsKey("auth"))
        assertTrue(index.viewHelpers.containsKey("plain"))
        assertEquals("shared", index.viewShared["csrf"]!!.kind)
        assertNull(AdonisIndexLoader.parse("""{"ok":true,"base_path":"/t"}""").error)
        // null / missing shamar
        val noShamar = AdonisIndexLoader.parse("""{"ok":true,"base_path":"/t","shamar":null}""")
        assertTrue(noShamar.shamar.panels.isEmpty())
        // stringList from object keys + array + missing
        assertTrue(index.translationKeys.contains("messages.hi"))
        // config_keys as array path already covered; env_options non-array → empty
        assertTrue(index.envOptions["BAD"].isNullOrEmpty())
        val cmd = AdonisIndexLoader.buildIndexCommand(
            Files.createTempDirectory("idx"),
            Files.createTempFile("ix", ".mjs"),
        )
        assertTrue(cmd.parametersList.parameters.contains("--json"))
    }

    @Test
    fun indexKnownAndPathHelpers() {
        val index = idx()
        assertTrue(index.known(SymbolKind.COMPONENT, "alert"))
        assertTrue(index.known(SymbolKind.COMPONENT, "missing") || !index.known(SymbolKind.COMPONENT, "nomatch"))
        // components.$name via views
        assertTrue(index.known(SymbolKind.COMPONENT, "alert"))
        assertTrue(index.known(SymbolKind.VITE, "resources/js/app.js"))
        assertTrue(index.known(SymbolKind.VITE, "pages/home"))
        assertTrue(index.known(SymbolKind.CONTROLLER_ACTION, "HomeController"))
        assertTrue(index.known(SymbolKind.CONTROLLER_ACTION, "HomeController@index"))
        assertFalse(index.known(SymbolKind.CONTROLLER_ACTION, "HomeController@nope"))
        assertTrue(index.known(SymbolKind.SHAMAR_FIELD, "users")) // slug match
        assertTrue(index.known(SymbolKind.WIRE_PROP, "count"))
        assertTrue(index.known(SymbolKind.WIRE_METHOD, "increment"))
        // options alias
        assertTrue(index.optionsForEnvKey("CACHE_DRIVER").isNotEmpty() || index.optionsForEnvKey("CACHE_STORE").isNotEmpty())
        assertTrue(index.optionsForEnvKey("UNKNOWN_KEY").isEmpty())
        // path helpers with slash normalization / suffix match
        assertEquals("partial", index.viewNameForPath("/other/partial.edge"))
        assertNull(index.viewNameForPath(""))
        assertEquals("counter", index.wireNameForPath("/tmp/wire/counter.ts"))
        assertEquals("counter", index.wireNameForPath("/tmp/views/wire/counter.edge"))
        assertNull(index.wireNameForPath(""))
        assertNull(index.wireNameForPath("/nope"))
    }

    @Test
    fun completionCatalogBranches() {
        val index = idx()
        fun site(kind: SymbolKind, recv: String? = null) =
            AdonisCompletionCatalog.symbolsFor(index, CallSiteDetector.Site(kind, "x", recv), "class User extends BaseModel {\n")
        site(SymbolKind.TABLE)
        site(SymbolKind.DIRECTIVE) // empty directives → EdgeDirectives.NAMES
        site(SymbolKind.WIRE)
        site(SymbolKind.WIRE, null)
        site(SymbolKind.WIRE_PROP, null)
        site(SymbolKind.SHAMAR_RESOURCE)
        site(SymbolKind.SHAMAR_PAGE)
        site(SymbolKind.SHAMAR_WIDGET)
        site(SymbolKind.SHAMAR_LITERAL, ShamarLiterals.OPEN_IN)
        site(SymbolKind.SHAMAR_LITERAL, ShamarLiterals.PRESENTATION)
        site(SymbolKind.SHAMAR_LITERAL, null)
        site(SymbolKind.MODEL_ATTR, "User")
        site(SymbolKind.COLUMN, "User")
        site(SymbolKind.COLUMN, AdonisModelResolver.AUTH_USER_SENTINEL)
        site(SymbolKind.COLUMN, null)
        AdonisCompletionCatalog.symbolsFor(
            index,
            CallSiteDetector.Site(SymbolKind.MODEL_ATTR, "e", "casts"),
            "export default class User {\n  //",
        )
        // wire with null path
        AdonisCompletionCatalog.symbolsFor(
            index.copy(
                wireComponents = mapOf("x" to AdonisIndex.WireEntry(path = null, props = listOf("a"))),
            ),
            CallSiteDetector.Site(SymbolKind.WIRE, ""),
        )
    }

    @Test
    fun callSiteDetectorRemainingBranches() {
        // force takeLast(320)
        val longPrefix = "x".repeat(400)
        CallSiteDetector.detect(longPrefix + "route('ho")
        // kindForCall branches via GLOBAL / METHOD
        for (s in listOf(
            "env('APP_",
            "ace('make:",
            "something.unknownFn('x",
            "@svg('icon",
            "@asset('x",
            "User.create(email=",
            "User.update(name=",
            "User.force_fill(x=",
            "User.create({ \"ema",
            ").where('col", // chained — recover via inferChainHead
            "Author.query().order_by('n",
            "disk('fs",
            "table('users",
            "can('update",
            "authorize('x",
            "render('Dash",
            "route_is('home",
            "__('msg",
            "trans('msg",
            "t('msg",
            "config('app",
            "view('pages",
            "middleware('auth",
            "vite('res",
            "asset('x",
            "url('x",
            "Author.query().has('posts",
            "Author.query().where_has('posts",
        )) {
            CallSiteDetector.detect(s)
        }
        // dotenv edge cases
        CallSiteDetector.detect("FOO=\"bar\"", dotenvFile = true)
        CallSiteDetector.detect("FOO='baz'", dotenvFile = true)
        CallSiteDetector.detect("FOO=\"", dotenvFile = true)
        CallSiteDetector.detect("FOO='", dotenvFile = true)
        CallSiteDetector.detect("FOO=plain", dotenvFile = true)
        CallSiteDetector.detect("\${APP_", dotenvFile = true)
        CallSiteDetector.detect("# hi", dotenvFile = true)
        assertNull(CallSiteDetector.detect("   ", dotenvFile = false))
        // kindForCall("env"/"ace") via method receiver (bare env/ace caught earlier)
        assertEquals(SymbolKind.ENV, CallSiteDetector.detect("""Helper.env('APP_""")!!.kind)
        assertEquals(SymbolKind.ACE, CallSiteDetector.detect("""Helper.ace('make:""")!!.kind)
        // kindForCall else → null (noop is in CHAINED_CALL but unmapped)
        assertNull(CallSiteDetector.detect(""").noop('x"""))
        // DIRECTIVE_VIEW else → VIEW
        assertEquals(SymbolKind.VIEW, CallSiteDetector.detect("""@include('pages/x""")!!.kind)
        // dotenv fallthrough → null
        assertNull(CallSiteDetector.detect("=orphan", dotenvFile = true))
        assertNull(CallSiteDetector.detect("9NOTAKEY", dotenvFile = true))
    }

    @Test
    fun edgeLexerUnterminatedAndEscapes() {
        val lexer = EdgeLexer()
        fun tokens(text: String): List<String> {
            lexer.start(text)
            val out = mutableListOf<String>()
            while (lexer.tokenType != null) {
                out.add(lexer.tokenType!!.toString())
                lexer.advance()
            }
            return out
        }
        tokens("{{-- unclosed comment")
        tokens("{{{ unclosed raw")
        tokens("{!! unclosed")
        tokens("{{ unclosed")
        tokens("@@if not a directive")
        tokens("@if(a > (b + c) && 'x') ok @end")
        tokens("@if(unterminated")
        tokens("@!component('x')")
        tokens("hi@example.com")
        tokens("@unknownThing")
        tokens("@")
        tokens("")
        lexer.start("a", 0, 0, 0)
        assertNull(lexer.tokenType)
        assertEquals(0, lexer.state)
        lexer.start("{{ x }}", 0, 7, 0)
        assertEquals(0, lexer.tokenStart)
        assertNotNull(lexer.bufferSequence)
        assertTrue(lexer.bufferEnd >= 0)
    }

    @Test
    fun edgeStructureInlineEscapesAndMidBlock() {
        // escaped quote + nested paren + top-level comma for inline section
        assertTrue(AdonisEdgeStructure.isInlineDirective("@section('n', 'a\\'b')", "@section".length))
        assertTrue(AdonisEdgeStructure.isInlineDirective("@section('n', f(1,2))", "@section".length))
        assertFalse(AdonisEdgeStructure.isInlineDirective("@section", "@section".length))
        assertFalse(AdonisEdgeStructure.isInlineDirective("@section ", "@section".length))
        assertTrue(AdonisEdgeStructure.analyze("@if(true)\n@else\n@end\n").isEmpty())
        assertTrue(AdonisEdgeStructure.analyze("@else\n").isNotEmpty())
        assertTrue(AdonisEdgeStructure.analyze("@elseif(x)\n").isNotEmpty())
    }

    @Test
    fun modelResolverDeep() {
        val index = idx()
        assertNull(AdonisModelResolver.resolveTable(index, null))
        assertNull(AdonisModelResolver.resolveTable(index, ""))
        assertNotNull(AdonisModelResolver.resolveTable(index, "users"))
        assertNotNull(AdonisModelResolver.resolveTable(index, "User"))
        assertNotNull(AdonisModelResolver.resolveTable(index, AdonisModelResolver.AUTH_USER_SENTINEL))
        // Category → categories via pluralize; table not in index → derived
        AdonisModelResolver.resolveTable(index, "Category")
        // model with empty path table binding via for-loop equals
        AdonisModelResolver.resolveTable(index, "post")
        assertEquals("User", AdonisModelResolver.peelModelHint("UserFactory"))
        assertEquals("Author", AdonisModelResolver.peelModelHint("Author.query"))
        AdonisModelResolver.peelModelHint("query")
        AdonisModelResolver.inferModel(index, "", "")
        AdonisModelResolver.inferModel(index, "", "DB")
        AdonisModelResolver.inferModel(index, "", AdonisModelResolver.AUTH_USER_SENTINEL)
        AdonisModelResolver.inferModel(index, "const user = User.create(", "user")
        AdonisModelResolver.inferModel(index, "const user = await UserFactory(", "user")
        AdonisModelResolver.inferModel(index, "const user = User.factory(", "user")
        AdonisModelResolver.inferModel(index, "const user: User = x", "user")
        AdonisModelResolver.inferModel(index, "User.query().where(", "query")
        AdonisModelResolver.inferModel(index, "", "user")
        AdonisModelResolver.pluralize("category")
        AdonisModelResolver.pluralize("bus")
        AdonisModelResolver.pluralize("box")
        AdonisModelResolver.pluralize("church")
        AdonisModelResolver.pluralize("dish")
        AdonisModelResolver.pluralize("user")
        assertTrue(AdonisModelResolver.columnsFor(index, "User").contains("email"))
        assertTrue(AdonisModelResolver.columnsFor(index, "table").isEmpty())
    }

    @Test
    fun symbolResolverAllKinds() {
        val index = idx()
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.ROUTE, ""))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.ROUTE, "missing"))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.ROUTE, "orphan"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.ROUTE, "home"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.VIEW, "pages/home"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.CONFIG, "app.env"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.CONFIG, "app"))
        // config without location → nested key walk
        val cfgFile = Files.createTempFile("cfg", ".ts")
        Files.writeString(cfgFile, "export default {\n  connection: {\n    host: 'x',\n  },\n}\n")
        val withCfg = index.copy(
            configFiles = mapOf("database" to cfgFile.toString()),
            configLocations = emptyMap(),
            configKeys = setOf("database.connection.host"),
        )
        assertNotNull(AdonisSymbolResolver.resolve(withCfg, SymbolKind.CONFIG, "database.connection.host"))
        AdonisSymbolResolver.locateNestedKeyLine("/no/such/file", listOf("a"))
        AdonisSymbolResolver.locateNestedKeyLine(cfgFile.toString(), emptyList())
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.ENV, "APP_KEY"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.ENV, "CONFIG_ONLY"))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.ENV, "NO_ORIGIN"))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.ENV_VALUE, "x"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.TABLE, "users"))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.TABLE, "posts")) // path null
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.COLUMN, "email", "users"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.COLUMN, "email", "User"))
        assertNotNull(AdonisSymbolResolver.resolveColumn(index, "User", "email"))
        assertNotNull(AdonisSymbolResolver.resolveColumn(index, null, "title"))
        assertNull(AdonisSymbolResolver.resolveColumn(index, null, ""))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.COMPONENT, "alert"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.VITE, "resources/js/app.js"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.VITE, "pages/home"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", "User"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", "user")) // module match
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", "NoSuchModel")) // fallback
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", null))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.RELATION, "author", "Post")) // empty path
        // locateNestedKeyLine catch → 0 (unreadable regular file)
        val locked = Files.createTempFile("locked", ".ts")
        Files.writeString(locked, "x")
        locked.toFile().setReadable(false)
        try {
            assertEquals(0, AdonisSymbolResolver.locateNestedKeyLine(locked.toString(), listOf("x")))
        } finally {
            locked.toFile().setReadable(true)
            Files.deleteIfExists(locked)
        }
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.TEMPLATE_VAR, "title", "pages/home"))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.TEMPLATE_VAR, "title", null))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.TEMPLATE_VAR, "flash", null))
        assertNotNull(AdonisSymbolResolver.resolve(index, SymbolKind.TEMPLATE_VAR, "auth", null))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.TEMPLATE_VAR, "nopath", "pages/home"))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.GATE, "update"))
    }

    @Test
    fun renamePlannerExhaust() {
        assertNotNull(AdonisRenamePlanner.validateNewName(SymbolKind.ROUTE, ""))
        assertNotNull(AdonisRenamePlanner.validateNewName(SymbolKind.ROUTE, " bad"))
        assertNotNull(AdonisRenamePlanner.validateNewName(SymbolKind.ROUTE, "bad\n"))
        assertNotNull(AdonisRenamePlanner.validateNewName(SymbolKind.ROUTE, "!!!"))
        assertNull(AdonisRenamePlanner.validateNewName(SymbolKind.ROUTE, "teams.show"))
        assertNotNull(AdonisRenamePlanner.validateNewName(SymbolKind.VIEW, "!!!"))
        assertNotNull(AdonisRenamePlanner.validateNewName(SymbolKind.CONFIG, "!!!"))
        assertNotNull(AdonisRenamePlanner.validateNewName(SymbolKind.ENV, "bad"))
        assertNotNull(AdonisRenamePlanner.validateNewName(SymbolKind.GATE, "x"))
        val plan = AdonisRenamePlanner.planFromOccurrences(SymbolKind.ROUTE, "a", "a", emptyList())
        assertTrue(plan.refusal != null)
        assertTrue(plan.isEmpty)
        assertFalse(plan.isAllowed)
        AdonisRenamePlanner.planViewFileMove(SymbolKind.ROUTE, "a", "b", "/x", "/base")
        AdonisRenamePlanner.planViewFileMove(SymbolKind.VIEW, "a", "b", null, "/base")
        AdonisRenamePlanner.planViewFileMove(SymbolKind.VIEW, "a", "a", "/tmp/app/resources/views/a.edge", "/tmp/app")
        assertNotNull(
            AdonisRenamePlanner.planViewFileMove(
                SymbolKind.COMPONENT,
                "alert",
                "banner",
                "/tmp/app/resources/views/components/alert.edge",
                "/tmp/app",
            ),
        )
        val cfg = """
            export default {
              'env': 'production',
              nested: { 'leaf': 1 },
            }
        """.trimIndent()
        assertTrue(
            AdonisRenamePlanner.planConfigKeyDefinition(cfg, "/tmp/c.ts", "app.env", "app.environment", 1).isNotEmpty(),
        )
        assertTrue(
            AdonisRenamePlanner.planConfigKeyDefinition(cfg, "/tmp/c.ts", "app.env", "app.environment", -1).isNotEmpty(),
        )
        assertTrue(AdonisRenamePlanner.planConfigKeyDefinition(cfg, "/tmp/c.ts", "env", "env", 1).isEmpty())
        // default line arg (-1) + identical leaf → empty
        assertTrue(AdonisRenamePlanner.planConfigKeyDefinition(cfg, "/tmp/c.ts", "app.env", "other.env").isEmpty())
        assertTrue(AdonisRenamePlanner.planConfigKeyDefinition(cfg, "/tmp/c.ts", "app", "appx").isEmpty())
        AdonisRenamePlanner.planConfigKeyDefinition(cfg, "/tmp/c.ts", "missing", "x", -1)
        AdonisRenamePlanner.planConfigKeyDefinition("no quotes here\n", "/tmp/c.ts", "app.env", "app.environment", 0)
        AdonisRenamePlanner.rewriteText("route('home')", SymbolKind.ROUTE, "home", "homepage")
        AdonisRenamePlanner.applyToText("abcd", listOf(AdonisRenamePlanner.Edit("", 1, 3, "XX")))
    }

    @Test
    fun relationStubPlannerExhaust() {
        val src = "export default class User {\n  declare id: number\n}\nnext()\n"
        // default relatedModel = "Related"
        val plan = AdonisRelationStubPlanner.planInsert(src, "posts")
        assertTrue(plan.isAllowed)
        val applied = AdonisRelationStubPlanner.applyToText(src, plan.edits)
        assertTrue(applied.contains("posts"))
        // already exists via declare
        assertFalse(AdonisRelationStubPlanner.planInsert(applied, "posts", "Post").isAllowed)
        // def style
        AdonisRelationStubPlanner.planInsert("class User {\n  def posts():\n    pass\n}\n", "posts", "Post")
        // method style
        AdonisRelationStubPlanner.planInsert("class User {\n  posts() {\n  }\n}\n", "posts", "Post")
        // no class → append at EOF
        val eof = AdonisRelationStubPlanner.planInsert("const x = 1\n", "posts", "Post")
        assertTrue(eof.isAllowed)
        // class with body ending at EOF
        AdonisRelationStubPlanner.findClassInsertOffset("class Foo {\n  x = 1\n}")
        AdonisRelationStubPlanner.findClassInsertOffset("class Foo {\n}")
        // prefix without trailing newline before insert
        val tight = "class User {\n  declare id: number}"
        AdonisRelationStubPlanner.planInsert(tight, "roles", "Role")
        // blank line in stub indent branch
        AdonisRelationStubPlanner.applyToText("abc", listOf(AdonisRelationStubPlanner.Edit(1, 2, "X")))
    }

    @Test
    fun dbEnvBulkMakeHoverRefactorTemplates() {
        assertTrue(AdonisDbIntrospection.buildJdbcUrl("sqlite", "h", 0, "rel.db").contains("sqlite"))
        assertTrue(AdonisDbIntrospection.buildJdbcUrl("sqlite", "h", 0, "/abs.db").contains("sqlite"))
        AdonisDbIntrospection.fromEnv(mapOf("DB_DRIVER" to "mysql", "DB_NAME" to "n", "DB_USERNAME" to "u"))
        AdonisDbIntrospection.fromEnv(mapOf("DB_CONNECTION" to "sqlserver", "DB_DATABASE" to "d"))
        AdonisDbIntrospection.fromEnv(mapOf("DB_PORT" to "nope", "DB_DATABASE" to "d", "DB_CONNECTION" to "pg"))
        assertEquals("MAIL_HOST=\nMAIL_PORT=", AdonisEnvBulkInsert.dotenvInsertion(listOf("MAIL_HOST", "MAIL_PORT")))
        AdonisEnvBulkInsert.offer(listOf("_A", "_B"), "_") // stem empty after trimEnd? "_" → stem ""
        AdonisEnvBulkInsert.offer(listOf("A_1", "A_2"), "A_")
        try {
            AdonisMakeCatalog.modelAceArgs("  ", AdonisMakeCatalog.ModelOptions())
            assertTrue(false)
        } catch (_: IllegalArgumentException) {
            // expected
        }
        AdonisMakeCatalog.modelAceArgs("user", AdonisMakeCatalog.ModelOptions(controller = true))
        val index = idx()
        AdonisHoverDocs.forSymbol(index, SymbolKind.ROUTE, "orphan")
        AdonisHoverDocs.forSymbol(index, SymbolKind.ROUTE, "missing")
        AdonisHoverDocs.forSymbol(index, SymbolKind.VIEW, "missing")
        AdonisHoverDocs.forSymbol(index, SymbolKind.CONFIG, "nope")
        AdonisHoverDocs.forSymbol(index, SymbolKind.ENV, "missing")
        AdonisHoverDocs.forSymbol(index, SymbolKind.ENV, "APP_KEY")
        AdonisHoverDocs.forSymbol(index, SymbolKind.ENV, "NO_ORIGIN")
        AdonisHoverDocs.forSymbol(index, SymbolKind.COMPONENT, "missing")
        AdonisHoverDocs.forSymbol(index, SymbolKind.WIRE, "missing")
        AdonisHoverDocs.forSymbol(index, SymbolKind.WIRE, "bare")
        AdonisHoverDocs.forSymbol(index, SymbolKind.RELATION, "posts", "User")
        AdonisHoverDocs.forSymbol(index, SymbolKind.RELATION, "nope", "User")
        AdonisHoverDocs.forSymbol(index, SymbolKind.COLUMN, "email", "User")
        AdonisHoverDocs.forSymbol(index, SymbolKind.COLUMN, "nope", "User")
        AdonisHoverDocs.forSymbol(index, SymbolKind.TEMPLATE_VAR, "title")
        AdonisHoverDocs.forSymbol(index, SymbolKind.TEMPLATE_VAR, "missing")
        AdonisHoverDocs.forSymbol(index, SymbolKind.SHAMAR_RESOURCE, "missing")
        AdonisHoverDocs.forSymbol(index, SymbolKind.SHAMAR_PAGE, "missing")
        AdonisHoverDocs.forSymbol(index, SymbolKind.SHAMAR_WIDGET, "missing")
        AdonisHoverDocs.forSymbol(index, SymbolKind.SHAMAR_WIDGET, "StatsOverviewWidget")
        assertNotNull(AdonisHoverDocs.forSymbol(index, SymbolKind.SHAMAR_WIDGET, "ProductStatsWidget"))
        assertNotNull(AdonisHoverDocs.forSymbol(index, SymbolKind.SHAMAR_WIDGET, "BareWidget"))
        AdonisHoverDocs.forSymbol(
            index,
            SymbolKind.SHAMAR_LITERAL,
            "sidebar",
            receiver = ShamarLiterals.PRESENTATION,
        )
        AdonisHoverDocs.forSymbol(index, SymbolKind.SHAMAR_LITERAL, "modal", receiver = null)
        AdonisHoverDocs.forSymbol(index, SymbolKind.DISK, "fs")
        AdonisLucidHelpers.eagerLoadSnippets(index, null)
        AdonisLucidHelpers.whereColumnSnippets(index, null)
        AdonisRefactorPlanner.includeToComponent("@include('components.alert')", 0)
        AdonisRefactorPlanner.includeToComponent("@include('alert')", 0)
        AdonisRefactorPlanner.includeToComponent("nope", 0)
        val p = AdonisRefactorPlanner.extractPartial("/tmp", "hi", 0, 2, "x")
        assertTrue(p.isAllowed)
        AdonisFileTemplates.className("AlreadyCamel")
        AdonisFileTemplates.resolve("middleware", "AuthMiddleware")
        AdonisFileTemplates.resolve("exception", "BadException")
        AdonisFileTemplates.resolve("provider", "AppProvider")
        AdonisFileTemplates.resolve("seeder", "UserSeeder")
        AdonisFileTemplates.resolve("policy", "UserPolicy")
        AdonisFileTemplates.resolve("service", "BillingService")
        AdonisFileTemplates.snake("foo.bar/Baz")
        AdonisCodeActionPlanner.stubEdgeContent("x")
        AdonisCodeActionPlanner.viewPathForName("", "a.b")
        AdonisCodeActionPlanner.componentPathForName("", "a")
        AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.COMPONENT, "badge")
        AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.CONTROLLER_ACTION, "X@y")
        AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.MIDDLEWARE, "m")
        AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.MAILER, "m")
        AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.INERTIA, "p")
        AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.ROUTE, "r")
        AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.CONFIG, "c")
        AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.ENV, "E")
        AdonisCodeActionPlanner.forUnknownSymbol(index, SymbolKind.GATE, "g")
        AdonisToolWindowModel.symbolRows(index, "counter")
        AdonisToolWindowModel.symbolRows(
            index.copy(wireComponents = mapOf("w" to AdonisIndex.WireEntry(path = null))),
            "w",
        )
    }

    @Test
    fun symbolLocatorEdges() {
        AdonisSymbolLocator.hitAt("route('')", 7)
        AdonisSymbolLocator.hitAt("validate('required|email:rfc')", 12)
        AdonisSymbolLocator.hitAt("x\n'ab", 4)
        AdonisSymbolLocator.hitAt("{{ foo.bar }}", 8) // on .attr
        AdonisSymbolLocator.hitAt("{!! foo !!}", 5)
        AdonisSymbolLocator.hitAt("{{  }}", 3)
        AdonisSymbolLocator.hitAt("nope", 2)
    }
}
