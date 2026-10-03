package com.adonis.ide

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import com.intellij.psi.tree.IElementType

/** Edge (base) + HTML (template data) roots, so HTML tooling sees real HTML. */
class EdgeFileViewProvider(
    manager: PsiManager,
    file: VirtualFile,
    eventSystemEnabled: Boolean,
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled),
    TemplateLanguageFileViewProvider {

    override fun getBaseLanguage(): Language = EdgeLanguage

    override fun getTemplateDataLanguage(): Language = HTMLLanguage.INSTANCE

    override fun getLanguages(): Set<Language> = setOf(EdgeLanguage, HTMLLanguage.INSTANCE)

    override fun getContentElementType(language: Language): IElementType? =
        if (language === HTMLLanguage.INSTANCE) EDGE_TEMPLATE_DATA else null

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
        EdgeFileViewProvider(manager, fileCopy, false)

    override fun createFile(lang: Language): PsiFile? {
        val definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null
        val file = definition.createFile(this)
        if (lang === HTMLLanguage.INSTANCE && file is PsiFileImpl) {
            file.contentElementType = EDGE_TEMPLATE_DATA
        }
        return file
    }
}

class EdgeFileViewProviderFactory : FileViewProviderFactory {
    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language?,
        manager: PsiManager,
        eventSystemEnabled: Boolean,
    ): FileViewProvider = EdgeFileViewProvider(manager, file, eventSystemEnabled)
}
