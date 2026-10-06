package dev.shamar.adonis.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Cached live column names from the selected Database tool DataSource.
 *
 * Completions only read the cache (never block the EDT on DAS). Refresh runs
 * on a pooled thread when settings change or [refreshAsync] is invoked.
 */
@Service(Service.Level.PROJECT)
class AdonisDbColumnCache(private val project: Project) {
    private val byTable = ConcurrentHashMap<String, Set<String>>()
    private val refreshing = AtomicBoolean(false)
    private val lastRefreshMs = AtomicLong(0L)
    private val lastError = AtomicReference<String?>(null)
    private val tableCount = AtomicLong(0L)

    fun columnsFor(tableName: String): Set<String> {
        if (tableName.isBlank()) return emptySet()
        val key = tableName.lowercase()
        byTable[key]?.let { return it }
        // Soft miss: kick a background refresh; caller gets migrations until ready.
        scheduleRefreshIfIdle()
        return emptySet()
    }

    fun snapshot(): Map<String, Set<String>> = byTable.toMap()

    fun lastRefreshEpochMs(): Long = lastRefreshMs.get()

    fun lastErrorMessage(): String? = lastError.get()

    fun cachedTableCount(): Int = tableCount.get().toInt()

    fun clear() {
        byTable.clear()
        tableCount.set(0)
        lastError.set(null)
    }

    fun refreshAsync(onDone: (() -> Unit)? = null) {
        val app = ApplicationManager.getApplication()
        app.executeOnPooledThread {
            refreshNow()
            if (onDone != null) {
                app.invokeLater { onDone() }
            }
        }
    }

    fun refreshNow() {
        if (!refreshing.compareAndSet(false, true)) return
        try {
            if (!AdonisDbBridge.isDatabasePluginAvailable()) {
                clear()
                lastError.set("Database plugin is not enabled")
                lastRefreshMs.set(System.currentTimeMillis())
                return
            }
            val settings = AdonisDbSettings.getInstance(project)
            if (settings.columnSource == AdonisColumnSource.MIGRATIONS) {
                clear()
                lastError.set(null)
                lastRefreshMs.set(System.currentTimeMillis())
                return
            }
            val ref = AdonisDbBridge.resolveSelectedRef(project, settings)
            if (ref == null) {
                clear()
                lastError.set("No Database tool DataSource selected")
                lastRefreshMs.set(System.currentTimeMillis())
                return
            }
            // Always pull the full introspected schema from the Database tool first.
            // (Per-table lookups used to target DbPsiFacade only, which is often empty.)
            val next = ConcurrentHashMap<String, Set<String>>()
            val all = AdonisDasIntrospector.introspectAllTables(
                project,
                ref.name,
                ref.uniqueId,
            )
            for ((t, cols) in all) {
                next[t.lowercase()] = cols
            }
            // Also map Adonis index table names → columns (alias / case variants).
            val index = AdonisProjectService.getInstance(project).cachedIndex()
                ?: AdonisProjectService.getInstance(project).index()
            for (table in index.tables.keys + index.modelMetadata.values.map { it.table }) {
                if (table.isBlank()) continue
                if (next.containsKey(table.lowercase())) continue
                val cols = AdonisDbBridge.columnsForTable(
                    project,
                    table,
                    ref.name,
                    ref.uniqueId,
                )
                if (cols.isNotEmpty()) {
                    next[table.lowercase()] = cols
                }
            }
            byTable.clear()
            byTable.putAll(next)
            tableCount.set(next.size.toLong())
            lastError.set(
                when {
                    next.isEmpty() && !AdonisDbBridge.isDatabasePluginAvailable() ->
                        "Database plugin is not enabled"
                    next.isEmpty() ->
                        AdonisDasIntrospector.diagnose(project, ref.name, ref.uniqueId)
                    else -> null
                },
            )
            lastRefreshMs.set(System.currentTimeMillis())
        } finally {
            refreshing.set(false)
        }
    }

    private fun scheduleRefreshIfIdle() {
        if (refreshing.get()) return
        if (System.currentTimeMillis() - lastRefreshMs.get() < 2_000L && byTable.isNotEmpty()) return
        refreshAsync()
    }

    companion object {
        fun getInstance(project: Project): AdonisDbColumnCache = project.service()

        /** Safe lookup for [AdonisModelResolver] — never throws; null project → empty. */
        fun liveColumns(project: Project?, tableName: String): Set<String> {
            if (project == null || tableName.isBlank()) return emptySet()
            return try {
                getInstance(project).columnsFor(tableName)
            } catch (_: Throwable) {
                emptySet()
            }
        }
    }
}
