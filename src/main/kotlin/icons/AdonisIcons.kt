package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Shared AdonisJS brand icons under `/icons/`. */
object AdonisIcons {
    /** Project-tree / file-type mark (brand purple / white). */
    @JvmField
    val File: Icon = IconLoader.getIcon("/icons/adonis.svg", javaClass)

    /**
     * Tool-window stripe icon — monochrome gray so New UI can recolor it to the
     * accent blue when selected (full-color brand marks paint as a blue blob).
     */
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/adonisToolWindow.svg", javaClass)
}
