package dev.shamar.adonis.ide

/**
 * Pure presentation model for the Adonis Tool Window.
 */
object AdonisToolWindowModel {
    data class Summary(
        val ok: Boolean,
        val error: String?,
        val views: Int,
        val routes: Int,
        val configKeys: Int,
        val components: Int,
        val tables: Int,
        val envKeys: Int,
        val gates: Int,
        val validationRules: Int,
    )

    data class SymbolRow(
        val kind: String,
        val name: String,
        val detail: String,
    )

    fun summary(index: AdonisIndex): Summary = Summary(
        ok = index.ok,
        error = index.error,
        views = index.views.size,
        routes = index.routes.size,
        configKeys = index.configKeys.size,
        components = index.components.size,
        tables = index.tables.size,
        envKeys = index.envKeys.size,
        gates = index.gates.size,
        validationRules = index.validationRules.size,
    )

    fun statusLine(summary: Summary): String = when {
        summary.error != null -> "Index error: ${summary.error}"
        !summary.ok -> "Index not ready"
        else ->
            "OK — ${summary.views} views, ${summary.routes} routes, " +
                "${summary.configKeys} config, ${summary.components} components, " +
                "${summary.tables} tables"
    }

    /**
     * Flat symbol browser rows, optionally filtered by [query] (case-insensitive
     * substring on name).
     */
    fun symbolRows(index: AdonisIndex, query: String = ""): List<SymbolRow> {
        val q = query.trim().lowercase()
        fun match(name: String) = q.isEmpty() || name.lowercase().contains(q)
        val out = mutableListOf<SymbolRow>()
        for ((name, route) in index.routes) {
            if (match(name)) {
                out.add(SymbolRow("route", name, "${route.methods.joinToString("|")} ${route.uri}"))
            }
        }
        for ((name, path) in index.views) {
            if (match(name)) out.add(SymbolRow("view", name, path))
        }
        for (name in index.configKeys) {
            if (match(name)) out.add(SymbolRow("config", name, ""))
        }
        for ((name, path) in index.components) {
            if (match(name)) out.add(SymbolRow("component", name, path))
        }
        for ((name, _) in index.envKeys) {
            if (match(name)) out.add(SymbolRow("env", name, ""))
        }
        for ((name, table) in index.tables) {
            if (match(name)) {
                out.add(SymbolRow("table", name, "${table.columns.size} columns"))
            }
        }
        for (name in index.gates) {
            if (match(name)) out.add(SymbolRow("gate", name, ""))
        }
        for ((name, entry) in index.wireComponents) {
            if (match(name)) out.add(SymbolRow("wire", name, entry.path ?: ""))
        }
        for ((name, res) in index.shamar.resources) {
            if (match(name)) out.add(SymbolRow("shamar-resource", name, res.label))
        }
        for ((name, page) in index.shamar.pages) {
            if (match(name)) out.add(SymbolRow("shamar-page", name, page.label))
        }
        return out.sortedWith(compareBy({ it.kind }, { it.name }))
    }
}
