package com.adonis.ide

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.nio.file.Path

/**
 * Alt-Enter / light-bulb: create a missing Edge view or component file.
 */
class AdonisCreateViewIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getText(): String = "Create Adonis view/component file"
    override fun getFamilyName(): String = "Adonis"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        resolveAction(project, editor, element) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val action = resolveAction(project, editor, element) ?: return
        AdonisStubFileWriter.applyCreateAction(action)
        AdonisProjectService.getInstance(project).rebuild()
    }

    private fun resolveAction(
        project: Project,
        editor: Editor?,
        element: PsiElement,
    ): AdonisCodeActionPlanner.Action? {
        val hit = hitAt(editor, element) ?: return null
        if (hit.kind != SymbolKind.VIEW && hit.kind != SymbolKind.COMPONENT) return null
        val index = AdonisProjectService.getInstance(project).index()
        return AdonisCodeActionPlanner.forUnknownSymbol(index, hit.kind, hit.name)
            .firstOrNull { it.createPath != null }
    }
}

/**
 * Alt-Enter: run the matching `ace make:*` for an unknown symbol.
 */
class AdonisAceMakeIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getText(): String = "Run ace make for Adonis symbol"
    override fun getFamilyName(): String = "Adonis"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        resolveArgs(project, editor, element) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val args = resolveArgs(project, editor, element) ?: return
        val root = AdonisProjectService.getInstance(project).appRoot() ?: return
        AdonisAceRunner.run(project, root, args)
    }

    private fun resolveArgs(
        project: Project,
        editor: Editor?,
        element: PsiElement,
    ): String? {
        val hit = hitAt(editor, element) ?: return null
        val index = AdonisProjectService.getInstance(project).index()
        return AdonisCodeActionPlanner.forUnknownSymbol(index, hit.kind, hit.name)
            .firstOrNull { it.aceArgs != null }
            ?.aceArgs
    }
}

/** Quick-fix attached to unknown-symbol annotations. */
class AdonisUnknownSymbolQuickFix(
    private val kind: SymbolKind,
    private val name: String,
) : IntentionAction {
    override fun getText(): String = when (kind) {
        SymbolKind.VIEW -> "Create view [$name]"
        SymbolKind.COMPONENT -> "Create component [$name]"
        else -> "Adonis fix for $name"
    }

    override fun getFamilyName(): String = "Adonis"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        kind == SymbolKind.VIEW || kind == SymbolKind.COMPONENT

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val index = AdonisProjectService.getInstance(project).index()
        val action = AdonisCodeActionPlanner.forUnknownSymbol(index, kind, name)
            .firstOrNull { it.createPath != null }
            ?: return
        AdonisStubFileWriter.applyCreateAction(action)
        AdonisProjectService.getInstance(project).rebuild()
    }

    override fun startInWriteAction(): Boolean = true
}

private fun hitAt(editor: Editor?, element: PsiElement): AdonisSymbolLocator.Hit? {
    val file = element.containingFile ?: return null
    val vFile = file.virtualFile ?: return null
    if (!AdonisNavigation.isSupportedFile(vFile.name, file.language)) return null
    val document = editor?.document ?: file.viewProvider.document ?: return null
    val offset = editor?.caretModel?.offset
        ?: (element.textRange.startOffset + element.textLength / 2)
    return AdonisSymbolLocator.hitAt(document.text, offset)
}

/** Process shell for `ace …` — excluded from the coverage gate. */
object AdonisAceRunner {
    fun run(project: Project, appRoot: Path, argsLine: String) {
        val args = argsLine.split(" ").filter { it.isNotBlank() }
        if (args.isEmpty()) return
        val cmd = buildAceCommand(appRoot, args)
        cmd.withWorkDirectory(appRoot.toFile())
        com.intellij.execution.util.ExecUtil.execAndGetOutput(cmd, 120_000)
        AdonisProjectService.getInstance(project).rebuild()
    }
}
