package com.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Exhaustive unit coverage for index helpers, resolver, locator, catalog, and
 * filesystem call-site search — drives the 98% Kover gate.
 */
class AdonisCoverageExhaustTest {
    private fun sampleIndex(): AdonisIndex {
        val json = """
            {
              "base_path": "/tmp/app",
              "ok": true,
              "views": {
                "welcome": "/tmp/app/resources/views/welcome.edge",
                "components.alert": "/tmp/app/resources/views/components/alert.edge"
              },
              "routes": {
                "home": {"uri": "/", "methods": ["GET"], "path": "/tmp/app/routes/web.py", "line": 12}
              },
              "config_keys": ["app.name", "app.env"],
              "config_files": {"app": "/tmp/app/config/app.py"},
              "config_locations": {
                "app.env": {"path": "/tmp/app/config/app.py", "line": 17}
              },
              "translation_keys": ["messages.hello"],
              "middleware_aliases": ["web"],
              "env_keys": {
                "APP_KEY": {
                  "path": null, "line": 0, "kind": "config", "detail": "",
                  "used_by": ["config/app.py:22"]
                },
                "APP_URL": {"path": "/tmp/app/.env", "line": 4, "kind": "env", "detail": "url"}
              },
              "env_options": {
                "CACHE_STORE": ["file", "redis"],
                "BROADCAST_CONNECTION": ["log", "redis"]
              },
              "tables": {
                "users": {
                  "path": "/tmp/mig.py", "line": 1,
                  "columns": {"id": {"path": "/tmp/mig.py", "line": 2}, "email": {"path": "/tmp/mig.py", "line": 3}},
                  "detail": "users"
                }
              },
              "model_metadata": {
                "User": {
                  "module": "user", "path": "/tmp/user.py",
                  "fillable": ["email"], "casts": {"id": "int"},
                  "relations": ["posts"], "relation_lines": {"posts": 10}
                }
              },
              "relations": {"User": ["posts"]},
              "casts": ["int"],
              "components": {"alert": "/tmp/alert.edge"},
              "gates": ["update"],
              "disks": ["local"],
              "queues": ["sync"],
              "caches": ["file"],
              "mailers": ["smtp"],
              "inertia_pages": ["Dashboard"],
              "ace_commands": ["serve"],
              "validation_rules": ["required", "email"],
              "directives": ["if"],
              "view_helpers": [{"name": "auth", "path": "/tmp/h.py", "line": 1, "kind": "helper"}],
              "view_shared": {"csrf": "token"},
              "view_data": {
                "welcome": {"title": {"path": "/tmp/c.py", "line": 5, "kind": "data"}}
              },
              "vite_entries": {"resources/js/app.js": "/tmp/app.js"},
              "controller_actions": {"WelcomeController": ["index"]}
            }
        """.trimIndent()
        return AdonisIndexLoader.parse(json)
    }

    @Test
    fun knownCoversAllKinds() {
        val index = sampleIndex()
        assertTrue(index.known(SymbolKind.ROUTE, "home"))
        assertTrue(index.known(SymbolKind.VIEW, "welcome"))
        assertTrue(index.known(SymbolKind.CONFIG, "app.env"))
        assertTrue(index.known(SymbolKind.TRANSLATION, "messages.hello"))
        assertTrue(index.known(SymbolKind.MIDDLEWARE, "web"))
        assertTrue(index.known(SymbolKind.ENV, "APP_KEY"))
        assertTrue(index.known(SymbolKind.ENV_VALUE, "anything"))
        assertTrue(index.known(SymbolKind.TABLE, "users"))
        assertTrue(index.known(SymbolKind.GATE, "update"))
        assertTrue(index.known(SymbolKind.COMPONENT, "alert"))
        assertTrue(index.known(SymbolKind.VALIDATION, "required"))
        assertTrue(index.known(SymbolKind.VALIDATION, "email:rfc"))
        assertTrue(index.known(SymbolKind.DISK, "local"))
        assertTrue(index.known(SymbolKind.QUEUE, "sync"))
        assertTrue(index.known(SymbolKind.CACHE, "file"))
        assertTrue(index.known(SymbolKind.MAILER, "smtp"))
        assertTrue(index.known(SymbolKind.INERTIA, "Dashboard"))
        assertTrue(index.known(SymbolKind.ACE, "serve"))
        assertTrue(index.known(SymbolKind.VITE, "resources/js/app.js"))
        assertTrue(index.known(SymbolKind.CAST, "int"))
        assertTrue(index.known(SymbolKind.TEMPLATE_VAR, "title"))
        assertTrue(index.known(SymbolKind.COLUMN, "email")) // soft-known
        assertTrue(index.known(SymbolKind.CONTROLLER_ACTION, "WelcomeController"))
        assertTrue(index.known(SymbolKind.CONTROLLER_ACTION, "WelcomeController@index"))
        assertFalse(index.known(SymbolKind.CONTROLLER_ACTION, "Missing@index"))
        assertFalse(index.known(SymbolKind.ROUTE, "missing"))
        assertFalse(index.known(SymbolKind.TRANSLATION, "nope"))
        assertEquals(AdonisIndex.empty("x").error, "x")
    }

