package dev.shamar.adonis.ide

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.editor.Editor

/**
 * Structured Edge directive insertions — argument placeholders + matching `@end`
 * for block openers (e.g. `@each(item in items)` … `@end`).
 */
object EdgeDirectiveSnippets {
    data class Spec(
        val name: String,
        val detail: String,
        /** Lookup presentable hint, e.g. `each(item in items) … @end`. */
        val presentable: String,
        /** True when the insertion should sit after `@!` (self-closing form). */
        val bang: Boolean = false,
        val build: (TemplateManager) -> Template,
    )

    fun specFor(name: String): Spec? = SPECS[name]

    /** Names that expand to a live template (others insert bare `name`). */
    fun hasSnippet(name: String): Boolean = name in SPECS

    fun startTemplate(editor: Editor, spec: Spec) {
        val project = editor.project ?: return
        val manager = TemplateManager.getInstance(project)
        val template = spec.build(manager)
        template.isToReformat = false
        manager.startTemplate(editor, template)
    }

    /**
     * Replace the typed directive prefix (after `@` / `@!`) with a live template.
     * [context] start/end cover the lookup text (e.g. `ea` / `each`), not the `@`.
     */
    fun applyLookup(context: InsertionContext, directiveName: String) {
        applyLookup(context.editor, context.startOffset, context.tailOffset, directiveName)
    }

    fun applyLookup(editor: Editor, start: Int, end: Int, directiveName: String) {
        val baseName = directiveName.removePrefix("!")
        val wantBang = directiveName.startsWith("!")
        val specKey = when {
            wantBang && "!$baseName" in SPECS -> "!$baseName"
            baseName in SPECS -> baseName
            else -> return
        }
        val spec = SPECS[specKey]!!
        val useBang = wantBang || spec.bang

        val document = editor.document
        if (end > start) {
            document.deleteString(start, end)
        }

        var offset = editor.caretModel.offset.coerceAtMost(document.textLength)
        val chars = document.charsSequence
        val afterBang = offset >= 2 && chars[offset - 1] == '!' && chars[offset - 2] == '@'
        val afterAt = offset >= 1 && chars[offset - 1] == '@'

        when {
            !afterAt && !afterBang -> {
                val prefix = if (useBang) "@!" else "@"
                document.insertString(offset, prefix)
                offset += prefix.length
                editor.caretModel.moveToOffset(offset)
            }
            useBang && afterAt && !afterBang -> {
                document.insertString(offset, "!")
                offset += 1
                editor.caretModel.moveToOffset(offset)
            }
            !useBang && afterBang -> {
                document.deleteString(offset - 1, offset)
                offset -= 1
                editor.caretModel.moveToOffset(offset)
            }
        }

        startTemplate(editor, spec)
    }

    /** Preview string used in unit tests / type text. */
    fun preview(name: String): String? = SPECS[name]?.presentable

    private fun block(
        manager: TemplateManager,
        id: String,
        header: (Template) -> Unit,
    ): Template {
        val t = manager.createTemplate("edge_$id", "Edge")
        header(t)
        t.addTextSegment("\n  ")
        t.addEndVariable()
        t.addTextSegment("\n@end")
        return t
    }

