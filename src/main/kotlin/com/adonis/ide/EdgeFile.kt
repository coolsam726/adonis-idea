package com.adonis.ide

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class EdgeFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, EdgeLanguage) {
    override fun getFileType(): FileType = EdgeFileType.INSTANCE

    override fun toString(): String = "EdgeFile"
}
