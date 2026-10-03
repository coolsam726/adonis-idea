package dev.shamar.adonis.ide

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/** Flat Edge PSI; HTML structure lives in the second root of the view provider. */
class EdgeParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = EdgeLexer()

    override fun createParser(project: Project?): PsiParser = EdgePsiParser

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.EMPTY

    override fun getCommentTokens(): TokenSet = TokenSet.create(EdgeTokens.COMMENT)

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = EdgeFile(viewProvider)

    companion object {
        val FILE = IFileElementType("EDGE_FILE", EdgeLanguage)
    }
}

private object EdgePsiParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val mark = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        mark.done(root)
        return builder.treeBuilt
    }
}
