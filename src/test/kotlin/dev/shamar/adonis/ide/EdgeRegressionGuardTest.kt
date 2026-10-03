package dev.shamar.adonis.ide

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import icons.AdonisIcons
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Hard guards for regressions found in 0.3.0:
 * blank dark icons, EDT indexer, missing @ autopopup, @form → HTML `<form>`.
 *
 * Keep assertions message-first so failures name the regression.
 */
class EdgeRegressionGuardTest : BasePlatformTestCase() {

    fun `test regression blank icons - dark SVGs must be well-formed XML`() {
        val loader = AdonisIcons::class.java.classLoader
        val paths = listOf(
            "icons/adonis.svg",
            "icons/adonis_dark.svg",
            "icons/edge.svg",
            "icons/edge_dark.svg",
            "META-INF/pluginIcon.svg",
            "META-INF/pluginIcon_dark.svg",
        )
        for (path in paths) {
            val bytes = loader.getResourceAsStream(path)?.readBytes()
            assertNotNull("REGRESSION(icons): missing resource $path", bytes)
            assertFalse(
                "REGRESSION(icons): $path contains illegal XML control bytes " +
                    "(blank tool-window / .edge icons in dark UI)",
                hasIllegalXmlControlBytes(bytes!!),
            )
            val doc = parseXml(bytes)
            assertNotNull("REGRESSION(icons): $path is not well-formed XML", doc)
        }
        assertSame(
            "REGRESSION(icons): tool-window icon must reuse AdonisIcons.File",
            AdonisIcons.File,
            AdonisIcons.ToolWindow,
        )
        assertSame(
            "REGRESSION(icons): EdgeFileType must expose Adonis brand icon",
            AdonisIcons.File,
            EdgeFileType.INSTANCE.icon,
        )
    }

    fun `test regression EDT indexer - cold index must defer on dispatch thread`() {
        assertTrue(
            "REGRESSION(edt): EDT + non-test must defer Node indexer",
            AdonisIndexThreading.shouldDeferRebuild(
                isDispatchThread = true,
                isUnitTestMode = false,
                isReadAccessAllowed = false,
            ),
        )
        assertTrue(
            "REGRESSION(edt): ReadAction (refs/highlighting) must defer Node indexer",
            AdonisIndexThreading.shouldDeferRebuild(
                isDispatchThread = false,
                isUnitTestMode = false,
                isReadAccessAllowed = true,
            ),
        )
        assertFalse(
            "REGRESSION(edt): unit tests may rebuild synchronously",
            AdonisIndexThreading.shouldDeferRebuild(
                isDispatchThread = true,
                isUnitTestMode = true,
                isReadAccessAllowed = true,
            ),
        )
        assertFalse(
            "REGRESSION(edt): plain background threads may rebuild synchronously",
            AdonisIndexThreading.shouldDeferRebuild(
                isDispatchThread = false,
                isUnitTestMode = false,
                isReadAccessAllowed = false,
            ),
        )
    }

    fun `test regression EDT indexer - rebuild entrypoints must be async in source`() {
        val toolWindow = readMainSource("AdonisToolWindowFactory.kt")
        assertTrue(
            "REGRESSION(edt): tool-window Rebuild must call rebuildAsync, not blocking rebuild()",
            toolWindow.contains("rebuildAsync"),
        )
        assertFalse(
            "REGRESSION(edt): tool-window button must not call .rebuild() on the EDT",
            Regex("""\.rebuild\s*\(\s*\)""").containsMatchIn(
                toolWindow.substringAfter("rebuild.addActionListener"),
            ),
        )

        val action = readMainSource("RebuildIndexAction.kt")
        assertTrue(
            "REGRESSION(edt): RebuildIndexAction must use Task.Backgroundable",
            action.contains("Task.Backgroundable"),
        )
        assertTrue(
            "REGRESSION(edt): RebuildIndexAction.run must call rebuild() off the EDT",
            action.contains("rebuild()"),
        )
    }

