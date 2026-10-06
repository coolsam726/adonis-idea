package dev.shamar.adonis.ide

import com.intellij.openapi.project.Project

/**
 * Resolve model / table hints to migration columns (and auth user → User).
 * Lucid relation / query method vocabulary: hasMany, belongsTo, hasOne, manyToMany,
 * preload, withCount, where, whereIn, orderBy, select.
 *
 * When [project] is provided, column names are also enriched from the Database tool
 * according to [AdonisDbSettings] (migrations / connection / both).
 */
object AdonisModelResolver {
    const val AUTH_USER_SENTINEL = "__auth_user__"

    /** Lucid / Mongoose relation declaration helpers. */
    val RELATION_METHODS: Set<String> = setOf(
        "hasMany", "belongsTo", "hasOne", "manyToMany",
        "has_many", "belongs_to", "has_one", "many_to_many",
    )

    /** Query / preload helpers that take a relation or column name. */
    val QUERY_RELATION_METHODS: Set<String> = setOf(
        "preload", "withCount", "with", "load", "loadMissing",
        "has", "whereHas", "orWhereHas", "doesntHave",
        "with_", "load_missing", "where_has", "or_where_has", "doesnt_have",
    )

    val QUERY_COLUMN_METHODS: Set<String> = setOf(
        "where", "whereIn", "whereNotIn", "whereNull", "whereNotNull",
        "orWhere", "orderBy", "orderByDesc", "groupBy", "having",
        "select", "pluck", "increment", "decrement",
    )

    private val SKIP = setOf(
        "DB", "Schema", "BaseModel", "Model", "Database",
        "self", "this", "os", "re", "sys", "json",
        // Migration Blueprint parameter — never treat as a Lucid model.
        "table", "define",
    )

    /** Builder / query chain segments that do not change the model. */
    private val CHAIN_NOISE = setOf(
        "query", "factory", "create", "update", "fill", "merge",
        "where", "orWhere", "whereIn", "whereNotIn", "orderBy", "orderByDesc",
        "groupBy", "having", "select", "preload", "withCount", "with",
        "load", "loadMissing", "first", "get", "find", "all", "paginate",
        "limit", "offset", "take", "skip", "latest", "oldest", "pluck",
        "value", "count", "exists", "firstOrCreate", "updateOrCreate",
        "firstOrNew", "make", "save", "delete", "refresh", "related",
        // snake_case noise
        "new_query", "or_where", "order_by", "group_by", "with_",
        "load_missing", "first_or_create", "update_or_create",
    )

    /** Known Lucid / ORM model class names from the index. */
    fun modelNames(index: AdonisIndex): Set<String> {
        val fromTables = index.tables.values.mapNotNull { it.model }.filter { it.isNotBlank() }.toSet()
        return fromTables + index.modelMetadata.keys
    }

    /** Prefer migration table for [hint] (table name or model class). */
    fun resolveTable(index: AdonisIndex, hint: String?): String? {
        if (hint.isNullOrBlank()) return null
        val key = if (hint == AUTH_USER_SENTINEL) authUserModel(index) else peelModelHint(hint)
        if (key in index.tables) return key
        for ((name, table) in index.tables) {
            if (table.model.equals(key, ignoreCase = true)) return name
        }
        // User → users, Post → posts
        val plural = pluralize(key.replaceFirstChar { it.lowercase() })
        if (plural in index.tables) return plural
        val singular = key.removeSuffix("s")
        if (singular in index.tables) return singular
        index.modelMetadata.entries.firstOrNull { (cls, m) ->
            cls.equals(key, true) || m.module.equals(key, true) ||
                m.table.equals(key, true)
        }?.let { (cls, meta) ->
            if (meta.table.isNotBlank() && meta.table in index.tables) return meta.table
            for ((name, table) in index.tables) {
                if (table.model.equals(cls, ignoreCase = true)) return name
            }
            val derived = pluralize(cls.replaceFirstChar { it.lowercase() })
            if (derived in index.tables) return derived
        }
        return null
    }

