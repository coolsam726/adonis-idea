package com.adonis.ide

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

/**
 * Hard (non-soft) references so Ctrl-hover underlines Adonis symbols and shows
 * the hand cursor the same way native Python go-to links do.
 */
class AdonisReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            AdonisReferenceProvider(),
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )
    }
}

class AdonisReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
        val vFile = file.virtualFile ?: return PsiReference.EMPTY_ARRAY
        if (!AdonisNavigation.isSupportedFile(vFile.name, file.language)) {
            return PsiReference.EMPTY_ARRAY
        }
        val text = element.text
        if (text.isEmpty() || text.length > 800) return PsiReference.EMPTY_ARRAY

        val index = AdonisProjectService.getInstance(element.project).index()
        if (!index.ok) return PsiReference.EMPTY_ARRAY

        val document = file.viewProvider.document ?: return PsiReference.EMPTY_ARRAY
        val fileText = document.text
        val elementStart = element.textRange.startOffset
        val elementEnd = element.textRange.endOffset

        // Prefer a probe inside the element; also try just after an opening quote.
        val probes = listOf(
            (elementStart + element.textLength / 2).coerceIn(0, fileText.length),
            (elementStart + 1).coerceIn(0, fileText.length),
            elementStart.coerceIn(0, fileText.length),
        )
        val hit = probes.firstNotNullOfOrNull { AdonisSymbolLocator.hitAt(fileText, it) }
            ?: return PsiReference.EMPTY_ARRAY

        if (hit.range.endOffset <= elementStart || hit.range.startOffset >= elementEnd) {
            return PsiReference.EMPTY_ARRAY
        }

        val viewName = index.viewNameForPath(vFile.path)
        val target = AdonisSymbolResolver.resolve(
            index,
            hit.kind,
            hit.name,
            receiver = hit.receiver,
            viewName = viewName,
        ) ?: return PsiReference.EMPTY_ARRAY

        // Attach the reference to the innermost element that fully covers the hit
        // so the underline sits on the symbol text, not a giant parent.
        if (element.textRange.startOffset < hit.range.startOffset ||
            element.textRange.endOffset > hit.range.endOffset
        ) {
            // Parent nodes also get visited — skip if a smaller child will claim it.
            val childClaims = element.children.any { child ->
                child.textRange.startOffset <= hit.range.startOffset &&
                    child.textRange.endOffset >= hit.range.endOffset
            }
            if (childClaims) return PsiReference.EMPTY_ARRAY
        }

        val relStart = (hit.range.startOffset - elementStart).coerceAtLeast(0)
        val relEnd = (hit.range.endOffset - elementStart).coerceAtMost(element.textLength)
        if (relStart >= relEnd) return PsiReference.EMPTY_ARRAY

        val symbolName = when (hit.kind) {
            SymbolKind.CONTROLLER_ACTION -> {
                val controller = index.resolveControllerName(hit.receiver)
                if (controller != null) "$controller@${hit.name}" else hit.name
            }
            else -> hit.name
        }

        return arrayOf(
            AdonisSymbolReference(
                element,
                TextRange(relStart, relEnd),
                target,
                hit.kind,
                symbolName,
            ),
        )
    }
}

class AdonisSymbolReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val target: AdonisSymbolResolver.Target,
    val kind: SymbolKind,
    val symbolName: String,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, /* soft = */ false),
    EmptyResolveMessageProvider {
    override fun resolve(): PsiElement =
        AdonisSymbolPsiElement(element.project, kind, symbolName, target)

    override fun getUnresolvedMessagePattern(): String =
        "Cannot resolve Adonis symbol"

    override fun handleElementRename(newElementName: String): PsiElement {
        AdonisRenamePlanner.validateNewName(kind, newElementName)?.let { reason ->
            throw IllegalArgumentException(reason)
        }
        return super.handleElementRename(newElementName)
    }
}