    private val SPECS: Map<String, Spec> = linkedMapOf(
        "each" to Spec(
            name = "each",
            detail = "loop",
            presentable = "each(item in items) … @end",
        ) { m ->
            block(m, "each") { t ->
                t.addTextSegment("each(")
                t.addVariable("ITEM", ConstantNode("item"), true)
                t.addTextSegment(" in ")
                t.addVariable("ITEMS", ConstantNode("items"), true)
                t.addTextSegment(")")
            }
        },
        "if" to Spec(
            name = "if",
            detail = "if",
            presentable = "if(condition) … @end",
        ) { m ->
            block(m, "if") { t ->
                t.addTextSegment("if(")
                t.addVariable("CONDITION", ConstantNode("condition"), true)
                t.addTextSegment(")")
            }
        },
        "unless" to Spec(
            name = "unless",
            detail = "unless",
            presentable = "unless(condition) … @end",
        ) { m ->
            block(m, "unless") { t ->
                t.addTextSegment("unless(")
                t.addVariable("CONDITION", ConstantNode("condition"), true)
                t.addTextSegment(")")
            }
        },
        "component" to Spec(
            name = "component",
            detail = "component",
            presentable = "component('name') … @end",
        ) { m ->
            block(m, "component") { t ->
                t.addTextSegment("component('")
                t.addVariable("NAME", ConstantNode("name"), true)
                t.addTextSegment("')")
            }
        },
        "slot" to Spec(
            name = "slot",
            detail = "slot",
            presentable = "slot('name') … @end",
        ) { m ->
            block(m, "slot") { t ->
                t.addTextSegment("slot('")
                t.addVariable("NAME", ConstantNode("name"), true)
                t.addTextSegment("')")
            }
        },
        "section" to Spec(
            name = "section",
            detail = "section",
            presentable = "section('name') … @end",
        ) { m ->
            block(m, "section") { t ->
                t.addTextSegment("section('")
                t.addVariable("NAME", ConstantNode("name"), true)
                t.addTextSegment("')")
            }
        },
        "layout" to Spec(
            name = "layout",
            detail = "layout",
            presentable = "layout('name') … @end",
        ) { m ->
            block(m, "layout") { t ->
                t.addTextSegment("layout('")
                t.addVariable("NAME", ConstantNode("layouts/main"), true)
                t.addTextSegment("')")
            }
        },
        "page" to Spec(
            name = "page",
            detail = "slot",
            presentable = "page() … @end",
        ) { m ->
            block(m, "page") { t ->
                t.addTextSegment("page()")
            }
        },
        "wire" to Spec(
            name = "wire",
            detail = "wire",
            presentable = "wire('name') … @end",
        ) { m ->
            block(m, "wire") { t ->
                t.addTextSegment("wire('")
                t.addVariable("NAME", ConstantNode("counter"), true)
                t.addTextSegment("')")
            }
        },
        "persist" to Spec(
            name = "persist",
            detail = "persist",
            presentable = "persist('key') … @end",
        ) { m ->
            block(m, "persist") { t ->
                t.addTextSegment("persist('")
                t.addVariable("KEY", ConstantNode("key"), true)
                t.addTextSegment("')")
            }
        },
        "pushTo" to Spec(
            name = "pushTo",
            detail = "stack",
            presentable = "pushTo('stack') … @end",
        ) { m ->
            block(m, "pushTo") { t ->
                t.addTextSegment("pushTo('")
                t.addVariable("STACK", ConstantNode("scripts"), true)
                t.addTextSegment("')")
            }
        },
        // Self-closing / one-shot helpers with arg placeholders (no @end).
        "include" to Spec(
            name = "include",
            detail = "include",
            presentable = "include('partial')",
        ) { m ->
            m.createTemplate("edge_include", "Edge").also { t ->
                t.addTextSegment("include('")
                t.addVariable("NAME", ConstantNode("partials/card"), true)
                t.addTextSegment("')")
                t.addEndVariable()
            }
        },
        "svg" to Spec(
            name = "svg",
            detail = "svg",
            presentable = "svg('icon')",
        ) { m ->
            m.createTemplate("edge_svg", "Edge").also { t ->
                t.addTextSegment("svg('")
                t.addVariable("NAME", ConstantNode("lucide:star"), true)
                t.addTextSegment("')")
                t.addEndVariable()
            }
        },
        "vite" to Spec(
            name = "vite",
            detail = "vite",
            presentable = "vite('entry')",
        ) { m ->
            m.createTemplate("edge_vite", "Edge").also { t ->
                t.addTextSegment("vite('")
                t.addVariable("ENTRY", ConstantNode("resources/js/app.js"), true)
                t.addTextSegment("')")
                t.addEndVariable()
            }
        },
        "!component" to Spec(
            name = "!component",
            detail = "component",
            presentable = "!component('name')",
            bang = true,
        ) { m ->
            // Template body has no leading `!` — caret is already after `@!`.
            m.createTemplate("edge_bang_component", "Edge").also { t ->
                t.addTextSegment("component('")
                t.addVariable("NAME", ConstantNode("button"), true)
                t.addTextSegment("')")
                t.addEndVariable()
            }
        },
        "let" to Spec(
            name = "let",
            detail = "assign",
            presentable = "let(name = value)",
        ) { m ->
            m.createTemplate("edge_let", "Edge").also { t ->
                t.addTextSegment("let(")
                t.addVariable("NAME", ConstantNode("name"), true)
                t.addTextSegment(" = ")
                t.addVariable("VALUE", ConstantNode("value"), true)
                t.addTextSegment(")")
                t.addEndVariable()
            }
        },
        "assign" to Spec(
            name = "assign",
            detail = "assign",
            presentable = "assign(name = value)",
        ) { m ->
            m.createTemplate("edge_assign", "Edge").also { t ->
                t.addTextSegment("assign(")
                t.addVariable("NAME", ConstantNode("name"), true)
                t.addTextSegment(" = ")
                t.addVariable("VALUE", ConstantNode("value"), true)
                t.addTextSegment(")")
                t.addEndVariable()
            }
        },
        "elseif" to Spec(
            name = "elseif",
            detail = "else if",
            presentable = "elseif(condition)",
        ) { m ->
            m.createTemplate("edge_elseif", "Edge").also { t ->
                t.addTextSegment("elseif(")
                t.addVariable("CONDITION", ConstantNode("condition"), true)
                t.addTextSegment(")")
                t.addEndVariable()
            }
        },
    )

    /** All snippet-backed directive names (for tests / docs). */
    fun snippetNames(): Set<String> = SPECS.keys
}
