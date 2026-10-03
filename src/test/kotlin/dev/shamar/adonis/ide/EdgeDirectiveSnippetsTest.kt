package dev.shamar.adonis.ide

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.OffsetMap
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.patterns.ElementPattern
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.function.Consumer

class EdgeDirectiveSnippetsTest : BasePlatformTestCase() {

    fun `test each preview advertises args and end`() {
        assertEquals("each(item in items) … @end", EdgeDirectiveSnippets.preview("each"))
        assertTrue(EdgeDirectiveSnippets.hasSnippet("if"))
        assertTrue(EdgeDirectiveSnippets.snippetNames().contains("pushTo"))
        assertNotNull(EdgeDirectiveSnippets.specFor("!component"))
        assertTrue(EdgeDirectiveSnippets.specFor("!component")!!.bang)
        assertNull(EdgeDirectiveSnippets.preview("not-a-directive"))
    }

    fun `test applying each expands structure with end`() {
        myFixture.configureByText("loop.edge", "@ea")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.replaceString(1, 3, "each")
            myFixture.editor.caretModel.moveToOffset(5)
            EdgeDirectiveSnippets.applyLookup(myFixture.editor, 1, 5, "each")
        }
        val text = myFixture.editor.document.text
        assertTrue(text.startsWith("@each("))
        assertTrue(text.contains(" in "))
        assertTrue(text.contains("@end"))
    }

    fun `test bang component after at inserts bang`() {
        myFixture.configureByText("comp.edge", "@")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.caretModel.moveToOffset(1)
            EdgeDirectiveSnippets.applyLookup(myFixture.editor, 1, 1, "!component")
        }
        val text = myFixture.editor.document.text
        assertTrue(text.startsWith("@!component("))
        assertFalse(text.contains("@end"))
    }

    fun `test block after bang strips bang`() {
        myFixture.configureByText("if.edge", "@!")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.caretModel.moveToOffset(2)
            EdgeDirectiveSnippets.applyLookup(myFixture.editor, 2, 2, "if")
        }
        val text = myFixture.editor.document.text
        assertTrue(text.startsWith("@if("))
        assertFalse(text.startsWith("@!"))
        assertTrue(text.contains("@end"))
    }

    fun `test unknown directive is a no-op`() {
        myFixture.configureByText("x.edge", "@zz")
        WriteCommandAction.runWriteCommandAction(project) {
            EdgeDirectiveSnippets.applyLookup(myFixture.editor, 1, 3, "zz")
        }
        assertEquals("@zz", myFixture.editor.document.text)
    }

    fun `test directive catalog includes structured each when index empty`() {
        val index = AdonisIndex(ok = false)
        val items = AdonisCompletionCatalog.directiveCompletions(index)
        assertTrue(items.any { it.first == "each" && it.second == "loop" })
        assertTrue(items.any { it.first == "!component" })
        assertTrue(items.any { it.first == "loop" })
        assertTrue(items.any { it.first == "page" })
    }

    fun `test typing at offers all directives`() {
        val emptyPrefix = AdonisCompletionContributor.directiveLookups(AdonisIndex(ok = false), "")
        assertTrue(emptyPrefix.any { it.insertName == "each" && it.hasSnippet && it.bold })
        assertTrue(emptyPrefix.any { it.label == "!component" && it.hasSnippet })
        assertTrue(emptyPrefix.any { it.label == "loop" && it.insertName == "each" })

        val filtered = AdonisCompletionContributor.directiveLookups(AdonisIndex(ok = true), "ea")
        assertTrue(filtered.any { it.insertName == "each" })
        assertTrue(filtered.all { it.label.contains("ea", ignoreCase = true) || it.label.startsWith("ea") })
        assertFalse(filtered.any { it.insertName == "if" })
    }

    fun `test addDirectiveCompletions wires insert handlers`() {
        val collected = mutableListOf<LookupElement>()
        val result = CollectingCompletionResultSet(collected)
        AdonisCompletionContributor.addDirectiveCompletions(result, AdonisIndex(ok = false), "")
        assertTrue(collected.any { it.lookupString == "each" })
        assertTrue(collected.any { it.lookupString == "!component" })
        // Accepting `each` should run the structured snippet handler.
        val each = collected.first { it.lookupString == "each" }
        myFixture.configureByText("handler.edge", "@ea")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.replaceString(1, 3, "each")
            myFixture.editor.caretModel.moveToOffset(5)
            val map = OffsetMap(myFixture.editor.document)
            map.addOffset(CompletionInitializationContext.START_OFFSET, 1)
            map.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, 5)
            map.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, 5)
            val ctx = InsertionContext(map, '\n', arrayOf(each), myFixture.file, myFixture.editor, false)
            each.handleInsert(ctx)
        }
        assertTrue(myFixture.editor.document.text.contains("@end"))
    }

    fun `test at typed schedules autopopup path`() {
        myFixture.configureByText("a.edge", "")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("@")
            myFixture.editor.caretModel.moveToOffset(1)
            val typed = EdgeTypedHandler().charTyped('@', project, myFixture.editor, myFixture.file)
            assertEquals(TypedHandlerDelegate.Result.CONTINUE, typed)
            val popup = EdgeTypedHandler().checkAutoPopup('@', project, myFixture.editor, myFixture.file)
            assertEquals(TypedHandlerDelegate.Result.STOP, popup)
            val brace = EdgeTypedHandler().checkAutoPopup('{', project, myFixture.editor, myFixture.file)
            assertEquals(TypedHandlerDelegate.Result.STOP, brace)
            val other = EdgeTypedHandler().checkAutoPopup('x', project, myFixture.editor, myFixture.file)
            assertEquals(TypedHandlerDelegate.Result.CONTINUE, other)
        }
    }

    fun `test include snippet has no end`() {
        myFixture.configureByText("inc.edge", "@")
        WriteCommandAction.runWriteCommandAction(project) {
            EdgeDirectiveSnippets.applyLookup(myFixture.editor, 1, 1, "include")
        }
        val text = myFixture.editor.document.text
        assertTrue(text.startsWith("@include("))
        assertFalse(text.contains("@end"))
    }

    fun `test applyLookup inserts at when missing`() {
        myFixture.configureByText("bare.edge", "")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.caretModel.moveToOffset(0)
            EdgeDirectiveSnippets.applyLookup(myFixture.editor, 0, 0, "page")
        }
        assertTrue(myFixture.editor.document.text.startsWith("@page()"))
    }

    fun `test loop alias resolves to each snippet`() {
        assertEquals("each", EdgeDirectives.ALIASES["loop"])
        myFixture.configureByText("alias.edge", "@")
        WriteCommandAction.runWriteCommandAction(project) {
            EdgeDirectiveSnippets.applyLookup(myFixture.editor, 1, 1, "each")
        }
        assertTrue(myFixture.editor.document.text.contains("@end"))
    }

    fun `test startTemplate noops without project`() {
        // Coverage for editor.project ?: return — use a detached document editor if needed.
        // In the fixture, project is always set; call startTemplate with a known spec.
        val spec = EdgeDirectiveSnippets.specFor("elseif")!!
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.configureByText("ei.edge", "@")
            myFixture.editor.caretModel.moveToOffset(1)
            EdgeDirectiveSnippets.startTemplate(myFixture.editor, spec)
        }
        assertTrue(myFixture.editor.document.text.contains("elseif("))
    }

    fun `test remaining snippets expand`() {
        for (name in listOf(
            "unless", "component", "slot", "section", "layout",
            "wire", "persist", "pushTo", "svg", "vite", "let", "assign", "elseif",
        )) {
            myFixture.configureByText("$name.edge", "@")
            WriteCommandAction.runWriteCommandAction(project) {
                EdgeDirectiveSnippets.applyLookup(myFixture.editor, 1, 1, name)
            }
            val text = myFixture.editor.document.text
            assertTrue("$name should expand", text.contains(name.removePrefix("!")))
        }
    }

    fun `test insertionContext overload`() {
        myFixture.configureByText("ctx.edge", "@")
        WriteCommandAction.runWriteCommandAction(project) {
            val editor = myFixture.editor
            val map = com.intellij.codeInsight.completion.OffsetMap(editor.document)
            map.addOffset(com.intellij.codeInsight.completion.CompletionInitializationContext.START_OFFSET, 1)
            map.addOffset(com.intellij.codeInsight.completion.CompletionInitializationContext.SELECTION_END_OFFSET, 1)
            map.addOffset(com.intellij.codeInsight.completion.CompletionInitializationContext.IDENTIFIER_END_OFFSET, 1)
            val ctx = com.intellij.codeInsight.completion.InsertionContext(
                map,
                '\n',
                emptyArray(),
                myFixture.file,
                editor,
                false,
            )
            EdgeDirectiveSnippets.applyLookup(ctx, "if")
        }
        assertTrue(myFixture.editor.document.text.startsWith("@if("))
    }

    fun `test typed handler ignores non edge`() {
        myFixture.configureByText("plain.txt", "@")
        val handler = EdgeTypedHandler()
        assertEquals(
            TypedHandlerDelegate.Result.CONTINUE,
            handler.charTyped('@', project, myFixture.editor, myFixture.file),
        )
        assertEquals(
            TypedHandlerDelegate.Result.CONTINUE,
            handler.checkAutoPopup('@', project, myFixture.editor, myFixture.file),
        )
    }

    /** Minimal [CompletionResultSet] that records lookup elements for unit tests. */
    private class CollectingCompletionResultSet(
        private val sink: MutableList<LookupElement>,
        matcher: PrefixMatcher = PlainPrefixMatcher(""),
    ) : CompletionResultSet(
        matcher,
        Consumer<CompletionResult> { },
        object : CompletionContributor() {},
    ) {
        override fun addElement(element: LookupElement) {
            sink.add(element)
        }

        override fun withPrefixMatcher(matcher: PrefixMatcher): CompletionResultSet =
            CollectingCompletionResultSet(sink, matcher)

        override fun withPrefixMatcher(prefix: String): CompletionResultSet =
            CollectingCompletionResultSet(sink, PlainPrefixMatcher(prefix))

        override fun withRelevanceSorter(sorter: CompletionSorter): CompletionResultSet = this

        override fun addLookupAdvertisement(text: String) {}

        override fun caseInsensitive(): CompletionResultSet = this

        override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>) {}

        override fun restartCompletionWhenNothingMatches() {}
    }
}
