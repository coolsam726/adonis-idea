package dev.shamar.adonis.ide

/**
 * Insert a Lucid relation declaration into a model source buffer.
 */
object AdonisRelationStubPlanner {
    data class Edit(
        val startOffset: Int,
        val endOffset: Int,
        val newText: String,
    )

    data class Plan(
        val edits: List<Edit> = emptyList(),
        val refusal: String? = null,
    ) {
        val isAllowed: Boolean get() = refusal == null && edits.isNotEmpty()
    }

    /**
     * Append [relationName] before the closing of the last top-level class,
     * or at EOF if no class body can be located.
     */
    fun planInsert(
        modelSource: String,
        relationName: String,
        relatedModel: String = "Related",
    ): Plan {
        val name = relationName.trim()
        if (name.isEmpty() || !name.matches(Regex("""^[A-Za-z_][\w]*$"""))) {
            return Plan(refusal = "Invalid relation name")
        }
        val alreadyExists =
            Regex("""\bdef\s+${Regex.escape(name)}\s*\(""").containsMatchIn(modelSource) ||
                Regex("""\bdeclare\s+${Regex.escape(name)}\s*:""").containsMatchIn(modelSource) ||
                Regex("""\b(?:async\s+)?${Regex.escape(name)}\s*\([^)]*\)\s*(?::|\{)""")
                    .containsMatchIn(modelSource)
        if (alreadyExists) {
            return Plan(refusal = "Method already exists")
        }
        val stub = AdonisLucidHelpers.relationMethodStub(name, relatedModel)
        val insertAt = findClassInsertOffset(modelSource)
            ?: return Plan(
                edits = listOf(Edit(modelSource.length, modelSource.length, "\n\n$stub\n")),
            )
        val indent = "    "
        val indented = stub.lines().joinToString("\n") { line ->
            if (line.isBlank()) "" else indent + line
        }
        val prefix = if (insertAt > 0 && modelSource[insertAt - 1] != '\n') "\n" else ""
        val block = "$prefix\n$indented\n"
        return Plan(edits = listOf(Edit(insertAt, insertAt, block)))
    }

    fun applyToText(text: String, edits: List<Edit>): String {
        var result = text
        for (edit in edits.sortedByDescending { it.startOffset }) {
            result = result.substring(0, edit.startOffset) +
                edit.newText +
                result.substring(edit.endOffset)
        }
        return result
    }

    /** Offset just before the final dedent that closes the last `class` body. */
    internal fun findClassInsertOffset(source: String): Int? {
        val classMatch = Regex("""(?m)^class\s+[A-Z][A-Za-z0-9_]*\b""").findAll(source).lastOrNull()
            ?: return null
        val afterClass = classMatch.range.last + 1
        // Scan for a line that returns to column 0 (next top-level stmt) after the class.
        var i = afterClass
        var sawBody = false
        while (i < source.length) {
            val lineStart = i
            while (i < source.length && source[i] != '\n') i++
            val line = source.substring(lineStart, i)
            if (line.isNotBlank()) {
                val indent = line.takeWhile { it == ' ' || it == '\t' }.length
                if (indent == 0 && sawBody && !line.trimStart().startsWith("#")) {
                    return lineStart
                }
                if (indent > 0) sawBody = true
            }
            if (i < source.length && source[i] == '\n') i++
        }
        return if (sawBody) source.length else null
    }
}
