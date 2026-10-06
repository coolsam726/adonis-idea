package dev.shamar.adonis.ide

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project

/**
 * Reflective Database Tools (DAS) access.
 *
 * Uses the Database plugin classloader (not the Adonis plugin loader) and prefers
 * [LocalDataSource] instances from the Database tool — those hold the introspected
 * schema model. `DbPsiFacade` is a secondary path and is often empty until PSI sync.
 */
object AdonisDasIntrospector {
    fun listDataSources(project: Project): List<AdonisDbBridge.DataSourceRef> {
        val out = linkedMapOf<String, AdonisDbBridge.DataSourceRef>()
        for (ds in localDataSources(project)) {
            val name = call(ds, "getName") as? String ?: continue
            val id = (call(ds, "getUniqueId") as? String)?.ifBlank { name } ?: name
            val dbmsName = dbmsNameOf(ds)
            out[id] = AdonisDbBridge.DataSourceRef(name, id, dbmsName)
        }
        for (ds in psiDataSources(project)) {
            val name = call(ds, "getName") as? String ?: continue
            val id = uniqueIdOf(ds) ?: name
            out.putIfAbsent(id, AdonisDbBridge.DataSourceRef(name, id, dbmsNameOf(ds)))
        }
        return out.values.toList()
    }

    fun columnsForTable(
        project: Project,
        tableName: String,
        dataSourceName: String,
        dataSourceUniqueId: String,
    ): Set<String> {
        val wanted = normalizeTable(tableName)
        if (wanted.isEmpty()) return emptySet()
        val ds = findDasDataSource(project, dataSourceName, dataSourceUniqueId) ?: return emptySet()
        val cols = linkedSetOf<String>()
        for (table in tablesOf(ds)) {
            if (!tableMatches(table, wanted)) continue
            for (column in columnsOf(table)) {
                val name = nameOf(column) ?: continue
                if (name.isNotBlank()) cols.add(name)
            }
        }
        return cols
    }

    fun introspectAllTables(
        project: Project,
        dataSourceName: String,
        dataSourceUniqueId: String,
    ): Map<String, Set<String>> {
        val ds = findDasDataSource(project, dataSourceName, dataSourceUniqueId) ?: return emptyMap()
        return introspectAllTablesFrom(ds)
    }

    /** Diagnostic: why introspection returned nothing (shown in Settings status). */
    fun diagnose(
        project: Project,
        dataSourceName: String,
        dataSourceUniqueId: String,
    ): String {
        if (databaseClassLoader() == null) {
            return "Database plugin classloader unavailable"
        }
        val locals = localDataSources(project)
        val psis = psiDataSources(project)
        if (locals.isEmpty() && psis.isEmpty()) {
            return "No DataSources in Database tool"
        }
        val ds = findDasDataSource(project, dataSourceName, dataSourceUniqueId)
            ?: return "Selected DataSource not found (locals=${locals.size}, psi=${psis.size})"
        val model = call(ds, "getModel")
        if (model == null) {
            return "DataSource has no schema model — connect and Refresh in the Database tool"
        }
        val tables = tablesOf(ds)
        if (tables.isEmpty()) {
            return "Schema model is empty — right-click the DataSource → Refresh"
        }
        val withCols = tables.count { columnsOf(it).isNotEmpty() }
        return "Loaded ${tables.size} table(s), $withCols with columns"
    }

    private fun introspectAllTablesFrom(ds: Any): Map<String, Set<String>> {
        val out = linkedMapOf<String, Set<String>>()
        for (table in tablesOf(ds)) {
            val name = normalizeTable(nameOf(table).orEmpty())
            if (name.isBlank()) continue
            val cols = linkedSetOf<String>()
            for (column in columnsOf(table)) {
                val c = nameOf(column) ?: continue
                if (c.isNotBlank()) cols.add(c)
            }
            if (cols.isNotEmpty()) out[name] = cols
        }
        return out
    }

    private fun findDasDataSource(
        project: Project,
        dataSourceName: String,
        dataSourceUniqueId: String,
    ): Any? {
        // Prefer LocalDataSource — this is what the Database tool edits and introspects into.
        matchDataSource(localDataSources(project), dataSourceName, dataSourceUniqueId)?.let { return it }
        matchDataSource(psiDataSources(project), dataSourceName, dataSourceUniqueId)?.let { return it }
        return localDataSources(project).firstOrNull()
            ?: psiDataSources(project).firstOrNull()
    }

    private fun matchDataSource(
        all: List<Any>,
        dataSourceName: String,
        dataSourceUniqueId: String,
    ): Any? {
        if (all.isEmpty()) return null
        if (dataSourceUniqueId.isNotBlank()) {
            all.firstOrNull {
                uniqueIdOf(it) == dataSourceUniqueId ||
                    (call(it, "getUniqueId") as? String) == dataSourceUniqueId
            }?.let { return it }
        }
        if (dataSourceName.isNotBlank()) {
            all.firstOrNull { (call(it, "getName") as? String) == dataSourceName }?.let { return it }
        }
        return null
    }

    private fun localDataSources(project: Project): List<Any> {
        val mgr = invokeStatic(
            "com.intellij.database.dataSource.LocalDataSourceManager",
            "getInstance",
            project,
        ) ?: return emptyList()
        return toList(call(mgr, "getDataSources") ?: return emptyList())
    }

    private fun psiDataSources(project: Project): List<Any> {
        val facade = invokeStatic(
            "com.intellij.database.psi.DbPsiFacade",
            "getInstance",
            project,
        ) ?: return emptyList()
        return toList(call(facade, "getDataSources") ?: return emptyList())
    }

