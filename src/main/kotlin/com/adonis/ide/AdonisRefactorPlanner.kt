package com.adonis.ide

/**
 * Pure refactor plans: extract Edge partial, convert ``@include`` → component.
 */
object AdonisRefactorPlanner {
    data class Edit(
        val startOffset: Int,
        val endOffset: Int,
        val newText: String,
    )

    data class Plan(
        val title: String,
        val createPath: String? = null,
        val createContent: String? = null,
        val edits: List<Edit> = emptyList(),
        val refusal: String? = null,
    ) {
        val isAllowed: Boolean get() = refusal == null && (edits.isNotEmpty() || createPath != null)
    }

    fun extractPartial(
        basePath: String,
        selection: String,
        selectionStart: Int,
        selectionEnd: Int,
        dottedName: String,
    ): Plan {
        val name = dottedName.trim()
        if (name.isEmpty()) return Plan("Extract partial", refusal = "Name required")
        if (!name.matches(Regex("""^[A-Za-z_][\w./-]*$"""))) {
            return Plan("Extract partial", refusal = "Invalid view name")
        }
        if (selection.isBlank()) return Plan("Extract partial", refusal = "Empty selection")
        val path = AdonisCodeActionPlanner.viewPathForName(basePath, name).toString()
        val include = "@include('$name')"
        return Plan(
            title = "Extract partial [$name]",
            createPath = path,
            createContent = selection.trimEnd() + "\n",
            edits = listOf(Edit(selectionStart, selectionEnd, include)),
        )
    }

    /**
     * ``@include('alert')`` / ``@include('components.alert')`` → ``<x-alert />``.
     */
    fun includeToComponent(source: String, offset: Int): Plan {
        val re = Regex("""@include(?:If|When|Unless)?\s*\(\s*(['"])(?<name>[^'"]*)\1""")
        val match = re.findAll(source).firstOrNull { m ->
            offset in m.range.first..(m.range.last + 1)
        } ?: return Plan("Convert include to component", refusal = "No @include under caret")
        var name = match.groups["name"]?.value
        if (name.isNullOrBlank()) {
            return Plan("Convert include to component", refusal = "Missing name")
        }
        if (name.startsWith("components.")) name = name.removePrefix("components.")
        val tag = "<x-$name />"
        return Plan(
            title = "Convert to <$tag>",
            edits = listOf(Edit(match.range.first, match.range.last + 1, tag)),
        )
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
}
