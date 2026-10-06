package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdonisDbColumnSourceTest {
    @Test
    fun parsesStorage() {
        assertEquals(AdonisColumnSource.BOTH, AdonisColumnSource.fromStorage(null))
        assertEquals(AdonisColumnSource.MIGRATIONS, AdonisColumnSource.fromStorage("migrations"))
        assertEquals(AdonisColumnSource.DATABASE, AdonisColumnSource.fromStorage("DATABASE"))
        assertEquals(AdonisColumnSource.BOTH, AdonisColumnSource.fromStorage("nope"))
    }

    @Test
    fun mergeModes() {
        val mig = setOf("email", "country_id")
        val live = setOf("email", "phone", "created_at")
        assertEquals(mig, AdonisModelResolver.mergeColumnSources(mig, live, AdonisColumnSource.MIGRATIONS))
        assertEquals(live, AdonisModelResolver.mergeColumnSources(mig, live, AdonisColumnSource.DATABASE))
        assertEquals(mig, AdonisModelResolver.mergeColumnSources(mig, emptySet(), AdonisColumnSource.DATABASE))
        val both = AdonisModelResolver.mergeColumnSources(mig, live, AdonisColumnSource.BOTH)
        assertTrue(both.containsAll(mig))
        assertTrue(both.containsAll(live))
        assertEquals(setOf("a", "b"), AdonisDbIntrospection.mergeColumns(setOf("a"), setOf("b")))
    }

    @Test
    fun columnsForHonorsSourceOverride() {
        val index = AdonisIndex(
            ok = true,
            tables = mapOf(
                "users" to AdonisIndex.TableEntry(
                    columns = mapOf("email" to AdonisIndex.Located()),
                    detail = "lucid",
                    model = "User",
                ),
            ),
        )
        val migOnly = AdonisModelResolver.columnsFor(
            index,
            "User",
            sourceOverride = AdonisColumnSource.MIGRATIONS,
            liveOverride = setOf("phone"),
        )
        assertEquals(setOf("email"), migOnly)
        assertFalse("phone" in migOnly)

        val dbOnly = AdonisModelResolver.columnsFor(
            index,
            "User",
            sourceOverride = AdonisColumnSource.DATABASE,
            liveOverride = setOf("phone"),
        )
        assertEquals(setOf("phone"), dbOnly)

        val both = AdonisModelResolver.columnsFor(
            index,
            "User",
            sourceOverride = AdonisColumnSource.BOTH,
            liveOverride = setOf("phone"),
        )
        assertTrue(both.containsAll(setOf("email", "phone")))
    }

    @Test
    fun settingsStateRoundTrip() {
        val settings = AdonisDbSettings()
        assertEquals(AdonisColumnSource.BOTH, settings.columnSource)
        settings.columnSource = AdonisColumnSource.MIGRATIONS
        settings.dataSourceName = "App DB"
        settings.dataSourceUniqueId = "uid-1"
        assertEquals("App DB", settings.dataSourceName)
        assertEquals("uid-1", settings.dataSourceUniqueId)
        val state = settings.getState()
        assertEquals("MIGRATIONS", state.columnSource)
        assertEquals("App DB", state.dataSourceName)
        val reloaded = AdonisDbSettings()
        reloaded.loadState(state)
        assertEquals(AdonisColumnSource.MIGRATIONS, reloaded.columnSource)
        assertEquals("uid-1", reloaded.dataSourceUniqueId)
        assertEquals("App DB", reloaded.dataSourceName)

        // project=null → live cache miss path still merges cleanly
        val bothEmptyLive = AdonisModelResolver.columnsFor(
            AdonisIndex(
                ok = true,
                tables = mapOf(
                    "users" to AdonisIndex.TableEntry(
                        columns = mapOf("email" to AdonisIndex.Located()),
                        model = "User",
                    ),
                ),
            ),
            "User",
            project = null,
            sourceOverride = AdonisColumnSource.BOTH,
            liveOverride = null,
        )
        assertEquals(setOf("email"), bothEmptyLive)
        assertTrue(AdonisDbColumnCache.liveColumns(null, "users").isEmpty())
    }
}
