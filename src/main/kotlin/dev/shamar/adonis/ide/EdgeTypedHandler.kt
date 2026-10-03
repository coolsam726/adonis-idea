package dev.shamar.adonis.ide

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiFile

/**
 * Edge typing aids:
 * - `{{` closes as `{{  }}` with the caret in the middle
 * - Typing `@` opens the full Edge directive completion list
 *
 * Autopopup follows the platform contract: schedule from [checkAutoPopup]
 * with a [Condition] (PSI is not up to date in that method). [charTyped]
 * re-schedules after `@` is in the document as a belt-and-suspenders path.
 */
class EdgeTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (!isEdge(file)) return Result.CONTINUE
        // Avoid fighting HTML brace autopairs with our own `{{  }}` helper.
        if (c == '{' || c == '}') return Result.STOP
        if (c == '@') {
            // Condition runs later on up-to-date PSI (after `@` is inserted).
            AutoPopupController.getInstance(project).scheduleAutoPopup(
                editor,
                CompletionType.BASIC,
                EDGE_FILE,
            )
            return Result.STOP
        }
        return Result.CONTINUE
    }

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
                    // Force schedule after the character is in the buffer.
                    AutoPopupController.getInstance(project).scheduleAutoPopup(
                        editor,
                        CompletionType.BASIC,
                        EDGE_FILE,
                    )
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

    private fun isDirectiveContinue(c: Char): Boolean =
        c == '!' || c == '.' || c.isLetterOrDigit() || c == '_'

    companion object {
        /** True for Edge files (base language or `*.edge` name). */
        val EDGE_FILE: Condition<PsiFile> = Condition { file ->
            file.viewProvider.baseLanguage === EdgeLanguage ||
                EdgeFileType.isEdgeFileName(file.name)
        }

        fun isEdge(file: PsiFile): Boolean = EDGE_FILE.value(file)
    }
}
