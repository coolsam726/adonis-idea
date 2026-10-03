package com.adonis.ide

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

/**
 * Alt-F7 / Find Usages for Adonis routes, views, config keys, components, and env keys.
 */
class AdonisFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean =
        resolveSymbol(element) != null

    override fun createFindUsagesHandler(
        element: PsiElement,
        forHighlightUsages: Boolean,
    ): FindUsagesHandler? {
        val symbol = resolveSymbol(element) ?: return null
        return AdonisFindUsagesHandler(symbol)
    }

    companion object {
        fun resolveSymbol(element: PsiElement): AdonisSymbolPsiElement? {
            if (element is AdonisSymbolPsiElement) {
                if (element.kind in AdonisCallSiteSearcher.SUPPORTED) return element
                return null
            }
            // Soft reference resolve target
            for (ref in element.references) {
                if (ref is AdonisSymbolReference) {
                    val resolved = ref.resolve()
                    if (resolved is AdonisSymbolPsiElement &&
                        resolved.kind in AdonisCallSiteSearcher.SUPPORTED
                    ) {
                        return resolved
                    }
                }
            }
            // Caret on a string / tag without going through the reference first
            val file = element.containingFile ?: return null
            val vFile = file.virtualFile ?: return null
            if (!AdonisNavigation.isSupportedFile(vFile.name, file.language)) return null
            val document = file.viewProvider.document ?: return null
            val offset = element.textRange.startOffset + element.textLength / 2
            val hit = AdonisSymbolLocator.hitAt(document.text, offset)
                ?: AdonisSymbolLocator.hitAt(document.text, element.textRange.startOffset + 1)
                ?: return null
            if (hit.kind !in AdonisCallSiteSearcher.SUPPORTED) return null
            val index = AdonisProjectService.getInstance(element.project).index()
            if (!index.ok) return null
            val viewName = index.viewNameForPath(vFile.path)
            val target = AdonisSymbolResolver.resolve(
                index,
                hit.kind,
                hit.name,
                receiver = hit.receiver,
                viewName = viewName,
            )
            return AdonisSymbolPsiElement(element.project, hit.kind, hit.name, target)
        }
    }
}

class AdonisFindUsagesHandler(
    private val symbol: AdonisSymbolPsiElement,
) : FindUsagesHandler(symbol) {
    override fun getPrimaryElements(): Array<PsiElement> = arrayOf(symbol)

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions,
    ): Boolean {
        val project = element.project
        val root = AdonisProjectService.getInstance(project).appRoot() ?: return true
        val occurrences = AdonisCallSiteSearcher.findUsages(root, symbol.kind, symbol.symbolName)
        for (info in AdonisUsageInfoFactory.usageInfos(project, occurrences)) {
            if (!processor.process(info)) return false
        }
        return true
    }

    override fun getFindUsagesOptions(dataContext: DataContext?): FindUsagesOptions {
        val options = super.getFindUsagesOptions(dataContext)
        options.isSearchForTextOccurrences = false
        return options
    }
}
