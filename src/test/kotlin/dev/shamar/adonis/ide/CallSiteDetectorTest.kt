package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSiteDetectorTest {
    @Test
    fun routeCall() {
        val site = CallSiteDetector.detect("""route("hom""")
        assertNotNull(site)
        assertEquals(SymbolKind.ROUTE, site!!.kind)
        assertEquals("hom", site.prefix)
    }

    @Test
    fun viewCall() {
        val site = CallSiteDetector.detect("""view('welcome""")
        assertEquals(SymbolKind.VIEW, site!!.kind)
        assertEquals("welcome", site.prefix)
    }

    @Test
    fun viewRenderCall() {
        val site = CallSiteDetector.detect("""return view.render('pages/auth/sign""")
        assertNotNull(site)
        assertEquals(SymbolKind.VIEW, site!!.kind)
        assertEquals("pages/auth/sign", site.prefix)
    }

    @Test
    fun ctxViewRenderCall() {
        val site = CallSiteDetector.detect("""ctx.view.render('pages/ho""")
        assertEquals(SymbolKind.VIEW, site!!.kind)
        assertEquals("pages/ho", site.prefix)
    }

    @Test
    fun viewRenderSyncCall() {
        val site = CallSiteDetector.detect("""view.renderSync('emails/welcome""")
        assertEquals(SymbolKind.VIEW, site!!.kind)
        assertEquals("emails/welcome", site.prefix)
    }

    @Test
    fun bareRenderStillInertia() {
        val site = CallSiteDetector.detect("""render('Dash""")
        assertEquals(SymbolKind.INERTIA, site!!.kind)
    }

    @Test
    fun configCall() {
        val site = CallSiteDetector.detect("""config("app.na""")
        assertEquals(SymbolKind.CONFIG, site!!.kind)
        assertEquals("app.na", site.prefix)
    }

    @Test
    fun gateCan() {
        val site = CallSiteDetector.detect("""can("edit-""")
        assertEquals(SymbolKind.GATE, site!!.kind)
    }

    @Test
    fun preloadRelation() {
        val site = CallSiteDetector.detect("""Post.query().preload('comm""")
        assertEquals(SymbolKind.RELATION, site!!.kind)
        assertEquals("comm", site.prefix)
    }

    @Test
    fun envGet() {
        val site = CallSiteDetector.detect("""env.get('APP_""")
        assertEquals(SymbolKind.ENV, site!!.kind)
        assertEquals("APP_", site.prefix)
    }

    @Test
    fun wireDirective() {
        val site = CallSiteDetector.detect("""@wire('count""")
        assertEquals(SymbolKind.WIRE, site!!.kind)
        assertEquals("count", site.prefix)
    }

    @Test
    fun shamarLiteralSites() {
        val openIn = CallSiteDetector.detect(""".openIn('emb""")
        assertEquals(SymbolKind.SHAMAR_LITERAL, openIn!!.kind)
        assertEquals(ShamarLiterals.OPEN_IN, openIn.receiver)
        assertEquals("emb", openIn.prefix)

        val presentation = CallSiteDetector.detect(""".presentation('side""")
        assertEquals(SymbolKind.SHAMAR_LITERAL, presentation!!.kind)
        assertEquals(ShamarLiterals.PRESENTATION, presentation.receiver)

        val pageMode = CallSiteDetector.detect("""static viewMode = 'full""")
        assertEquals(SymbolKind.SHAMAR_LITERAL, pageMode!!.kind)
        assertEquals(ShamarLiterals.PAGE_MODE, pageMode.receiver)

        val color = CallSiteDetector.detect(""".chartColor('dang""")
        assertEquals(SymbolKind.SHAMAR_LITERAL, color!!.kind)
        assertEquals(ShamarLiterals.STAT_COLOR, color.receiver)

        assertEquals(SymbolKind.SHAMAR_FIELD, CallSiteDetector.detect("""RelationTable.make('ord""")!!.kind)
        assertTrue(ShamarLiterals.valuesFor(ShamarLiterals.OPEN_IN).contains("download"))
        assertEquals("DialogPresentation", ShamarLiterals.detailFor(ShamarLiterals.PRESENTATION))
        assertTrue(ShamarLiterals.valuesFor("unknown").isEmpty())
    }

    @Test
    fun routeAs() {
        val site = CallSiteDetector.detect(""".as('posts.""")
        assertEquals(SymbolKind.ROUTE, site!!.kind)
        assertEquals("posts.", site.prefix)
    }

    @Test
    fun validationRule() {
        val site = CallSiteDetector.detect("""validate({"email": "requ""")
        assertEquals(SymbolKind.VALIDATION, site!!.kind)
        assertEquals("requ", site.prefix)
    }

    @Test
    fun edgeInclude() {
        val site = CallSiteDetector.detect("""@include('layouts.""")
        assertEquals(SymbolKind.VIEW, site!!.kind)
    }

    @Test
    fun edgeComponent() {
        val site = CallSiteDetector.detect("""<x-alert.""")
        assertEquals(SymbolKind.COMPONENT, site!!.kind)
        assertEquals("alert.", site.prefix)
    }

    @Test
    fun edgeDirective() {
        val site = CallSiteDetector.detect("""  @en""")
        assertEquals(SymbolKind.DIRECTIVE, site!!.kind)
        assertEquals("en", site.prefix)
    }

    @Test
    fun envCall() {
        val site = CallSiteDetector.detect("""env("APP_""")
        assertEquals(SymbolKind.ENV, site!!.kind)
    }

    @Test
    fun templateVarEcho() {
        val site = CallSiteDetector.detect("""{{ tit""")
        assertEquals(SymbolKind.TEMPLATE_VAR, site!!.kind)
        assertEquals("tit", site.prefix)
    }

    @Test
    fun dotenvBareKey() {
        val site = CallSiteDetector.detect("QUEUE_CON", dotenvFile = true)
        assertEquals(SymbolKind.ENV, site!!.kind)
        assertEquals("QUEUE_CON", site.prefix)
    }

    @Test
    fun dotenvValueOptions() {
        val site = CallSiteDetector.detect("QUEUE_CONNECTION=re", dotenvFile = true)
        assertEquals(SymbolKind.ENV_VALUE, site!!.kind)
        assertEquals("QUEUE_CONNECTION", site.receiver)
        assertEquals("re", site.prefix)
    }

    @Test
    fun envDefaultSecondArg() {
        val site = CallSiteDetector.detect("""env("QUEUE_CONNECTION", "sy""")
        assertEquals(SymbolKind.ENV_VALUE, site!!.kind)
        assertEquals("QUEUE_CONNECTION", site.receiver)
        assertEquals("sy", site.prefix)
    }
}

