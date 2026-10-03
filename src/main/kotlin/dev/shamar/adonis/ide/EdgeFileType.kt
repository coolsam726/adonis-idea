package dev.shamar.adonis.ide

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Edge templates (``.edge``).
 *
 * The compound extension is not something IntelliJ matches on its own, so the
 * type also identifies itself by virtual file and is backed by an overrider and
 * a detector — otherwise HTML claims the file and Edge syntax never binds.
 */
class EdgeFileType private constructor() :
    LanguageFileType(EdgeLanguage),
    FileTypeIdentifiableByVirtualFile {
    override fun getName(): String = "Edge"

    override fun getDescription(): String = "Adonis Edge template (.edge)"

    override fun getDefaultExtension(): String = "edge"

    override fun getIcon(): Icon =
        IconLoader.getIcon("/icons/edge.svg", EdgeFileType::class.java)

    override fun isMyFileType(file: VirtualFile): Boolean =
        !file.isDirectory && isEdgeFileName(file.name)

    companion object {
        @JvmField
        val INSTANCE = EdgeFileType()

        fun isEdgeFileName(name: String): Boolean =
            name.endsWith(".edge", ignoreCase = true)
    }
}
