package dev.shamar.adonis.ide

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Edge typing aids:
 * - `{{` closes as `{{  }}` with the caret in the middle
 * - Typing `@` (and continuing a directive name) opens directive completion
 */
class EdgeTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (isEdge(file)) {
            if (c == '@' || isDirectiveContinue(c)) {
                val before = editor.document.text.substring(
                    0,
                    editor.caretModel.offset.coerceAtMost(editor.document.textLength),
                )
                if (c == '@' || CallSiteDetector.detect(before)?.kind == SymbolKind.DIRECTIVE) {
                    AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                }
            }
            if (c == '@') return Result.CONTINUE
        }
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
        if (!isEdge(file)) return Result.CONTINUE
        if (c == '{' || c == '}') return Result.STOP
        if (c == '@') {
            // Schedule in charTyped after `@` is in the document — not here.
            return Result.STOP
        }
        return Result.CONTINUE
    }

    private fun isDirectiveContinue(c: Char): Boolean =
        c == '!' || c == '.' || c.isLetterOrDigit() || c == '_'

    private fun isEdge(file: PsiFile): Boolean =
        file.viewProvider.baseLanguage === EdgeLanguage ||
            EdgeFileType.isEdgeFileName(file.name)
}
