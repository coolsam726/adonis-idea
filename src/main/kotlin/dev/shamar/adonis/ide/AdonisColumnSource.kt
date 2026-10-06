package dev.shamar.adonis.ide

/**
 * Where column names for completions come from (Laravel IDEA–style).
 */
enum class AdonisColumnSource {
    /** Models + `database/migrations` only (default offline). */
    MIGRATIONS,

    /** JetBrains Database tool DataSource schema only (falls back to migrations if empty). */
    DATABASE,

    /** Union of migrations/models and live Database tool schema. */
    BOTH,
    ;

    companion object {
        fun fromStorage(raw: String?): AdonisColumnSource =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BOTH
    }
}
