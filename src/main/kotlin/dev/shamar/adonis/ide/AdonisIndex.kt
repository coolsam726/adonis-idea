package dev.shamar.adonis.ide

/**
 * In-memory symbol index produced by the bundled Node indexer (`indexer/index.mjs --json`).
 *
 * Name sets drive completions; [Located] / path maps drive Ctrl-click navigation.
 * Optional Shamar / Wire fields are empty when those layers are absent — never requires
 * any Shamar npm packages at runtime.
 */
data class AdonisIndex(
    val basePath: String = "",
    val ok: Boolean = false,
    val error: String? = null,
    val framework: FrameworkEntry = FrameworkEntry(),
    /** Dotted / slash view name → absolute template path. */
    val views: Map<String, String> = emptyMap(),
    val routes: Map<String, RouteEntry> = emptyMap(),
    val configKeys: Set<String> = emptySet(),
    /** Config file stem (`app`) → absolute path. */
    val configFiles: Map<String, String> = emptyMap(),
    /** Dotted config key → declaration location (`app.env` → line of `"env"`). */
    val configLocations: Map<String, Located> = emptyMap(),
    val translationKeys: Set<String> = emptySet(),
    val middlewareAliases: Set<String> = emptySet(),
    val envKeys: Map<String, EnvEntry> = emptyMap(),
    /** Suggested values for env keys (``QUEUE_CONNECTION`` → sync/redis/…). */
    val envOptions: Map<String, List<String>> = emptyMap(),
    val tables: Map<String, TableEntry> = emptyMap(),
    val modelMetadata: Map<String, ModelEntry> = emptyMap(),
    val relations: Map<String, List<String>> = emptyMap(),
    val casts: Set<String> = emptySet(),
    /** Edge / Blade-style component / x- tag name → path. */
    val components: Map<String, String> = emptyMap(),
    val gates: Set<String> = emptySet(),
    val disks: Set<String> = emptySet(),
    val queues: Set<String> = emptySet(),
    val caches: Set<String> = emptySet(),
    val mailers: Set<String> = emptySet(),
    val inertiaPages: Set<String> = emptySet(),
    val aceCommands: Set<String> = emptySet(),
    val validationRules: Set<String> = emptySet(),
    val directives: Set<String> = emptySet(),
    val viewHelpers: Map<String, ViewVarEntry> = emptyMap(),
    val viewShared: Map<String, ViewVarEntry> = emptyMap(),
    val viewData: Map<String, Map<String, ViewVarEntry>> = emptyMap(),
    val viteEntries: Map<String, String> = emptyMap(),
    val controllerActions: Map<String, List<String>> = emptyMap(),
    /**
     * Controller class or `Class@action` → declaration location.
     * Keys use the exported class name (`SessionController`, `SessionController@store`).
     */
    val controllerLocations: Map<String, Located> = emptyMap(),
    /** Wire component name → class path / view / props / methods. */
    val wireComponents: Map<String, WireEntry> = emptyMap(),
    val shamar: ShamarEntry = ShamarEntry(),
) {
    data class Located(val path: String? = null, val line: Int = 0)

    data class FrameworkEntry(
        val adonis: Boolean = true,
        val shamar: Boolean = false,
        /** True when `@shamar/wire` / `app/wire` / wire views are present. */
        val wire: Boolean = false,
        val orm: String = "unknown",
    )

    /** Offer `@wire` / `@persist` only when the Wire layer is detected. */
    val wireEnabled: Boolean
        get() = framework.wire || wireComponents.isNotEmpty()

    data class WireEntry(
        val path: String? = null,
        val view: String? = null,
        val props: List<String> = emptyList(),
        val methods: List<String> = emptyList(),
    )

    data class ShamarEntry(
        val panels: List<String> = emptyList(),
        val resources: Map<String, ShamarResourceEntry> = emptyMap(),
        val pages: Map<String, ShamarPageEntry> = emptyMap(),
        val widgets: Map<String, ShamarWidgetEntry> = emptyMap(),
        val navGroups: List<String> = emptyList(),
        val fieldTypes: List<String> = emptyList(),
        val columnTypes: List<String> = emptyList(),
        val widgetTypes: List<String> = emptyList(),
        val icons: List<String> = emptyList(),
    )

    data class ShamarWidgetEntry(
        val className: String = "",
        val panel: String = "",
        val kind: String? = null,
        val path: String? = null,
    )

    data class ShamarResourceEntry(
        val className: String = "",
        val panel: String = "",
        val slug: String = "",
        val label: String = "",
        val navigationGroup: String? = null,
        val icon: String? = null,
        val model: String? = null,
        val path: String? = null,
    )

    data class ShamarPageEntry(
        val className: String = "",
        val panel: String = "",
        val slug: String = "",
        val label: String = "",
        val navigationGroup: String? = null,
        val icon: String? = null,
        val path: String? = null,
    )

    data class EnvEntry(
        val path: String? = null,
        val line: Int = 0,
        val kind: String = "",
        val detail: String = "",
        val usedBy: List<String> = emptyList(),
    )

    data class ViewVarEntry(
        val path: String? = null,
        val line: Int = 0,
        val kind: String = "data",
    )

    data class RouteEntry(
        val uri: String,
        val methods: List<String>,
        val path: String? = null,
        val line: Int = 0,
    )

    data class TableEntry(
        val columns: Map<String, Located> = emptyMap(),
        val detail: String = "",
        val path: String? = null,
        val line: Int = 0,
        /** Lucid / ORM model class bound to this table (`User` for `users`). */
        val model: String? = null,
    )

    data class ModelEntry(
        val fillable: List<String> = emptyList(),
        val guarded: List<String> = emptyList(),
        val hidden: List<String> = emptyList(),
        val casts: Map<String, String> = emptyMap(),
        val relations: List<String> = emptyList(),
        /** Relation method name → 1-based line in the model file. */
        val relationLines: Map<String, Int> = emptyMap(),
        val module: String = "",
        val path: String = "",
        val table: String = "",
        val orm: String = "",
    )

    fun known(kind: SymbolKind, name: String): Boolean = when (kind) {
        SymbolKind.ROUTE -> routes.containsKey(name)
        SymbolKind.VIEW -> views.containsKey(name)
        SymbolKind.CONFIG -> configKeys.contains(name)
        SymbolKind.TRANSLATION -> translationKeys.isNotEmpty() && translationKeys.contains(name)
        SymbolKind.MIDDLEWARE -> middlewareAliases.contains(name)
        SymbolKind.ENV -> envKeys.containsKey(name)
        SymbolKind.ENV_VALUE -> true
        SymbolKind.TABLE -> tables.containsKey(name)
        SymbolKind.GATE -> gates.contains(name)
        SymbolKind.COMPONENT ->
            components.containsKey(name) || views.containsKey("components.$name")
        SymbolKind.VALIDATION -> {
            val rule = name.substringBefore(":")
            validationRules.contains(rule)
        }
        SymbolKind.DISK -> disks.contains(name)
        SymbolKind.QUEUE -> queues.contains(name)
        SymbolKind.CACHE -> caches.contains(name)
        SymbolKind.MAILER -> mailers.contains(name)
        SymbolKind.INERTIA -> inertiaPages.contains(name)
        SymbolKind.ACE -> aceCommands.contains(name)
        SymbolKind.VITE -> viteEntries.containsKey(name) || views.containsKey(name)
        SymbolKind.CAST -> casts.contains(name)
        SymbolKind.TEMPLATE_VAR -> templateVarNames().contains(name.substringBefore('.'))
        SymbolKind.CONTROLLER_ACTION -> {
            val controller = name.substringBefore('@')
            val action = name.substringAfter('@', missingDelimiterValue = "")
            when {
                controllerActions.containsKey(name) -> true
                controllerActions.containsKey(controller) && action.isEmpty() -> true
                controllerActions[controller]?.contains(action) == true -> true
                else -> false
            }
        }
        SymbolKind.WIRE -> wireComponents.containsKey(name)
        SymbolKind.WIRE_PROP -> wireComponents.values.any { name in it.props }
        SymbolKind.WIRE_METHOD -> wireComponents.values.any { name in it.methods }
        SymbolKind.SHAMAR_RESOURCE -> shamar.resources.containsKey(name)
        SymbolKind.SHAMAR_PAGE -> shamar.pages.containsKey(name)
        SymbolKind.SHAMAR_WIDGET ->
            shamar.widgets.containsKey(name) || shamar.widgetTypes.contains(name)
        SymbolKind.SHAMAR_FIELD -> shamar.fieldTypes.contains(name) ||
            shamar.resources.values.any { it.slug == name || it.label == name }
        SymbolKind.SHAMAR_COLUMN -> shamar.columnTypes.contains(name)
        SymbolKind.SHAMAR_NAV -> shamar.navGroups.contains(name)
        SymbolKind.COLUMN, SymbolKind.RELATION, SymbolKind.DIRECTIVE, SymbolKind.ATTR,
        SymbolKind.MODEL_ATTR, SymbolKind.EDGE_LITERAL, SymbolKind.EDGE_PROP_KEY,
        SymbolKind.SHAMAR_LITERAL -> true
    }

    fun templateVarNames(): Set<String> =
        viewHelpers.keys + viewShared.keys + viewData.values.flatMap { it.keys }.toSet()

    fun optionsForEnvKey(key: String): List<String> {
        envOptions[key]?.let { return it }
        val aliases = mapOf(
            "QUEUE_DRIVER" to "QUEUE_CONNECTION",
            "QUEUE_CONNECTION" to "QUEUE_DRIVER",
            "CACHE_DRIVER" to "CACHE_STORE",
            "CACHE_STORE" to "CACHE_DRIVER",
            "BROADCAST_DRIVER" to "BROADCAST_CONNECTION",
            "BROADCAST_CONNECTION" to "BROADCAST_DRIVER",
        )
        val alt = aliases[key] ?: return emptyList()
        return envOptions[alt] ?: emptyList()
    }

    fun viewNameForPath(absolutePath: String): String? {
        if (absolutePath.isBlank()) return null
        val normalized = absolutePath.replace('\\', '/')
        views.entries.firstOrNull { (_, path) ->
            path.replace('\\', '/') == normalized
        }?.let { return it.key }
        return views.entries.firstOrNull { (_, path) ->
            normalized.endsWith(path.replace('\\', '/')) ||
                path.replace('\\', '/').endsWith(normalized)
        }?.key
    }

    fun wireNameForPath(absolutePath: String): String? {
        if (absolutePath.isBlank()) return null
        val normalized = absolutePath.replace('\\', '/')
        return wireComponents.entries.firstOrNull { (_, entry) ->
            entry.path?.replace('\\', '/') == normalized ||
                entry.view?.replace('\\', '/') == normalized
        }?.key
    }

    /**
     * Map a route-tuple receiver (`controllers.Session`, `Session`, `SessionController`)
     * to the indexed controller class name.
     */
    fun resolveControllerName(receiver: String?): String? {
        if (receiver.isNullOrBlank()) return null
        val simple = receiver.substringAfterLast('.').trim()
        if (simple.isEmpty()) return null
        if (controllerActions.containsKey(simple)) return simple
        val asController =
            if (simple.endsWith("Controller")) simple else "${simple}Controller"
        if (controllerActions.containsKey(asController)) return asController
        return controllerActions.keys.firstOrNull { key ->
            key.equals(simple, ignoreCase = true) ||
                key.equals(asController, ignoreCase = true) ||
                key.removeSuffix("Controller").equals(simple, ignoreCase = true)
        }
    }

    companion object {
        fun empty(error: String? = null) = AdonisIndex(ok = false, error = error)
    }
}

