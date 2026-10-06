package dev.shamar.adonis.ide

import com.intellij.openapi.project.Project

/**
 * Reflective Database Tools (DAS) access.
 *
 * The WebStorm Gradle SDK artifact does not always ship `com.intellij.database`
 * jars, but real WebStorm installs bundle Database Tools. Reflection keeps the
 * plugin compiling against WS while enabling live schema when the plugin is on.
 */
object AdonisDasIntrospector {
    fun listDataSources(project: Project): List<AdonisDbBridge.DataSourceRef> {
        val out = linkedMapOf<String, AdonisDbBridge.DataSourceRef>()
        // LocalDataSourceManager.getInstance(project).dataSources
        invokeStatic("com.intellij.database.dataSource.LocalDataSourceManager", "getInstance", project)?.let { mgr ->
            val list = call(mgr, "getDataSources") as? Collection<*> ?: emptyList<Any>()
            for (ds in list) {
                if (ds == null) continue
                val name = call(ds, "getName") as? String ?: continue
                val id = (call(ds, "getUniqueId") as? String)?.ifBlank { name } ?: name
                val dbms = call(ds, "getDbms")
                val dbmsName = dbms?.let { call(it, "getName") as? String }.orEmpty()
                out[id] = AdonisDbBridge.DataSourceRef(name, id, dbmsName)
            }
        }
        // DbPsiFacade.getInstance(project).dataSources
        invokeStatic("com.intellij.database.psi.DbPsiFacade", "getInstance", project)?.let { facade ->
            val list = call(facade, "getDataSources") as? Collection<*> ?: emptyList<Any>()
            for (ds in list) {
                if (ds == null) continue
                val name = call(ds, "getName") as? String ?: continue
                val id = uniqueIdOf(ds) ?: name
                val dbms = call(ds, "getDbms")
                val dbmsName = dbms?.let { call(it, "getName") as? String }.orEmpty()
                out.putIfAbsent(id, AdonisDbBridge.DataSourceRef(name, id, dbmsName))
            }
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
        val ds = findPsiDataSource(project, dataSourceName, dataSourceUniqueId) ?: return emptySet()
        val cols = linkedSetOf<String>()
        for (table in tablesOf(ds)) {
            if (!tableMatches(table, wanted)) continue
            for (column in columnsOf(table)) {
                val name = call(column, "getName") as? String ?: continue
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
        val ds = findPsiDataSource(project, dataSourceName, dataSourceUniqueId) ?: return emptyMap()
        val out = linkedMapOf<String, Set<String>>()
        for (table in tablesOf(ds)) {
            val name = normalizeTable((call(table, "getName") as? String).orEmpty())
            if (name.isBlank()) continue
            val cols = linkedSetOf<String>()
            for (column in columnsOf(table)) {
                val c = call(column, "getName") as? String ?: continue
                if (c.isNotBlank()) cols.add(c)
            }
            if (cols.isNotEmpty()) out[name] = cols
        }
        return out
    }

    private fun findPsiDataSource(
        project: Project,
        dataSourceName: String,
        dataSourceUniqueId: String,
    ): Any? {
        val facade = invokeStatic("com.intellij.database.psi.DbPsiFacade", "getInstance", project)
            ?: return null
        val all = (call(facade, "getDataSources") as? Collection<*>)?.filterNotNull().orEmpty()
        if (all.isEmpty()) return null
        if (dataSourceUniqueId.isNotBlank()) {
            all.firstOrNull { uniqueIdOf(it) == dataSourceUniqueId }?.let { return it }
        }
        if (dataSourceName.isNotBlank()) {
            all.firstOrNull { (call(it, "getName") as? String) == dataSourceName }?.let { return it }
        }
        return all.firstOrNull()
    }

    private fun tablesOf(ds: Any): List<Any> {
        // DasUtil.getTables(ds)
        invokeStatic("com.intellij.database.util.DasUtil", "getTables", ds)?.let { result ->
            return toList(result)
        }
        // ds.model.traverser() …
        val model = call(ds, "getModel") ?: return emptyList()
        val traverser = call(model, "traverser") ?: return emptyList()
        val visited = call(traverser, "visitAll") ?: return emptyList()
        return toList(visited).filter { isDasTable(it) }
    }

    private fun columnsOf(table: Any): List<Any> {
        invokeStatic("com.intellij.database.util.DasUtil", "getColumns", table)?.let { result ->
            return toList(result)
        }
        val kindClass = classOrNull("com.intellij.database.model.ObjectKind") ?: return emptyList()
        val columnKind = kindClass.enumConstants?.firstOrNull {
            (it as Enum<*>).name == "COLUMN"
        } ?: return emptyList()
        val children = call(table, "getDasChildren", columnKind) ?: return emptyList()
        return toList(children)
    }

    private fun tableMatches(table: Any, wanted: String): Boolean {
        val simple = normalizeTable((call(table, "getName") as? String).orEmpty())
        if (simple.equals(wanted, ignoreCase = true)) return true
        val schema = invokeStatic("com.intellij.database.util.DasUtil", "getSchema", table)
        val schemaName = schema?.let { call(it, "getName") as? String }
        if (!schemaName.isNullOrBlank()) {
            val qualified = normalizeTable("$schemaName.$simple")
            if (qualified.equals(wanted, ignoreCase = true)) return true
            if (qualified.endsWith(".$wanted", ignoreCase = true)) return true
        }
        return false
    }

    private fun uniqueIdOf(ds: Any): String? {
        val delegate = call(ds, "getDelegate")
        if (delegate != null) {
            (call(delegate, "getUniqueId") as? String)?.ifBlank { null }?.let { return it }
        }
        return call(ds, "getName") as? String
    }

    private fun isDasTable(obj: Any): Boolean {
        val cls = classOrNull("com.intellij.database.model.DasTable") ?: return false
        return cls.isInstance(obj)
    }

    private fun normalizeTable(name: String): String =
        name.trim().removeSurrounding("\"").removeSurrounding("`").removeSurrounding("'")

    private fun toList(value: Any): List<Any> = when (value) {
        is Array<*> -> value.filterNotNull()
        is Iterable<*> -> value.filterNotNull()
        is Sequence<*> -> value.filterNotNull().toList()
        else -> {
            // Iterable from Java (DasUtil often returns Iterable)
            try {
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

    private fun classOrNull(name: String): Class<*>? = try {
        Class.forName(name)
    } catch (_: Throwable) {
        null
    }

    private fun invokeStatic(className: String, method: String, vararg args: Any?): Any? {
        val cls = classOrNull(className) ?: return null
        val methods = cls.methods.filter { it.name == method && it.parameterCount == args.size }
        for (m in methods) {
            try {
                return m.invoke(null, *args)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun call(target: Any, method: String, vararg args: Any?): Any? {
        val methods = target.javaClass.methods.filter {
            it.name == method && it.parameterCount == args.size
        }
        for (m in methods) {
            try {
                return m.invoke(target, *args)
            } catch (_: Throwable) {
            }
        }
        return null
    }
}
