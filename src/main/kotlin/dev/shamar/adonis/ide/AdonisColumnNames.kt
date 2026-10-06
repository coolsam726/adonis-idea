package dev.shamar.adonis.ide

/**
 * Lucid / Mongoose column naming helpers.
 *
 * Lucid stores DB columns in snake_case but model properties are camelCase.
 * Query builders accept either form; attribute access (`user.countryId`) uses camelCase.
 * Mongoose schema paths are exact keys (usually camelCase) — no forced dual aliases.
 */
object AdonisColumnNames {
    fun toSnake(name: String): String {
        if (name.isBlank()) return name
        return name
            .replace(Regex("""([a-z0-9])([A-Z])"""), "$1_$2")
            .replace(Regex("""([A-Z]+)([A-Z][a-z])"""), "$1_$2")
            .replace('-', '_')
            .lowercase()
    }

    fun toCamel(name: String): String {
        if (name.isBlank() || !name.contains('_') && !name.contains('-')) return name
        val parts = name.split('_', '-').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return name
        return parts.first().lowercase() + parts.drop(1).joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Completions for `where` / `orderBy` / `select` string sites.
     * Lucid: snake_case first, then camelCase. Mongoose: indexed names as-is.
     */
    fun forQuery(names: Collection<String>, orm: String?): List<String> {
        if (names.isEmpty()) return emptyList()
        if (orm.equals("mongoose", ignoreCase = true)) {
            return names.distinct().sorted()
        }
        val snakes = linkedSetOf<String>()
        val camels = linkedSetOf<String>()
        for (n in names) {
            snakes.add(toSnake(n))
            camels.add(toCamel(n))
        }
        return (snakes + camels).toList()
    }

    /**
     * Completions for `user.countryId` / fillable keys — prefer camelCase.
     * Applies to Lucid and Mongoose (schema property names).
     */
    fun forAttr(names: Collection<String>): List<String> =
        names.map { toCamel(it) }.distinct()
}
