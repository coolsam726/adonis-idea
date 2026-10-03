package dev.shamar.adonis.ide

/**
 * Pure completion catalog — driven by [AdonisIndex] + [AdonisModelResolver].
 */
object AdonisCompletionCatalog {
    fun symbolsFor(
        index: AdonisIndex,
        site: CallSiteDetector.Site,
        beforeCaret: String = "",
    ): List<Pair<String, String>> {
        return when (site.kind) {
            SymbolKind.ROUTE -> index.routes
                .filterKeys { !it.startsWith("__path:") }
                .map { (n, r) -> n to "${r.methods.joinToString("|")} ${r.uri}".trim() }
            SymbolKind.VIEW -> index.views.keys.map { it to "view" }
            SymbolKind.CONFIG -> index.configKeys.map { it to "config" }
            SymbolKind.TRANSLATION -> index.translationKeys.map { it to "trans" }
            SymbolKind.MIDDLEWARE -> index.middlewareAliases.map { it to "middleware" }
            SymbolKind.ENV -> index.envKeys.map { (n, e) ->
                n to (e.detail.ifBlank { "env" })
            }
            SymbolKind.ENV_VALUE -> {
                val key = site.receiver ?: return emptyList()
                index.optionsForEnvKey(key).map { it to "$key option" }
            }
            SymbolKind.TABLE -> index.tables.map { (n, t) -> n to (t.detail.ifBlank { "table" }) }
            SymbolKind.COLUMN, SymbolKind.MODEL_ATTR, SymbolKind.ATTR -> {
                val hint = resolveHint(index, site, beforeCaret)
                AdonisModelResolver.columnsFor(index, hint).map { it to columnDetail(index, hint, it) }
            }
            SymbolKind.RELATION -> {
                val hint = resolveHint(index, site, beforeCaret)
                relationsFor(index, hint).map { it to "relation" }
            }
            SymbolKind.CAST -> index.casts.map { it to "cast" }
            SymbolKind.GATE -> index.gates.map { it to "gate" }
            SymbolKind.COMPONENT -> {
                val fromComponents = index.components.keys.map { it to "component" }
                val fromViews = index.views.keys
                    .filter { it.startsWith("components.") || it.startsWith("components/") }
                    .map { it.removePrefix("components.").removePrefix("components/") to "component" }
                (fromComponents + fromViews).distinctBy { it.first }
            }
            SymbolKind.VALIDATION -> index.validationRules.map { it to "rule" }
            SymbolKind.DISK -> index.disks.map { it to "disk" }
            SymbolKind.QUEUE -> index.queues.map { it to "queue" }
            SymbolKind.CACHE -> index.caches.map { it to "cache" }
            SymbolKind.MAILER -> index.mailers.map { it to "mailer" }
            SymbolKind.INERTIA -> index.inertiaPages.map { it to "inertia" }
            SymbolKind.ACE -> index.aceCommands.map { it to "ace" }
            SymbolKind.VITE -> (index.viteEntries.keys + index.views.keys).map { it to "asset" }
            SymbolKind.DIRECTIVE -> directiveCompletions(index)
            SymbolKind.TEMPLATE_VAR -> index.templateVarNames().map { it to "var" }
            SymbolKind.CONTROLLER_ACTION -> {
                val controller = index.resolveControllerName(site.receiver)
                val actions = if (controller != null) {
                    index.controllerActions[controller].orEmpty()
                } else {
                    index.controllerActions.values.flatten().distinct()
                }
                actions.map { it to "action" }
            }
            SymbolKind.WIRE -> index.wireComponents.map { (n, w) ->
                n to (w.path?.substringAfterLast('/') ?: "wire")
            }
            SymbolKind.WIRE_PROP -> wireMembers(index, site, props = true)
            SymbolKind.WIRE_METHOD -> wireMembers(index, site, props = false)
            SymbolKind.SHAMAR_RESOURCE -> index.shamar.resources.map { (n, r) ->
                n to (r.label.ifBlank { r.slug }.ifBlank { "resource" })
            }
            SymbolKind.SHAMAR_PAGE -> index.shamar.pages.map { (n, p) ->
                n to (p.label.ifBlank { p.slug }.ifBlank { "page" })
            }
            SymbolKind.SHAMAR_FIELD -> index.shamar.fieldTypes.map { it to "field" }
            SymbolKind.SHAMAR_COLUMN -> index.shamar.columnTypes.map { it to "column" }
            SymbolKind.SHAMAR_NAV -> index.shamar.navGroups.map { it to "nav" }
            SymbolKind.EDGE_LITERAL -> {
                val prop = site.receiver ?: return emptyList()
                EdgeTagRegistry.literalsForProp(prop).map { it to prop }
            }
            SymbolKind.EDGE_PROP_KEY -> {
                val tag = site.receiver ?: return emptyList()
                EdgeTagRegistry.propKeysForTag(tag).map { it to "prop" }
            }
        }
    }

    private fun wireMembers(
        index: AdonisIndex,
        site: CallSiteDetector.Site,
        props: Boolean,
    ): List<Pair<String, String>> {
        val wireName = site.receiver
        val entries = if (wireName != null) {
            listOfNotNull(index.wireComponents[wireName])
        } else {
            index.wireComponents.values.toList()
        }
        val detail = if (props) "prop" else "method"
        return entries.flatMap { entry ->
            val names = if (props) entry.props else entry.methods
            names.map { it to detail }
        }.distinctBy { it.first }
    }

