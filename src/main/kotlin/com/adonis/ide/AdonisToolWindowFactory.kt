package com.adonis.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

/**
 * Adonis Tool Window — index health + searchable symbol browser.
 * UI shell; rows/status from [AdonisToolWindowModel].
 */
class AdonisToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AdonisToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class AdonisToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val status = JBLabel(" ")
    private val filter = JBTextField()
    private val model = SymbolTableModel()
    private val table = JBTable(model)

    init {
        val top = JPanel(BorderLayout(8, 8))
        val rebuild = JButton("Rebuild Index")
        rebuild.addActionListener {
            AdonisProjectService.getInstance(project).rebuild()
            refresh()
        }
        top.add(rebuild, BorderLayout.WEST)
        top.add(status, BorderLayout.CENTER)
        val filterRow = JPanel(BorderLayout(8, 0))
        filterRow.add(JBLabel("Filter:"), BorderLayout.WEST)
        filterRow.add(filter, BorderLayout.CENTER)
        filter.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = refresh()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = refresh()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = refresh()
        })
        val north = JPanel(BorderLayout())
        north.add(top, BorderLayout.NORTH)
        north.add(filterRow, BorderLayout.SOUTH)
        add(north, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        refresh()
    }

    fun refresh() {
        val index = AdonisProjectService.getInstance(project).index()
        val summary = AdonisToolWindowModel.summary(index)
        status.text = AdonisToolWindowModel.statusLine(summary)
        model.rows = AdonisToolWindowModel.symbolRows(index, filter.text)
        model.fireTableDataChanged()
    }

    private class SymbolTableModel : AbstractTableModel() {
        var rows: List<AdonisToolWindowModel.SymbolRow> = emptyList()
        private val cols = arrayOf("Kind", "Name", "Detail")
        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = cols.size
        override fun getColumnName(column: Int): String = cols[column]
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
            when (columnIndex) {
                0 -> rows[rowIndex].kind
                1 -> rows[rowIndex].name
                else -> rows[rowIndex].detail
            }
    }
}
