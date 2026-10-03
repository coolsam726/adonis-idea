package dev.shamar.adonis.ide

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Native Adonis / Edge completions driven by [AdonisIndex].
 *
 * Edge `@` directives always complete (even before the index is warm) and
 * expand to structured snippets with argument placeholders + `@end` where needed.
 */
class AdonisCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val file = parameters.originalFile.virtualFile ?: return
                    val name = file.name
                    if (!AdonisNavigation.isSupportedFile(name, parameters.originalFile.language)) {
                        return
                    }
                    val isEdge = name.endsWith(".edge") ||
                        parameters.originalFile.language === EdgeLanguage
                    val isEnv = name == ".env" || name.startsWith(".env.")

                    val project = parameters.position.project
                    // [AdonisProjectService.index] never blocks the EDT on a cold rebuild.
                    val index = AdonisProjectService.getInstance(project).index()

                    val document = parameters.editor.document
                    val offset = parameters.offset
                    val before = document.text.substring(0, offset.coerceAtMost(document.textLength))
                    val site = CallSiteDetector.detect(before, dotenvFile = isEnv)

                    // Edge `@` / `@ea` — always offer directives (index optional).
                    // Also treat bare trailing `@` when detector somehow missed.
                    val atDirective = site?.kind == SymbolKind.DIRECTIVE ||
                        (isEdge && (before.endsWith("@") || before.endsWith("@!")))
                    if (atDirective) {
                        val prefix = site?.prefix.orEmpty()
                        addDirectiveCompletions(result, index, prefix)
                        result.stopHere()
                        return
                    }

                    // Prop keys / method|variant literals work without a warm index.
                    val indexOptional = site?.kind == SymbolKind.EDGE_PROP_KEY ||
                        site?.kind == SymbolKind.EDGE_LITERAL
                    if (!indexOptional && !index.ok && index.views.isEmpty() && index.routes.isEmpty()) {
                        return
                    }

                    if (site == null) {
                        if (isEdge && before.trimEnd().endsWith("@").not()) {
                            val prefix = result.prefixMatcher.prefix
                            addAll(
                                result,
                                index.directives.ifEmpty { EdgeDirectives.NAMES },
                                "directive",
                                prefix,
                            )
                            addAll(result, index.templateVarNames(), "helper", prefix)
                        }
                        return
                    }

                    val items = AdonisCompletionCatalog.symbolsFor(index, site, before)
                    val prefixed = result.withPrefixMatcher(site.prefix)
                    for ((label, detail) in items) {
                        if (!label.startsWith(site.prefix) && site.prefix.isNotEmpty()) {
                            if (!label.contains(site.prefix, ignoreCase = true)) continue
                        }
                        prefixed.addElement(
                            LookupElementBuilder.create(label)
                                .withTypeText(detail, true)
                                .withPresentableText(label),
                        )
                    }

                    if (isEnv && site.kind == SymbolKind.ENV) {
                        addEnvBulkInsert(prefixed, index, site.prefix)
                    }
                }
            },
        )
    }

    companion object {
        fun symbolsFor(index: AdonisIndex, site: CallSiteDetector.Site): List<Pair<String, String>> =
            AdonisCompletionCatalog.symbolsFor(index, site)

        data class DirectiveLookup(
            val label: String,
            val insertName: String,
            val presentable: String,
            val detail: String,
            val hasSnippet: Boolean,
            val bold: Boolean,
        )

        /**
         * Pure catalog for Edge `@` completions — used by the contributor and tests.
         * Empty [prefix] lists every directive.
         */
        fun directiveLookups(index: AdonisIndex, prefix: String): List<DirectiveLookup> {
            val items = AdonisCompletionCatalog.directiveCompletions(index)
            val out = ArrayList<DirectiveLookup>(items.size)
            for ((label, detail) in items) {
                if (prefix.isNotEmpty() &&
                    !label.startsWith(prefix) &&
                    !label.contains(prefix, ignoreCase = true)
                ) {
                    continue
                }
                val insertName = EdgeDirectives.ALIASES[label] ?: label
                val snippet = EdgeDirectiveSnippets.specFor(insertName)
                    ?: EdgeDirectiveSnippets.specFor(label)
                val presentable = snippet?.presentable ?: insertName
                val typeText = snippet?.detail ?: detail
                out.add(
                    DirectiveLookup(
                        label = label,
                        insertName = insertName,
                        presentable = presentable,
                        detail = typeText,
                        hasSnippet = snippet != null,
                        bold = prefix.isEmpty() && snippet != null && presentable.contains("@end"),
                    ),
                )
            }
            return out
        }

        fun addDirectiveCompletions(
            result: CompletionResultSet,
            index: AdonisIndex,
            prefix: String,
        ) {
            // Empty prefix after `@` must list everything — do not inherit a sticky matcher
            // (autopopup often arrives with a leftover HTML/identifier matcher).
            val prefixed = result.withPrefixMatcher(PlainPrefixMatcher(prefix, /* caseSensitive = */ false))
            for (item in directiveLookups(index, prefix)) {
                var element = LookupElementBuilder.create(item.insertName)
                    .withPresentableText(item.presentable)
                    .withTypeText(item.detail, true)
                    .withLookupString(item.label)
                    .withLookupString(item.insertName)
                if (item.hasSnippet) {
                    element = element.withInsertHandler(directiveSnippetHandler(item.insertName))
                }
                if (item.bold) {
                    element = element.bold()
                }
                // Beat HTML tag / attribute contributors for `@form`, `@button`, …
                prefixed.addElement(PrioritizedLookupElement.withPriority(element, 1000.0))
            }
        }

        private fun directiveSnippetHandler(directiveName: String): InsertHandler<LookupElement> =
            InsertHandler { context, _ ->
                EdgeDirectiveSnippets.applyLookup(context, directiveName)
            }

        private fun addEnvBulkInsert(
            result: CompletionResultSet,
            index: AdonisIndex,
            prefix: String,
        ) {
            val offer = AdonisEnvBulkInsert.offer(index.envKeys.keys, prefix) ?: return
            val insertion = AdonisEnvBulkInsert.dotenvInsertion(offer.keys)
            val handler = InsertHandler<LookupElement> { context, _ ->
                val doc = context.document
                val start = context.startOffset
                val end = context.tailOffset
                doc.replaceString(start, end, insertion)
                context.editor.caretModel.moveToOffset(start + insertion.length)
            }
            result.addElement(
                LookupElementBuilder.create(offer.lookupString)
                    .withPresentableText(offer.presentableText)
                    .withTypeText("env bulk", true)
                    .withInsertHandler(handler)
                    .bold(),
            )
        }

        private fun addAll(
            result: CompletionResultSet,
            items: Collection<String>,
            detail: String,
            prefix: String,
        ) {
            for (label in items) {
                if (prefix.isNotEmpty() && !label.startsWith(prefix) &&
                    !label.contains(prefix, ignoreCase = true)
                ) {
                    continue
                }
                result.addElement(
                    LookupElementBuilder.create(label).withTypeText(detail, true),
                )
            }
        }
    }
}
