package com.adonis.ide

import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolve an indexed symbol name to a filesystem location.
 */
object AdonisSymbolResolver {
    data class Target(val path: String, val line: Int = 0)

    fun resolve(
        index: AdonisIndex,
        kind: SymbolKind,
        name: String,
        receiver: String? = null,
        viewName: String? = null,
    ): Target? {
        if (name.isBlank()) return null
        return when (kind) {
            SymbolKind.ROUTE -> {
                val route = index.routes[name] ?: return null
                val path = route.path ?: return null
                Target(path, route.line)
            }
            SymbolKind.VIEW -> {
                val path = index.views[name] ?: return null
                Target(path)
            }
            SymbolKind.CONFIG -> resolveConfig(index, name)
            SymbolKind.ENV -> {
                val entry = index.envKeys[name] ?: return null
                entry.path?.let { return Target(it, entry.line) }
                // Config-only key: jump to first env() usage site (config/app.py:18).
                val origin = entry.usedBy.firstOrNull() ?: return null
                return parseUsedBy(index.basePath, origin)
            }
            SymbolKind.ENV_VALUE -> null
            SymbolKind.TABLE -> {
                val table = index.tables[name] ?: return null
                val path = table.path ?: return null
                Target(path, table.line)
            }
            SymbolKind.COLUMN -> resolveColumn(index, receiver, name)
            SymbolKind.COMPONENT -> {
                val path = index.components[name]
                    ?: index.views["components.$name"]
                    ?: return null
                Target(path)
            }
            SymbolKind.VITE -> {
                val path = index.viteEntries[name] ?: index.views[name] ?: return null
                Target(path)
            }
            SymbolKind.RELATION -> resolveRelation(index, name, receiver)
            SymbolKind.TEMPLATE_VAR -> resolveTemplateVar(index, name, viewName)
            SymbolKind.CONTROLLER_ACTION -> resolveControllerAction(index, name, receiver)
            else -> null
        }
    }

    /**
     * Resolve ``[controllers.Session, 'store']`` to SessionController#store only.
     * Requires a controller receiver when multiple controllers share the action name.
     */
    fun resolveControllerAction(
        index: AdonisIndex,
        action: String,
        receiver: String?,
    ): Target? {
        if (action.isBlank()) return null
        // Allow fully-qualified ``SessionController@store`` as the name.
        val (controllerHint, actionName) = if ('@' in action) {
            action.substringBefore('@') to action.substringAfter('@')
        } else {
            receiver to action
        }
        if (actionName.isBlank()) return null

        val controller = index.resolveControllerName(controllerHint)
            ?: uniqueControllerForAction(index, actionName)
            ?: return null

        if (index.controllerActions[controller]?.contains(actionName) != true &&
            !index.controllerLocations.containsKey("$controller@$actionName")
        ) {
            return null
        }

        index.controllerLocations["$controller@$actionName"]?.let { loc ->
            val path = loc.path ?: return@let
            return Target(path, loc.line.coerceAtLeast(0))
        }
        val classLoc = index.controllerLocations[controller]
        val path = classLoc?.path ?: return null
        val line = locateMethodLine(path, actionName)
        return Target(path, line)
    }

    private fun uniqueControllerForAction(index: AdonisIndex, action: String): String? {
        val matches = index.controllerActions.entries.filter { action in it.value }.map { it.key }
        return matches.singleOrNull()
    }

    /** Scan a controller source file for an indented method declaration (0-based line). */
    internal fun locateMethodLine(path: String, method: String): Int {
        val file = Path.of(path)
        if (!Files.isRegularFile(file)) return 0
        val lines = try {
            Files.readAllLines(file)
        } catch (_: Exception) {
            return 0
        }
        val pattern = Regex("""^\s+(?:async\s+)?${Regex.escape(method)}\s*\(""")
        for (i in lines.indices) {
            if (pattern.containsMatchIn(lines[i])) return i
        }
        return 0
    }

