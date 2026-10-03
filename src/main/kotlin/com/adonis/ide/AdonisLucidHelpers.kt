package com.adonis.ide

/**
 * Lucid ORM helper snippets and suggestions.
 */
object AdonisLucidHelpers {
    data class Snippet(val label: String, val template: String, val detail: String)

    /** `Post.query().preload('comments')` / `withCount` suggestions for a model. */
    fun eagerLoadSnippets(index: AdonisIndex, receiver: String?): List<Snippet> {
        val rels = AdonisCompletionCatalog.relationsFor(index, receiver).sorted()
        if (rels.isEmpty()) return emptyList()
        val recv = receiver?.substringAfterLast('.') ?: "Model"
        return rels.flatMap { rel ->
            listOf(
                Snippet(
                    label = "$recv.query().preload('$rel')",
                    template = "$recv.query().preload('$rel')",
                    detail = "eager load",
                ),
                Snippet(
                    label = "$recv.query().withCount('$rel')",
                    template = "$recv.query().withCount('$rel')",
                    detail = "with count",
                ),
            )
        }
    }

    /** `where('email', …)` column helpers. */
    fun whereColumnSnippets(index: AdonisIndex, receiver: String?): List<Snippet> {
        val cols = AdonisCompletionCatalog.columnsFor(index, receiver).sorted()
        val recv = receiver?.substringAfterLast('.') ?: "query"
        return cols.map { col ->
            Snippet(
                label = "$recv.where('$col', …)",
                template = "$recv.where('$col', \$value)",
                detail = "column",
            )
        }
    }

    fun relationMethodStub(relationName: String, relatedModel: String = "Related"): String =
        """
        @hasMany(() => $relatedModel)
        declare $relationName: HasMany<typeof $relatedModel>
        """.trimIndent()

    fun eagerLoadSnippet(relation: String): String = "preload('$relation')"

    fun whereSnippet(column: String): String = "where('$column', \$value)"

    fun relationMethodNames(): List<String> =
        listOf("hasMany", "belongsTo", "hasOne", "manyToMany", "hasManyThrough")
}
