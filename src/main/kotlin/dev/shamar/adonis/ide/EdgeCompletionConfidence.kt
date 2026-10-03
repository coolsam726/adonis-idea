package dev.shamar.adonis.ide

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState

/**
 * HTML/XML completion confidence often skips autopopup in template text.
 * After `@` / `@dir` in Edge files we must never skip — otherwise typing `@`
 * schedules a popup that is immediately cancelled.
 */
class EdgeCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(
        contextElement: PsiElement,
        psiFile: PsiFile,
        offset: Int,
    ): ThreeState {
        if (!EdgeTypedHandler.isEdge(psiFile)) return ThreeState.UNSURE
        val text = psiFile.viewProvider.contents
        val end = offset.coerceAtMost(text.length).coerceAtLeast(0)
        val before = text.subSequence(0, end).toString()
        if (CallSiteDetector.detect(before)?.kind == SymbolKind.DIRECTIVE) {
            return ThreeState.NO
        }
        // Bare `@` / `@!` while the detector prefix group is still empty.
        if (before.endsWith("@") || before.endsWith("@!")) {
            return ThreeState.NO
        }
        return ThreeState.UNSURE
    }
}
