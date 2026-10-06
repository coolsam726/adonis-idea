package dev.shamar.adonis.ide

/**
 * Hover / quick-doc text for Adonis symbols and Edge directives.
 */
object AdonisHoverDocs {
    /** Baseline Edge directive docs (without leading `@`). */
    val DIRECTIVES: Map<String, String> = mapOf(
        "if" to "`@if(condition)` — conditional block; close with `@end`.",
        "elseif" to "`@elseif(condition)` — else-if branch inside `@if`.",
        "else" to "`@else` — final branch inside `@if` / `@unless` / `@each`.",
        "unless" to "`@unless(condition)` — inverted conditional; close with `@end`.",
        "each" to "`@each(item in items)` or `@each((item, i) in items)` — iterate; optional `@else` for empty; close with `@end`.",
        "end" to "`@end` — closes the nearest open Edge block (`@if`, `@each`, `@component`, …).",
        "include" to "`@include('partial')` — render another view inline (supports `disk::path`).",
        "includeIf" to "`@includeIf(condition, 'partial')` — include when condition is truthy.",
        "includeWhen" to "`@includeWhen(condition, 'partial')` — include when condition is truthy.",
        "includeUnless" to "`@includeUnless(condition, 'partial')` — include when condition is falsy.",
        "component" to "`@component('name')` / `@!component('name')` — component; close with `@end`.",
        "slot" to "`@slot('name')` — named slot body; close with `@end`. Render via `\$slots.name()`.",
        "section" to "`@section('name')` … `@end`, or inline `@section('name', 'value')`.",
        "layout" to "`@layout()` — Hypermedia layout component (block); or classic `@layout('master')`.",
        "page" to "`@page()` — main page slot inside a layout; close with `@end`.",
        "inject" to "`@inject({ key: value })` — share state with child components via `\$context`.",
        "eval" to "`@eval(expression)` — evaluate an expression (often `await \$slots.main()`).",
        "let" to "`@let(name = value)` — assign a local template variable.",
        "assign" to "`@assign(name = value)` — assign a template variable.",
        "vite" to "`@vite(['resources/js/app.js'])` — Vite entry tags.",
        "stack" to "`@stack('name')` — render a named stack.",
        "pushTo" to "`@pushTo('stack')` — append content to a stack; close with `@end`.",
        "svg" to "`@svg('icon')` — inline an SVG asset (Iconify).",
        "debugger" to "`@debugger` — emit a JS `debugger` statement in compiled output.",
        "dump" to "`@dump(value)` / `@dump(state)` — pretty-print a value in the rendered HTML.",
        "newError" to "`@newError('message')` — throw a template error.",
        "wire" to "`@wire('component')` — mount a Wire component; close with `@end`.",
        "persist" to "`@persist` — persist nested Wire state across morphs; close with `@end`.",
        "form" to "`@form({ route, method, … })` — Hypermedia form component with CSRF; close with `@end`.",
    )

    fun directive(name: String): String? = DIRECTIVES[name]

    fun forSymbol(index: AdonisIndex, kind: SymbolKind, name: String, receiver: String? = null): String? {
        if (name.isBlank()) return null
        return when (kind) {
            SymbolKind.ROUTE -> {
                val route = index.routes[name]
                if (route == null) "**route** `$name`\n\n_Unknown named route._"
                else {
                    val methods = route.methods.joinToString(" | ")
                    val loc = route.path?.let { "\n\n`$it:${route.line}`" }.orEmpty()
                    "**route** `$name`\n\n`$methods` `${route.uri}`$loc"
                }
            }
            SymbolKind.VIEW -> {
                val path = index.views[name]
                if (path == null) "**view** `$name`\n\n_Not found in resources/views._"
                else "**view** `$name`\n\n`$path`"
            }
            SymbolKind.CONFIG -> {
                val known = index.configKeys.contains(name)
                val status = if (known) "indexed" else "not in config"
                val loc = index.configLocations[name]?.path?.let { "\n\n`$it`" }.orEmpty()
                "**config** `$name`\n\n$status$loc"
            }
            SymbolKind.ENV -> {
                val entry = index.envKeys[name]
                if (entry == null) "**env** `$name`\n\n_Unknown key._"
                else {
                    val detail = entry.detail.ifBlank { entry.kind.ifBlank { "env" } }
                    val loc = entry.path?.let { "\n\n`$it:${entry.line}`" }.orEmpty()
                    "**env** `$name`\n\n$detail$loc"
                }
            }
            SymbolKind.COMPONENT -> {
                val path = index.components[name] ?: index.views["components.$name"]
                if (path == null) "**component** `$name`\n\n_Not found._"
                else "**component** `$name`\n\n`$path`"
            }
            SymbolKind.WIRE -> {
                val entry = index.wireComponents[name]
                if (entry == null) "**wire** `$name`\n\n_Unknown Wire component._"
                else {
                    val path = entry.path ?: entry.view ?: ""
                    "**wire** `$name`\n\n`$path`"
                }
            }
            SymbolKind.RELATION -> {
                val rels = AdonisCompletionCatalog.relationsFor(index, receiver)
                if (name in rels) "**relation** `$name`" + (receiver?.let { " on `$it`" }.orEmpty())
                else "**relation** `$name`\n\n_Unknown relation._"
            }
            SymbolKind.COLUMN, SymbolKind.ATTR, SymbolKind.MODEL_ATTR -> {
                val cols = AdonisModelResolver.columnsFor(index, receiver)
                if (name in cols) "**column** `$name`" + (receiver?.let { " ($it)" }.orEmpty())
                else "**column** `$name`\n\n_Unknown column._"
            }
            SymbolKind.TEMPLATE_VAR -> {
                if (index.templateVarNames().contains(name.substringBefore('.'))) {
                    "**template var** `$name`"
                } else {
                    "**template var** `$name`\n\n_Unknown in view data / shared / helpers._"
                }
            }
            SymbolKind.DIRECTIVE -> directive(name)
            SymbolKind.SHAMAR_RESOURCE -> {
                val r = index.shamar.resources[name]
                if (r == null) "**shamar resource** `$name`\n\n_Unknown._"
                else "**shamar resource** `$name`\n\n${r.label} · panel `${r.panel}`"
            }
            SymbolKind.SHAMAR_PAGE -> {
                val p = index.shamar.pages[name]
                if (p == null) "**shamar page** `$name`\n\n_Unknown._"
                else "**shamar page** `$name`\n\n${p.label} · panel `${p.panel}`"
            }
            SymbolKind.SHAMAR_WIDGET -> {
                val w = index.shamar.widgets[name]
                when {
                    w != null -> {
                        val kindLabel = w.kind?.let { " · $it" }.orEmpty()
                        "**shamar widget** `$name`\n\npanel `${w.panel}`$kindLabel"
                    }
                    name in index.shamar.widgetTypes -> "**shamar widget type** `$name`"
                    else -> "**shamar widget** `$name`\n\n_Unknown._"
                }
            }
            SymbolKind.SHAMAR_LITERAL -> {
                val family = receiver ?: "value"
                "**shamar** `${ShamarLiterals.detailFor(family)}` `$name`"
            }
            else -> {
                if (index.known(kind, name)) "**${kind.name.lowercase()}** `$name`"
                else null
            }
        }
    }
}
