package dev.shamar.adonis.ide

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * ReferencesSearch for [AdonisSymbolPsiElement] so Find Usages / highlight
 * usages share the same call-site scan as [AdonisFindUsagesHandlerFactory].
 */
class AdonisReferencesSearchExecutor :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(
        queryParameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val element = queryParameters.elementToSearch
        if (element !is AdonisSymbolPsiElement) return
        if (element.kind !in AdonisCallSiteSearcher.SUPPORTED) return

        val project = queryParameters.project
        val root = AdonisProjectService.getInstance(project).appRoot() ?: return
        val occurrences = AdonisCallSiteSearcher.findUsages(root, element.kind, element.symbolName)
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        val lfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()

        for (occ in occurrences) {
            val vFile = lfs.findFileByNioFile(occ.path)
                ?: lfs.findFileByPath(occ.path.toString())
                ?: continue
            val psiFile = psiManager.findFile(vFile) ?: continue
            // Build a soft reference on a leaf covering the occurrence when possible
            val leaf = psiFile.findElementAt(occ.range.startOffset) ?: continue
            val relStart = (occ.range.startOffset - leaf.textRange.startOffset).coerceAtLeast(0)
            val relEnd = (occ.range.endOffset - leaf.textRange.startOffset)
                .coerceAtMost(leaf.textLength)
            if (relStart >= relEnd) continue
            val ref = AdonisSymbolReference(
                leaf,
                com.intellij.openapi.util.TextRange(relStart, relEnd),
                AdonisSymbolResolver.Target("", 0),
                element.kind,
                element.symbolName,
            )
            if (!consumer.process(ref)) return
        }
    }
}