enum class SymbolKind {
    ROUTE, VIEW, CONFIG, TRANSLATION, MIDDLEWARE, ENV, ENV_VALUE, TABLE, COLUMN,
    RELATION, CAST, GATE, COMPONENT, VALIDATION, DISK, QUEUE, CACHE, MAILER,
    INERTIA, ACE, VITE, DIRECTIVE, TEMPLATE_VAR, CONTROLLER_ACTION,
    /** Instance attribute: ``user.name`` / ``auth().user.email``. */
    ATTR,
    /** Model list/dict keys: ``fillable`` / ``guarded`` / ``casts`` keys. */
    MODEL_ATTR,
    /** Wire component name (`@wire('counter')` / `<wire:counter`). */
    WIRE,
    /** Dollar-wire prop access (`$` + `wire.count`). */
    WIRE_PROP,
    /** Dollar-wire method call (`$` + `wire.save(`). */
    WIRE_METHOD,
    SHAMAR_RESOURCE,
    SHAMAR_PAGE,
    SHAMAR_WIDGET,
    SHAMAR_FIELD,
    SHAMAR_COLUMN,
    SHAMAR_NAV,
    /** Shamar builder string enums (`openIn`, `presentation`, page modes, Stat colors). */
    SHAMAR_LITERAL,
    /** Fixed Edge / starter-kit string literals (`method`, `variant`, …). */
    EDGE_LITERAL,
    /** Object-literal prop keys inside `@tag({ | })`. Receiver = tag name. */
    EDGE_PROP_KEY,
}
