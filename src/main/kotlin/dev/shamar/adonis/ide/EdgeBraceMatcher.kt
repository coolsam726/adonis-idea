package dev.shamar.adonis.ide

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/**
 * Edge manages `{{ }}` itself via [EdgeTypedHandler]. Returning no pairs
 * stops the platform from auto-inserting a lone `}` for each `{`, which used
 * to produce `{{  }}}` when combined with our echo closer.
 */
class EdgeBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = emptyArray()

    override fun isPairedBracesAllowedBeforeType(
        lbraceType: IElementType,
        contextType: IElementType?,
    ): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int =
        openingBraceOffset
}