    @Test
    fun envOptionsAliasesAndViewPath() {
        val index = sampleIndex()
        assertEquals(listOf("file", "redis"), index.optionsForEnvKey("CACHE_STORE"))
        assertEquals(listOf("file", "redis"), index.optionsForEnvKey("CACHE_DRIVER"))
        assertEquals(listOf("log", "redis"), index.optionsForEnvKey("BROADCAST_DRIVER"))
        assertTrue(index.optionsForEnvKey("UNKNOWN").isEmpty())
        assertEquals("welcome", index.viewNameForPath("/tmp/app/resources/views/welcome.edge"))
        assertEquals("welcome", index.viewNameForPath("resources/views/welcome.edge"))
        assertNull(index.viewNameForPath(""))
        assertNull(index.viewNameForPath("/nope"))
    }

    @Test
    fun resolverBranches() {
        val index = sampleIndex()
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.ROUTE, ""))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.ENV_VALUE, "x"))
        assertNull(AdonisSymbolResolver.resolve(index, SymbolKind.GATE, "update"))
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/config/app.py", 21),
            AdonisSymbolResolver.resolve(index, SymbolKind.ENV, "APP_KEY"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/.env", 4),
            AdonisSymbolResolver.resolve(index, SymbolKind.ENV, "APP_URL"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/mig.py", 1),
            AdonisSymbolResolver.resolve(index, SymbolKind.TABLE, "users"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/mig.py", 3),
            AdonisSymbolResolver.resolveColumn(index, "user", "email"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/mig.py", 3),
            AdonisSymbolResolver.resolveColumn(index, null, "email"),
        )
        assertNull(AdonisSymbolResolver.resolveColumn(index, null, ""))
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/alert.edge", 0),
            AdonisSymbolResolver.resolve(index, SymbolKind.COMPONENT, "alert"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app.js", 0),
            AdonisSymbolResolver.resolve(index, SymbolKind.VITE, "resources/js/app.js"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/user.py", 10),
            AdonisSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", receiver = "User"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/user.py", 10),
            AdonisSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", receiver = "user"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/h.py", 1),
            AdonisSymbolResolver.resolve(index, SymbolKind.TEMPLATE_VAR, "auth"),
        )
        // config stem-only via files map when locations miss
        val thin = index.copy(configLocations = emptyMap())
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/config/app.py", 0),
            AdonisSymbolResolver.resolve(thin, SymbolKind.CONFIG, "app"),
        )
    }

    @Test
    fun completionCatalogAllKinds() {
        val index = sampleIndex()
        fun kind(k: SymbolKind, recv: String? = null) =
            AdonisCompletionCatalog.symbolsFor(index, CallSiteDetector.Site(k, "", receiver = recv))

        assertTrue(kind(SymbolKind.ROUTE).any { it.first == "home" })
        assertTrue(kind(SymbolKind.VIEW).any { it.first == "welcome" })
        assertTrue(kind(SymbolKind.CONFIG).any { it.first == "app.env" })
        assertTrue(kind(SymbolKind.TRANSLATION).any { it.first == "messages.hello" })
        assertTrue(kind(SymbolKind.MIDDLEWARE).any { it.first == "web" })
        assertTrue(kind(SymbolKind.ENV).any { it.first == "APP_KEY" })
        assertTrue(kind(SymbolKind.ENV_VALUE, "CACHE_STORE").any { it.first == "redis" })
        assertTrue(kind(SymbolKind.ENV_VALUE).isEmpty())
        assertTrue(kind(SymbolKind.TABLE).any { it.first == "users" })
        assertTrue(kind(SymbolKind.COLUMN, "users").any { it.first == "email" && it.second.contains("column") })
        // No receiver → no column dump (avoids Blueprint `table.` noise)
        assertTrue(kind(SymbolKind.COLUMN).isEmpty())
        assertTrue(kind(SymbolKind.RELATION, "User").contains("posts" to "relation"))
        assertTrue(kind(SymbolKind.RELATION).contains("posts" to "relation"))
        assertTrue(kind(SymbolKind.CAST).contains("int" to "cast"))
        assertTrue(kind(SymbolKind.GATE).contains("update" to "gate"))
        assertTrue(kind(SymbolKind.COMPONENT).contains("alert" to "component"))
        assertTrue(kind(SymbolKind.VALIDATION).contains("required" to "rule"))
        assertTrue(kind(SymbolKind.DISK).contains("local" to "disk"))
        assertTrue(kind(SymbolKind.QUEUE).contains("sync" to "queue"))
        assertTrue(kind(SymbolKind.CACHE).contains("file" to "cache"))
        assertTrue(kind(SymbolKind.MAILER).contains("smtp" to "mailer"))
        assertTrue(kind(SymbolKind.INERTIA).contains("Dashboard" to "inertia"))
        assertTrue(kind(SymbolKind.ACE).contains("serve" to "ace"))
        assertTrue(kind(SymbolKind.VITE).any { it.second == "asset" })
        assertTrue(kind(SymbolKind.DIRECTIVE).contains("if" to "directive"))
        assertTrue(kind(SymbolKind.DIRECTIVE).any { it.first == "each" && it.second == "loop" })
        assertTrue(kind(SymbolKind.DIRECTIVE).any { it.first == "loop" })
        assertTrue(kind(SymbolKind.TEMPLATE_VAR).any { it.first == "title" })
        assertTrue(kind(SymbolKind.CONTROLLER_ACTION).contains("index" to "action"))
        assertTrue(AdonisCompletionCatalog.columnsFor(index, "User").contains("email"))
        assertTrue(AdonisCompletionCatalog.relationsFor(index, "User").contains("posts"))
    }

    @Test
    fun locatorAndDetectorExtras() {
        assertNull(AdonisSymbolLocator.hitAt("abc", -1))
        assertNull(AdonisSymbolLocator.hitAt("nope", 2))
        val raw = """{!! body !!}"""
        val hit = AdonisSymbolLocator.hitAt(raw, raw.indexOf("body") + 1)
        assertNotNull(hit)
        assertEquals(SymbolKind.TEMPLATE_VAR, hit!!.kind)

        assertEquals(SymbolKind.COMPONENT, CallSiteDetector.detect("""@component('al""")!!.kind)
        assertEquals(SymbolKind.ROUTE, CallSiteDetector.detect("""@route("hom""")!!.kind)
        assertEquals(SymbolKind.TRANSLATION, CallSiteDetector.detect("""@lang('msg""")!!.kind)
        assertEquals(SymbolKind.CAST, CallSiteDetector.detect("""casts = {"x": "dat""")!!.kind)
        val ctrlSite = CallSiteDetector.detect("""[WelcomeController, "ind""")!!
        assertEquals(SymbolKind.CONTROLLER_ACTION, ctrlSite.kind)
        assertEquals("WelcomeController", ctrlSite.receiver)
        assertEquals(SymbolKind.ACE, CallSiteDetector.detect("""ace("ser""")!!.kind)
        assertEquals(SymbolKind.VITE, CallSiteDetector.detect("""vite("resources""")!!.kind)
        assertEquals(SymbolKind.INERTIA, CallSiteDetector.detect("""render("Dash""")!!.kind)
        assertTrue(CallSiteDetector.detect("""validate({"x": "required|em""")!!.validationSegment)
        assertNull(CallSiteDetector.detect("# APP_KEY", dotenvFile = true))
        assertEquals(
            SymbolKind.ENV,
            CallSiteDetector.detect("export APP_KE", dotenvFile = true)!!.kind,
        )
    }

    @Test
    fun findUsagesWalksAppTree() {
        val root = Files.createTempDirectory("adonis-fu")
        Files.createDirectories(root.resolve("start"))
        Files.createDirectories(root.resolve("resources/views"))
        Files.writeString(
            root.resolve("start/routes.ts"),
            "return route(\"home\")\n",
        )
        Files.writeString(
            root.resolve("resources/views/welcome.edge"),
            "@include('auth.login')\n<x-alert/>\n",
        )
        Files.writeString(root.resolve(".env"), "APP_KEY=secret\n")
        // skipped subtree
        Files.createDirectories(root.resolve("start/node_modules"))
        Files.writeString(root.resolve("start/node_modules/x.ts"), "route(\"home\")")

        val routes = AdonisCallSiteSearcher.findUsages(root, SymbolKind.ROUTE, "home")
        assertEquals(1, routes.size)
        val views = AdonisCallSiteSearcher.findUsages(root, SymbolKind.VIEW, "auth.login")
        assertEquals(1, views.size)
        val comps = AdonisCallSiteSearcher.findUsages(root, SymbolKind.COMPONENT, "alert")
        assertEquals(1, comps.size)
        val envs = AdonisCallSiteSearcher.findUsages(root, SymbolKind.ENV, "APP_KEY")
        assertTrue(envs.isNotEmpty())
        assertTrue(AdonisCallSiteSearcher.findUsages(root, SymbolKind.ROUTE, "").isEmpty())
        assertTrue(AdonisCallSiteSearcher.findUsages(root, SymbolKind.GATE, "x").isEmpty())
    }

    @Test
    fun indexCommandAndEmptyParse() {
        val root = Files.createTempDirectory("adonis-cmd")
        val indexer = root.resolve("index.mjs")
        Files.writeString(indexer, "// stub\n")
        val cmd = AdonisIndexLoader.buildIndexCommand(root, indexer)
        assertTrue(cmd.commandLineString.contains("--json"))
        assertTrue(cmd.commandLineString.contains("--path"))
        assertTrue(cmd.commandLineString.contains("index.mjs"))

        val bad = AdonisIndexLoader.parse("""{"ok": false, "error": "boom"}""")
        assertFalse(bad.ok)
        assertEquals("boom", bad.error)
    }

    @Test
    fun detectorRemainingBranches() {
        // Non-dotenv ${…} interpolation
        assertEquals(SymbolKind.ENV, CallSiteDetector.detect("x=\${APP_")!!.kind)
        assertEquals(SymbolKind.GATE, CallSiteDetector.detect("""@can("upd""")!!.kind)
        assertEquals(SymbolKind.GATE, CallSiteDetector.detect("""@cannot("upd""")!!.kind)
        assertEquals(SymbolKind.VITE, CallSiteDetector.detect("""@asset("js/""")!!.kind)
        assertEquals(SymbolKind.VITE, CallSiteDetector.detect("""@vite("res""")!!.kind)
        assertEquals(SymbolKind.VIEW, CallSiteDetector.detect("""@includeUnless('x""")!!.kind)
        assertEquals(SymbolKind.TRANSLATION, CallSiteDetector.detect("""__("msg""")!!.kind)
        assertEquals(SymbolKind.TRANSLATION, CallSiteDetector.detect("""trans("msg""")!!.kind)
        assertEquals(SymbolKind.MIDDLEWARE, CallSiteDetector.detect("""middleware("web""")!!.kind)
        assertEquals(SymbolKind.DISK, CallSiteDetector.detect("""disk("loc""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.where("ema""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.order_by("cre""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.select("id""")!!.kind)
        assertEquals(SymbolKind.TABLE, CallSiteDetector.detect("""DB.table("use""")!!.kind)
        assertNull(CallSiteDetector.detect("plain text here"))
        // Quoted dotenv values → stripDotenvValuePrefix
        assertEquals("re", CallSiteDetector.detect("APP_KEY=\"re", dotenvFile = true)!!.prefix)
        assertEquals("", CallSiteDetector.detect("APP_KEY='", dotenvFile = true)!!.prefix)
        assertEquals("red", CallSiteDetector.detect("APP_KEY=\"red\"", dotenvFile = true)!!.prefix)
        assertEquals("red", CallSiteDetector.detect("APP_KEY=\"red", dotenvFile = true)!!.prefix)
    }

    @Test
    fun resolverRemainingBranches() {
        val index = sampleIndex()
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/mig.py", 3),
            AdonisSymbolResolver.resolve(index, SymbolKind.COLUMN, "email", receiver = "users"),
        )
        // component via views["components.$name"] when components map misses
        val viaView = index.copy(components = emptyMap())
        assertEquals(
            AdonisSymbolResolver.Target(
                "/tmp/app/resources/views/components/alert.edge",
                0,
            ),
            AdonisSymbolResolver.resolve(viaView, SymbolKind.COMPONENT, "alert"),
        )
        assertNull(AdonisSymbolResolver.resolve(viaView.copy(views = emptyMap()), SymbolKind.COMPONENT, "alert"))
        // plural strip: userses? tableHint posts → post
        val tables = index.tables + mapOf(
            "post" to AdonisIndex.TableEntry(
                columns = mapOf("title" to AdonisIndex.Located("/tmp/p.py", 1)),
                path = "/tmp/p.py",
            ),
        )
        val withPost = index.copy(tables = tables)
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/p.py", 1),
            AdonisSymbolResolver.resolveColumn(withPost, "posts", "title"),
        )
        assertNull(AdonisSymbolResolver.resolveColumn(index, null, "missing_col"))
        // config nested scan fallback
        val dir = Files.createTempDirectory("cfg")
        val file = dir.resolve("app.py")
        Files.writeString(file, """{"name": "x", "env": "local"}""")
        val cfg = index.copy(
            configLocations = emptyMap(),
            configFiles = mapOf("app" to file.toString()),
        )
        val t = AdonisSymbolResolver.resolve(cfg, SymbolKind.CONFIG, "app.env")
        assertNotNull(t)
        assertEquals(file.toString(), t!!.path)
        // relation via module match
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/user.py", 10),
            AdonisSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", receiver = "app.models.user"),
        )
        // template var with null path skipped
        val noPath = index.copy(
            viewData = mapOf(
                "welcome" to mapOf("title" to AdonisIndex.ViewVarEntry(path = null, line = 1)),
            ),
            viewShared = emptyMap(),
            viewHelpers = emptyMap(),
        )
        assertNull(AdonisSymbolResolver.resolve(noPath, SymbolKind.TEMPLATE_VAR, "title", viewName = "welcome"))
        // locateNestedKeyLine missing file / empty segments
        assertEquals(0, AdonisSymbolResolver.locateNestedKeyLine("/no/such/file.py", listOf("a")))
        assertEquals(0, AdonisSymbolResolver.locateNestedKeyLine(file.toString(), emptyList()))
        // ENV usedBy without basePath
        val noBase = index.copy(basePath = "")
        val env = AdonisSymbolResolver.resolve(noBase, SymbolKind.ENV, "APP_KEY")
        assertNotNull(env)
        assertTrue(env!!.path.contains("config/app.py"))
    }

    @Test
    fun renameAndCatalogRemaining() {
        assertTrue(
            AdonisRenamePlanner.validateNewName(SymbolKind.VIEW, "bad name")!!.contains("View"),
        )
        assertTrue(
            AdonisRenamePlanner.validateNewName(SymbolKind.CONFIG, "bad name")!!.contains("Config"),
        )
        assertEquals(
            "Name unchanged",
            AdonisRenamePlanner.planFromOccurrences(
                SymbolKind.ROUTE,
                "a",
                "a",
                AdonisCallSiteSearcher.findInText("""route("a")""", SymbolKind.ROUTE, "a"),
            ).refusal,
        )
        assertTrue(
            AdonisRenamePlanner.rewriteText("""route("a")""", SymbolKind.ROUTE, "a", "bad name")
                .refusal != null,
        )
        assertTrue(AdonisRenamePlanner.Plan(SymbolKind.ROUTE, "a", "b", emptyList()).isEmpty)
        try {
            AdonisRenamePlanner.applyToText("ab", listOf(AdonisRenamePlanner.Edit("", 0, 5, "x")))
            assertTrue("expected failure", false)
        } catch (_: IllegalArgumentException) {
        }
        val index = sampleIndex()
        // columnsFor via model fillable when table missing
        val noTables = index.copy(tables = emptyMap())
        assertTrue(AdonisCompletionCatalog.columnsFor(noTables, "user").contains("email"))
        assertTrue(AdonisCompletionCatalog.columnsFor(noTables, "user").contains("id"))
    }

    @Test
    fun locatorDottedAttrAndIndexLoaderBranches() {
        val text = "{{ user.name }}"
        val hit = AdonisSymbolLocator.hitAt(text, text.indexOf("name") + 1)
        assertNotNull(hit)
        assertEquals("user", hit!!.name)
        assertNull(AdonisSymbolLocator.hitAt("{{  }}", 3))

        val root = Files.createTempDirectory("idx-cmd")
        val indexer = root.resolve("indexer.mjs")
        Files.writeString(indexer, "// stub\n")
        val cmd = AdonisIndexLoader.buildIndexCommand(root, indexer)
        assertTrue(cmd.commandLineString.contains("indexer.mjs"))
        assertTrue(cmd.commandLineString.contains("--path"))
        assertTrue(cmd.commandLineString.contains("--json"))

        val objList = AdonisIndexLoader.parse(
            """{"ok": true, "config_keys": {"app.name": true}, "casts": 1}""",
        )
        assertTrue(objList.configKeys.contains("app.name"))
        assertTrue(objList.casts.isEmpty())
    }
}
