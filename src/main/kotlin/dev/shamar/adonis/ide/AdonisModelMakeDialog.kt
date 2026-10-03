package dev.shamar.adonis.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Interactive ``make:model`` dialog — name + companion flags matching Adonis Ace.
 * Thin UI shell; flag formatting lives in [AdonisMakeCatalog.modelAceArgs].
 */
class AdonisModelMakeDialog(
    project: Project,
    initialName: String = "",
) : DialogWrapper(project) {
    private val nameField = JTextField(initialName, 28)
    private val allBox = JCheckBox("All companions (--migration --factory --controller)")
    private val migrationBox = JCheckBox("Migration (--migration)")
    private val factoryBox = JCheckBox("Factory (--factory)")
    private val controllerBox = JCheckBox("Controller (--controller)")

    init {
        title = "New Adonis Model"
        allBox.addActionListener {
            val on = allBox.isSelected
            listOf(migrationBox, factoryBox, controllerBox).forEach {
                it.isSelected = on
                it.isEnabled = !on
            }
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val form = JPanel(BorderLayout(0, 8))
        val nameRow = JPanel(BorderLayout(8, 0))
        nameRow.add(JLabel("Model name:"), BorderLayout.WEST)
        nameRow.add(nameField, BorderLayout.CENTER)
        val boxes = JPanel(GridLayout(0, 1, 0, 2))
        boxes.add(JLabel("Also generate:"))
        boxes.add(allBox)
        boxes.add(migrationBox)
        boxes.add(factoryBox)
        boxes.add(controllerBox)
        form.add(nameRow, BorderLayout.NORTH)
        form.add(boxes, BorderLayout.CENTER)
        return form
    }

    fun modelName(): String = nameField.text.trim()

    fun options(): AdonisMakeCatalog.ModelOptions = AdonisMakeCatalog.ModelOptions(
        all = allBox.isSelected,
        migration = migrationBox.isSelected,
        factory = factoryBox.isSelected,
        controller = controllerBox.isSelected,
    )

    override fun getPreferredFocusedComponent(): JComponent = nameField

    companion object {
        fun prompt(project: Project): Pair<String, AdonisMakeCatalog.ModelOptions>? {
            val dialog = AdonisModelMakeDialog(project)
            if (!dialog.showAndGet()) return null
            val name = dialog.modelName()
            if (name.isEmpty()) return null
            return name to dialog.options()
        }
    }
}
