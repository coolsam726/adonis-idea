package com.adonis.ide

/**
 * Safe rename planning for Adonis string symbols (routes, views, config keys,
 * components, env keys).
 *
 * Pure text planning — IntelliJ applies the edits via [AdonisRenameProcessor].
 * View renames can also [FileMove] the template; config renames rewrite the
 * definition-site dict key when [configLocations] are known.
 */
object AdonisRenamePlanner {
    /** Kinds that support Shift-F6 / Rename safely via call-site text edits. */
    val RENAMABLE = setOf(
        SymbolKind.ROUTE,
        SymbolKind.VIEW,
        SymbolKind.CONFIG,
        SymbolKind.COMPONENT,
        SymbolKind.ENV,
    )

    data class Edit(
        /** Absolute path, or empty for in-memory / single-buffer plans. */
        val path: String,
        val startOffset: Int,
        val endOffset: Int,
        val newText: String,
    )

    data class FileMove(
        val fromPath: String,
        val toPath: String,
    )

    data class Plan(
        val kind: SymbolKind,
        val oldName: String,
        val newName: String,
        val edits: List<Edit>,
        /** Optional filesystem moves (view / component templates). */
        val fileMoves: List<FileMove> = emptyList(),
        /** Human-readable reason when [edits] is empty / rename refused. */
        val refusal: String? = null,
    ) {
        val isEmpty: Boolean get() = edits.isEmpty() && fileMoves.isEmpty()
        val isAllowed: Boolean get() = refusal == null && (edits.isNotEmpty() || fileMoves.isNotEmpty())
    }

    fun validateNewName(kind: SymbolKind, newName: String): String? {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return "Name cannot be empty"
        if (trimmed != newName) return "Name cannot have leading/trailing whitespace"
        if (trimmed.contains('\n') || trimmed.contains('\r')) return "Name cannot contain newlines"
        return when (kind) {
            SymbolKind.ROUTE -> {
                if (!ROUTE_NAME.matches(trimmed)) {
                    "Route names must be dotted identifiers (e.g. dashboard, teams.show)"
                } else null
            }
            SymbolKind.VIEW, SymbolKind.COMPONENT -> {
                if (!VIEW_NAME.matches(trimmed)) {
                    "View/component names must be dotted path segments (e.g. auth.login)"
                } else null
            }
            SymbolKind.CONFIG -> {
                if (!CONFIG_KEY.matches(trimmed)) {
                    "Config keys must be dotted identifiers (e.g. app.env)"
                } else null
            }
            SymbolKind.ENV -> {
                if (!ENV_KEY.matches(trimmed)) {
                    "Env keys must be SCREAMING_SNAKE_CASE identifiers"
                } else null
            }
            else -> "Rename is not supported for ${kind.name.lowercase()}"
        }
    }

    /**
     * Build a rename plan from already-discovered call-site occurrences.
     * Occurrences are replaced left-to-right within each path (descending offset
     * order so offsets stay valid when applied sequentially).
     */
    fun planFromOccurrences(
        kind: SymbolKind,
        oldName: String,
        newName: String,
        occurrences: List<AdonisCallSiteSearcher.Occurrence>,
        fileMoves: List<FileMove> = emptyList(),
        definitionEdits: List<Edit> = emptyList(),
    ): Plan {
        validateNewName(kind, newName)?.let { reason ->
            return Plan(kind, oldName, newName, emptyList(), refusal = reason)
        }
        if (oldName == newName) {
            return Plan(kind, oldName, newName, emptyList(), refusal = "Name unchanged")
        }
        val callEdits = occurrences.map { occ ->
            Edit(
                path = occ.path.toString(),
                startOffset = occ.range.startOffset,
                endOffset = occ.range.endOffset,
                newText = newName,
            )
        }
        val edits = (callEdits + definitionEdits)
            .sortedWith(compareBy({ it.path }, { -it.startOffset }))
        if (edits.isEmpty() && fileMoves.isEmpty()) {
            return Plan(kind, oldName, newName, emptyList(), refusal = "No usages found")
        }
        return Plan(kind, oldName, newName, edits, fileMoves = fileMoves)
    }

