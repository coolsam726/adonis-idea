package dev.shamar.adonis.ide

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The regressions this plugin kept shipping were all invisible to unit tests:
 * HTML painted plain, or the editor refusing to open. These assert the two
 * things a user actually sees — file type and painted colors.
 */
class EdgeHighlightingTest : BasePlatformTestCase() {

    fun `test edge_html files are owned by the edge file type`() {
        val file = myFixture.configureByText("welcome.edge", "<div>{{ name }}</div>")
        assertEquals(EdgeFileType.INSTANCE, file.virtualFile.fileType)
        assertEquals(EdgeLanguage, file.viewProvider.baseLanguage)
    }

    fun `test html psi root exists alongside edge`() {
        val file = myFixture.configureByText("welcome.edge", "<div>{{ name }}</div>")
        val html = file.viewProvider.getPsi(HTMLLanguage.INSTANCE)
        assertNotNull("expected an HTML root for HTML completion/inspections", html)
        assertTrue(file.viewProvider.languages.contains(HTMLLanguage.INSTANCE))
    }

    fun `test html is painted with html colors and edge on top`() {
        val text = "<div class=\"card\">{{ name }}</div>"
        myFixture.configureByText("welcome.edge", text)

        assertTrue(
            "tag name must use HTML colors, not plain text",
            keysAt(text, text.indexOf("div")).contains(XmlHighlighterColors.HTML_TAG_NAME),
        )
        assertTrue(
            "attribute name must use HTML colors",
            keysAt(text, text.indexOf("class")).contains(XmlHighlighterColors.HTML_ATTRIBUTE_NAME),
        )
        assertTrue(
            "echo must use the Edge template color",
            keysAt(text, text.indexOf("{{")).contains(EdgeColors.ECHO),
        )

        // Proves the HTML colors above come from the layer, not from a default.
        val unlayered = LexerEditorHighlighter(
            EdgeSyntaxHighlighter,
            EditorColorsManager.getInstance().globalScheme,
        )
        unlayered.setText(text)
        assertTrue(
            "without the layer HTML is unpainted — that was the 0.1.6 bug",
            unlayered.createIterator(text.indexOf("div")).textAttributesKeys.isEmpty(),
        )
    }

    fun `test html spans split by an echo still highlight as one document`() {
        val text = "<a href=\"{{ url }}\" class=\"btn\">go</a>"
        myFixture.configureByText("welcome.edge", text)

        assertTrue(
            "attribute after an echo must still be HTML, not leftover text",
            keysAt(text, text.indexOf("class")).contains(XmlHighlighterColors.HTML_ATTRIBUTE_NAME),
        )
    }

    fun `test typing a second brace closes the echo and centers the caret`() {
        myFixture.configureByText("welcome.edge", "<p>{{</p>")
        myFixture.editor.caretModel.moveToOffset("<p>{{".length)

        // Call the handler under a write command — myFixture.type also wakes
        // LSP4IJ, which cannot boot a Python server in these headless tests.
        lateinit var result: TypedHandlerDelegate.Result
        WriteCommandAction.runWriteCommandAction(project) {
            result = EdgeTypedHandler().charTyped(
                '{',
                project,
                myFixture.editor,
                myFixture.file,
            )
        }

        assertEquals(TypedHandlerDelegate.Result.STOP, result)
        assertEquals("<p>{{  }}</p>", myFixture.editor.document.text)
        assertEquals("<p>{{ ".length, myFixture.editor.caretModel.offset)
    }

    fun `test a stray autoinserted brace is consumed so the echo is not triple-closed`() {
        // Platform brace matching often leaves `{|}` after the first `{`, then
        // the second `{` yields `{{|}` — without consuming that `}` we get `}}}`.
        myFixture.configureByText("welcome.edge", "<p></p>")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("<p>{{}</p>")
            myFixture.editor.caretModel.moveToOffset("<p>{{".length)
            EdgeTypedHandler().charTyped('{', project, myFixture.editor, myFixture.file)
        }

        assertEquals("<p>{{  }}</p>", myFixture.editor.document.text)
        assertEquals("<p>{{ ".length, myFixture.editor.caretModel.offset)
    }

    fun `test a single brace is left alone`() {
        myFixture.configureByText("welcome.edge", "<style>.a {</style>")
        myFixture.editor.caretModel.moveToOffset("<style>.a {".length)

        lateinit var result: TypedHandlerDelegate.Result
        WriteCommandAction.runWriteCommandAction(project) {
            result = EdgeTypedHandler().charTyped(
                '{',
                project,
                myFixture.editor,
                myFixture.file,
            )
        }

        assertEquals(TypedHandlerDelegate.Result.CONTINUE, result)
        assertEquals("<style>.a {</style>", myFixture.editor.document.text)
    }

    private fun keysAt(text: String, offset: Int): List<TextAttributesKey> {
        require(offset >= 0) { "offset not found in $text" }
        val highlighter = EdgeEditorHighlighter(
            project,
            myFixture.file.virtualFile,
            EditorColorsManager.getInstance().globalScheme,
        )
        highlighter.setText(text)
        return highlighter.createIterator(offset).textAttributesKeys.toList()
    }
}
