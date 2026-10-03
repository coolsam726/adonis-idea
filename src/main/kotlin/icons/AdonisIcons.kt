package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Shared AdonisJS brand icons (16×16 SVG under `/icons/`). */
object AdonisIcons {
    @JvmField
    val File: Icon = IconLoader.getIcon("/icons/adonis.svg", javaClass)

    @JvmField
    val ToolWindow: Icon = File
}
