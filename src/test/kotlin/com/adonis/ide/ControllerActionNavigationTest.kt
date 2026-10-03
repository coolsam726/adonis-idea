package com.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ControllerActionNavigationTest {
    private fun index(): AdonisIndex = AdonisIndex(
        ok = true,
        controllerActions = mapOf(
            "SessionController" to listOf("create", "store", "destroy"),
            "NewAccountController" to listOf("create", "store"),
        ),
        controllerLocations = mapOf(
            "SessionController" to AdonisIndex.Located("/tmp/session_controller.ts", 10),
            "SessionController@store" to AdonisIndex.Located("/tmp/session_controller.ts", 42),
            "SessionController@create" to AdonisIndex.Located("/tmp/session_controller.ts", 20),
            "SessionController@destroy" to AdonisIndex.Located("/tmp/session_controller.ts", 55),
            "NewAccountController" to AdonisIndex.Located("/tmp/new_account_controller.ts", 5),
            "NewAccountController@store" to AdonisIndex.Located("/tmp/new_account_controller.ts", 30),
        ),
    )

    @Test
    fun detectorCapturesControllerReceiver() {
        val site = CallSiteDetector.detect("""router.post('login', [controllers.Session, 'sto""")
        assertNotNull(site)
        assertEquals(SymbolKind.CONTROLLER_ACTION, site!!.kind)
        assertEquals("sto", site.prefix)
        assertEquals("controllers.Session", site.receiver)
    }

    @Test
    fun detectorCapturesBareControllerClass() {
        val site = CallSiteDetector.detect("""[SessionController, "create""")
        assertEquals("SessionController", site!!.receiver)
        assertEquals(SymbolKind.CONTROLLER_ACTION, site.kind)
    }

    @Test
    fun resolveControllerNameMapsGeneratedAlias() {
        val index = index()
        assertEquals("SessionController", index.resolveControllerName("controllers.Session"))
        assertEquals("SessionController", index.resolveControllerName("Session"))
        assertEquals("SessionController", index.resolveControllerName("SessionController"))
        assertNull(index.resolveControllerName(null))
        assertNull(index.resolveControllerName(""))
        assertNull(index.resolveControllerName("Missing"))
    }

    @Test
    fun resolveStoreGoesToSessionControllerOnly() {
        val target = AdonisSymbolResolver.resolve(
            index(),
            SymbolKind.CONTROLLER_ACTION,
            "store",
            receiver = "controllers.Session",
        )
        assertEquals(AdonisSymbolResolver.Target("/tmp/session_controller.ts", 42), target)
    }

    @Test
    fun resolveDoesNotPickOtherControllersStore() {
        val session = AdonisSymbolResolver.resolve(
            index(),
            SymbolKind.CONTROLLER_ACTION,
            "store",
            receiver = "controllers.Session",
        )
        val account = AdonisSymbolResolver.resolve(
            index(),
            SymbolKind.CONTROLLER_ACTION,
            "store",
            receiver = "controllers.NewAccount",
        )
        assertEquals("/tmp/session_controller.ts", session!!.path)
        assertEquals("/tmp/new_account_controller.ts", account!!.path)
        assertFalse(session.path == account.path)
    }

    @Test
    fun ambiguousActionWithoutReceiverIsNotGuessed() {
        assertNull(
            AdonisSymbolResolver.resolve(
                index(),
                SymbolKind.CONTROLLER_ACTION,
                "store",
                receiver = null,
            ),
        )
    }

    @Test
    fun uniqueActionWithoutReceiverStillResolves() {
        val target = AdonisSymbolResolver.resolve(
            index(),
            SymbolKind.CONTROLLER_ACTION,
            "destroy",
            receiver = null,
        )
        assertEquals(AdonisSymbolResolver.Target("/tmp/session_controller.ts", 55), target)
    }

    @Test
    fun unknownActionOnKnownControllerReturnsNull() {
        assertNull(
            AdonisSymbolResolver.resolve(
                index(),
                SymbolKind.CONTROLLER_ACTION,
                "nope",
                receiver = "controllers.Session",
            ),
        )
        assertNull(
            AdonisSymbolResolver.resolve(
                index(),
                SymbolKind.CONTROLLER_ACTION,
                "",
                receiver = "controllers.Session",
            ),
        )
        assertNull(
            AdonisSymbolResolver.resolve(
                index(),
                SymbolKind.CONTROLLER_ACTION,
                "SessionController@",
            ),
        )
    }

    @Test
    fun locationWithNullPathFallsThroughToClassScan() {
        val file = Files.createTempFile("sess", ".ts")
        try {
            Files.writeString(
                file,
                "export default class SessionController {\n  async store() {}\n}\n",
            )
            val index = AdonisIndex(
                ok = true,
                controllerActions = mapOf("SessionController" to listOf("store")),
                controllerLocations = mapOf(
                    "SessionController@store" to AdonisIndex.Located(null, 99),
                    "SessionController" to AdonisIndex.Located(file.toString(), 0),
                ),
            )
            val target = AdonisSymbolResolver.resolve(
                index,
                SymbolKind.CONTROLLER_ACTION,
                "store",
                receiver = "Session",
            )
            assertNotNull(target)
            assertEquals(file.toString(), target!!.path)
            assertTrue(target.line >= 1)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun locateMethodLineUnreadableFile() {
        val locked = Files.createTempFile("locked-ctrl", ".ts")
        Files.writeString(locked, "  store() {}")
        locked.toFile().setReadable(false)
        try {
            assertEquals(0, AdonisSymbolResolver.locateMethodLine(locked.toString(), "store"))
        } finally {
            locked.toFile().setReadable(true)
            Files.deleteIfExists(locked)
        }
    }

    @Test
    fun resolveControllerNameCaseInsensitive() {
        val index = AdonisIndex(
            ok = true,
            controllerActions = mapOf("SessionController" to listOf("store")),
        )
        assertEquals("SessionController", index.resolveControllerName("sessioncontroller"))
    }

    @Test
    fun qualifiedNameResolves() {
        val target = AdonisSymbolResolver.resolve(
            index(),
            SymbolKind.CONTROLLER_ACTION,
            "SessionController@store",
        )
        assertEquals(42, target!!.line)
    }

    @Test
    fun locatorHitIncludesReceiver() {
        val text = """router.post('login', [controllers.Session, 'store'])"""
        val offset = text.indexOf("store") + 1
        val hit = AdonisSymbolLocator.hitAt(text, offset)
        assertNotNull(hit)
        assertEquals(SymbolKind.CONTROLLER_ACTION, hit!!.kind)
        assertEquals("store", hit.name)
        assertEquals("controllers.Session", hit.receiver)
    }

    @Test
    fun completionsScopedToController() {
        val site = CallSiteDetector.Site(
            SymbolKind.CONTROLLER_ACTION,
            "st",
            receiver = "controllers.Session",
        )
        val labels = AdonisCompletionCatalog.symbolsFor(index(), site).map { it.first }.toSet()
        assertTrue("store" in labels)
        assertTrue("create" in labels)
        assertTrue("destroy" in labels)
        // NewAccount-only actions are not injected as extras; both share create/store.
        assertEquals(setOf("create", "store", "destroy"), labels)
    }

    @Test
    fun tsFilesAreSupportedForNavigation() {
        assertTrue(AdonisNavigation.isSupportedFile("routes.ts", null))
        assertTrue(AdonisNavigation.isSupportedFile("app.js", null))
        assertTrue(AdonisNavigation.isSupportedFile("page.tsx", null))
        assertFalse(AdonisNavigation.isSupportedFile("readme.md", null))
    }

    @Test
    fun locateMethodLineFindsIndentedMethod() {
        val file = Files.createTempFile("session", ".ts")
        try {
            Files.writeString(
                file,
                """
                export default class SessionController {
                  async create() {}
                  async store({ request }) {
                    return null
                  }
                }
                """.trimIndent(),
            )
            val line = AdonisSymbolResolver.locateMethodLine(file.toString(), "store")
            assertTrue(line > 0)
            assertEquals(0, AdonisSymbolResolver.locateMethodLine(file.toString(), "missing"))
            assertEquals(0, AdonisSymbolResolver.locateMethodLine("/no/such/file.ts", "store"))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun resolveFallsBackToClassPathAndScan() {
        val file = Files.createTempFile("acct", ".ts")
        try {
            Files.writeString(
                file,
                """
                export default class OnlyController {
                  async uniqueAction() {
                    return 1
                  }
                }
                """.trimIndent(),
            )
            val index = AdonisIndex(
                ok = true,
                controllerActions = mapOf("OnlyController" to listOf("uniqueAction")),
                controllerLocations = mapOf(
                    "OnlyController" to AdonisIndex.Located(file.toString(), 0),
                ),
            )
            val target = AdonisSymbolResolver.resolve(
                index,
                SymbolKind.CONTROLLER_ACTION,
                "uniqueAction",
                receiver = "Only",
            )
            assertNotNull(target)
            assertEquals(file.toString(), target!!.path)
            assertTrue(target.line >= 1)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun loaderParsesControllerLocations() {
        val index = AdonisIndexLoader.parse(
            """
            {
              "ok": true,
              "controller_actions": {"SessionController": ["store"]},
              "controller_locations": {
                "SessionController@store": {"path": "/tmp/s.ts", "line": 7}
              }
            }
            """.trimIndent(),
        )
        assertEquals(listOf("store"), index.controllerActions["SessionController"])
        assertEquals(
            AdonisIndex.Located("/tmp/s.ts", 7),
            index.controllerLocations["SessionController@store"],
        )
    }
}
