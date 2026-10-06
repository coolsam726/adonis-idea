package dev.shamar.adonis.ide

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project

/**
 * Safe entry point to Database Tools APIs.
 *
 * The Database plugin is optional at runtime (WebStorm users can disable it).
 * Callers must check [isDatabasePluginAvailable] before expecting live columns.
 */
object AdonisDbBridge {
    const val DATABASE_PLUGIN_ID = "com.intellij.database"

    data class DataSourceRef(
        val name: String,
        val uniqueId: String,
        val dbmsLabel: String = "",
    )

    fun isDatabasePluginAvailable(): Boolean {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(DATABASE_PLUGIN_ID))
        return plugin != null && plugin.isEnabled
    }

    fun listDataSources(project: Project): List<DataSourceRef> {
        if (!isDatabasePluginAvailable()) return emptyList()
        return try {
            AdonisDasIntrospector.listDataSources(project)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Read column names for [tableName] from the selected (or named) DataSource.
     * Returns empty when the plugin/schema is unavailable — never throws to callers.
     */
    fun columnsForTable(
        project: Project,
        tableName: String,
        dataSourceName: String,
        dataSourceUniqueId: String,
    ): Set<String> {
        if (!isDatabasePluginAvailable() || tableName.isBlank()) return emptySet()
        return try {
            AdonisDasIntrospector.columnsForTable(
                project,
                tableName,
                dataSourceName,
                dataSourceUniqueId,
            )
        } catch (_: Throwable) {
            emptySet()
        }
    }

    fun resolveSelectedRef(project: Project, settings: AdonisDbSettings): DataSourceRef? {
        val all = listDataSources(project)
        if (all.isEmpty()) return null
        if (settings.dataSourceUniqueId.isNotBlank()) {
            all.firstOrNull { it.uniqueId == settings.dataSourceUniqueId }?.let { return it }
        }
        if (settings.dataSourceName.isNotBlank()) {
            all.firstOrNull { it.name == settings.dataSourceName }?.let { return it }
        }
        return all.firstOrNull()
    }
}
