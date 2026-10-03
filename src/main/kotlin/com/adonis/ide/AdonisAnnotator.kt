package com.adonis.ide

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Squiggles unknown Adonis symbols; highlights known navigable ones as references
 * so Ctrl-hover shows the underline even before a soft PsiReference resolves.
 */
class AdonisAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile ?: return
        val vFile = file.virtualFile ?: return
        if (!AdonisNavigation.isSupportedFile(vFile.name, file.language)) return

        val text = element.text
        if (text.length < 1 || text.length > 400) return

        val project = element.project
        val index = AdonisProjectService.getInstance(project).index()
        if (!index.ok) return

        val document = file.viewProvider.document ?: return
        val fileText = document.text
        val start = element.textRange.startOffset
        val probe = (start + text.length / 2).coerceIn(0, fileText.length)
        val hit = AdonisSymbolLocator.hitAt(fileText, probe)
            ?: AdonisSymbolLocator.hitAt(fileText, start + if (text.startsWith("\"") || text.startsWith("'")) 1 else 0)
            ?: return

        if (hit.range.endOffset <= start || hit.range.startOffset >= element.textRange.endOffset) return

        val viewName = index.viewNameForPath(vFile.path)
        val resolvable = AdonisSymbolResolver.resolve(
            index,
            hit.kind,
            hit.name,
            receiver = hit.receiver,
            viewName = viewName,
        ) != null

        val range = TextRange(
            hit.range.startOffset.coerceAtLeast(start),
            hit.range.endOffset.coerceAtMost(element.textRange.endOffset),
        )
        if (range.startOffset >= range.endOffset) return

        if (resolvable) {
            // Always paint the Ctrl-hover hyperlink style so the affordance is
            // visible even when a soft/hard PsiReference is slow to attach.
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                .create()
            return
        }

        if (hit.kind !in ANNOTATED) return
        if (hit.kind == SymbolKind.TRANSLATION && index.translationKeys.isEmpty()) return
        if (hit.kind == SymbolKind.GATE && index.gates.isEmpty()) return
        if (index.known(hit.kind, hit.name)) return

        holder.newAnnotation(
            HighlightSeverity.WARNING,
            "Unknown Adonis ${hit.kind.name.lowercase()}: ${hit.name}",
        )
            .range(range)
            .withFix(AdonisUnknownSymbolQuickFix(hit.kind, hit.name))
            .create()
    }

    companion object {
        private val ANNOTATED = setOf(
            SymbolKind.ROUTE,
            SymbolKind.VIEW,
            SymbolKind.CONFIG,
            SymbolKind.TRANSLATION,
            SymbolKind.COMPONENT,
            SymbolKind.GATE,
            SymbolKind.MIDDLEWARE,
            SymbolKind.DISK,
            SymbolKind.INERTIA,
            SymbolKind.ENV,
            SymbolKind.TEMPLATE_VAR,
        )
    }
}