    /**
     * Columns for a model/table hint: migration/model catalog, optionally merged with
     * live Database tool columns (see [AdonisDbSettings]).
     *
     * Unknown receivers (e.g. Blueprint ``table.``) return empty — never dump every
     * column in the database.
     */
    fun columnsFor(
        index: AdonisIndex,
        hint: String?,
        project: Project? = null,
        sourceOverride: AdonisColumnSource? = null,
        liveOverride: Set<String>? = null,
    ): Set<String> {
        if (hint.isNullOrBlank()) return emptySet()
        if (hint == AUTH_USER_SENTINEL) {
            return mergeWithLive(
                index,
                columnsForResolved(index, authUserModel(index)),
                resolveTable(index, AUTH_USER_SENTINEL),
                project,
                sourceOverride,
                liveOverride,
            )
        }
        val peeled = peelModelHint(hint)
        if (peeled in SKIP || peeled.equals("table", ignoreCase = true)) {
            return emptySet()
        }
        val tableName = resolveTable(index, hint)
        val migrationOrModel = when {
            tableName != null && !index.tables[tableName]?.columns.isNullOrEmpty() ->
                index.tables[tableName]!!.columns.keys
            else -> columnsForResolved(index, peeled)
        }
        return mergeWithLive(index, migrationOrModel, tableName, project, sourceOverride, liveOverride)
    }

    /**
     * Pure merge used by tests and [columnsFor].
     * [DATABASE] falls back to migrations when the live set is empty.
     */
    fun mergeColumnSources(
        migrations: Set<String>,
        live: Set<String>,
        source: AdonisColumnSource,
    ): Set<String> = when (source) {
        AdonisColumnSource.MIGRATIONS -> migrations
        AdonisColumnSource.DATABASE -> if (live.isNotEmpty()) live else migrations
        AdonisColumnSource.BOTH -> AdonisDbIntrospection.mergeColumns(migrations, live)
    }

    private fun mergeWithLive(
        index: AdonisIndex,
        migrations: Set<String>,
        tableName: String?,
        project: Project?,
        sourceOverride: AdonisColumnSource?,
        liveOverride: Set<String>?,
    ): Set<String> {
        val source = sourceOverride
            ?: project?.let { AdonisDbSettings.getInstance(it).columnSource }
            ?: AdonisColumnSource.MIGRATIONS
        if (source == AdonisColumnSource.MIGRATIONS || tableName.isNullOrBlank()) {
            return migrations
        }
        val live = liveOverride ?: AdonisDbColumnCache.liveColumns(project, tableName)
        return mergeColumnSources(migrations, live, source)
    }

    /** ORM detail for a model/table hint (`lucid`, `mongoose`, …). */
    fun ormFor(index: AdonisIndex, hint: String?): String? {
        if (hint.isNullOrBlank()) return index.framework.orm
        val tableName = resolveTable(index, hint)
        if (tableName != null) {
            val detail = index.tables[tableName]?.detail?.ifBlank { null }
            if (detail != null) return detail
        }
        val peeled = if (hint == AUTH_USER_SENTINEL) authUserModel(index) else peelModelHint(hint)
        index.modelMetadata[peeled]?.orm?.ifBlank { null }?.let { return it }
        return index.framework.orm?.ifBlank { null }
    }

    private fun columnsForResolved(index: AdonisIndex, model: String): Set<String> {
        index.modelMetadata[model]?.let { meta ->
            return (meta.fillable + meta.guarded + meta.hidden + meta.casts.keys).toSet()
        }
        index.modelMetadata.entries.firstOrNull { (_, m) -> m.module.equals(model, true) }?.let {
            val meta = it.value
            return (meta.fillable + meta.guarded + meta.hidden + meta.casts.keys).toSet()
        }
        return emptySet()
    }

    fun authUserModel(index: AdonisIndex): String {
        index.tables.values.firstOrNull { it.model.equals("User", true) }?.model?.let { return it }
        if ("User" in index.modelMetadata) return "User"
        index.modelMetadata.keys.firstOrNull { it.equals("User", true) }?.let { return it }
        return "User"
    }