    /**
     * Map a view/component dotted name rename onto a template file move when the
     * index knows the source path.
     */
    fun planViewFileMove(
        kind: SymbolKind,
        oldName: String,
        newName: String,
        indexedPath: String?,
        basePath: String,
    ): FileMove? {
        if (kind != SymbolKind.VIEW && kind != SymbolKind.COMPONENT) return null
        if (indexedPath.isNullOrBlank()) return null
        val to = when (kind) {
            SymbolKind.VIEW -> AdonisCodeActionPlanner.viewPathForName(basePath, newName).toString()
            SymbolKind.COMPONENT -> AdonisCodeActionPlanner.componentPathForName(basePath, newName).toString()
            else -> return null
        }
        if (indexedPath.replace('\\', '/') == to.replace('\\', '/')) return null
        return FileMove(fromPath = indexedPath, toPath = to)
    }

    /**
     * Rewrite the leaf dict key in a config module for ``app.env`` → ``app.environment``.
     * [line] is 0-based; when unknown, searches the whole buffer for a safe match.
     */
    fun planConfigKeyDefinition(
        configText: String,
        configPath: String,
        oldKey: String,
        newKey: String,
        line: Int = -1,
    ): List<Edit> {
        if (oldKey == newKey) return emptyList()
        val oldLeaf = oldKey.substringAfterLast('.')
        val newLeaf = newKey.substringAfterLast('.')
        if (oldLeaf.isEmpty() || newLeaf.isEmpty() || oldLeaf == newLeaf) {
            // Stem-only or identical leaf — nothing to rewrite in the dict.
            return emptyList()
        }
        val lines = configText.split('\n')
        val candidates = if (line in lines.indices) {
            listOf(line)
        } else {
            lines.indices.toList()
        }
        for (li in candidates) {
            val row = lines[li]
            val quoteMatch = Regex("""(['"])${Regex.escape(oldLeaf)}\1\s*:""").find(row) ?: continue
            val lineStart = lines.take(li).sumOf { it.length + 1 }
            val keyStart = lineStart + quoteMatch.range.first + 1 // skip opening quote
            val keyEnd = keyStart + oldLeaf.length
            return listOf(
                Edit(
                    path = configPath,
                    startOffset = keyStart,
                    endOffset = keyEnd,
                    newText = newLeaf,
                ),
            )
        }
        return emptyList()
    }

    /**
     * Apply [edits] that target a single in-memory buffer (paths ignored / matched).
     * Edits must be sorted descending by startOffset.
     */
    fun applyToText(text: String, edits: List<Edit>): String {
        var result = text
        val ordered = edits.sortedByDescending { it.startOffset }
        for (edit in ordered) {
            require(edit.startOffset in 0..result.length && edit.endOffset in edit.startOffset..result.length) {
                "Edit out of range: ${edit.startOffset}-${edit.endOffset} for length ${result.length}"
            }
            result = result.substring(0, edit.startOffset) + edit.newText + result.substring(edit.endOffset)
        }
        return result
    }

    /**
     * Convenience: scan [text] for [oldName] call sites and return the rewritten buffer.
     */
    fun rewriteText(
        text: String,
        kind: SymbolKind,
        oldName: String,
        newName: String,
        dotenvFile: Boolean = false,
    ): Plan {
        val occ = AdonisCallSiteSearcher.findInText(text, kind, oldName, dotenvFile = dotenvFile)
        val plan = planFromOccurrences(kind, oldName, newName, occ)
        if (!plan.isAllowed) return plan
        val rewritten = applyToText(text, plan.edits)
        // Re-emit plan with a synthetic single-file path for callers that only need text.
        return plan.copy(
            edits = listOf(
                Edit("", 0, text.length, rewritten),
            ),
        )
    }

    private val ROUTE_NAME = Regex("""^[A-Za-z_][\w.-]*$""")
    private val VIEW_NAME = Regex("""^[A-Za-z_][\w./-]*$""")
    private val CONFIG_KEY = Regex("""^[A-Za-z_][\w.]*$""")
    private val ENV_KEY = Regex("""^[A-Z][A-Z0-9_]*$""")
}