    private fun resolveHint(
        index: AdonisIndex,
        site: CallSiteDetector.Site,
        beforeCaret: String,
    ): String? {
        val raw = site.receiver ?: return null
        if (raw == AdonisModelResolver.AUTH_USER_SENTINEL) {
            return AdonisModelResolver.AUTH_USER_SENTINEL
        }
        if (site.kind == SymbolKind.MODEL_ATTR) {
            enclosingModelClass(beforeCaret)?.let { return it }
        }
        return AdonisModelResolver.inferModel(index, beforeCaret, raw)
            ?: AdonisModelResolver.peelModelHint(raw)
    }

    private fun enclosingModelClass(before: String): String? {
        val re = Regex("""class\s+([A-Z][A-Za-z0-9_]*)\s*(?:extends|implements|[:({\[])""")
        return re.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    private fun columnDetail(
        index: AdonisIndex,
        hint: String?,
        @Suppress("UNUSED_PARAMETER") column: String,
    ): String {
        val table = AdonisModelResolver.resolveTable(index, hint) ?: return "column"
        return "column · $table"
    }

    /**
     * Edge `@` completions: always merge lexer-known names with the index, keep
     * `@each` visible as the loop directive, and expose `for` / `loop` /
     * `foreach` aliases that insert `each`.
     */
    fun directiveCompletions(index: AdonisIndex): List<Pair<String, String>> {
        val names = (index.directives + EdgeDirectives.NAMES).toMutableSet()
        // Drop bang-prefixed indexer leftovers; we re-add the known bang form below.
        names.removeAll { it.startsWith("!") }
        // Hide Blade-style closers from the default `@` list (aliases still insert `@end`).
        names.removeAll { it.startsWith("end") && it != "end" }
        val out = linkedMapOf<String, String>()
        for (name in names.sorted()) {
            val detail = EdgeDirectiveSnippets.specFor(name)?.detail ?: when (name) {
                "end" -> "close"
                else -> "directive"
            }
            out[name] = detail
        }
        // File-based tag components: `@form`, `@field.root`, `@layouts.app`, …
        for (tag in tagComponentNames(index)) {
            out.putIfAbsent(tag, "component")
        }
        // Always seed starter-kit tags + snippet names (index may not list them yet).
        for (name in EdgeDirectiveSnippets.snippetNames()) {
            val detail = EdgeDirectiveSnippets.specFor(name)?.detail ?: "directive"
            out.putIfAbsent(name, detail)
        }
        for (key in EdgeTagRegistry.PROP_KEYS.keys) {
            out.putIfAbsent(key, "component")
            val root = key.substringBefore('.')
            if (root != key) out.putIfAbsent(root, "component")
        }
        out.putIfAbsent("!component", "component")
        out.putIfAbsent("!button", "component")
        out.putIfAbsent("!link", "component")
        for ((alias, real) in EdgeDirectives.ALIASES) {
            // Hide Blade-style `endif` / `endeach` aliases from the `@` list.
            if (alias.startsWith("end")) continue
            if (real in EdgeDirectives.NAMES || real in names) {
                out.putIfAbsent(alias, "→ @$real")
            }
        }
        return out.map { (n, d) -> n to d }
    }

    /** Short + dotted tag names from indexed components / component views. */
    fun tagComponentNames(index: AdonisIndex): Set<String> {
        val out = linkedSetOf<String>()
        for (key in index.components.keys) {
            val n = key.removePrefix("components.").removePrefix("components/")
                .replace('/', '.')
            if (n.isNotBlank()) out.add(n)
        }
        for (key in index.views.keys) {
            when {
                key.startsWith("components.") || key.startsWith("components/") -> {
                    val n = key.removePrefix("components.").removePrefix("components/")
                        .replace('/', '.')
                    if (n.isNotBlank()) out.add(n)
                }
                key.startsWith("layouts.") || key.startsWith("layouts/") -> {
                    val n = "layouts." + key.removePrefix("layouts.").removePrefix("layouts/")
                        .replace('/', '.')
                    out.add(n)
                }
                key.startsWith("partials.") || key.startsWith("partials/") -> {
                    val n = "partials." + key.removePrefix("partials.").removePrefix("partials/")
                        .replace('/', '.')
                    out.add(n)
                }
            }
        }
        return out
    }

    /** @deprecated Prefer [AdonisModelResolver.columnsFor]; kept for tests. */
    fun columnsFor(index: AdonisIndex, receiver: String?): Set<String> =
        AdonisModelResolver.columnsFor(index, receiver)

    fun relationsFor(index: AdonisIndex, receiver: String?): Set<String> {
        if (receiver != null) {
            val hint = if (receiver == AdonisModelResolver.AUTH_USER_SENTINEL) {
                AdonisModelResolver.authUserModel(index)
            } else {
                receiver.substringAfterLast('.')
            }
            index.relations[hint]?.let { return it.toSet() }
            index.modelMetadata[hint]?.relations?.let { return it.toSet() }
            index.modelMetadata.entries.firstOrNull { (cls, m) ->
                cls.equals(hint, true) || m.module.equals(hint, true)
            }?.value?.relations?.let { return it.toSet() }
        }
        return index.relations.values.flatten().toSet()
    }
}
