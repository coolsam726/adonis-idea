package dev.shamar.adonis.ide

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

/**
 * Settings → Editor → Color Scheme → Edge.
 *
 * Lets users theme directives, echoes, and comments instead of a flat default.
 */
class EdgeColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = "Edge"

    override fun getIcon(): Icon = EdgeFileType.INSTANCE.icon

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getHighlighter(): SyntaxHighlighter = EdgeSyntaxHighlighter

    override fun getDemoText(): String = DEMO

    override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey>? =
        mutableMapOf(
            "dir" to EdgeColors.DIRECTIVE,
            "echo" to EdgeColors.ECHO,
            "raw" to EdgeColors.RAW_ECHO,
            "comment" to EdgeColors.COMMENT,
        )

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Directive", EdgeColors.DIRECTIVE),
            AttributesDescriptor("Echo", EdgeColors.ECHO),
            AttributesDescriptor("Raw echo", EdgeColors.RAW_ECHO),
            AttributesDescriptor("Comment", EdgeColors.COMMENT),
        )

        private val DEMO: String =
            "<dir>@layouts.app({ title: 'Dashboard' })</dir>\n" +
                "  <dir>@page()</dir>\n" +
                "    <comment>{{-- Welcome banner --}}</comment>\n" +
                "    <h1><echo>{{ user.name }}</echo></h1>\n" +
                "    <p><raw>{{{ excerpt(post.content, 280) }}}</raw></p>\n" +
                "    <dir>@each(item in items)</dir>\n" +
                "      <li><echo>{{ item }}</echo></li>\n" +
                "    <dir>@end</dir>\n" +
                "  <dir>@end</dir>\n" +
                "<dir>@end</dir>"
    }
}
