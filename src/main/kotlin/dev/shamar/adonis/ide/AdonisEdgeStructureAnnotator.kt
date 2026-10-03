package dev.shamar.adonis.ide

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Unmatched Edge `@if` / `@each` / `@wire` … `@end` structure diagnostics.
 */
class AdonisEdgeStructureAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        val name = element.virtualFile?.name ?: element.name
        if (!name.endsWith(".edge")) return
        val text = element.text ?: return
        for (issue in AdonisEdgeStructure.analyze(text)) {
            val range = TextRange(issue.startOffset, issue.endOffset)
            if (range.startOffset >= range.endOffset) continue
            holder.newAnnotation(HighlightSeverity.ERROR, issue.message)
                .range(range)
                .create()
        }
    }
}
