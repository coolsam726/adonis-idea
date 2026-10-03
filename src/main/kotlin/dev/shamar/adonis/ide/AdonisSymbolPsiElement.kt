package dev.shamar.adonis.ide

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import java.util.Objects

/**
 * Stable PSI stand-in for an Adonis indexed symbol (route/view/config/…).
 *
 * Resolves from string references so Find Usages / ReferencesSearch can key off
 * `(kind, name)`. [getContainingFile] points at the indexed target so the IDE
 * treats the reference like a real go-to link (underline + hand cursor).
 */
class AdonisSymbolPsiElement(
    private val project: Project,
    val kind: SymbolKind,
    val symbolName: String,
    private val target: AdonisSymbolResolver.Target? = null,
) : FakePsiElement() {
    override fun getProject(): Project = project

    override fun getParent(): PsiElement? = getContainingFile()

    override fun getContainingFile(): PsiFile? {
        val path = target?.path ?: return null
        val vFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
        return PsiManager.getInstance(project).findFile(vFile)
    }

    override fun getName(): String = symbolName
    override fun getPresentableText(): String = "${kind.name.lowercase()}: $symbolName"
    override fun canNavigate(): Boolean = target != null
    override fun canNavigateToSource(): Boolean = target != null

    override fun navigate(requestFocus: Boolean) {
        val path = target?.path ?: return
        val vFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return
        OpenFileDescriptor(project, vFile, target.line.coerceAtLeast(0), 0).navigate(requestFocus)
    }

    override fun isEquivalentTo(another: PsiElement?): Boolean =
        another is AdonisSymbolPsiElement &&
            another.kind == kind &&
            another.symbolName == symbolName

    override fun equals(other: Any?): Boolean =
        other is AdonisSymbolPsiElement &&
            other.kind == kind &&
            other.symbolName == symbolName &&
            other.project == project

    override fun hashCode(): Int = Objects.hash(kind, symbolName, project)
}