    /**
     * Strip query/factory chain noise: ``Author.query.where`` → ``Author``,
     * ``AuthorFactory`` → ``Author``.
     */
    fun peelModelHint(hint: String): String {
        var head = hint.substringAfterLast('.').ifBlank { hint }
        if (hint.contains('.')) {
            val first = hint.substringBefore('.')
            if (first.isNotEmpty() && first[0].isUpperCase()) {
                head = first
            }
        }
        if (head.endsWith("Factory") && head.length > "Factory".length) {
            val model = head.removeSuffix("Factory")
            if (model.isNotEmpty() && model[0].isUpperCase()) return model
        }
        if (head in CHAIN_NOISE) return hint.substringBefore('.')
        return head
    }

    /**
     * Infer model class for a simple receiver name using text before the caret
     * (annotations / assignments) plus ``user`` → ``User`` heuristic.
     */
    fun inferModel(index: AdonisIndex, beforeCaret: String, receiver: String): String? {
        if (receiver.isBlank() || receiver in SKIP) return null
        if (receiver == AUTH_USER_SENTINEL) return authUserModel(index)
        val models = modelNames(index)
        val peeled = peelModelHint(receiver)
        if (peeled.isNotEmpty() && peeled[0].isUpperCase() && (models.isEmpty() || peeled in models)) {
            return peeled
        }
        if (peeled != receiver && peeled in models) return peeled

        val inferred = inferFromAssignment(beforeCaret, receiver)
            ?: inferFromAnnotation(beforeCaret, receiver)
            ?: inferFromFactory(beforeCaret, receiver)
            ?: inferChainHead(beforeCaret)
        if (inferred != null) {
            val model = peelModelHint(inferred)
            if (models.isEmpty() || model in models) return model
        }
        val camel = receiver.split('_').joinToString("") { part ->
            part.replaceFirstChar { ch -> ch.uppercase() }
        }
        if (camel in models) return camel
        if (receiver.equals("user", true) && models.any { it.equals("User", true) }) {
            return authUserModel(index)
        }
        return null
    }

    /**
     * ``Author.query().where(`` / ``Author.factory().create(`` — last model class
     * that opened a chain ending at the caret.
     */
    fun inferChainHead(before: String): String? {
        val re = Regex(
            """\b([A-Z][A-Za-z0-9_]*)\.(?:query|factory)\s*\(""",
        )
        return re.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    private fun inferFromAssignment(before: String, receiver: String): String? {
        val re = Regex(
            """\b${Regex.escape(receiver)}\s*=\s*(?:await\s+)?([A-Z][A-Za-z0-9_]*)\s*(?:\(|\.|Factory)""",
        )
        return re.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    private fun inferFromFactory(before: String, receiver: String): String? {
        val factoryCls = Regex(
            """\b${Regex.escape(receiver)}\s*=\s*(?:await\s+)?([A-Z][A-Za-z0-9_]*)Factory\s*\(""",
        )
        factoryCls.findAll(before).lastOrNull()?.groupValues?.get(1)?.let { return it }
        val modelFactory = Regex(
            """\b${Regex.escape(receiver)}\s*=\s*(?:await\s+)?([A-Z][A-Za-z0-9_]*)\s*\.\s*factory\s*\(""",
        )
        return modelFactory.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    private fun inferFromAnnotation(before: String, receiver: String): String? {
        // TypeScript: `const user: User` / `user: User | null`
        val re = Regex(
            """\b${Regex.escape(receiver)}\s*:\s*([A-Z][A-Za-z0-9_]*)""",
        )
        return re.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    fun pluralize(singular: String): String {
        val s = singular.lowercase()
        return when {
            s.endsWith("y") && s.length > 1 && s[s.length - 2] !in "aeiou" ->
                s.dropLast(1) + "ies"
            s.endsWith("s") || s.endsWith("x") || s.endsWith("ch") || s.endsWith("sh") ->
                s + "es"
            else -> s + "s"
        }
    }
}
