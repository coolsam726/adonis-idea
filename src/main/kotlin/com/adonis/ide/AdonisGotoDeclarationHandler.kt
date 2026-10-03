package com.adonis.ide

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement

/**
 * Ctrl-click / Go to Declaration for Adonis string symbols and Edge `{{ vars }}`.
 */
class AdonisGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null || editor == null) return null
        val project = sourceElement.project
        val file = sourceElement.containingFile?.virtualFile ?: return null
        if (!AdonisNavigation.isSupportedFile(file.name, sourceElement.containingFile.language)) {
            return null
        }

        val document = editor.document
        val caret = offset.coerceIn(0, document.textLength)
        val hit = AdonisSymbolLocator.hitAt(document.text, caret) ?: return null

        val index = AdonisProjectService.getInstance(project).index()
        if (!index.ok) return null

        val viewName = index.viewNameForPath(file.path)
        val target = AdonisSymbolResolver.resolve(
            index,
            hit.kind,
            hit.name,
            receiver = hit.receiver,
            viewName = viewName,
        ) ?: return null

        val nav = AdonisNavigation.navigationElement(project, target) ?: return null
        return arrayOf(nav)
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String =
        "Go to Adonis symbol"
}

/** Shared navigation helpers. */
object AdonisNavigation {
    fun isSupportedFile(name: String, language: com.intellij.lang.Language?): Boolean {
        val isEdge = name.endsWith(".edge") || language === EdgeLanguage
        val isPython = name.endsWith(".py")
        val isEnv = name == ".env" || name.startsWith(".env.")
        return isEdge || isPython || isEnv
    }

    fun navigationElement(project: Project, target: AdonisSymbolResolver.Target): PsiElement? {
        val vFile = LocalFileSystem.getInstance().findFileByPath(target.path) ?: return null
        val line = target.line.coerceAtLeast(0)
        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vFile)
        return object : FakePsiElement() {
            override fun getParent(): PsiElement? = psiFile
            override fun getProject(): Project = project
            override fun getContainingFile() = psiFile
            override fun getName(): String = target.path
            override fun canNavigate(): Boolean = true
            override fun canNavigateToSource(): Boolean = true
            override fun navigate(requestFocus: Boolean) {
                OpenFileDescriptor(project, vFile, line, 0).navigate(requestFocus)
            }
            override fun getPresentableText(): String = "${target.path}:${target.line + 1}"
        }
    }
}
