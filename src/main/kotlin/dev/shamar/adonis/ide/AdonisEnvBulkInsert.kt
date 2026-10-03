package dev.shamar.adonis.ide

/**
 * Bulk ``MAIL_*``-style env key insertion for dotenv completion.
 */
object AdonisEnvBulkInsert {
    data class Offer(
        /** Lookup string used for prefix matching (e.g. ``MAIL_``). */
        val lookupString: String,
        val presentableText: String,
        val keys: List<String>,
    )

    /**
     * Keys that start with [prefix] (case-insensitive). Empty prefix → no bulk offer.
     */
    fun matchingKeys(allKeys: Collection<String>, prefix: String): List<String> {
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()
        return allKeys
            .filter { it.startsWith(p, ignoreCase = true) }
            .distinct()
            .sorted()
    }

    /**
     * When 2+ keys share [prefix], offer a bulk insert. Uses an uppercase stem
     * for the label (``MAIL`` → ``Insert all MAIL_*``).
     */
    fun offer(allKeys: Collection<String>, prefix: String): Offer? {
        val matches = matchingKeys(allKeys, prefix)
        if (matches.size <= 1) return null
        val stem = prefix.trim().trimEnd('_').uppercase()
        if (stem.isEmpty()) return null
        val lookup = if (prefix.endsWith("_")) prefix else "${prefix}_"
        return Offer(
            lookupString = lookup,
            presentableText = "Insert all ${stem}_* (${matches.size} keys)",
            keys = matches,
        )
    }

    /** Dotenv body: one ``KEY=`` per line (values left blank for the author). */
    fun dotenvInsertion(keys: List<String>): String =
        keys.joinToString("\n") { "$it=" }
}
