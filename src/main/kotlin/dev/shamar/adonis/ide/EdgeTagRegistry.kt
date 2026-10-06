package dev.shamar.adonis.ide

/**
 * Single source of truth for Edge builtin tags + Hypermedia starter-kit props.
 *
 * File-based tag components (`@form`, `@field.root`) are accepted by the lexer
 * whenever they carry `(…)` args; this registry drives structure, completions,
 * and prop-key hints.
 */
object EdgeTagRegistry {
    /** Builtin names always colored even without `(…)`. */
    val BUILTIN: Set<String> = setOf(
        "if", "elseif", "else", "unless", "each",
        "component", "slot", "include", "includeIf", "includeWhen", "includeUnless",
        "section", "layout", "page",
        "inject", "eval", "let", "assign",
        "vite", "stack", "pushTo", "svg",
        "debugger", "newError", "dump",
        "wire", "persist",
        // Inertia root-template tags (void — never take @end).
        "inertia", "inertiaHead", "viteReactRefresh",
        // Hypermedia starter-kit tag components (accepted while typing, before `(`).
        "form", "link", "button", "avatar",
        "end",
        "endif", "endunless", "endeach", "endcomponent", "endslot",
        "endsection", "endlayout", "endwire", "endpersist",
    )

    val OPENERS: Set<String> = setOf(
        "if", "unless", "each", "component", "slot", "section", "layout",
        "wire", "persist", "page", "pushTo",
        "form",
    )

    val MID_BLOCK: Set<String> = setOf("elseif", "else")

    val VOID: Set<String> = setOf(
        "include", "includeIf", "includeWhen", "includeUnless",
        "svg", "vite", "inject", "eval", "let", "assign",
        "debugger", "newError", "dump", "stack",
        // @inertia() / @inertiaHead() / @viteReactRefresh() — self-closing package tags
        "inertia", "inertiaHead", "viteReactRefresh",
    )

    /** May self-close when args include a top-level comma (value form). */
    val INLINEABLE: Set<String> = setOf("section", "layout")

    val CLOSER: String = "end"

    val LEGACY_CLOSERS: Set<String> = setOf(
        "endif", "endunless", "endeach", "endcomponent", "endslot",
        "endsection", "endlayout", "endwire", "endpersist", "endforeach",
        "endfor", "endwhile", "endempty", "endisset", "show", "endslot",
    )

    /** Roots treated as tag components even without `(…)` (email guard). */
    val TAG_COMPONENT_ROOTS: Set<String> = setOf(
        "layouts", "components", "partials", "shamar", "wire",
        "field", "input", "select", "textarea", "checkbox", "radio", "alert",
    )

    val ALIASES: Map<String, String> = mapOf(
        "for" to "each",
        "foreach" to "each",
        "loop" to "each",
        "endif" to "end",
        "endeach" to "end",
    )

    /** Prop keys offered inside `@tag({ |` for Hypermedia starter components. */
    val PROP_KEYS: Map<String, List<String>> = mapOf(
        "form" to listOf("action", "method", "route", "routeParams", "routeOptions"),
        "link" to listOf("text", "route", "routeParams", "routeOptions", "href"),
        "button" to listOf("text", "type", "class"),
        "field.root" to listOf("name", "id"),
        "field.label" to listOf("text"),
        "input.control" to listOf("type", "value", "autocomplete", "minlength", "maxlength", "placeholder"),
        "select.control" to listOf("options"),
        "textarea.control" to listOf("rows", "placeholder"),
        "checkbox.group" to listOf("name"),
        "checkbox.control" to listOf("value"),
        "radio.group" to listOf("name"),
        "radio.control" to listOf("value"),
        "alert.root" to listOf("variant", "autoDismiss"),
        "alert.title" to listOf("text"),
        "alert.description" to listOf("text"),
        "avatar" to listOf("src", "initials", "alt"),
    )

    val HTTP_METHODS: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE")

    val ALERT_VARIANTS: List<String> = listOf("destructive", "success", "warning", "info")

    /** Object-prop name → completion kind for string values. */
    fun propValueKind(prop: String): SymbolKind? = when (prop) {
        "route", "signedRoute" -> SymbolKind.ROUTE
        "method", "variant" -> SymbolKind.EDGE_LITERAL
        else -> null
    }

    fun literalsForProp(prop: String): List<String> = when (prop) {
        "method" -> HTTP_METHODS
        "variant" -> ALERT_VARIANTS
        else -> emptyList()
    }

    fun propKeysForTag(tagName: String): List<String> {
        PROP_KEYS[tagName]?.let { return it }
        val root = tagName.substringBefore('.')
        PROP_KEYS[root]?.let { return it }
        // field.label → try field.label exact already; try suffix
        return PROP_KEYS.entries.firstOrNull { (k, _) ->
            tagName == k || tagName.endsWith(".$k") || tagName.endsWith(k)
        }?.value.orEmpty()
    }
}