class AdonisSymbolLocatorTest {
    @Test
    fun configLiteralHit() {
        val text = """x = config("app.env")"""
        val offset = text.indexOf("env") + 1
        val hit = AdonisSymbolLocator.hitAt(text, offset)
        assertNotNull(hit)
        assertEquals(SymbolKind.CONFIG, hit!!.kind)
        assertEquals("app.env", hit.name)
    }

    @Test
    fun templateVarHit() {
        val text = """Hello {{ title }} world"""
        val offset = text.indexOf("title") + 2
        val hit = AdonisSymbolLocator.hitAt(text, offset)
        assertNotNull(hit)
        assertEquals(SymbolKind.TEMPLATE_VAR, hit!!.kind)
        assertEquals("title", hit.name)
    }
}

class AdonisIndexParseTest {
    @Test
    fun parsesFixture() {
        val json = """
            {
              "base_path": "/tmp/app",
              "ok": true,
              "error": null,
              "views": {"welcome": "/tmp/app/resources/views/welcome.edge"},
              "routes": {
                "home": {"name": "home", "uri": "/", "methods": ["GET"], "path": "/tmp/app/routes/web.py", "line": 12}
              },
              "config_keys": ["app.name", "app.env"],
              "config_files": {"app": "/tmp/app/config/app.py"},
              "config_locations": {
                "app.env": {"path": "/tmp/app/config/app.py", "line": 17},
                "app.name": {"path": "/tmp/app/config/app.py", "line": 16}
              },
              "translation_keys": ["messages.hello"],
              "middleware_aliases": ["web", "auth"],
              "env_keys": {
                "APP_KEY": {
                  "name": "APP_KEY",
                  "path": "/tmp/app/.env",
                  "line": 3,
                  "kind": "env",
                  "detail": "Set in .env",
                  "used_by": ["config/app.py:22"]
                }
              },
              "env_options": {
                "QUEUE_CONNECTION": ["database", "redis", "sync"],
                "QUEUE_DRIVER": ["database", "redis", "sync"]
              },
              "tables": {
                "users": {
                  "name": "users",
                  "path": "/tmp/app/database/migrations/0001_users.py",
                  "line": 5,
                  "columns": {
                    "id": {"name": "id", "path": "/tmp/app/database/migrations/0001_users.py", "line": 6},
                    "email": {"name": "email", "path": "/tmp/app/database/migrations/0001_users.py", "line": 7}
                  },
                  "detail": "users"
                }
              },
              "model_metadata": {
                "User": {
                  "module": "user",
                  "path": "/tmp/app/app/models/user.py",
                  "fillable": ["email"],
                  "casts": {"id": "int"},
                  "relations": ["posts"],
                  "relation_lines": {"posts": 42}
                }
              },
              "relations": {"User": ["posts"]},
              "casts": ["int", "datetime"],
              "components": {"alert": "/tmp/x"},
              "gates": ["update"],
              "disks": ["local"],
              "queues": ["sync"],
              "caches": ["file"],
              "mailers": ["smtp"],
              "inertia_pages": ["Dashboard"],
              "ace_commands": ["serve", "ide:index"],
              "validation_rules": ["required", "email"],
              "directives": ["if", "endif"],
              "view_helpers": [{"name": "auth", "path": "/tmp/helpers.py", "line": 9, "kind": "helper"}],
              "view_shared": {
                "csrf_token": {"name": "csrf_token", "path": "/tmp/auth.py", "line": 5, "kind": "shared"}
              },
              "view_data": {
                "welcome": {
                  "title": {
                    "name": "title",
                    "kind": "data",
                    "path": "/tmp/app/app/http/controllers/welcome_controller.py",
                    "line": 22
                  }
                }
              },
              "vite_entries": {"resources/js/app.js": "/tmp/app.js"},
              "controller_actions": {"WelcomeController": ["index"]}
            }
        """.trimIndent()
        val index = AdonisIndexLoader.parse(json)
        assertTrue(index.ok)
        assertTrue(index.views.containsKey("welcome"))
        assertEquals("/", index.routes["home"]!!.uri)
        assertEquals("/tmp/app/routes/web.py", index.routes["home"]!!.path)
        assertEquals(12, index.routes["home"]!!.line)
        assertTrue(index.configKeys.contains("app.name"))
        assertEquals("/tmp/app/config/app.py", index.configFiles["app"])
        assertEquals(17, index.configLocations["app.env"]!!.line)
        assertTrue(index.tables["users"]!!.columns.containsKey("email"))
        assertEquals(listOf("posts"), index.relations["User"])
        assertEquals(42, index.modelMetadata["User"]!!.relationLines["posts"])
        assertTrue(index.validationRules.contains("required"))
        assertTrue(index.known(SymbolKind.ROUTE, "home"))
        assertTrue(!index.known(SymbolKind.ROUTE, "missing"))
        assertEquals("welcome", index.viewNameForPath("/tmp/app/resources/views/welcome.edge"))

        val site = CallSiteDetector.Site(SymbolKind.ROUTE, "ho")
        val items = AdonisCompletionContributor.symbolsFor(index, site)
        assertTrue(items.any { it.first == "home" })

        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/routes/web.py", 12),
            AdonisSymbolResolver.resolve(index, SymbolKind.ROUTE, "home"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/resources/views/welcome.edge", 0),
            AdonisSymbolResolver.resolve(index, SymbolKind.VIEW, "welcome"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/config/app.py", 17),
            AdonisSymbolResolver.resolve(index, SymbolKind.CONFIG, "app.env"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/x", 0),
            AdonisSymbolResolver.resolve(index, SymbolKind.COMPONENT, "alert"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/.env", 3),
            AdonisSymbolResolver.resolve(index, SymbolKind.ENV, "APP_KEY"),
        )
        assertEquals(listOf("database", "redis", "sync"), index.optionsForEnvKey("QUEUE_CONNECTION"))
        assertEquals(listOf("database", "redis", "sync"), index.optionsForEnvKey("QUEUE_DRIVER"))
        val envSite = CallSiteDetector.Site(SymbolKind.ENV_VALUE, "re", receiver = "QUEUE_CONNECTION")
        val envItems = AdonisCompletionContributor.symbolsFor(index, envSite)
        assertTrue(envItems.any { it.first == "redis" })
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/database/migrations/0001_users.py", 7),
            AdonisSymbolResolver.resolveColumn(index, "users", "email"),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/app/app/models/user.py", 42),
            AdonisSymbolResolver.resolve(index, SymbolKind.RELATION, "posts"),
        )
        assertEquals(
            AdonisSymbolResolver.Target(
                "/tmp/app/app/http/controllers/welcome_controller.py",
                22,
            ),
            AdonisSymbolResolver.resolve(
                index,
                SymbolKind.TEMPLATE_VAR,
                "title",
                viewName = "welcome",
            ),
        )
        assertEquals(
            AdonisSymbolResolver.Target("/tmp/auth.py", 5),
            AdonisSymbolResolver.resolve(index, SymbolKind.TEMPLATE_VAR, "csrf_token"),
        )
    }

    @Test
    fun configFallbackScansFile() {
        val dir = java.nio.file.Files.createTempDirectory("adonis-config")
        val file = dir.resolve("app.py")
        java.nio.file.Files.writeString(
            file,
            """
            config = {
                "name": "x",
                "env": "local",
            }
            """.trimIndent(),
        )
        val line = AdonisSymbolResolver.locateNestedKeyLine(file.toString(), listOf("env"))
        assertEquals(2, line)
    }
}