    fun `test regression at autopopup - checkAutoPopup schedules and confidence never skips`() {
        myFixture.configureByText("guard.edge", "")
        val handler = EdgeTypedHandler()
        WriteCommandAction.runWriteCommandAction(project) {
            assertEquals(
                "REGRESSION(@popup): checkAutoPopup('@') must STOP after scheduling " +
                    "(platform contract — Condition runs on up-to-date PSI)",
                TypedHandlerDelegate.Result.STOP,
                handler.checkAutoPopup('@', project, myFixture.editor, myFixture.file),
            )
            myFixture.editor.document.setText("@")
            myFixture.editor.caretModel.moveToOffset(1)
            assertEquals(
                "REGRESSION(@popup): charTyped('@') must CONTINUE after scheduling",
                TypedHandlerDelegate.Result.CONTINUE,
                handler.charTyped('@', project, myFixture.editor, myFixture.file),
            )
            myFixture.editor.document.setText("@f")
            myFixture.editor.caretModel.moveToOffset(2)
            assertEquals(
                TypedHandlerDelegate.Result.CONTINUE,
                handler.charTyped('f', project, myFixture.editor, myFixture.file),
            )
        }
        assertTrue(
            "REGRESSION(@popup): EDGE_FILE condition must accept *.edge",
            EdgeTypedHandler.EDGE_FILE.value(myFixture.file),
        )
        assertEquals(
            "REGRESSION(@popup): CallSiteDetector must see bare '@' as DIRECTIVE",
            SymbolKind.DIRECTIVE,
            CallSiteDetector.detect("@")!!.kind,
        )
        assertEquals(
            SymbolKind.DIRECTIVE,
            CallSiteDetector.detect("@form")!!.kind,
        )

        // HTML confidence must not cancel the popup after `@`.
        myFixture.configureByText("conf.edge", "@")
        myFixture.editor.caretModel.moveToOffset(1)
        val confidence = EdgeCompletionConfidence()
        val skip = confidence.shouldSkipAutopopup(myFixture.file, myFixture.file, 1)
        assertEquals(
            "REGRESSION(@popup): EdgeCompletionConfidence must return NO (never skip) after '@'",
            com.intellij.util.ThreeState.NO,
            skip,
        )

        val typedSrc = readMainSource("EdgeTypedHandler.kt")
        assertTrue(
            "REGRESSION(@popup): checkAutoPopup must scheduleAutoPopup (JetBrains contract)",
            typedSrc.contains("fun checkAutoPopup") &&
                typedSrc.substringAfter("fun checkAutoPopup")
                    .substringBefore("fun charTyped")
                    .contains("scheduleAutoPopup"),
        )
        val xml = readResourceText("META-INF/plugin.xml")
        assertTrue(
            "REGRESSION(@popup): plugin.xml must register EdgeCompletionConfidence",
            xml.contains("EdgeCompletionConfidence"),
        )
    }

    fun `test regression form vs html - bare form is Edge directive and cold catalog includes it`() {
        assertEquals(
            "REGRESSION(@form): bare '@form' must lex as DIRECTIVE (not HTML text)",
            listOf(EdgeTokens.DIRECTIVE),
            tokenize("@form").map { it.first },
        )
        assertTrue(
            "REGRESSION(@form): 'form' must be a builtin tag name",
            "form" in EdgeTagRegistry.BUILTIN,
        )
        assertTrue(
            "REGRESSION(@form): 'form' must be a block opener",
            "form" in EdgeTagRegistry.OPENERS,
        )

        val cold = AdonisCompletionCatalog.directiveCompletions(AdonisIndex.empty())
        assertTrue(
            "REGRESSION(@form): cold index must still offer 'form' (HTML must not win)",
            cold.any { it.first == "form" },
        )
        assertTrue(
            "REGRESSION(@form): cold index must offer '!button'",
            cold.any { it.first == "!button" },
        )

        val lookups = AdonisCompletionContributor.directiveLookups(AdonisIndex.empty(), "form")
        assertTrue(
            "REGRESSION(@form): prefix 'form' must resolve to Edge form lookup",
            lookups.any { it.insertName == "form" && it.hasSnippet },
        )

        val contributorSrc = readMainSource("AdonisCompletionContributor.kt")
        assertTrue(
            "REGRESSION(@form): contributor must stopHere() on DIRECTIVE sites " +
                "so HTML <form> cannot steal",
            contributorSrc.contains("stopHere()"),
        )
        assertTrue(
            "REGRESSION(@form): directive completions must use PrioritizedLookupElement",
            contributorSrc.contains("PrioritizedLookupElement.withPriority"),
        )
    }

    fun `test regression plugin xml still wires icons typed handler and completion`() {
        val xml = readResourceText("META-INF/plugin.xml")
        assertTrue(xml.contains("""icon="/icons/adonis.svg""""))
        assertTrue(xml.contains("EdgeTypedHandler"))
        assertTrue(xml.contains("AdonisCompletionContributor"))
        assertTrue(xml.contains("AdonisToolWindowFactory"))
        assertTrue(xml.contains("""id="Adonis""""))
    }

    private fun tokenize(text: String): List<Pair<com.intellij.psi.tree.IElementType, String>> {
        val lexer = EdgeLexer()
        lexer.start(text)
        val out = ArrayList<Pair<com.intellij.psi.tree.IElementType, String>>()
        while (lexer.tokenType != null) {
            out += lexer.tokenType!! to text.substring(lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
        }
        return out
    }

    private fun hasIllegalXmlControlBytes(bytes: ByteArray): Boolean =
        bytes.any { b ->
            val u = b.toInt() and 0xFF
            u < 32 && u !in setOf(9, 10, 13)
        }

    private fun parseXml(bytes: ByteArray): Document =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    private fun readMainSource(fileName: String): String {
        val roots = listOf(
            "src/main/kotlin/dev/shamar/adonis/ide/$fileName",
            "../src/main/kotlin/dev/shamar/adonis/ide/$fileName",
        )
        for (rel in roots) {
            val f = java.io.File(rel)
            if (f.isFile) return f.readText()
        }
        // Fallback: locate from user.dir (Gradle sets project root).
        val fromRoot = java.io.File(
            System.getProperty("user.dir"),
            "src/main/kotlin/dev/shamar/adonis/ide/$fileName",
        )
        assertTrue("missing source $fileName at ${fromRoot.absolutePath}", fromRoot.isFile)
        return fromRoot.readText()
    }

    private fun readResourceText(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: AdonisIcons::class.java.classLoader.getResourceAsStream(path)
        assertNotNull("missing classpath resource $path", stream)
        return stream!!.bufferedReader().readText()
    }

}
