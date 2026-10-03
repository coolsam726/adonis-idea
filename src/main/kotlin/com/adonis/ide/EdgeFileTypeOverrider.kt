package com.adonis.ide

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

/**
 * Force ``*.edge`` onto Edge (EP: ``com.intellij.fileTypeOverrider``).
 *
 * Without this, IntelliJ's ``*.html`` association wins for compound names and
 * the Edge highlighter never runs.
 */
class EdgeFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (EdgeFileType.isEdgeFileName(file.name)) {
            return EdgeFileType.INSTANCE
        }
        return null
    }
}
