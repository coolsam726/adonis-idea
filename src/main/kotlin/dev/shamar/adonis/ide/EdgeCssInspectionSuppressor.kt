package dev.shamar.adonis.ide

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement

/**
 * Edge dual-PSI exposes an HTML root so platform CSS inspections run inside `*.edge`.
 * Custom properties (`var(--brand)`) often live in Vite/global CSS, so suppress the
 * false-positive "Unresolved custom property" inspection in Edge files only.
 */
class EdgeCssInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId !in SUPPRESSED) return false
        return isEdgeFile(element)
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY

    companion object {
        val SUPPRESSED: Set<String> = setOf(
            "CssUnresolvedCustomProperty",
        )

        fun isEdgeFile(element: PsiElement): Boolean {
            val file = element.containingFile ?: return false
            if (file.fileType == EdgeFileType.INSTANCE) return true
            val vf = file.virtualFile
            if (vf != null && vf.name.endsWith(".edge", ignoreCase = true)) return true
            val vp = file.viewProvider
            return vp.baseLanguage == EdgeLanguage || vp.getPsi(EdgeLanguage) != null
        }
    }
}