    fun resolveColumn(index: AdonisIndex, tableHint: String?, column: String): Target? {
        if (column.isBlank()) return null
        fun fromTable(table: AdonisIndex.TableEntry): Target? {
            val col = table.columns[column] ?: return null
            val path = col.path ?: table.path ?: return null
            return Target(path, col.line)
        }
        if (tableHint != null) {
            index.tables[tableHint]?.let { fromTable(it)?.let { t -> return t } }
            val plural = tableHint + "s"
            index.tables[plural]?.let { fromTable(it)?.let { t -> return t } }
            index.tables[tableHint.removeSuffix("s")]?.let { fromTable(it)?.let { t -> return t } }
        }
        for (table in index.tables.values) {
            fromTable(table)?.let { return it }
        }
        return null
    }

    private fun resolveConfig(index: AdonisIndex, name: String): Target? {
        index.configLocations[name]?.let { loc ->
            val path = loc.path ?: return@let
            return Target(path, loc.line)
        }
        val stem = name.substringBefore('.', name)
        val path = index.configFiles[stem] ?: return null
        val afterStem = name.substringAfter('.', missingDelimiterValue = "")
        if (afterStem.isEmpty()) return Target(path)
        val line = locateNestedKeyLine(path, afterStem.split('.'))
        return Target(path, line)
    }

    private fun resolveRelation(index: AdonisIndex, name: String, receiver: String?): Target? {
        val meta = when {
            receiver != null -> {
                val simple = receiver.substringAfterLast('.')
                index.modelMetadata.entries.firstOrNull { (className, m) ->
                    name in m.relations && (
                        className.equals(simple, true) ||
                            m.module.equals(simple, true) ||
                            className.equals(receiver, true)
                        )
                } ?: index.modelMetadata.entries.firstOrNull { (_, m) -> name in m.relations }
            }
            else -> index.modelMetadata.entries.firstOrNull { (_, m) -> name in m.relations }
        } ?: return null
        val path = meta.value.path.ifBlank { return null }
        val line = meta.value.relationLines[name] ?: 0
        return Target(path, line)
    }

    private fun resolveTemplateVar(index: AdonisIndex, name: String, viewName: String?): Target? {
        val root = name.substringBefore('.', name)
        if (viewName != null) {
            index.viewData[viewName]?.get(root)?.let { entry ->
                val path = entry.path ?: return@let
                return Target(path, entry.line)
            }
        }
        // Prefer data keys from any view when the template mapping is unknown.
        for (vars in index.viewData.values) {
            vars[root]?.let { entry ->
                val path = entry.path ?: return@let
                return Target(path, entry.line)
            }
        }
        index.viewShared[root]?.let { entry ->
            val path = entry.path ?: return@let
            return Target(path, entry.line)
        }
        index.viewHelpers[root]?.let { entry ->
            val path = entry.path ?: return@let
            return Target(path, entry.line)
        }
        return null
    }

    /**
     * Fallback when `config_locations` is absent (older adonis): walk quoted keys
     * in the stem file for each dotted segment after the stem.
     */
    internal fun locateNestedKeyLine(path: String, segments: List<String>): Int {
        if (segments.isEmpty()) return 0
        val file = Path.of(path)
        if (!Files.isRegularFile(file)) return 0
        val lines = try {
            Files.readAllLines(file)
        } catch (_: Exception) {
            return 0
        }
        var searchFrom = 0
        var lastLine = 0
        for (seg in segments) {
            val pattern = Regex("""['"]${Regex.escape(seg)}['"]\s*:""")
            var found = false
            for (i in searchFrom until lines.size) {
                if (pattern.containsMatchIn(lines[i])) {
                    lastLine = i
                    searchFrom = i + 1
                    found = true
                    break
                }
            }
            if (!found) break
        }
        return lastLine
    }

    /** ``config/app.py:18`` → Target (1-based line in origin → 0-based). */
    private fun parseUsedBy(basePath: String, origin: String): Target? {
        val idx = origin.lastIndexOf(':')
        if (idx <= 0) return null
        val rel = origin.substring(0, idx)
        val lineOneBased = origin.substring(idx + 1).toIntOrNull() ?: return null
        val path = if (basePath.isNotBlank()) {
            Path.of(basePath, rel).toString()
        } else {
            rel
        }
        return Target(path, (lineOneBased - 1).coerceAtLeast(0))
    }
}
