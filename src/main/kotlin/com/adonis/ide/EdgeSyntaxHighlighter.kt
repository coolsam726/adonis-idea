package com.adonis.ide

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

/**
 * Colors Edge syntax only — HTML is layered on top of
 * [EdgeTokens.TEMPLATE_DATA] by [EdgeEditorHighlighterProvider].
 */
class EdgeSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(
        project: Project?,
        virtualFile: VirtualFile?,
    ): SyntaxHighlighter = EdgeSyntaxHighlighter
}

object EdgeSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = EdgeLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            EdgeTokens.COMMENT -> COMMENT_KEYS
            EdgeTokens.ECHO, EdgeTokens.RAW_ECHO -> ECHO_KEYS
            EdgeTokens.DIRECTIVE -> DIRECTIVE_KEYS
            else -> EMPTY
        }

    private val COMMENT_KEYS = arrayOf(EdgeColors.COMMENT)
    private val ECHO_KEYS = arrayOf(EdgeColors.ECHO)
    private val DIRECTIVE_KEYS = arrayOf(EdgeColors.DIRECTIVE)
    private val EMPTY = emptyArray<TextAttributesKey>()
}

object EdgeColors {
    val COMMENT: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "EDGE_COMMENT",
        DefaultLanguageHighlighterColors.BLOCK_COMMENT,
    )
    val ECHO: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "EDGE_ECHO",
        DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR,
    )
    val DIRECTIVE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "EDGE_DIRECTIVE",
        DefaultLanguageHighlighterColors.KEYWORD,
    )
}
