package dev.shamar.adonis.ide

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings → Tools → AdonisJS: column completion source + Database tool DataSource.
 */
class AdonisDbConfigurable(private val project: Project) : Configurable {
    private var panel: JPanel? = null
    private lateinit var sourceCombo: ComboBox<SourceItem>
    private lateinit var dataSourceCombo: ComboBox<DsItem>
    private lateinit var statusLabel: JBLabel
    private lateinit var refreshButton: JButton

    private data class SourceItem(val source: AdonisColumnSource, val label: String) {
        override fun toString(): String = label
    }

    private data class DsItem(val name: String, val uniqueId: String, val label: String) {
        override fun toString(): String = label
    }

    override fun getDisplayName(): String = "AdonisJS"

    override fun createComponent(): JComponent {
        sourceCombo = ComboBox(
            arrayOf(
                SourceItem(
                    AdonisColumnSource.MIGRATIONS,
                    "Migrations / models only",
                ),
                SourceItem(
                    AdonisColumnSource.DATABASE,
                    "Database tool connection only",
                ),
                SourceItem(
                    AdonisColumnSource.BOTH,
                    "Database tool connection + migrations",
                ),
            ),
        )
        dataSourceCombo = ComboBox()
        statusLabel = JBLabel(" ")
        statusLabel.border = JBUI.Borders.emptyTop(8)
        refreshButton = JButton("Refresh schema cache")
        refreshButton.addActionListener {
            AdonisDbColumnCache.getInstance(project).refreshAsync {
                updateStatus()
            }
            statusLabel.text = "Refreshing…"
        }

        reloadDataSources()
        reset()

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("Column completion source:", sourceCombo, 1, false)
            .addLabeledComponent("Database tool DataSource:", dataSourceCombo, 1, false)
            .addComponent(refreshButton)
            .addComponent(statusLabel)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        val hint = JBLabel(
            "<html><body style='width:420px'>" +
                "Pick a connection from the Database tool. Schema must be loaded " +
                "(connect / Refresh in the Database tool) before live columns appear. " +
                "When the Database plugin is disabled, migrations/models are always used." +
                "</body></html>",
        )
        hint.border = JBUI.Borders.emptyTop(12)

        panel = JPanel(BorderLayout()).apply {
            add(form, BorderLayout.NORTH)
            add(hint, BorderLayout.SOUTH)
        }
        updateStatus()
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = AdonisDbSettings.getInstance(project)
        val source = (sourceCombo.selectedItem as? SourceItem)?.source ?: return false
        val ds = dataSourceCombo.selectedItem as? DsItem
        return source != settings.columnSource ||
            (ds?.name ?: "") != settings.dataSourceName ||
            (ds?.uniqueId ?: "") != settings.dataSourceUniqueId
    }

    override fun apply() {
        val settings = AdonisDbSettings.getInstance(project)
        settings.columnSource =
            (sourceCombo.selectedItem as? SourceItem)?.source ?: AdonisColumnSource.BOTH
        val ds = dataSourceCombo.selectedItem as? DsItem
        settings.dataSourceName = ds?.name.orEmpty()
        settings.dataSourceUniqueId = ds?.uniqueId.orEmpty()
        AdonisDbColumnCache.getInstance(project).refreshAsync {
            updateStatus()
        }
    }

    override fun reset() {
        val settings = AdonisDbSettings.getInstance(project)
        for (i in 0 until sourceCombo.itemCount) {
            val item = sourceCombo.getItemAt(i)
            if (item.source == settings.columnSource) {
                sourceCombo.selectedIndex = i
                break
            }
        }
        reloadDataSources()
        var selected = false
        for (i in 0 until dataSourceCombo.itemCount) {
            val item = dataSourceCombo.getItemAt(i)
            if (
                (settings.dataSourceUniqueId.isNotBlank() && item.uniqueId == settings.dataSourceUniqueId) ||
                (settings.dataSourceName.isNotBlank() && item.name == settings.dataSourceName)
            ) {
                dataSourceCombo.selectedIndex = i
                selected = true
                break
            }
        }
        if (!selected && dataSourceCombo.itemCount > 0) {
            dataSourceCombo.selectedIndex = 0
        }
        updateStatus()
    }

    private fun reloadDataSources() {
        if (!::dataSourceCombo.isInitialized) return
        dataSourceCombo.removeAllItems()
        if (!AdonisDbBridge.isDatabasePluginAvailable()) {
            dataSourceCombo.addItem(
                DsItem("", "", "(Database plugin not available)"),
            )
            dataSourceCombo.isEnabled = false
            refreshButton.isEnabled = false
            return
        }
        dataSourceCombo.isEnabled = true
        refreshButton.isEnabled = true
        val sources = AdonisDbBridge.listDataSources(project)
        if (sources.isEmpty()) {
            dataSourceCombo.addItem(
                DsItem("", "", "(No DataSources — add one in the Database tool)"),
            )
            return
        }
        for (ref in sources) {
            val label = if (ref.dbmsLabel.isBlank()) ref.name else "${ref.name} (${ref.dbmsLabel})"
            dataSourceCombo.addItem(DsItem(ref.name, ref.uniqueId, label))
        }
    }

    private fun updateStatus() {
        if (!::statusLabel.isInitialized) return
        val cache = AdonisDbColumnCache.getInstance(project)
        val err = cache.lastErrorMessage()
        val tables = cache.cachedTableCount()
        val refreshed = cache.lastRefreshEpochMs()
        statusLabel.text = when {
            !AdonisDbBridge.isDatabasePluginAvailable() ->
                "Status: Database plugin disabled — using migrations/models only"
            err != null -> "Status: $err"
            refreshed == 0L -> "Status: schema cache not loaded yet (click Refresh)"
            else -> "Status: $tables table(s) cached from Database tool"
        }
    }
}
