package com.adonis.ide

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * `{{` closes itself as `{{  }}` with the caret in the middle, like Blade.
 *
 * Also stops the completion auto-popup while the braces are being typed —
 * suggestions belong to Ctrl+Space or the first typed letter, not to `{`.
 */
class EdgeTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (c != '{' || !isEdge(file)) return Result.CONTINUE
        val document = editor.document
        val caret = editor.caretModel.offset
        // Only after the second brace of `{{`, and not for `{{{`.
        if (caret < 2) return Result.CONTINUE
        val text = document.charsSequence
        if (text[caret - 1] != '{' || text[caret - 2] != '{') return Result.CONTINUE
        if (caret >= 3 && text[caret - 3] == '{') return Result.CONTINUE
        if (text.startsWith("}}", caret)) return Result.CONTINUE

        // A brace matcher (HTML root / platform default) may already have
        // inserted a single `}` for the `{` we just typed — consume it so we
        // don't end up with `{{  }}}`.
        var insertAt = caret
        if (insertAt < text.length && text[insertAt] == '}' &&
            (insertAt + 1 >= text.length || text[insertAt + 1] != '}')
        ) {
            document.deleteString(insertAt, insertAt + 1)
        }

        document.insertString(insertAt, "  }}")
        editor.caretModel.moveToOffset(insertAt + 1)
        return Result.STOP
    }

    override fun checkAutoPopup(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (isEdge(file) && (c == '{' || c == '}')) return Result.STOP
        return Result.CONTINUE
    }

    private fun isEdge(file: PsiFile): Boolean =
        file.viewProvider.baseLanguage === EdgeLanguage ||
            EdgeFileType.isEdgeFileName(file.name)
}
