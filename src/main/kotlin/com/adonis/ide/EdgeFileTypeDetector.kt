package com.adonis.ide

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry.FileTypeDetector
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile

/**
 * High-priority detector so ``*.edge`` is Edge before HTML content sniffing.
 */
class EdgeFileTypeDetector : FileTypeDetector {
    override fun detect(
        file: VirtualFile,
        firstBytes: ByteSequence,
        firstCharsIfText: CharSequence?,
    ): FileType? {
        if (EdgeFileType.isEdgeFileName(file.name)) {
            return EdgeFileType.INSTANCE
        }
        return null
    }

    override fun getDesiredContentPrefixLength(): Int = 0
}