    private fun tablesOf(ds: Any): List<Any> {
        // DasUtil.getTables(DasDataSource) — LocalDataSource and DbDataSource both qualify.
        invokeStatic("com.intellij.database.util.DasUtil", "getTables", ds)?.let { result ->
            val list = toList(result)
            if (list.isNotEmpty()) return list.filter { isDasTable(it) }.ifEmpty { list }
        }
        // Fallback: model traverser
        val model = call(ds, "getModel") ?: return emptyList()
        val traverser = call(model, "traverser") ?: return emptyList()
        // Prefer traverse() / visitRoots / roots depending on API generation.
        val visited = call(traverser, "traverse")
            ?: call(traverser, "visitAll")
            ?: call(traverser, "getRoots")
            ?: return emptyList()
        return toList(visited).filter { isDasTable(it) }
    }

    private fun columnsOf(table: Any): List<Any> {
        invokeStatic("com.intellij.database.util.DasUtil", "getColumns", table)?.let { result ->
            val list = toList(result)
            if (list.isNotEmpty()) return list
        }
        val kindClass = classOrNull("com.intellij.database.model.ObjectKind") ?: return emptyList()
        val columnKind = kindClass.enumConstants?.firstOrNull {
            (it as Enum<*>).name == "COLUMN"
        } ?: return emptyList()
        val children = call(table, "getDasChildren", columnKind) ?: return emptyList()
        return toList(children)
    }

    private fun tableMatches(table: Any, wanted: String): Boolean {
        val simple = normalizeTable(nameOf(table).orEmpty())
        if (simple.equals(wanted, ignoreCase = true)) return true
        // DasUtil.getSchema(DasObject) returns String in current Database Tools.
        val schemaName = when (val schema = invokeStatic("com.intellij.database.util.DasUtil", "getSchema", table)) {
            is String -> schema
            null -> null
            else -> call(schema, "getName") as? String
        }
        if (!schemaName.isNullOrBlank()) {
            val qualified = normalizeTable("$schemaName.$simple")
            if (qualified.equals(wanted, ignoreCase = true)) return true
            if (qualified.endsWith(".$wanted", ignoreCase = true)) return true
        }
        return false
    }

    private fun uniqueIdOf(ds: Any): String? {
        (call(ds, "getUniqueId") as? String)?.ifBlank { null }?.let { return it }
        val delegate = call(ds, "getDelegate") ?: call(ds, "getDelegateDataSource")
        if (delegate != null) {
            (call(delegate, "getUniqueId") as? String)?.ifBlank { null }?.let { return it }
        }
        return call(ds, "getName") as? String
    }

    private fun dbmsNameOf(ds: Any): String {
        val dbms = call(ds, "getDbms") ?: return ""
        return (call(dbms, "getName") as? String)
            ?: (call(dbms, "name") as? String)
            ?: dbms.toString()
    }

    private fun nameOf(obj: Any): String? =
        (invokeStatic("com.intellij.database.util.DasUtil", "getName", obj) as? String)
            ?: (call(obj, "getName") as? String)

    private fun isDasTable(obj: Any): Boolean {
        val cls = classOrNull("com.intellij.database.model.DasTable") ?: return false
        return cls.isInstance(obj)
    }

    private fun normalizeTable(name: String): String =
        name.trim().removeSurrounding("\"").removeSurrounding("`").removeSurrounding("'")

    private fun toList(value: Any): List<Any> = when (value) {
        is Array<*> -> value.filterNotNull()
        is Collection<*> -> value.filterNotNull()
        is Iterable<*> -> value.filterNotNull()
        is Sequence<*> -> value.filterNotNull().toList()
        else -> {
            try {
                // JBIterable / custom iterables
                val iter = call(value, "iterator") as? Iterator<*>
                if (iter != null) {
                    val out = mutableListOf<Any>()
                    while (iter.hasNext()) {
                        iter.next()?.let { out.add(it) }
                    }
                    out
                } else {
                    emptyList()
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    private fun databaseClassLoader(): ClassLoader? {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(AdonisDbBridge.DATABASE_PLUGIN_ID))
            ?: return null
        return try {
            // IJ 2024+: Plugin.classLoader; older: getPluginClassLoader()
            val cl = call(plugin, "getClassLoader") as? ClassLoader
                ?: call(plugin, "getPluginClassLoader") as? ClassLoader
            cl ?: plugin.javaClass.classLoader
        } catch (_: Throwable) {
            plugin.javaClass.classLoader
        }
    }

    private fun classOrNull(name: String): Class<*>? {
        val cl = databaseClassLoader()
        return try {
            if (cl != null) Class.forName(name, true, cl) else Class.forName(name)
        } catch (_: Throwable) {
            try {
                Class.forName(name)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun invokeStatic(className: String, method: String, vararg args: Any?): Any? {
        val cls = classOrNull(className) ?: return null
        val methods = cls.methods.filter { it.name == method && it.parameterCount == args.size }
        for (m in methods) {
            try {
                if (!m.canAccess(null)) m.isAccessible = true
                return m.invoke(null, *args)
            } catch (_: Throwable) {
            }
        }
        // Also try declared methods (package/protected edge cases).
        for (m in cls.declaredMethods.filter { it.name == method && it.parameterCount == args.size }) {
            try {
                m.isAccessible = true
                return m.invoke(null, *args)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun call(target: Any, method: String, vararg args: Any?): Any? {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            val methods = (cls.methods + cls.declaredMethods)
                .filter { it.name == method && it.parameterCount == args.size }
                .distinctBy { it.toGenericString() }
            for (m in methods) {
                try {
                    m.isAccessible = true
                    return m.invoke(target, *args)
                } catch (_: Throwable) {
                }
            }
            cls = cls.superclass
        }
        return null
    }
}
